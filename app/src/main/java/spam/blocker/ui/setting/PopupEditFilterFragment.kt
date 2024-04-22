package spam.blocker.ui.setting

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import spam.blocker.db.PatternFilter
import spam.blocker.R
import spam.blocker.def.Def
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
        val container_pattern_phone = view.findViewById<TextInputLayout>(R.id.container_pattern_phone)
        val switch_for_particular_number = view.findViewById<SwitchCompat>(R.id.switch_particular_number)
        val edit_pattern_phone = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern_phone)
        val edit_desc = view.findViewById<TextInputEditText>(R.id.popup_edit_desc)
        val chk_for_call = view.findViewById<CheckBox>(R.id.popup_chk_call)
        val chk_for_sms = view.findViewById<CheckBox>(R.id.popup_chk_sms)
        val radio_blackwhitelist = view.findViewById<RadioGroup>(R.id.popup_radio_blackwhitelist)
        val radio_whitelist = view.findViewById<RadioButton>(R.id.popup_radio_whitelist)
        val radio_blacklist = view.findViewById<RadioButton>(R.id.popup_radio_blacklist)
        val edit_priority = view.findViewById<TextInputEditText>(R.id.edit_priority)

        val btn_save = view.findViewById<MaterialButton>(R.id.popup_btn_save)

        val init = initFilter

        container_pattern.hint = if (forSms) str_content_pattern else str_number_pattern

        edit_pattern.setText(init.pattern)
        edit_pattern.addTextChangedListener {// validate regex
            container_pattern.helperText = if (Util.isRegexValid(it.toString())) "" else resources.getString(R.string.invalid_regex_pattern)
        }
        if (forSms) {
            Util.setupImgHint(ctx, viewLifecycleOwner, view.findViewById(R.id.popup_help_particular_number))
            switch_for_particular_number.setOnClickListener{
                val checked = switch_for_particular_number.isChecked
                container_pattern_phone.visibility = if(checked) View.VISIBLE else View.GONE
            }
            if (init.patternExtra != "") {
                switch_for_particular_number.isChecked = true

                edit_pattern_phone.setText(init.patternExtra)
            } else {
                container_pattern_phone.visibility = View.GONE
            }
        } else {
            row_particular.visibility = View.GONE
            container_pattern_phone.visibility = View.GONE
        }
        edit_desc.setText(init.description)
        chk_for_call.isChecked = init.isForCall()
        if (forSms) {
            chk_for_call.visibility = View.INVISIBLE
        }
        chk_for_sms.isChecked = init.isForSms()
        if (init.isWhitelist()) {
            radio_blackwhitelist.check(R.id.popup_radio_whitelist)
        } else if (init.isBlacklist) {
            radio_blackwhitelist.check(R.id.popup_radio_blacklist)
        }
        edit_priority.setText(init.priority.toString())

        btn_save.setOnClickListener {
            init.pattern = edit_pattern.text.toString()
            if(switch_for_particular_number.isChecked && edit_pattern_phone.text.toString() != "") {
                init.patternExtra = edit_pattern_phone.text.toString()
            } else {
                init.patternExtra = ""
            }
            init.description = edit_desc.text.toString()
            init.setForCall(chk_for_call.isChecked)
            init.setForSms(chk_for_sms.isChecked)
            if (radio_blacklist.isChecked) {
                init.isBlacklist = true
            }
            if (radio_whitelist.isChecked) {
                init.isBlacklist = false
            }
            init.priority = Integer.parseInt(edit_priority.text.toString())
            if (Util.isPatternValid(init.pattern) && Util.isPatternValid(init.patternExtra)) {
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