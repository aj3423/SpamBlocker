package spam.blocker.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.CallService
import spam.blocker.service.Checker
import spam.blocker.service.SmsReceiver
import spam.blocker.ui.util.Util.Companion.setupImageTooltip
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.util.Util

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
        val container_phone = view.findViewById<TextInputLayout>(R.id.container_test_phone)
        val edit_phone = view.findViewById<TextInputEditText>(R.id.edit_test_phone)
        val container_sms = view.findViewById<TextInputLayout>(R.id.container_test_sms)
        val edit_sms = view.findViewById<TextInputEditText>(R.id.edit_test_sms)
        val help_test = view.findViewById<ImageView>(R.id.help_test)
        val btn_test = view.findViewById<MaterialButton>(R.id.btn_test)
        val label_result = view.findViewById<TextView>(R.id.test_result)
        val img_reason = view.findViewById<ImageView>(R.id.test_reason)

        if (forType == Def.ForQuickCopy)
            container_phone.visibility = View.GONE


        setupImageTooltip(ctx, viewLifecycleOwner, help_test,
            if (forType == Def.ForQuickCopy) R.string.help_test_quick_copy else R.string.help_test_rules)

        fun clearResult() {
            label_result.visibility = View.GONE
            img_reason.visibility = View.GONE
        }
        container_sms.visibility = if (forType != Def.ForCall) View.VISIBLE else View.GONE
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
            val rawNumber = edit_phone.text.toString()
            val sms = edit_sms.text.toString()

            val red = resources.getColor(R.color.salmon, null)
            val green = resources.getColor(R.color.dark_sea_green, null)

            label_result.visibility = View.VISIBLE

            if (forType != Def.ForQuickCopy) {
                val r = if (forType == Def.ForCall)
                    CallService().processCall(ctx, rawNumber)
                else
                    SmsReceiver().processSms(ctx, rawNumber, sms)

                // set result text color
                label_result.setTextColor(if (r.shouldBlock) red else green)

                label_result.text = Util.resultStr(ctx, r.result, r.reason())
                if (r.result == Def.RESULT_ALLOWED_BY_RECENT_APP) {
                    img_reason.visibility = View.VISIBLE
                    img_reason.setImageDrawable(Util.getAppsMap(ctx)[r.reason()]?.icon)
                }

            } else {
                val r = Checker.checkQuickCopy(ctx, sms)

                label_result.setTextColor(if (r == null) red else green)

                if (r != null) {
                    val byRule = if (r.first.description.isEmpty()) r.first.pattern else r.first.description
                    label_result.text = "$byRule\n\n  ${r.second}"
                } else {
                    label_result.text = resources.getString(R.string.no_match_found)
                }
            }
        }
    }
}