package spam.blocker.ui.setting

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import spam.blocker.R
import spam.blocker.ui.util.Algorithm.b64Decode
import spam.blocker.ui.util.Algorithm.b64Encode
import spam.blocker.ui.util.Algorithm.compressString
import spam.blocker.ui.util.Algorithm.decompressString
import spam.blocker.ui.util.UI.Companion.setupImageTooltip
import spam.blocker.ui.util.UI.Companion.showIf
import spam.blocker.ui.util.dynamicPopupMenu
import spam.blocker.util.Clipboard
import spam.blocker.util.ClosableDialogFragment
import spam.blocker.config.Configs
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
        val btn_export = view.findViewById<MaterialButton>(R.id.btn_backup_export)
        val btn_import = view.findViewById<MaterialButton>(R.id.btn_backup_import)
        val spin_copy = view.findViewById<MaterialButton>(R.id.btn_backup_copy)
        val help = view.findViewById<ImageView>(R.id.help_backup)

        setupImageTooltip(ctx, viewLifecycleOwner, help, R.string.help_backup)

        fun updateButtons() {
            val input = edit.text!!.toString().trim()

            // input box is empty
            val isEmpty = input.isEmpty()

            // input config is same as current config
            var sameAsCurrent = false

            if (!isEmpty) {
                val curr = Configs()
                curr.load(ctx)
                val currCfgStr = Json.encodeToString(curr)

                sameAsCurrent = input == currCfgStr
            }

            showIf(btn_export, isEmpty)
            showIf(spin_copy, !isEmpty && sameAsCurrent)
            showIf(btn_import, !isEmpty && !sameAsCurrent)
        }

        updateButtons()

        edit.addTextChangedListener {
           updateButtons()
        }

        btn_export.setOnClickListener {
            val currCfg = Configs()
            currCfg.load(ctx)
            edit.setText(Json.encodeToString(currCfg))
        }
        btn_import.setOnClickListener {
            var input = edit.text!!.toString().trim()

            // It supports both json and b64+compressed string.
            // First, try to recover b64+compressed to plain json,
            //   if it fails, it is already json string.
            try {
                input = decompressString(b64Decode(input))
            } catch (_:Exception) {}

            val alert = AlertDialog.Builder(ctx)

            // import from json string
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

        spin_copy.setOnClickListener {
            val menu = ctx.resources.getStringArray(R.array.copy_as_list).toList()
            dynamicPopupMenu(ctx, menu, spin_copy) { i ->
                val input = edit.text!!.toString().trim()

                val currCfg = Configs()
                currCfg.load(ctx)

                when(i) {
                    0 -> Clipboard.copy(ctx, input)
                    1 -> Clipboard.copy(ctx, b64Encode(compressString(input)))
                }
            }
        }
    }
}