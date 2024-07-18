package spam.blocker.ui.setting

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Add
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.AddAll
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Clear
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.RemoveAt
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Set
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.databinding.SettingFragmentBinding
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.PatternRule
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RuleTable
import spam.blocker.def.Def
import spam.blocker.ui.util.Algorithm.b64Decode
import spam.blocker.ui.util.Algorithm.compressString
import spam.blocker.ui.util.Algorithm.decompressToString
import spam.blocker.ui.util.FileInChooser
import spam.blocker.ui.util.FileOutChooser
import spam.blocker.ui.util.TimeRangePicker
import spam.blocker.ui.util.UI.Companion.applyTheme
import spam.blocker.ui.util.UI.Companion.setupImageTooltip
import spam.blocker.ui.util.UI.Companion.showIf
import spam.blocker.ui.util.dynamicPopupMenu
import spam.blocker.util.Csv
import spam.blocker.util.Launcher
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.Permissions
import spam.blocker.util.Permissions.Companion.isCallLogPermissionGranted
import spam.blocker.util.Permissions.Companion.isContactsPermissionGranted
import spam.blocker.util.ProtectedPermission
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.Contact
import spam.blocker.util.SharedPref.Dialed
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.OffTime
import spam.blocker.util.SharedPref.RecentApps
import spam.blocker.util.SharedPref.RepeatedCall
import spam.blocker.util.SharedPref.Stir
import spam.blocker.util.Util
import spam.blocker.util.Util.Companion.truncate
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class SettingFragment : Fragment() {

    private var _binding: SettingFragmentBinding? = null
    private val binding get() = _binding!!

    private var recentApps = ObservableArrayList<String>()

    private var numberRules = ObservableArrayList<PatternRule>()
    private var contentRules = ObservableArrayList<PatternRule>()
    private var quickCopyRules = ObservableArrayList<PatternRule>()

    private lateinit var onEnabledChange: () -> Unit

    @SuppressLint("Range", "ClickableViewAccessibility", "NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingFragmentBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val ctx = requireContext()

        // global settings
        setupGlobalEnabled(root)
        setupTheme(root)
        setupLanguage(root)
        setupBackupRestore(root)
        setupAbout(root)

        // sim settings
        setupStir(root)
        setupContacts(root)
        setupRepeatedCall(root)
        setupDialed(root)
        clearUninstalledRecentApps()
        setupRecentApps(root)
        setupBlockType(root)
        setupOffTime(root)

        setupRules( root,
            R.id.recycler_number_filters, R.id.btn_add_number_filter, R.id.btn_test_number_filters,
            numberRules, NumberRuleTable(), Def.ForNumber
        ) { onNumberAddLongPress(root) }

        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_number_filter),
            R.string.help_number_filter
        )

        setupRules( root,
            R.id.recycler_content_filters, R.id.btn_add_content_filter, R.id.btn_test_content_filters,
            contentRules, ContentRuleTable(), Def.ForSms )
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_sms_content_filter),
            R.string.help_sms_content_filter
        )

        setupRules( root,
            R.id.recycler_quick_copy, R.id.btn_add_quick_copy, R.id.btn_test_quick_copy,
            quickCopyRules, QuickCopyRuleTable(), Def.ForQuickCopy )
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_quick_copy),
            R.string.help_quick_copy
        )

        return root
    }

    private val importBlacklistsChooser = FileInChooser(this) // must be initialized during fragment creation

    private fun onNumberAddLongPress(root: View) {
        val ctx = requireContext()
        val addBtn = root.findViewById<MaterialButton>(R.id.btn_add_number_filter)

        val items = resources.getStringArray(R.array.import_csv_type).toList()
        dynamicPopupMenu(ctx, items, addBtn) { clickedIdx ->
            importBlacklistsChooser.load { raw: ByteArray? ->
                if (raw == null)
                    return@load

                val rules = Csv.parseToMaps(raw).map {
                    PatternRule.fromMap(it)
                }

                when (clickedIdx) {
                    0 -> { // import as single rule
                        val joined = rules.map {
                            Util.clearNumber(it.pattern)
                        }.filter {
                            it.isNotEmpty()
                        }.joinToString ( separator = "|" )

                        val wrapped = "($joined)"

                        val rule = PatternRule().apply {
                            pattern = wrapped

                            val formatter = DateTimeFormatter.ofPattern("yy_MM_dd")
                            val ymd = LocalDate.now().format(formatter)

                            description = "$ymd (${rules.size}) ${ctx.getString(R.string.imported)}"
                        }
                        // 1. add to db
                        val table = NumberRuleTable()
                        table.addNewRule(ctx, rule)

                        // 2. refresh gui
                        asyncReloadFromDb(ctx, table, numberRules)
                    }
                    1 -> { // import as multi rules
                        // 1. add to db
                        val table = NumberRuleTable()
                        rules.forEach {
                            table.addNewRule(ctx, it)
                        }

                        // 2. refresh gui
                        asyncReloadFromDb(ctx, table, numberRules)
                    }
                }
            }
        }
    }

    private fun setupGlobalEnabled(root: View) {
        val ctx = requireContext()
        val spf = Global(ctx)

        val switch = root.findViewById<SwitchCompat>(R.id.switch_globally_enabled)
        val group_quick_filters = root.findViewById<RelativeLayout>(R.id.group_quick_filters)
        val group_regex_filters = root.findViewById<RelativeLayout>(R.id.group_regex_filters)
        val img_call = root.findViewById<ImageView>(R.id.enabled_for_call)
        val img_sms = root.findViewById<ImageView>(R.id.enabled_for_sms)

        val green = ctx.getColor(R.color.text_green)
        val red = ctx.getColor(R.color.salmon)
        val teal = ctx.getColor(R.color.teal_200)
        val gray = ctx.getColor(R.color.text_grey)
        val g = Global(ctx)
        fun updateImagesColor() {
            val callEnabled = g.isCallEnabled()
            val hasCallPermission = Permissions.isCallScreeningEnabled(ctx)
            img_call.setColorFilter(if (hasCallPermission && callEnabled) teal else gray, PorterDuff.Mode.SRC_IN)

            val smsEnabled = g.isSmsEnabled()
            val hasSmsPermission = Permissions.isReceiveSmsPermissionGranted(ctx)
            img_sms.setColorFilter(if (hasSmsPermission && smsEnabled) teal else gray, PorterDuff.Mode.SRC_IN)
        }

        onEnabledChange = {
            val enabled = spf.isGloballyEnabled()
            if (switch.isChecked != enabled)
                switch.isChecked = enabled

            // top bar color
            requireActivity().window.statusBarColor = if (enabled) green else red

            showIf(group_quick_filters, enabled)
            showIf(group_regex_filters, enabled)

            showIf(img_call, enabled)
            showIf(img_sms, enabled)
            updateImagesColor()

            if (enabled)
                Util.checkDoubleNotifications(ctx)
        }
        onEnabledChange()
        switch.setOnClickListener {
            spf.toggleGloballyEnabled()
            onEnabledChange()
        }

        updateImagesColor()
        fun popup() {
            PopupEnableConfigFragment { callEnabled: Boolean, smsEnabled: Boolean ->
                g.setCallEnabled(callEnabled)
                g.setSmsEnabled(smsEnabled)
                updateImagesColor()
            }.show(requireActivity().supportFragmentManager, "tag_config_enabled")
        }
        img_call.setOnClickListener { popup() }
        img_sms.setOnClickListener { popup() }

        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_globally_enabled),
            R.string.help_globally_enabled
        )
    }
    private fun setupTheme(root: View) {
        val ctx = requireContext()
        val spf = Global(ctx)
        val btn = root.findViewById<MaterialButton>(R.id.btn_theme)
        val type = spf.getThemeType()
        val items = resources.getStringArray(R.array.theme_list).toList()
        btn.text = items[type]
        btn.setOnClickListener {
            dynamicPopupMenu(ctx, items, btn) { clickedIdx ->
                spf.setThemeType(clickedIdx)
                btn.text = items[clickedIdx]
                applyTheme(clickedIdx)
            }
        }
    }

    private fun setupBackupRestore(root: View) {
        val ctx = requireContext()
        val spin_export = root.findViewById<MaterialButton>(R.id.btn_backup_export)
        val btn_import = root.findViewById<MaterialButton>(R.id.btn_backup_import)


        val exportFileChooser = FileOutChooser(this) // must be initialized during fragment creation
        spin_export.setOnClickListener {
            val menu = ctx.resources.getStringArray(R.array.export_as_list).toList()
            dynamicPopupMenu(ctx, menu, spin_export) { i ->
                val compress = i == 1

                // prepare file name
                val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
                val ymd = LocalDate.now().format(formatter)
                val fn = "SpamBlocker.${ymd}.${if(compress) "gz" else "json"}"

                // prepare file content
                val curr = Configs()
                curr.load(ctx)
                val content = if (compress) {
                    compressString(curr.toJsonString())
                } else {
                    curr.toPrettyJsonString().toByteArray()
                }

                exportFileChooser.create(fn, content)
            }
        }

        // import
        val importChooser = FileInChooser(this) // must be initialized during fragment creation
        btn_import.setOnClickListener {
            importChooser.load{ raw: ByteArray? ->

                if (raw == null)
                    return@load

                val alert = AlertDialog.Builder(ctx)

                fun onDecodeSucc(str: String) {
                    val newCfg = Configs.createFromJson(str)
                    newCfg.apply(ctx)

                    alert.apply {
                        setTitle(" ")
                        setIcon(R.drawable.ic_check_green)
                        setMessage(resources.getString(R.string.imported_successfully))
                        setPositiveButton(R.string.ok) { _, _ ->
                            Launcher.selfRestart(ctx)
                        }
                    }.create().show()
                }
                fun onDecodeFail() {
                    alert.apply {
                        setTitle(" ")
                        setIcon(R.drawable.ic_fail_red)
                        setMessage(resources.getString(R.string.import_fail))
                    }.create().show()
                }

                try {
                    // for history compatibility, text file contains b64(gzip)
                    val jsonStr = decompressToString(b64Decode(String(raw)))
                    onDecodeSucc(jsonStr)
                } catch (_: Exception) {
                    try {
                        // try gzip compressed
                        val jsonStr = decompressToString(raw)
                        onDecodeSucc(jsonStr)
                    } catch (e: Exception) {
                        // try plain json string
                        try {
                            val jsonStr = String(raw)
                            onDecodeSucc(jsonStr)
                        } catch (e: Exception) {
                            onDecodeFail()
                        }
                    }
                }
            }
        }
    }
    private fun setupAbout(root: View) {
        val btn = root.findViewById<MaterialButton>(R.id.btn_about)
        btn.setOnClickListener {
            val repo = "https://github.com/aj3423/SpamBlocker"
            val msg =
                "${resources.getString(R.string.version)}:<br>&emsp;${BuildConfig.VERSION_NAME}<br><br>" +
                        "${resources.getString(R.string.source_code)}:<br>&emsp;<a href=\"$repo\">$repo</a><br><br>"

            val builder = AlertDialog.Builder(context).apply {
                setTitle(resources.getString(R.string.about))
                setMessage(Html.fromHtml(msg, 0))
                setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
            }

            val dlg = builder.create()
            dlg.show()
            // launch browser when the link is clicked
            dlg.findViewById<TextView>(android.R.id.message).movementMethod =
                LinkMovementMethod.getInstance()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupRepeatedCall(root: View) {
        val ctx = requireContext()
        val spf = RepeatedCall(ctx)

        val btn = root.findViewById<MaterialButton>(R.id.btn_config_repeated_call)
        val switch = root.findViewById<SwitchCompat>(R.id.switch_allow_repeated_call)

        fun updateButton() {
            val (times, inXMin) = spf.getConfig()
            val labelMin = resources.getString(R.string.min)
            btn.text = "$times / $inXMin $labelMin"
            showIf(btn, spf.isEnabled() && isCallLogPermissionGranted(ctx))
        }


        updateButton()
        btn.setOnClickListener {
            PopupRepeatedConfigFragment { times: Int, inXMin: Int ->
                spf.setConfig(times, inXMin)
                updateButton()
            }.show(requireActivity().supportFragmentManager, "tag_config_repeated")
        }

        switch.isChecked = spf.isEnabled()
                && isCallLogPermissionGranted(ctx)

        val permChain = PermissionChain(this,
            listOf(
                Permission(Manifest.permission.READ_CALL_LOG),
                Permission(Manifest.permission.READ_SMS, true)
            )
        )

        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isCallLogPermissionGranted(ctx)) { // already granted
                    spf.setEnabled(true)
                } else {
                    permChain.ask { allGranted ->
                        switch.isChecked = allGranted
                        spf.setEnabled(allGranted)
                        updateButton()
                    }
                }
            } else {
                spf.setEnabled(false)
            }
            updateButton()
        }
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_repeated_call),
            R.string.help_repeated_call
        )
    }
    private fun setupDialed(root: View) {
        val ctx = requireContext()
        val spf = Dialed(ctx)

        val btn = root.findViewById<MaterialButton>(R.id.btn_config_dialed)
        val switch = root.findViewById<SwitchCompat>(R.id.switch_enable_dialed)

        fun updateButton() {
            val nDay = spf.getConfig()
            val labelDays =
                resources.getString(if (nDay > 1) R.string.days else R.string.day)
            btn.text = "$nDay $labelDays"
            showIf(btn, spf.isEnabled() && isCallLogPermissionGranted(ctx))
        }

        updateButton()
        btn.setOnClickListener {
            PopupDialedConfigFragment { inXDays: Int ->
                spf.setConfig(inXDays)
                updateButton()
            }.show(requireActivity().supportFragmentManager, "tag_config_dialed")
        }

        switch.isChecked = spf.isEnabled()
                && isCallLogPermissionGranted(ctx)

        val permChain = PermissionChain(this,
            listOf(
                Permission(Manifest.permission.READ_CALL_LOG),
                Permission(Manifest.permission.READ_SMS, true)
            )
        )

        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isCallLogPermissionGranted(ctx)) { // already granted
                    spf.setEnabled(true)
                } else {
                    permChain.ask { allGranted ->
                        switch.isChecked = allGranted
                        spf.setEnabled(allGranted)
                        updateButton()
                    }
                }
            } else {
                spf.setEnabled(false)
            }
            updateButton()
        }
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_dialed),
            R.string.help_dialed
        )
    }

    private fun setupStir(root: View) {
        val ctx = requireContext()
        val spf = Stir(ctx)

        val btnInclusive = root.findViewById<MaterialButton>(R.id.btn_config_stir)
        val switch = root.findViewById<SwitchCompat>(R.id.switch_enable_stir)

        fun updateButton() {
            val isExclusive = spf.isExclusive()
            showIf(btnInclusive, spf.isEnabled())

            val color = if (isExclusive) R.color.salmon else R.color.text_grey
            btnInclusive.setTextColor(resources.getColor(color, null))
            btnInclusive.setStrokeColorResource(color)

            val trailingQuestionMark = if (spf.isIncludeUnverified()) " (?)" else ""
            btnInclusive.text =
                if (isExclusive)
                    "${ctx.resources.getString(R.string.exclusive)}$trailingQuestionMark"
                else
                    "${ctx.resources.getString(R.string.inclusive)}$trailingQuestionMark"
        }

        updateButton()

        btnInclusive.setOnClickListener {
            PopupStirConfigFragment { isExclusive: Boolean, includeUnverified: Boolean ->
                spf.setExclusive(isExclusive)
                spf.setIncludeUnverified(includeUnverified)
                updateButton()
            }.show(requireActivity().supportFragmentManager, "tag_config_stir")
        }

        switch.isChecked = spf.isEnabled()

        switch.setOnCheckedChangeListener { _, isChecked ->
            spf.setEnabled(isChecked)
            updateButton()
        }
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_stir),
            R.string.help_stir
        )
    }

    private fun setupContacts(root: View) {
        val ctx = requireContext()
        val spf = Contact(ctx)

        // maybe it has been turned off in settings
        if (!isContactsPermissionGranted(ctx)) {
            spf.setEnabled(false)
        }

        val btnInclusive = root.findViewById<MaterialButton>(R.id.btn_config_contact)
        val switchContactEnabled = root.findViewById<SwitchCompat>(R.id.switch_permit_contacts)


        fun updateButton() {
            val isExclusive = spf.isExclusive()
            showIf(btnInclusive, spf.isEnabled())

            val color = if (isExclusive) R.color.salmon else R.color.text_grey
            btnInclusive.setTextColor(resources.getColor(color, null))
            btnInclusive.setStrokeColorResource(color)
            btnInclusive.text =
                if (isExclusive) ctx.resources.getString(R.string.exclusive) else ctx.resources.getString(
                    R.string.inclusive
                )
        }

        updateButton()

        btnInclusive.setOnClickListener {
            spf.toggleExclusive()
            updateButton()
        }

        switchContactEnabled.isChecked = spf.isEnabled() && isContactsPermissionGranted(ctx)

        val permChain = PermissionChain(this,
            listOf(
                Permission(Manifest.permission.READ_CONTACTS)
            )
        )

        switchContactEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isContactsPermissionGranted(ctx)) { // already granted
                    spf.setEnabled(true)
                } else {
                    permChain.ask { allGranted ->
                        switchContactEnabled.isChecked = allGranted
                        spf.setEnabled(allGranted)
                        updateButton()
                    }
                }
            } else {
                spf.setEnabled(false)
            }
            updateButton()
        }
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_enable_contacts),
            R.string.help_contact
        )
    }

    // Clear uninstalled apps from the recent apps list
    private fun clearUninstalledRecentApps() {
        val ctx = requireContext()
        val spf = RecentApps(ctx)

        val cleared = spf.getList().filter {
            Util.isPackageInstalled(ctx, it)
        }
        spf.setList(cleared)
    }


    @SuppressLint("NotifyDataSetChanged", "ClickableViewAccessibility")
    private fun setupRecentApps(root: View) {
        val ctx = requireContext()
        val spf = RecentApps(ctx)

        // config
        val btn_config = root.findViewById<MaterialButton>(R.id.btn_config_recent_apps)
        val recyclerAppIcons = root.findViewById<RecyclerView>(R.id.recycler_app_icons)

        // update button and recycler
        fun updateUI() {

            val inXmin = spf.getConfig()
            btn_config.text = "$inXmin ${resources.getString(R.string.min)}"

            val visible = recentApps.size > 0 && Permissions.isUsagePermissionGranted(ctx)
            showIf(btn_config, visible)
            showIf(recyclerAppIcons, visible)
        }
        updateUI()
        btn_config.setOnClickListener {
            PopupRecentAppConfigFragment { inXMin: Int ->
                spf.setConfig(inXMin)
                updateUI()
            }.show(requireActivity().supportFragmentManager, "tag_config_recent_app")
        }


        recentApps.clear()
        recentApps.addAll(spf.getList())

        val permChain = PermissionChain(this,
            listOf(
                ProtectedPermission(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    intentName = Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    prompt = ctx.getString(R.string.prompt_go_to_usage_permission_setting)
                )
            )
        )
        fun popupRecentApps() {
            permChain.ask { allGranted ->
                if (allGranted) {
                    val dialog = PopupAppListFragment(recentApps)
                    dialog.show(requireActivity().supportFragmentManager, "tag_select_apps")
                }
                updateUI()
            }
        }

        val adapterRecentApps = AppListAdapter(ctx, recentApps) { popupRecentApps() }
        recentApps.observe(viewLifecycleOwner) {
            updateUI()
            spf.setList(recentApps.toList())
            when (it.action) {
                Add -> adapterRecentApps.notifyItemInserted(it.actionInt!!)
                AddAll -> adapterRecentApps.notifyDataSetChanged()
                Set -> adapterRecentApps.notifyItemChanged(it.actionInt!!)
                Clear -> adapterRecentApps.notifyDataSetChanged()
                RemoveAt -> adapterRecentApps.notifyItemRemoved(it.actionInt!!)

                else -> {}
            }
        }

        recyclerAppIcons.setAdapter(adapterRecentApps)
        recyclerAppIcons.setLayoutManager(
            LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false))

        val btnSelectApp = root.findViewById<ImageButton>(R.id.btn_select_app)
        btnSelectApp.setOnClickListener {
            popupRecentApps()
        }

        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_recent_apps),
            R.string.help_recent_apps
        )
    }

    private fun setupBlockType(root: View) {
        val ctx = requireContext()
        val spf = BlockType(ctx)

        val btn = root.findViewById<MaterialButton>(R.id.setting_btn_block_type)
        val types = ctx.resources.getStringArray(R.array.block_type_list).toList()
        fun updateButton() {
            btn.text = types[spf.getType()]
        }
        updateButton()

        val permChain = PermissionChain(this,
            listOf(
                Permission(Manifest.permission.READ_PHONE_STATE),
                Permission(Manifest.permission.READ_CALL_LOG),
                Permission(Manifest.permission.ANSWER_PHONE_CALLS)
            )
        )

        btn.setOnClickListener {
            dynamicPopupMenu(ctx, types, btn) { clickedIdx ->
                when(clickedIdx) {
                    Def.BLOCK_TYPE_ANSWER_AND_HANG -> {
                        permChain.ask { allGranted ->
                            if (allGranted) {
                                spf.setType(Def.BLOCK_TYPE_ANSWER_AND_HANG)
                            }
                            updateButton()
                        }
                    }
                    else -> {
                        spf.setType(clickedIdx)
                        updateButton()
                    }
                }
            }
        }
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_block_type), R.string.help_block_type
        )
    }

    private fun setupLanguage(root: View) {
        val ctx = requireContext()
        val spf = Global(ctx)
        val btn = root.findViewById<MaterialButton>(R.id.btn_language)

        val lang = spf.getLanguage()
        btn.text = lang

        val languages = ctx.resources.getStringArray(R.array.language_list).toList()

        btn.setOnClickListener {
            dynamicPopupMenu(ctx, languages, btn) { clickedIdx ->
                val newLang = languages[clickedIdx]
                if (newLang != lang) {
                    spf.setLanguage(newLang)
                    Launcher.selfRestart(ctx)
                }
            }
        }

        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_language),
            R.string.help_language
        )
    }

    private fun setupOffTime(root: View) {
        val ctx = requireContext()
        val spf = OffTime(ctx)

        val switchEnabled = root.findViewById<SwitchCompat>(R.id.switch_enable_off_time)
        val btn = root.findViewById<MaterialButton>(R.id.btn_off_time)

        switchEnabled.isChecked = spf.isEnabled()

        fun updateButton() {
            val (sHour, sMin) = spf.getStart()
            val (eHour, eMin) = spf.getEnd()

            btn.text = Util.formatTimeRange(sHour, sMin, eHour, eMin)
            showIf(btn, spf.isEnabled())
        }
        updateButton()
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            spf.setEnabled(isChecked)
            updateButton()
        }
        btn.setOnClickListener {
            val (startH, startM) = spf.getStart()
            val (endH, endM) = spf.getEnd()

            TimeRangePicker(this, startH, startM, endH, endM) { stH, stM, etH, etM ->
                spf.setStart(stH, stM)
                spf.setEnd(etH, etM)
                updateButton()
            }.show()
        }
        setupImageTooltip(
            ctx, viewLifecycleOwner, root.findViewById(R.id.setting_help_off_time),
            R.string.help_off_time
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun asyncReloadFromDb(ctx: Context, table: RuleTable, filters: ObservableArrayList<PatternRule>) {
        GlobalScope.launch(Dispatchers.IO) {
            val records = table.listAll(ctx)
            withContext(Dispatchers.Main) {
                filters.clear()
                filters.addAll(records)
            }
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    private fun setupRules(
        root: View,
        recyclerId: Int, addBtnId: Int, testBtnId: Int,
        filters: ObservableArrayList<PatternRule>,
        dbTable: RuleTable,
        forRuleType: Int,
        onAddLongClick: (()->Unit)? = null
    ) {
        val ctx = requireContext()

        val recycler = root.findViewById<RecyclerView>(recyclerId)
        val layoutManager = LinearLayoutManager(ctx, RecyclerView.VERTICAL, false)
        recycler.setLayoutManager(layoutManager)

        asyncReloadFromDb(ctx, dbTable, filters)

        val onItemClick = { clickedF: PatternRule -> Unit
            val i = filters.indexOf(clickedF)

            val dialog = PopupEditRuleFragment(
                filters[i], { newRule -> // on save button clicked
                    // 1. update this filter in memory
                    val existing = filters[i]
                    existing.pattern = newRule.pattern
                    existing.patternExtra = newRule.patternExtra
                    existing.description = newRule.description
                    existing.flags = newRule.flags
                    existing.isBlacklist = newRule.isBlacklist
                    existing.schedule = newRule.schedule

                    // 2. update in db
                    dbTable.updateRuleById(ctx, existing.id, existing)

                    // 3. gui update
                    asyncReloadFromDb(ctx, dbTable, filters)
                }, forRuleType
            )

            dialog.show(requireActivity().supportFragmentManager, "tag_edit_filter")
        }
        val onItemLongClick = { clickedF: PatternRule ->
            val i = filters.indexOf(clickedF)
            val viewHolder = recycler.findViewHolderForAdapterPosition(i);
            val popup = PopupMenu(ctx, viewHolder?.itemView)
            popup.menuInflater.inflate(R.menu.rule_context_menu, popup.menu)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.rule_clone -> {
                        // 1. add to db
                        dbTable.addNewRule(ctx, clickedF)

                        // 2. refresh gui
                        asyncReloadFromDb(ctx, dbTable, filters)
                    }
                }
                false
            }
            popup.show()
        }

        val adapter = RuleAdapter(ctx, onItemClick, onItemLongClick, filters, forRuleType)
        recycler.setAdapter(adapter)

        // limit recycler height when it exceeds 100 items, otherwise it may crash by OOM.
        fun adjustRecyclerHeight() {
            val params = recycler.layoutParams as ViewGroup.LayoutParams
            if (filters.size > 100) {
                var h = (Util.getScreenHeight(ctx) * 0.7).toInt() // height == 70% of screen height
                if (h<=0 || h > 1000)
                    h = 1000
                params.height = h
                recycler.layoutParams = params

                recycler.isNestedScrollingEnabled = true
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                recycler.layoutParams = params

                recycler.isNestedScrollingEnabled = false
            }
        }

        filters.observe(viewLifecycleOwner) {

            adjustRecyclerHeight()

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
            val defaultRule = PatternRule().apply {
                if (forRuleType == Def.ForQuickCopy) { // set it for copying sms content by default
                    flags.set(Def.FLAG_FOR_CALL, false)
                    flags.set(Def.FLAG_FOR_PASSED, true)
                    flags.set(Def.FLAG_FOR_CONTENT, true)
                }
            }
            val dialog = PopupEditRuleFragment(
                defaultRule, { newF -> // callback
                    // 1. add to db
                    dbTable.addNewRule(ctx, newF)

                    // 2. refresh gui
                    asyncReloadFromDb(ctx, dbTable, filters)
                }, forRuleType
            )

            dialog.show(requireActivity().supportFragmentManager, "tag_edit_filter")
        }
        if (onAddLongClick != null) {
            btnAdd.setOnLongClickListener {
                onAddLongClick()
                true
            }
        }
        val btnTest = root.findViewById<MaterialButton>(testBtnId)
        btnTest.setOnClickListener {
            PopupTestFragment(forRuleType)
                .show(requireActivity().supportFragmentManager, "tag_test")
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
                dbTable.delById(ctx, fToDel.id)

                // 2. remove from ArrayList
                filters.removeAt(i)

                // 3. show snackbar
                Snackbar.make(recycler, truncate(fToDel.pattern), Snackbar.LENGTH_LONG).setAction(
                    resources.getString(R.string.undelete),
                ) {
                    dbTable.addRuleWithId(ctx, fToDel)
                    filters.add(i, fToDel)
                }.show()
            }
        }
        ItemTouchHelper(swipeLeftCallback).attachToRecyclerView(recycler)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        numberRules.removeObservers(viewLifecycleOwner)
        recentApps.removeObservers(viewLifecycleOwner)

    }

    // Synchronize the UI on Tile clicking
    private lateinit var broadcastReceiver: BroadcastReceiver

    override fun onResume() {
        super.onResume()

        onEnabledChange()

        // handle tile click event, restart app
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action == Def.ACTION_TILE_TOGGLE) {
                    onEnabledChange()
                }
            }
        }

        val lbm = LocalBroadcastManager.getInstance(requireContext())
        lbm.registerReceiver(broadcastReceiver, IntentFilter(Def.ACTION_TILE_TOGGLE))

        Util.checkDoubleNotifications(requireContext())
    }

    override fun onPause() {
        super.onPause()

        val lbm = LocalBroadcastManager.getInstance(requireContext())
        lbm.unregisterReceiver(broadcastReceiver)
    }
}