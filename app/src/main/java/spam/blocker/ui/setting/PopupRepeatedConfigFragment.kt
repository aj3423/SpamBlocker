package spam.blocker.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import spam.blocker.R
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.util.SharedPref.RepeatedCall
import spam.blocker.util.Util

class PopupRepeatedConfigFragment : ClosableDialogFragment() {
    lateinit var handleSave : (Int, Int) -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_config_repeated, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spf = RepeatedCall(requireContext())
        val (times, inXMin) = spf.getConfig()

        // widgets
        val container_times = view.findViewById<TextInputLayout>(R.id.container_repeated_call_times)
        val container_in_x_min = view.findViewById<TextInputLayout>(R.id.container_repeated_call_in_x_min)
        val edit_times = view.findViewById<TextInputEditText>(R.id.edit_repeated_call_times)
        val edit_in_x_min = view.findViewById<TextInputEditText>(R.id.edit_repeated_call_in_x_min)
        val btn_save = view.findViewById<MaterialButton>(R.id.popup_repeated_call_btn_save)

        edit_times.setText(times.toString())
        edit_in_x_min.setText(inXMin.toString())
        edit_times.addTextChangedListener {// validate regex
            container_times.helperText = if (Util.isInt(it.toString())) "" else resources.getString(R.string.invalid_number)
        }
        edit_in_x_min.addTextChangedListener {// validate regex
            container_in_x_min.helperText = if (Util.isInt(it.toString())) "" else resources.getString(R.string.invalid_number)
        }

        btn_save.setOnClickListener {
            val times = edit_times.text.toString().toIntOrNull()
            val inXMin = edit_in_x_min.text.toString().toIntOrNull()
            if (times != null && inXMin != null) {
                close()
                handleSave(times, inXMin)
            }

        }
    }

}