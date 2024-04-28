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
import spam.blocker.service.SmsReceiver
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.util.Util

class PopupTestFragment(val forSms: Boolean) : ClosableDialogFragment() {
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
        val edit_phone = view.findViewById<TextInputEditText>(R.id.edit_test_phone)
        val container_sms = view.findViewById<TextInputLayout>(R.id.container_test_sms)
        val edit_sms = view.findViewById<TextInputEditText>(R.id.edit_test_sms)
        val help_test = view.findViewById<ImageView>(R.id.help_test)
        val btn_test = view.findViewById<MaterialButton>(R.id.btn_test)
        val label_result = view.findViewById<TextView>(R.id.test_result)
        val img_reason = view.findViewById<ImageView>(R.id.test_reason)

        Util.setupImgHint(ctx, viewLifecycleOwner, help_test)

        fun clearResult() {
            label_result.visibility = View.GONE
            img_reason.visibility = View.GONE
        }
        container_sms.visibility = if (forSms) View.VISIBLE else View.GONE
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

            val r = if (forSms)
                SmsReceiver().processSms(ctx, rawNumber, sms)
            else
                CallService().processCall(ctx, rawNumber)

            // show result
            label_result.visibility = View.VISIBLE

            // set result text color
            val color = resources.getColor(if (r.shouldBlock) R.color.salmon else R.color.dark_sea_green, null)
            label_result.setTextColor(color)

            label_result.text = Util.resultStr(ctx, r.result, r.reason())
            if (r.result == Def.RESULT_ALLOWED_BY_RECENT_APP) {
                img_reason.visibility = View.VISIBLE
                img_reason.setImageDrawable(Util.getAppsMap(ctx)[r.reason()]?.icon)
            }

        }
    }
}