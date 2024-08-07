package spam.blocker.ui.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import spam.blocker.R
import spam.blocker.databinding.PopupRegexFlagsBinding
import spam.blocker.def.Def
import spam.blocker.ui.util.UI.Companion.setupImageTooltip
import spam.blocker.util.Flag

class MaterialInputRegexFlagsUtil {
    companion object {
        fun attach(ctx: Context, viewLifecycleOwner: LifecycleOwner, container: TextInputLayout, edit: TextInputEditText, initFlags: Flag) {

            container.endIconMode = TextInputLayout.END_ICON_CUSTOM
            container.isEndIconVisible = true
            fun updateFlagsIcon() {
                val imdlc = initFlags.toStr(Def.MAP_REGEX_FLAGS, Def.LIST_REGEX_FLAG_INVERSE)
                container.endIconDrawable = if (imdlc.isEmpty())
                    ContextCompat.getDrawable(ctx, R.drawable.ic_flags)
                else
                    TextDrawable(imdlc, Color.MAGENTA)
            }
            updateFlagsIcon()
            container.setEndIconOnClickListener {
                val layoutInflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val binding = PopupRegexFlagsBinding.inflate(layoutInflater)
                val root = binding.root
                val help = root.findViewById<ImageView>(R.id.help_regex_flags)
                setupImageTooltip(ctx, viewLifecycleOwner, help, R.string.help_regex_flags)

                val flags_i = root.findViewById<CheckBox>(R.id.flag_ignore_case)
                val flags_d = root.findViewById<CheckBox>(R.id.flag_dot_match_all)
                val flags_r = root.findViewById<CheckBox>(R.id.flag_raw_number)
                fun bindCheckEvents(chk: CheckBox, flag: Int) {
                    chk.setOnCheckedChangeListener { _, isChecked ->
                        initFlags.set(flag, isChecked)
                        updateFlagsIcon()
                    }
                }
                fun initCheck(chk: CheckBox, flag: Int) {
                    bindCheckEvents(chk, flag)
                    chk.isChecked = initFlags.has(flag)
                }
                initCheck(flags_i, Def.FLAG_REGEX_IGNORE_CASE)
                initCheck(flags_d, Def.FLAG_REGEX_DOT_MATCH_ALL)
                initCheck(flags_r, Def.FLAG_REGEX_RAW_NUMBER)

                val popUp = PopupWindow(ctx).apply {
                    contentView = binding.root
                    isFocusable = true
                    isTouchable = true
                    setBackgroundDrawable(ColorDrawable(ctx.getColor(R.color.dialog_bg)))
                }

                popUp.showAsDropDown(it)
            }

            // re-show the endIcon when the input loses focus
            edit.setOnFocusChangeListener { _, _ ->
                container.isEndIconVisible = true
            }
        }
    }
}