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

class PopupRecentAppConfigFragment(val handleSave : (Int) -> Unit) : ClosableDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_config_recent_app, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spf = SharedPref(requireContext())
        val inXMin = spf.getRecentAppConfig()

        // widgets
        val container_in_x_min = view.findViewById<TextInputLayout>(R.id.container_recent_app_in_x_min)
        val edit_in_x_min = view.findViewById<TextInputEditText>(R.id.edit_recent_app_in_x_min)
        val btn_save = view.findViewById<MaterialButton>(R.id.popup_recent_app_btn_save)

        edit_in_x_min.setText(inXMin.toString())
        edit_in_x_min.addTextChangedListener {// validate regex
            container_in_x_min.helperText = if (Util.isInt(it.toString())) "" else resources.getString(R.string.invalid_number)
        }

        btn_save.setOnClickListener {
            val inXMin = edit_in_x_min.text.toString().toIntOrNull()
            if (inXMin != null) {
                close()
                handleSave(inXMin)
            }

        }
    }

}