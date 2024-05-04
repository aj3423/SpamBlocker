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
import spam.blocker.db.PatternFilter
import spam.blocker.ui.util.MaterialInputRegexFlagsUtil
import spam.blocker.ui.util.Util.Companion.setupImageTooltip
import spam.blocker.util.Util


class PopupEditFilterFragment(
    private val initFilter: PatternFilter,
    private val handleSave: (PatternFilter) -> Unit,
    private val forSms: Boolean
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
        val str_number_pattern = res.getString(R.string.number_pattern)
        val str_content_pattern = res.getString(R.string.sms_content_pattern)

        // widgets
        val container_pattern = view.findViewById<TextInputLayout>(R.id.container_pattern)
        val edit_pattern = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern)
        val row_particular = view.findViewById<LinearLayout>(R.id.row_sms_particular_number)
        val switch_for_particular_number = view.findViewById<SwitchCompat>(R.id.switch_particular_number)
        val container_pattern_particular = view.findViewById<TextInputLayout>(R.id.container_pattern_phone)
        val edit_pattern_particular = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern_phone)
        val edit_desc = view.findViewById<TextInputEditText>(R.id.popup_edit_desc)
        val chk_for_call = view.findViewById<CheckBox>(R.id.popup_chk_call)
        val chk_for_sms = view.findViewById<CheckBox>(R.id.popup_chk_sms)
        val radio_blackwhitelist = view.findViewById<RadioGroup>(R.id.popup_radio_blackwhitelist)
        val radio_whitelist = view.findViewById<RadioButton>(R.id.popup_radio_whitelist)
        val radio_blacklist = view.findViewById<RadioButton>(R.id.popup_radio_blacklist)
        val edit_priority = view.findViewById<TextInputEditText>(R.id.edit_priority)
        val row_importance = view.findViewById<LinearLayout>(R.id.row_importance)
        val help_importance = view.findViewById<ImageView>(R.id.popup_help_importance)
        val spin_importance = view.findViewById<Spinner>(R.id.spin_importance)

        val btn_save = view.findViewById<MaterialButton>(R.id.popup_btn_save_filter)

        val init = initFilter
        // make a clone, so the flags of init won't be modified on the fly
        val initPatternFlags = Flag(init.patternFlags.value)
        val initPatternExtraFlags = Flag(init.patternExtraFlags.value)

        MaterialInputRegexFlagsUtil.attach(ctx, viewLifecycleOwner, container_pattern, edit_pattern, initPatternFlags)

        container_pattern.hint = if (forSms) str_content_pattern else str_number_pattern

        fun showHelpOnError(container: TextInputLayout, edit: TextInputEditText) {
            edit.addTextChangedListener {// validate regex
                val (ok, errorReason) = Util.validateRegex(ctx, it.toString())
                container.helperText = errorReason
            }
        }

        showHelpOnError(container_pattern, edit_pattern)
        edit_pattern.setText(init.pattern)
        if (forSms) {
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
        }

        // description
        edit_desc.setText(init.description)
        // priority
        edit_priority.setText(init.priority.toString())
        // call / sms
        chk_for_call.isChecked = init.isForCall()
        if (forSms) {
            chk_for_call.visibility = View.INVISIBLE
        }
        chk_for_sms.isChecked = init.isForSms()
        // whitelist / blacklist
        if (init.isWhitelist()) {
            radio_blackwhitelist.check(R.id.popup_radio_whitelist)
        } else if (init.isBlacklist) {
            radio_blackwhitelist.check(R.id.popup_radio_blacklist)
        }
        row_importance.visibility = if(init.isBlacklist) View.VISIBLE else View.GONE
        radio_blackwhitelist.setOnCheckedChangeListener { _, checkedId ->
            row_importance.visibility = if (checkedId == R.id.popup_radio_blacklist) View.VISIBLE else View.GONE
        }
        // importance
        setupImageTooltip(ctx, viewLifecycleOwner, help_importance, R.string.help_importance, false)
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

            val (ok1, _) = Util.validateRegex(ctx, init.pattern)
            val (ok2, _) = Util.validateRegex(ctx, init.patternExtra)
            if (ok1 && ok2) {
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