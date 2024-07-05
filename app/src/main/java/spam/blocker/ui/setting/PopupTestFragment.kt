package spam.blocker.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.CallScreeningService
import spam.blocker.service.Checker
import spam.blocker.service.SmsReceiver
import spam.blocker.ui.util.UI.Companion.setupImageTooltip
import spam.blocker.ui.util.UI.Companion.showIf
import spam.blocker.util.AppInfo
import spam.blocker.util.ClosableDialogFragment

class PopupTestFragment(val forType: Int) : ClosableDialogFragment() {
    companion object {
        // save the phone/sms content that user inputted
        var currentPhone = ""
        var currentSms = ""
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        // widgets

        val radio_type = view.findViewById<RadioGroup>(R.id.test_radio_type)
        val edit_phone = view.findViewById<TextInputEditText>(R.id.edit_test_phone)
        val container_sms = view.findViewById<TextInputLayout>(R.id.container_test_sms)
        val edit_sms = view.findViewById<TextInputEditText>(R.id.edit_test_sms)
        val help_test = view.findViewById<ImageView>(R.id.help_test)
        val btn_test = view.findViewById<MaterialButton>(R.id.btn_test)
        val label_result = view.findViewById<TextView>(R.id.test_result)
        val img_reason = view.findViewById<ImageView>(R.id.test_reason)


        fun clearResult() {
            label_result.visibility = View.GONE
            img_reason.visibility = View.GONE
        }
        fun updateUI() {
            showIf(container_sms, radio_type.checkedRadioButtonId == R.id.test_radio_sms)
            clearResult()
        }
        // type radio
        radio_type.check(
            if (forType == Def.ForNumber)
                R.id.test_radio_call else R.id.test_radio_sms)
        radio_type.setOnCheckedChangeListener { _, checkedId ->
            updateUI()
        }
        updateUI()

        setupImageTooltip(ctx, viewLifecycleOwner, help_test,
           R.string.help_test_rules)

        edit_phone.setText(currentPhone)
        edit_phone.addTextChangedListener {
            currentPhone = it.toString()
            clearResult()
        }
        edit_sms.setText(currentSms)
        edit_sms.addTextChangedListener {
            currentSms = it.toString()
            clearResult()
        }

        btn_test.setOnClickListener {
            val red = resources.getColor(R.color.salmon, null)
            val green = resources.getColor(R.color.text_green, null)

            label_result.visibility = View.VISIBLE

            val r = if (radio_type.checkedRadioButtonId == R.id.test_radio_call)
                CallScreeningService().processCall(ctx, currentPhone)
            else
                SmsReceiver().processSms(ctx, currentPhone, currentSms)

            // set result text color
            label_result.setTextColor(if (r.shouldBlock) red else green)

            label_result.text = Checker.resultStr(ctx, r.result, r.reason())
            if (r.result == Def.RESULT_ALLOWED_BY_RECENT_APP) {
                img_reason.visibility = View.VISIBLE
                img_reason.setImageDrawable(AppInfo.fromPackage(ctx, r.reason()).icon)
            }
        }
    }
}