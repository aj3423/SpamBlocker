package spam.blocker.ui.widgets

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import spam.blocker.util.Lambda1
import spam.blocker.util.Lambda2
import spam.blocker.util.Util.basename
import spam.blocker.util.Util.getFilename
import spam.blocker.util.Util.readDataFromUri
import spam.blocker.util.Util.writeDataToUri


// Show file choose dialog, and write data to the selected file
class FileWriteChooser {
    private lateinit var launcher : ManagedActivityResultLauncher<Intent, ActivityResult>

    private lateinit var content: ByteArray
    private var onResult: Lambda1<Boolean>? = null

    @Composable
    fun Compose() {
        val ctx = LocalContext.current

        launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    writeDataToUri(ctx, uri, content)
                    onResult?.invoke(true)
                }
            } else {
                onResult?.invoke(false)
            }
        }
    }

    fun popup(
        filename: String,
        content: ByteArray,
        mimeType: String = "application/txt",
        onResult: Lambda1<Boolean>? = null
    ) {
        this.content = content
        this.onResult = onResult

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType(mimeType)

            putExtra(Intent.EXTRA_TITLE, filename)
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
        mimeType: String = "*/*",
        onResult: Lambda2<String?, ByteArray?>,
    ) {
        this.onResult = onResult

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
        }
        launcher.launch(intent)
    }
}
@Composable
fun rememberFileReadChooser() : FileReadChooser {
    return remember { FileReadChooser() }
}
