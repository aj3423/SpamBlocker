package spam.blocker.ui.setting

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

import spam.blocker.R
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.Permissions
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.Util

class PopupEnableConfigFragment : ClosableDialogFragment() {
    lateinit var handleSave : (Boolean, Boolean) -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_config_enabled, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val spf = Global(ctx)

        // widgets
        val switch_call = view.findViewById<SwitchCompat>(R.id.switch_enable_for_call)
        val switch_sms = view.findViewById<SwitchCompat>(R.id.switch_enable_for_sms)
        val btn_save = view.findViewById<MaterialButton>(R.id.popup_btn_save_enable)

        switch_call.isChecked = spf.isCallEnabled() && Permissions.isCallScreeningEnabled(ctx)
        switch_sms.isChecked = spf.isSmsEnabled() && Permissions.isReceiveSmsPermissionGranted(ctx)

        switch_call.setOnClickListener {
            val isChecked = switch_call.isChecked
            if (isChecked) {

                Permissions.askAsScreeningApp { granted ->
                    switch_call.isChecked = granted
                }
            }
        }
        val permChain = PermissionChain(this,
            listOf( Permission(Manifest.permission.RECEIVE_SMS) )
        )
        switch_sms.setOnClickListener {
            val isChecked = switch_sms.isChecked
            if (isChecked) {
                permChain.ask { allGranted ->
                    switch_sms.isChecked = allGranted
                    if (allGranted) {
                        Util.checkDoubleNotifications(ctx)
                    }
                }
            }
        }

        btn_save.setOnClickListener {
            close()
            handleSave(switch_call.isChecked, switch_sms.isChecked )
        }
    }
}