package spam.blocker.util

import androidx.fragment.app.DialogFragment

open class ClosableDialogFragment : DialogFragment() {
    fun close() {
        val fragmentManager = requireActivity().supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.remove(this).commit()
    }
}