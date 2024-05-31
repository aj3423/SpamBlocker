package spam.blocker.ui.setting

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import spam.blocker.R
import spam.blocker.ui.util.UI.Companion.setupImageTooltip
import spam.blocker.util.Clipboard
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.util.Configs
import spam.blocker.util.Launcher


class PopupBackupFragment() : ClosableDialogFragment() {

   override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_backup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        // widgets
        val edit = view.findViewById<TextInputEditText>(R.id.edit_backup)
        val btn = view.findViewById<MaterialButton>(R.id.btn_backup)
        val help = view.findViewById<ImageView>(R.id.help_backup)

        setupImageTooltip(ctx, viewLifecycleOwner, help, R.string.help_backup)


        val blue = R.color.dodger_blue
        val green = R.color.teal_200
        fun updateButton() {
            var btnColor = green
            var btnText = R.string.export

            val input = edit.text!!.toString()

            if (input.trim().isNotEmpty()) {
                val currCfg = Configs()
                currCfg.load(ctx)

                if (input == Json.encodeToString(currCfg)) {
                    btnText = R.string.copy
                } else {
                    btnText = R.string.import_
                    btnColor = blue
                }
            }
            btn.setTextColor(resources.getColor(btnColor, null))
            btn.setStrokeColorResource(btnColor)
            btn.text = resources.getString(btnText)
        }
        edit.addTextChangedListener {
           updateButton()
        }

        btn.setOnClickListener {
            val currCfg = Configs()
            currCfg.load(ctx)

            val input = edit.text!!.toString()
            if (input.trim().isEmpty()) {
                edit.setText(Json.encodeToString(currCfg))
            } else {
                if (input == Json.encodeToString(currCfg)) {
                    Clipboard.copy(ctx, input)
                } else {
                    val alert = AlertDialog.Builder(ctx)

                    try {
                        val newCfg = Json.decodeFromString<Configs>(input)
                        newCfg.apply(ctx)

                        alert.setTitle(" ")
                        alert.setIcon(R.drawable.ic_check_green)
                        alert.setMessage(resources.getString(R.string.imported_successfully))
                        alert.setPositiveButton(R.string.ok) { _, _ ->
                            Launcher.selfRestart(ctx)
                        }

                    } catch (e: Exception) {
                        alert.setTitle(resources.getString(R.string.import_fail))
                        alert.setMessage(e.message)
                        alert.setIcon(R.drawable.ic_fail_red)
                    }

                    alert.create().show()
                }
            }
            updateButton()
        }

        updateButton()

    }
}