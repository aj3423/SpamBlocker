package spam.blocker.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

import spam.blocker.R
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.util.SharedPref.Stir

class PopupStirConfigFragment(val handleSave : (Boolean, Boolean) -> Unit) : ClosableDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_config_stir, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spf = Stir(requireContext())

        // widgets
        val radio_type = view.findViewById<RadioGroup>(R.id.popup_radio_stir_in_exclusive)
        val switch_include_unverified = view.findViewById<SwitchCompat>(R.id.switch_stir_include_unverified)
        val btn_save = view.findViewById<MaterialButton>(R.id.popup_stir_btn_save)

        radio_type.check(if (spf.isExclusive())
            R.id.radio_stir_exclusive
        else
            R.id.radio_stir_inclusive)

        switch_include_unverified.isChecked = spf.isIncludeUnverified()

        btn_save.setOnClickListener {
            close()
            handleSave(
                radio_type.checkedRadioButtonId == R.id.radio_stir_exclusive,
                switch_include_unverified.isChecked
            )
        }
    }
}