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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import spam.blocker.R
import spam.blocker.db.Flag
import spam.blocker.db.PatternRule
import spam.blocker.def.Def
import spam.blocker.ui.util.MaterialInputRegexFlagsUtil
import spam.blocker.ui.util.Util.Companion.setupImageTooltip
import spam.blocker.util.Util


class PopupEditFilterFragment(
    private val initFilter: PatternRule,
    private val handleSave: (PatternRule) -> Unit,
    private val forType: Int
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_edit_number_filter, container, false)
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
        val container_priority = view.findViewById<TextInputLayout>(R.id.container_priority)
        val edit_priority = view.findViewById<TextInputEditText>(R.id.edit_priority)
        val row_type = view.findViewById<LinearLayout>(R.id.row_rule_type)
        val row_importance = view.findViewById<LinearLayout>(R.id.row_importance)
        val help_importance = view.findViewById<ImageView>(R.id.popup_help_importance)
        val spin_importance = view.findViewById<Spinner>(R.id.spin_importance)

        val btn_save = view.findViewById<MaterialButton>(R.id.popup_btn_save_filter)

        val init = initFilter
        // make a clone, so the flags of init won't be modified on the fly
        val initPatternFlags = Flag(init.patternFlags.value)
        val initPatternExtraFlags = Flag(init.patternExtraFlags.value)

        if (forType == Def.ForQuickCopy)
            init.isBlacklist = false

        MaterialInputRegexFlagsUtil.attach(ctx, viewLifecycleOwner, container_pattern, edit_pattern, initPatternFlags)

        container_pattern.hint = res.getString(when(forType) {
            Def.ForCall -> R.string.number_pattern
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
                container_pattern_particular.visibility = if(checked) View.VISIBLE else View.GONE
            }
            if (init.patternExtra != "") {
                switch_for_particular_number.isChecked = true

                edit_pattern_particular.setText(init.patternExtra)
            } else {
                container_pattern_particular.visibility = View.GONE
            }
        } else {
            row_particular.visibility = View.GONE
            container_pattern_particular.visibility = View.GONE

            // hide things
            if (forType == Def.ForQuickCopy) {
                row_type.visibility = View.GONE
            }
        }

        edit_desc.setText(init.description)
        edit_priority.setText(init.priority.toString())

        setupImageTooltip(ctx, viewLifecycleOwner, help_apply_to, R.string.help_apply_to)
        chk_for_call.isChecked = init.isForCall()
        if (forType != Def.ForCall) {
            chk_for_call.visibility = View.INVISIBLE
        }
        chk_for_sms.isChecked = init.isForSms()
        // whitelist / blacklist
        if (init.isWhitelist()) {
            radio_blackwhitelist.check(R.id.popup_radio_whitelist)
        } else if (init.isBlacklist) {
            radio_blackwhitelist.check(R.id.popup_radio_blacklist)
        }
        row_importance.visibility = if(init.isBlacklist && forType != Def.ForQuickCopy) View.VISIBLE else View.GONE
        radio_blackwhitelist.setOnCheckedChangeListener { _, checkedId ->
            row_importance.visibility = if (checkedId == R.id.popup_radio_blacklist) View.VISIBLE else View.GONE
        }
        // importance
        setupImageTooltip(ctx, viewLifecycleOwner, help_importance, R.string.help_importance)
        spin_importance.setSelection(init.importance)

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