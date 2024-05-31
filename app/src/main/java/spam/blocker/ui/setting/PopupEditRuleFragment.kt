package spam.blocker.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.dpro.widgets.WeekdaysPicker
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import spam.blocker.R
import spam.blocker.db.PatternRule
import spam.blocker.def.Def
import spam.blocker.ui.util.MaterialInputRegexFlagsUtil
import spam.blocker.ui.util.TimeRangePicker
import spam.blocker.ui.util.UI.Companion.setupImageTooltip
import spam.blocker.ui.util.UI.Companion.showIf
import spam.blocker.util.Flag
import spam.blocker.util.Schedule
import spam.blocker.util.Util


class PopupEditRuleFragment(
    private val initFilter: PatternRule,
    private val handleSave: (PatternRule) -> Unit,
    private val forType: Int
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_edit_rule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val res = ctx.resources

        // widgets
        val container_pattern = view.findViewById<TextInputLayout>(R.id.container_pattern)
        val edit_pattern = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern)
        val row_particular = view.findViewById<LinearLayout>(R.id.row_sms_particular_number)
        val switch_for_particular_number = view.findViewById<SwitchCompat>(R.id.switch_particular_number)
        val container_pattern_particular = view.findViewById<TextInputLayout>(R.id.container_pattern_phone)
        val edit_pattern_particular = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern_phone)
        val edit_desc = view.findViewById<TextInputEditText>(R.id.popup_edit_desc)
        val help_apply_to = view.findViewById<ImageView>(R.id.popup_help_apply_to)
        val chk_for_call = view.findViewById<CheckBox>(R.id.popup_chk_call)
        val chk_for_sms = view.findViewById<CheckBox>(R.id.popup_chk_sms)
        val radio_blackwhitelist = view.findViewById<RadioGroup>(R.id.popup_radio_blackwhitelist)
        val radio_whitelist = view.findViewById<RadioButton>(R.id.popup_radio_whitelist)
        val radio_blacklist = view.findViewById<RadioButton>(R.id.popup_radio_blacklist)
        val edit_priority = view.findViewById<TextInputEditText>(R.id.edit_priority)
        val row_type = view.findViewById<LinearLayout>(R.id.row_rule_type)
        val row_importance = view.findViewById<LinearLayout>(R.id.row_importance)
        val row_block_type = view.findViewById<LinearLayout>(R.id.row_block_type)
        val spin_block_type = view.findViewById<Spinner>(R.id.spin_block_type)
        val help_importance = view.findViewById<ImageView>(R.id.popup_help_importance)
        val spin_importance = view.findViewById<Spinner>(R.id.spin_importance)
        val btn_schedule = view.findViewById<MaterialButton>(R.id.popup_btn_schedule)
        val switch_schedule = view.findViewById<SwitchCompat>(R.id.switch_schedule)
        val picker_weekday = view.findViewById<WeekdaysPicker>(R.id.picker_weekdays)
        val row_weekday = view.findViewById<LinearLayout>(R.id.row_weekdays)
        var row_schedule = view.findViewById<LinearLayout>(R.id.row_schedule)

        val btn_save = view.findViewById<MaterialButton>(R.id.popup_btn_save_filter)


        val init = initFilter
        // make a clone, so the flags of init won't be modified on the fly
        val initPatternFlags = Flag(init.patternFlags.value)
        val initPatternExtraFlags = Flag(init.patternExtraFlags.value)

        if (forType == Def.ForQuickCopy)
            init.isBlacklist = false

        MaterialInputRegexFlagsUtil.attach(ctx, viewLifecycleOwner, container_pattern, edit_pattern, initPatternFlags)

        container_pattern.hint = res.getString(when(forType) {
            Def.ForNumber -> R.string.number_pattern
            Def.ForSms -> R.string.sms_content_pattern
            else -> R.string.quick_copy
        })

        fun showHelpOnError(container: TextInputLayout, edit: TextInputEditText) {
            edit.addTextChangedListener {// validate regex
                container.helperText = Util.validateRegex(ctx, it.toString())
            }
        }

        showHelpOnError(container_pattern, edit_pattern)
        edit_pattern.setText(init.pattern)

        if (forType == Def.ForSms) {
            showHelpOnError(container_pattern_particular, edit_pattern_particular)
            setupImageTooltip(ctx, viewLifecycleOwner, view.findViewById(R.id.popup_help_particular_number), R.string.help_for_particular_number)
            MaterialInputRegexFlagsUtil.attach(ctx, viewLifecycleOwner, container_pattern_particular, edit_pattern_particular, initPatternExtraFlags)
            switch_for_particular_number.setOnClickListener{
                val checked = switch_for_particular_number.isChecked
                showIf(container_pattern_particular, checked)
            }
            if (init.patternExtra != "") {
                switch_for_particular_number.isChecked = true

                edit_pattern_particular.setText(init.patternExtra)
            }
        }
        showIf(row_particular, forType == Def.ForSms)
        showIf(row_type, forType != Def.ForQuickCopy)
        showIf(container_pattern_particular, forType == Def.ForSms && init.patternExtra != "")

        edit_desc.setText(init.description)
        edit_priority.setText(init.priority.toString())

        // ForCall / ForSMS
        setupImageTooltip(ctx, viewLifecycleOwner, help_apply_to, R.string.help_apply_to)
        chk_for_call.isChecked = init.isForCall()
        showIf(chk_for_call, forType == Def.ForNumber, true)
        chk_for_sms.isChecked = init.isForSms()

        // Whitelist / Blacklist
        if (init.isWhitelist()) {
            radio_blackwhitelist.check(R.id.popup_radio_whitelist)
        } else if (init.isBlacklist) {
            radio_blackwhitelist.check(R.id.popup_radio_blacklist)
        }
        radio_blackwhitelist.setOnCheckedChangeListener { _, checkedId ->
            showIf(row_importance, checkedId == R.id.popup_radio_blacklist)
            showIf(row_block_type, checkedId == R.id.popup_radio_blacklist)
        }
        // Block type
        showIf(row_block_type, init.isBlacklist && forType == Def.ForNumber)
        spin_block_type.setSelection(init.blockType)

        // Importance
        showIf(row_importance, init.isBlacklist && forType != Def.ForQuickCopy)
        setupImageTooltip(ctx, viewLifecycleOwner, help_importance, R.string.help_importance)
        spin_importance.setSelection(init.importance)

        // Schedule
        showIf(row_schedule, forType != Def.ForQuickCopy)
        // Any gui change should apply to this variable, which will be saved later
        val schedule = Schedule.parseFromStr(init.schedule)

        fun updateScheduleButton() {
            btn_schedule.text = schedule.timeRangeDisplayStr(ctx)
        }
        updateScheduleButton()
        btn_schedule.setOnClickListener {
           TimeRangePicker(this,
                schedule.startHour, schedule.startMin, schedule.endHour, schedule.endMin)
           { stH, stM, etH, etM ->
               schedule.startHour = stH
               schedule.startMin = stM
               schedule.endHour = etH
               schedule.endMin = etM

               updateScheduleButton()
           }.show()
        }

        fun onScheduleEnabledChange() {
            showIf(btn_schedule, schedule.enabled)
            showIf(row_weekday, schedule.enabled)
        }
        onScheduleEnabledChange()
        switch_schedule.isChecked = schedule.enabled
        switch_schedule.setOnCheckedChangeListener { _, isChecked ->
            schedule.enabled = isChecked
            onScheduleEnabledChange()
        }
        // Weekday pickers
        picker_weekday.selectedDays = schedule.weekdays
        picker_weekday.setOnWeekdaysChangeListener { _, _, selectedDays ->
            schedule.weekdays.clear()
            schedule.weekdays.addAll(selectedDays)
        }

        // Save Button
        btn_save.setOnClickListener {
            init.pattern = edit_pattern.text.toString()
            if(switch_for_particular_number.isChecked && edit_pattern_particular.text.toString() != "") {
                init.patternExtra = edit_pattern_particular.text.toString()
            } else {
                init.patternExtra = ""
            }
            init.description = edit_desc.text.toString()
            init.priority = Integer.parseInt(edit_priority.text.toString())
            init.setForCall(chk_for_call.isChecked)
            init.setForSms(chk_for_sms.isChecked)
            init.patternFlags = Flag(initPatternFlags.value)
            init.patternExtraFlags = Flag(initPatternExtraFlags.value)
            if (radio_blacklist.isChecked) {
                init.isBlacklist = true
            }
            if (radio_whitelist.isChecked) {
                init.isBlacklist = false
            }
            init.importance = spin_importance.selectedItemPosition
            init.blockType = spin_block_type.selectedItemPosition
            init.schedule = schedule.serializeToStr()

            val err1 = Util.validateRegex(ctx, init.pattern)
            val err2 = Util.validateRegex(ctx, init.patternExtra)
            if (err1 == null && err2 == null) {
                close()
                handleSave(init)
            }
        }
    }

    private fun close() {
        val fragmentManager = requireActivity().supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.remove(this).commit()
    }
}