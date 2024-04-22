package spam.blocker.ui.setting

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import spam.blocker.R
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Add
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.AddAll
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Clear
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Set
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.RemoveAt
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.databinding.SettingFragmentBinding
import spam.blocker.db.ContentFilterTable
import spam.blocker.db.PatternFilter
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.PatternTable
import spam.blocker.def.Def
import spam.blocker.util.Permission
import spam.blocker.util.Permission.Companion.isContactsPermissionGranted
import spam.blocker.util.SharedPref
import spam.blocker.util.Util

class SettingFragment : Fragment() {

    private var _binding: SettingFragmentBinding? = null
    private val binding get() = _binding!!

    private var recentApps = ObservableArrayList<String>()

    private var numberFilters = ObservableArrayList<PatternFilter>()
    private var contentFilters = ObservableArrayList<PatternFilter>()

    @SuppressLint("Range", "ClickableViewAccessibility", "NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingFragmentBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val ctx = requireContext()
        val spf = SharedPref(ctx)

        // global enable switch
        val switchGlobalEnable = root.findViewById<SwitchCompat>(R.id.switch_globally_enabled)
        switchGlobalEnable.isChecked = spf.isGloballyEnabled()
        switchGlobalEnable.setOnClickListener {
            spf.toggleGloballyEnabled()
            requireActivity().window.statusBarColor = ContextCompat.getColor(
                ctx,
                if (spf.isGloballyEnabled()) R.color.dark_sea_green else R.color.salmon
            )
        }
        // theme switch
        val switchTheme = root.findViewById<SwitchCompat>(R.id.switch_theme)
        val dark = spf.isDarkTheme()
        switchTheme.isChecked = dark
        switchTheme.setOnClickListener {
            spf.toggleDarkTheme()
            Util.applyTheme(spf.isDarkTheme())
        }

        setupAllowContacts(root)

        setupAllowRepeated(root)

        setupRecentApps(root)

        setupFilter(
            root,
            R.id.recycler_number_filters, R.id.btn_add_number_filter, R.id.btn_clear_number_filters,
            numberFilters, NumberFilterTable(), false
        )
        setupFilter(
            root,
            R.id.recycler_content_filters,
            R.id.btn_add_content_filter,
            R.id.btn_clear_content_filters,
            contentFilters,
            ContentFilterTable(),
            true
        )

        // tooltips
        setupTooltips(root)

        return root
    }

    private fun setupAllowRepeated(root: View) {
        val spf = SharedPref(requireContext())
        val switchAllowRepeated = root.findViewById<SwitchCompat>(R.id.switch_allow_repeated_call)
        switchAllowRepeated.isChecked = spf.isRepeatedAllowed()
        switchAllowRepeated.setOnClickListener {
            spf.setAllowRepeated(!spf.isRepeatedAllowed())
        }
    }

