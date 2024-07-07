package spam.blocker.ui.util

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import spam.blocker.util.Util.Companion.readDataFromUri
import spam.blocker.util.Util.Companion.writeDataToUri

// Show file choose dialog, and write data to the selected file
class FileOutChooser(
    private val fragment: Fragment,
    private val mimeType: String = "application/txt"
) {
    private var onResult: (Boolean) -> Unit = {}

    private var content: ByteArray? = null

    private var launcher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->

                    if (content != null)
                        writeDataToUri(fragment.requireContext(), uri, content!!)

                    onResult(true)
                }
            } else {
                onResult(false)
            }
        }

    fun create(
        title: String,
        content: ByteArray?,
        onResult: (Boolean) -> Unit = {},
    ) {
        this.content = content
        this.onResult = onResult

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType(mimeType)

            putExtra(Intent.EXTRA_TITLE, title)
        }
        launcher.launch(intent)
    }
}


// Show file choose dialog, load data from the selected file
class FileInChooser(
    private val fragment: Fragment,
    private val mimeType: String = "*/*"
) {
    private var onResult: (ByteArray?) -> Unit = {}

    private var launcher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    val data = readDataFromUri(fragment.requireContext(), uri)
                    onResult(data)
                }
            } else {
                onResult(null)
            }
        }

    fun load(
        onResult: (ByteArray?) -> Unit = {},
    ) {
        this.onResult = onResult

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
        }
        launcher.launch(intent)
    }
}
