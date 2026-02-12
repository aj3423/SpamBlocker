package spam.blocker.ui.widgets

import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import spam.blocker.util.Lambda2
import spam.blocker.util.Util.basename
import spam.blocker.util.Util.getFilename
import spam.blocker.util.Util.readDataFromUri
import spam.blocker.util.Util.writeDataToUri


// Force the initial dir to solve the "ghost file" issue.
private val downloadsUri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Download"
)

// Show file choose dialog, and write data to the selected file
class FileWriteChooser {
    private lateinit var launcher : ManagedActivityResultLauncher<Intent, ActivityResult>

    private lateinit var content: ByteArray

    @Composable
    fun Compose() {
        val ctx = LocalContext.current

        launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    writeDataToUri(ctx, uri, content)
                }
            }
        }
    }

    fun popup(
        filename: String,
        content: ByteArray,
    ) {
        this.content = content

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, filename)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/gzip"))
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri)
        }
        launcher.launch(intent)
    }
}

@Composable
fun rememberFileWriteChooser() : FileWriteChooser {
    return remember { FileWriteChooser() }
}



// Show file choose dialog, load data from the selected file
class FileReadChooser {
    private lateinit var launcher : ManagedActivityResultLauncher<Intent, ActivityResult>

    private lateinit var onResult: Lambda2<String?, ByteArray?> // <fileName, content>

    @Composable
    fun Compose() {
        val ctx = LocalContext.current
        launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    val filename = getFilename(ctx, uri)
                    val data = readDataFromUri(ctx, uri)

                    onResult(basename(filename!!), data)
                }
            } else {
                onResult(null, null)
            }
        }
    }

    fun popup(
        type: String = "*/*",
        mimeTypes: Array<String>? = null,
        onResult: Lambda2<String?, ByteArray?>,
    ) {
        this.onResult = onResult

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType(type)
            mimeTypes?.let {
                putExtra(Intent.EXTRA_MIME_TYPES, it)
            }
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri)
        }
        launcher.launch(intent)
    }
}

@Composable
fun rememberFileReadChooser() : FileReadChooser {
    return remember { FileReadChooser() }
}