    private fun setupAllowContacts(root: View) {
        val ctx = requireContext()
        val spf = SharedPref(ctx)

        // maybe it has been turned off in settings
        if (!isContactsPermissionGranted(ctx)) {
            spf.setAllowContacts(false)
        }

        val switchPermitContact = root.findViewById<SwitchCompat>(R.id.switch_permit_contacts)
        val spfPermitted = spf.isContactsAllowed()
        switchPermitContact.isChecked = spfPermitted
        val launcherPermitContact = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            switchPermitContact.isChecked = isGranted
            spf.setAllowContacts(isGranted)
        }
        switchPermitContact.setOnClickListener {
            Log.d(Def.TAG, "clicked.. currently permitted: $spfPermitted")

            val newState = !spf.isContactsAllowed()

            if (newState) {
                if (isContactsPermissionGranted(ctx)) { // already granted
                    spf.setAllowContacts(true)
                } else {
                    launcherPermitContact.launch(Manifest.permission.READ_CONTACTS)
                }
            } else {
                spf.setAllowContacts(false)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged", "ClickableViewAccessibility")
    private fun setupRecentApps(root: View) {
        val ctx = requireContext()
        val spf = SharedPref(ctx)

        recentApps.clear()
        recentApps.addAll(spf.getRecentAppList())
        val adapterRecentApps = AppListAdapter(ctx, recentApps)
        recentApps.observe(viewLifecycleOwner) {
            Log.d(Def.TAG, "recentApps action in SettingFragment: " + it.action.toString())

            spf.setRecentAppList(recentApps.toList())
            when (it.action) {
                Add -> adapterRecentApps.notifyItemInserted(it.actionInt!!)
                AddAll -> adapterRecentApps.notifyDataSetChanged()
                Set -> adapterRecentApps.notifyItemChanged(it.actionInt!!)
                Clear -> adapterRecentApps.notifyDataSetChanged()
                RemoveAt -> adapterRecentApps.notifyItemRemoved(it.actionInt!!)

                else -> {}
            }
        }

        fun popupRecentApps() {
            if (!Permission.isUsagePermissionGranted(ctx)) {
                AlertDialog.Builder(ctx)
                    .setMessage(ctx.resources.getString(R.string.prompt_go_to_usage_permission_setting))
                    .setPositiveButton(ctx.resources.getString(R.string.ok)) { dialog, _ ->
                        dialog.dismiss()
                        Permission.goToUsagePermissionSetting(ctx)
                    }
                    .setNegativeButton(ctx.resources.getString(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                return
            }
            val dialog = PopupAppListFragment(recentApps)
            dialog.show(requireActivity().supportFragmentManager, "tag_select_apps")
        }

        val recyclerAppIcons = root.findViewById<RecyclerView>(R.id.recycler_app_icons)
        recyclerAppIcons.setAdapter(adapterRecentApps)

        val layoutManagerApps = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
        recyclerAppIcons.setLayoutManager(layoutManagerApps)
        recyclerAppIcons.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    popupRecentApps()
                }
            }
            true
        }

        val btnSelectApp = root.findViewById<ImageButton>(R.id.btn_select_app)
        btnSelectApp.setOnClickListener {
            popupRecentApps()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("NotifyDataSetChanged")
    private fun setupFilter(
        root: View,
        recyclerId: Int, addBtnId: Int, clearBtnId: Int,
        filters: ObservableArrayList<PatternFilter>,
        dbTable: PatternTable,
        smsOnly: Boolean
    ) {
        val ctx = requireContext()

        val recycler = root.findViewById<RecyclerView>(recyclerId)
        val layoutManager = LinearLayoutManager(ctx, RecyclerView.VERTICAL, false)
        recycler.setLayoutManager(layoutManager)

        fun asyncReloadFromDb() {
            GlobalScope.launch(Dispatchers.IO) {
                val records = dbTable.listAll(ctx)
                withContext(Dispatchers.Main) {
                    filters.clear()
                    filters.addAll(records)
                }
            }
        }
        asyncReloadFromDb()

        val adapter = PatternAdapter(ctx, { clickedF -> // onItemClickCallback
            val i = filters.indexOf(clickedF)

            val dialog = PopupEditFilterFragment(
                filters[i], { newF -> // on save button clicked
                    // 1. update this filter in memory
                    val existing = filters[i]
                    existing.pattern = newF.pattern
                    existing.patternExtra = newF.patternExtra
                    existing.description = newF.description
                    existing.flagCallSms = newF.flagCallSms
                    existing.isBlacklist = newF.isBlacklist

                    // 2. update in db
                    dbTable.updatePatternFilter(ctx, existing.id, existing)

                    // 3. gui update
                    asyncReloadFromDb()
                }, smsOnly)

            dialog.show(requireActivity().supportFragmentManager, "tag_edit_filter")
        }, filters, smsOnly)
        recycler.setAdapter(adapter)

        filters.observe(viewLifecycleOwner) {
            Log.d(Def.TAG, "action in SettingFragment: " + it.action.toString())

            when (it.action) {
                Add -> adapter.notifyItemInserted(it.actionInt!!)
                AddAll -> adapter.notifyDataSetChanged()
                Set -> adapter.notifyItemChanged(it.actionInt!!)
                Clear -> adapter.notifyDataSetChanged()
                RemoveAt -> adapter.notifyItemRemoved(it.actionInt!!)
                else -> {}
            }
        }

        val btnAdd = root.findViewById<MaterialButton>(addBtnId)
        btnAdd.setOnClickListener {
            val dialog = PopupEditFilterFragment(
                PatternFilter(), { newF -> // callback
                    // 1. add to db
                    dbTable.addNewPatternFilter(ctx, newF)

                    // 2. refresh gui
                    asyncReloadFromDb()
                }, smsOnly
            )

            dialog.show(requireActivity().supportFragmentManager, "tag_edit_filter")
        }
        val btnDel = root.findViewById<MaterialButton>(clearBtnId)
        btnDel.setOnClickListener {
            val builder = AlertDialog.Builder(context)
            builder.setMessage(resources.getString(R.string.confirm_del_all_filters))
                .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
                    // 1. add to db and get new ID
                    dbTable.delAllPatternFilters(ctx)

                    // 2. clear filter list
                    filters.clear()
                }
                .show()
        }

        val swipeLeftCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView,
                h: RecyclerView.ViewHolder,
                t: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val i = viewHolder.absoluteAdapterPosition
                val fToDel = filters[i]

                // 1. delete from db
                dbTable.delPatternFilter(ctx, fToDel.id)

                // 2. remove from ArrayList
                filters.removeAt(i)

                // 3. show snackbar
                Snackbar.make(recycler, fToDel.pattern, Snackbar.LENGTH_LONG).setAction(
                    resources.getString(R.string.undelete),
                ) {
                    dbTable.addPatternFilterWithId(ctx, fToDel)
                    filters.add(i, fToDel)
                }.show()
            }
        }
        ItemTouchHelper(swipeLeftCallback).attachToRecyclerView(recycler)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        numberFilters.removeObservers(viewLifecycleOwner)
        recentApps.removeObservers(viewLifecycleOwner)
    }

    private fun setupTooltips(root: View) {
        val ctx = requireContext()
        Util.setupImgHint(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_globally_enabled)
        )
        Util.setupImgHint(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_permit_contacts)
        )
        Util.setupImgHint(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_repeated_call)
        )
        Util.setupImgHint(ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_recent_apps))
        Util.setupImgHint(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_number_filter)
        )
        Util.setupImgHint(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_sms_content_filter)
        )
    }
}