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
import spam.blocker.util.SharedPref
import spam.blocker.util.Util

class PopupDialedConfigFragment(val handleSave : (Int) -> Unit) : ClosableDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_config_dialed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spf = SharedPref(requireContext())
        val inXMin = spf.getDialedConfig()

        // widgets
        val container_in_x_day = view.findViewById<TextInputLayout>(R.id.container_dialed_in_x_day)
        val edit_in_x_day = view.findViewById<TextInputEditText>(R.id.edit_dialed_in_x_day)
        val btn_save = view.findViewById<MaterialButton>(R.id.popup_dialed_btn_save)

        edit_in_x_day.setText(inXMin.toString())
        edit_in_x_day.addTextChangedListener {// validate regex
            container_in_x_day.helperText = if (Util.isInt(it.toString())) "" else resources.getString(R.string.invalid_number)
        }

        btn_save.setOnClickListener {
            val inXDay = edit_in_x_day.text.toString().toIntOrNull()
            if (inXDay != null) {
                close()
                handleSave(inXDay)
            }
        }
    }
}