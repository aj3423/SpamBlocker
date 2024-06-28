package spam.blocker.ui.crash

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import spam.blocker.R
import spam.blocker.databinding.CrashReportActivityBinding
import spam.blocker.ui.util.UI
import spam.blocker.util.Clipboard
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.Util

class CrashReportActivity : AppCompatActivity() {

    private lateinit var binding: CrashReportActivityBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val spf = Global(this)

        // theme
        UI.applyTheme(spf.getThemeType())

        binding = CrashReportActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Get the error message from the intent
        val stackTrace = intent.getStringExtra("stackTrace")

        val edit = binding.root.findViewById<TextInputEditText>(R.id.edit_crash_reason)
        edit.setText("$stackTrace")

        val btn = binding.root.findViewById<MaterialButton>(R.id.btn_crash_report)
        btn.setOnClickListener {
            Clipboard.copy(this, edit.text.toString())
        }
    }
}