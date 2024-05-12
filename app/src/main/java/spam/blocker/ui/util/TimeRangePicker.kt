package spam.blocker.ui.util

import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class TimeRangePicker(
    private val fragment: Fragment,
    private val initStartHour: Int, private val initStartMin: Int,
    private val initEndHour: Int, private val initEndMin: Int,
    private val onOK: (Int,Int,Int,Int) -> Unit
) {
    fun show() {

        val fmt24h = DateFormat.is24HourFormat(fragment.requireContext())
        val timeFormat = if (fmt24h) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        val stPicker = MaterialTimePicker.Builder().apply {
            setTitleText("Start Time")
            setTimeFormat(timeFormat)
            setHour(initStartHour)
            setMinute(initStartMin)
        }.build()

        val etPicker = MaterialTimePicker.Builder().apply {
            setTitleText("End Time")
            setTimeFormat(timeFormat)
            setHour(initEndHour)
            setMinute(initEndMin)
        }.build()

        stPicker.addOnPositiveButtonClickListener {
            etPicker.show(fragment.childFragmentManager, "tag_off_time_end")
        }
        etPicker.addOnCancelListener {
            stPicker.show(fragment.childFragmentManager, "tag_off_time_start")
        }
        etPicker.addOnPositiveButtonClickListener {
            onOK(stPicker.hour, stPicker.minute, etPicker.hour, etPicker.minute)
        }
        stPicker.show(fragment.childFragmentManager, "tag_off_time_start")
    }
}