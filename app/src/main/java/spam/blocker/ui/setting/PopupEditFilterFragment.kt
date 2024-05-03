package spam.blocker.ui.setting

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import spam.blocker.R
import spam.blocker.db.PatternFilter
import spam.blocker.def.Def
import spam.blocker.util.Util


class TextDrawable(private val text: String, private val color: Int) : Drawable() {
    private val paint: Paint = Paint()

    init {
        paint.setColor(Color.WHITE)
        paint.textSize = 36f
        paint.isAntiAlias = true
        paint.isFakeBoldText = true
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.textAlign = Paint.Align.LEFT
        paint.color = color
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds

        val x = bounds.centerX().toFloat() - paint.measureText(text) / 2
        val y = bounds.centerY().toFloat() + paint.textSize / 2
        canvas.drawText(text, x, y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.setAlpha(alpha)
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.setColorFilter(cf)
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}

class PopupEditFilterFragment(
    private val initFilter: PatternFilter,
    private val handleSave: (PatternFilter) -> Unit,
    private val forSms: Boolean
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_edit_number_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val res = ctx.resources
        val str_number_pattern = res.getString(R.string.number_pattern)
        val str_content_pattern = res.getString(R.string.sms_content_pattern)

        // widgets
        val container_pattern = view.findViewById<TextInputLayout>(R.id.container_pattern)
        val edit_pattern = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern)
        val row_particular = view.findViewById<LinearLayout>(R.id.row_sms_particular_number)
        val container_pattern_phone = view.findViewById<TextInputLayout>(R.id.container_pattern_phone)
        val switch_for_particular_number = view.findViewById<SwitchCompat>(R.id.switch_particular_number)
        val edit_pattern_phone = view.findViewById<TextInputEditText>(R.id.popup_edit_pattern_phone)
        val edit_desc = view.findViewById<TextInputEditText>(R.id.popup_edit_desc)
        val chk_for_call = view.findViewById<CheckBox>(R.id.popup_chk_call)
        val chk_for_sms = view.findViewById<CheckBox>(R.id.popup_chk_sms)
        val radio_blackwhitelist = view.findViewById<RadioGroup>(R.id.popup_radio_blackwhitelist)
        val radio_whitelist = view.findViewById<RadioButton>(R.id.popup_radio_whitelist)
        val radio_blacklist = view.findViewById<RadioButton>(R.id.popup_radio_blacklist)
        val edit_priority = view.findViewById<TextInputEditText>(R.id.edit_priority)
        val row_importance = view.findViewById<LinearLayout>(R.id.row_importance)
        val help_importance = view.findViewById<ImageView>(R.id.popup_help_importance)
        val spin_importance = view.findViewById<Spinner>(R.id.spin_importance)

        val btn_save = view.findViewById<MaterialButton>(R.id.popup_btn_save_filter)

        val init = initFilter

//        val imdlc = "imdlc"
//        var i = 1
//        container_pattern.endIconMode = TextInputLayout.END_ICON_CUSTOM
//        container_pattern.endIconDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_shade, null)
//        container_pattern.isEndIconVisible = true
//        container_pattern.setEndIconOnClickListener {
//            Toast.makeText(ctx, " icon clicked, popup dropdown checkboxes....", Toast.LENGTH_SHORT).show()
//            i += 1
//            val drawable = TextDrawable(imdlc.substring(0, i), ctx.resources.getColor(R.color.orange, null))
//            container_pattern.endIconDrawable = drawable
//        }

        container_pattern.hint = if (forSms) str_content_pattern else str_number_pattern

        edit_pattern.setText(init.pattern)
        edit_pattern.addTextChangedListener {// validate regex
            container_pattern.helperText = if (Util.isRegexValid(it.toString())) "" else resources.getString(R.string.invalid_regex_pattern)
        }
        if (forSms) {
            Util.setupImageTooltip(ctx, viewLifecycleOwner, view.findViewById(R.id.popup_help_particular_number), R.string.help_for_particular_number)
            switch_for_particular_number.setOnClickListener{
                val checked = switch_for_particular_number.isChecked
                container_pattern_phone.visibility = if(checked) View.VISIBLE else View.GONE
            }
            if (init.patternExtra != "") {
                switch_for_particular_number.isChecked = true

                edit_pattern_phone.setText(init.patternExtra)
            } else {
                container_pattern_phone.visibility = View.GONE
            }
        } else {
            row_particular.visibility = View.GONE
            container_pattern_phone.visibility = View.GONE
        }

        // description
        edit_desc.setText(init.description)
        // priority
        edit_priority.setText(init.priority.toString())
        // call / sms
        chk_for_call.isChecked = init.isForCall()
        if (forSms) {
            chk_for_call.visibility = View.INVISIBLE
        }
        chk_for_sms.isChecked = init.isForSms()
        // whitelist / blacklist
        if (init.isWhitelist()) {
            radio_blackwhitelist.check(R.id.popup_radio_whitelist)
        } else if (init.isBlacklist) {
            radio_blackwhitelist.check(R.id.popup_radio_blacklist)
        }
        row_importance.visibility = if(init.isBlacklist) View.VISIBLE else View.GONE
        radio_blackwhitelist.setOnCheckedChangeListener { _, checkedId ->
            row_importance.visibility = if (checkedId == R.id.popup_radio_blacklist) View.VISIBLE else View.GONE
        }
        // importance
        Util.setupImageTooltip(ctx, viewLifecycleOwner, help_importance, R.string.help_importance, false)
        spin_importance.setSelection(init.importance)

        btn_save.setOnClickListener {
            init.pattern = edit_pattern.text.toString()
            if(switch_for_particular_number.isChecked && edit_pattern_phone.text.toString() != "") {
                init.patternExtra = edit_pattern_phone.text.toString()
            } else {
                init.patternExtra = ""
            }
            init.description = edit_desc.text.toString()
            init.priority = Integer.parseInt(edit_priority.text.toString())
            init.setForCall(chk_for_call.isChecked)
            init.setForSms(chk_for_sms.isChecked)
            if (radio_blacklist.isChecked) {
                init.isBlacklist = true
            }
            if (radio_whitelist.isChecked) {
                init.isBlacklist = false
            }
            init.importance = spin_importance.selectedItemPosition
            if (Util.isRegexValid(init.pattern) && Util.isRegexValid(init.patternExtra)) {
                close()
                handleSave(init)
            }
        }
    }

    private fun close() {
        val fragmentManager = requireActivity().supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.remove(this).commit()
    }
}