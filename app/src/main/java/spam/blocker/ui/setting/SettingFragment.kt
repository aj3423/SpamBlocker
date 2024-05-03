package spam.blocker.ui.setting

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import spam.blocker.R
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
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

        setupContacts(root)

        setupRepeatedCall(root)

        setupRecentApps(root)

        setupFilter(
            root,
            R.id.recycler_number_filters,
            R.id.btn_add_number_filter,
            R.id.btn_test_number_filters,
            numberFilters,
            NumberFilterTable(),
            false
        )
        setupFilter(
            root,
            R.id.recycler_content_filters,
            R.id.btn_add_content_filter,
            R.id.btn_test_content_filters,
            contentFilters,
            ContentFilterTable(),
            true
        )

        // tooltips
        setupTooltips(root)

        return root
    }

    @SuppressLint("SetTextI18n")
    private fun setupRepeatedCall(root: View) {
        val spf = SharedPref(requireContext())
        val btn_config = root.findViewById<MaterialButton>(R.id.btn_config_repeated_call)
        val switchAllowRepeated = root.findViewById<SwitchCompat>(R.id.switch_allow_repeated_call)

        fun updateButton() {
            val cfg = spf.getRepeatedConfig()
            val labelTimes =
                resources.getString(if (cfg.first == 1) R.string.time else R.string.times)
            val labelMin = resources.getString(R.string.min)
            btn_config.text = "${cfg.first} $labelTimes / ${cfg.second} $labelMin"
            btn_config.visibility = if (switchAllowRepeated.isChecked) View.VISIBLE else View.GONE
        }

        switchAllowRepeated.isChecked = spf.isRepeatedAllowed()
        switchAllowRepeated.setOnClickListener {
            spf.setAllowRepeated(!spf.isRepeatedAllowed())
            updateButton()
        }

        updateButton()
        btn_config.setOnClickListener {
            PopupRepeatedConfigFragment { times: Int, inXMin: Int ->
                spf.setRepeatedConfig(times, inXMin)
                updateButton()
            }.show(requireActivity().supportFragmentManager, "tag_config_repeated")
        }
    }


    private fun setupContacts(root: View) {
        val ctx = requireContext()
        val spf = SharedPref(ctx)

        // maybe it has been turned off in settings
        if (!isContactsPermissionGranted(ctx)) {
            spf.setContactEnabled(false)
        }

        val btnInclusive = root.findViewById<MaterialButton>(R.id.btn_config_contact)
        val switchContactEnabled = root.findViewById<SwitchCompat>(R.id.switch_permit_contacts)


        fun updateButton() {
            val isExclusive = spf.isContactExclusive()
            btnInclusive.visibility = if (spf.isContactEnabled()) View.VISIBLE else View.GONE

            val color = if (isExclusive) R.color.salmon else R.color.hint_grey
            btnInclusive.setTextColor(resources.getColor(color, null))
            btnInclusive.setStrokeColorResource(color)
            btnInclusive.text = if (isExclusive) ctx.resources.getString(R.string.exclusive) else ctx.resources.getString(R.string.inclusive)
        }

        updateButton()

        btnInclusive.setOnClickListener {
            spf.toggleContactExclusive()
            updateButton()
        }
        switchContactEnabled.isChecked = spf.isContactEnabled()
        val launcherPermitContact = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            switchContactEnabled.isChecked = isGranted
            spf.setContactEnabled(isGranted)
        }
        switchContactEnabled.setOnClickListener {
            val newState = !spf.isContactEnabled()

            if (newState) {
                if (isContactsPermissionGranted(ctx)) { // already granted
                    spf.setContactEnabled(true)
                } else {
                    launcherPermitContact.launch(Manifest.permission.READ_CONTACTS)
                }
            } else {
                spf.setContactEnabled(false)
            }
            updateButton()
        }
    }

    @SuppressLint("NotifyDataSetChanged", "ClickableViewAccessibility")
    private fun setupRecentApps(root: View) {
        val ctx = requireContext()
        val spf = SharedPref(ctx)

        // config
        val btn_config = root.findViewById<MaterialButton>(R.id.btn_config_recent_apps)

        fun updateButton() {
            val inXmin = spf.getRecentAppConfig()
            btn_config.text = "${inXmin} ${resources.getString(R.string.min)}"
            btn_config.visibility = if (recentApps.size > 0) View.VISIBLE else View.GONE
        }
        updateButton()
        btn_config.setOnClickListener {
            PopupRecentAppConfigFragment { inXMin: Int ->
                spf.setRecentAppConfig(inXMin)
                updateButton()
            }.show(requireActivity().supportFragmentManager, "tag_config_recent_app")
        }


        recentApps.clear()
        recentApps.addAll(spf.getRecentAppList())
        val adapterRecentApps = AppListAdapter(ctx, recentApps)
        recentApps.observe(viewLifecycleOwner) {
//            Log.d(Def.TAG, "recentApps action in SettingFragment: " + it.action.toString())

            updateButton()
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


        // recycler
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
        recyclerId: Int, addBtnId: Int, testBtnId: Int,
        filters: ObservableArrayList<PatternFilter>,
        dbTable: PatternTable,
        forSms: Boolean
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
                }, forSms
            )

            dialog.show(requireActivity().supportFragmentManager, "tag_edit_filter")
        }, filters, forSms)
        recycler.setAdapter(adapter)

        filters.observe(viewLifecycleOwner) {
//            Log.d(Def.TAG, "action in SettingFragment: " + it.action.toString())

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
                }, forSms
            )

            dialog.show(requireActivity().supportFragmentManager, "tag_edit_filter")
        }
        val btnTest = root.findViewById<MaterialButton>(testBtnId)
        btnTest.setOnClickListener {
            PopupTestFragment(forSms)
                .show(requireActivity().supportFragmentManager, "tag_test")        }

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
        Util.setupImageTooltip(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_globally_enabled),
            R.string.help_globally_enabled
        )
        Util.setupImageTooltip(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_enable_contacts),
            R.string.help_contact
        )
        Util.setupImageTooltip(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_repeated_call),
            R.string.help_repeated_call
        )
        Util.setupImageTooltip(ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_recent_apps), R.string.help_recent_apps)
        Util.setupImageTooltip(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_number_filter),
            R.string.help_number_filter
        )
        Util.setupImageTooltip(
            ctx,
            viewLifecycleOwner,
            root.findViewById(R.id.setting_help_sms_content_filter),
            R.string.help_sms_content_filter
        )
    }
}