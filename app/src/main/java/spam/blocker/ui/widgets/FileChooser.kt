package spam.blocker.ui.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.net.toUri
import spam.blocker.util.Lambda1
import spam.blocker.util.spf

val MIME_GZ = arrayOf("application/octet-stream", "application/gzip")
val MIME_TEXT = arrayOf("text/plain")
val MIME_CSV = arrayOf("text/*")

val MIME_ICON = arrayOf(
    "image/png", "image/jpeg", "image/x-icon", "image/vnd.microsoft.icon",
    "image/bmp", "mage/x-bmp"
)

// Force the initial dir to solve the "ghost file" issue.
private val downloadsUri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Download"
)

data class InitFile(
    val filename: String,
    val mimeType : Array<String>,
    // Remember the last accessed dir for different operations, e.g. "import csv", "backup restore", "choose notification icon"
    val rememberDirTag: String? = null,
)


// Show file choose dialog, and write data to the selected file
object FileChooser {
    private var launcherRead : ManagedActivityResultLauncher<Array<String>, Uri?>? = null
    private var launcherWrite : ManagedActivityResultLauncher<String, Uri?>? = null

    private var initAttr: InitFile? = null
    private var onResult: Lambda1<Uri?>? = null

    @Composable
    fun Compose() {
        val ctx = LocalContext.current

        launcherWrite = rememberLauncherForActivityResult(
            contract = object : ActivityResultContracts.CreateDocument("*/*") {
                override fun createIntent(context: Context, input: String): Intent {
                    return super.createIntent(context, input).apply {
                        putExtra(Intent.EXTRA_TITLE, initAttr!!.filename)
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, loadUri(ctx))
                        putExtra(Intent.EXTRA_MIME_TYPES, initAttr!!.mimeType)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                }
            },
            onResult = { uri ->
                // Remember the last saved folder
                saveUri(ctx, uri)

                // Call user callback (actual file write)
                onResult?.let { it(uri) }
            }
        )
        launcherRead = rememberLauncherForActivityResult(
            contract = object : ActivityResultContracts.OpenDocument() {
                override fun createIntent(context: Context, input: Array<String>): Intent {
                    return super.createIntent(context, input).apply {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, loadUri(ctx))
                        putExtra(Intent.EXTRA_TITLE, initAttr!!.filename)
                        putExtra(Intent.EXTRA_MIME_TYPES, initAttr!!.mimeType)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                }
            },
            onResult = { uri ->
                // Remember the last read folder
                saveUri(ctx, uri)

                // Call user callback (actual file read)
                onResult?.let{ it(uri) }
            }
        )
    }
    private fun saveUri(ctx: Context, uri: Uri?) {
        if (uri != null && initAttr!!.rememberDirTag != null) {
            spf.SharedPref(ctx).prefs.edit {
                putString(
                    initAttr!!.rememberDirTag,
                    uri.toString()
                )
            }
        }
    }
    private fun loadUri(ctx: Context) : Uri {
        return initAttr!!.rememberDirTag?.let {
            spf.SharedPref(ctx).prefs.getString(it, null)
        }?.toUri() ?: downloadsUri
    }

    fun popupRead(
        init: InitFile,
        onResult: Lambda1<Uri?>
    ) {
        this.initAttr = init
        this.onResult = onResult

        launcherRead?.launch(arrayOf("*/*"))
    }

    fun popupWrite(
        init: InitFile,
        onResult: Lambda1<Uri?>
    ) {
        initAttr = init
        this.onResult = onResult

        launcherWrite?.launch("*/*")
    }
}
