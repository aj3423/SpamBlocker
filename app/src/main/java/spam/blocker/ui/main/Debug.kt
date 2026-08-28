package spam.blocker.ui.main


import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

private const val TAG = "SpamBlocker"
private const val SO_NAME = "LiteRT.so"
private const val MODEL_NAME = "gemma3-270m-it-q8.litertlm"

fun debug(ctx: Context) {
    Thread({ summarize(ctx) }, "litert-demo").start()
}

// Assumes Downloads/test contains LiteRT.so and gemma3-270m-it-q8.litertlm.
// Grant that folder with the Auto Backup workflow (Write File directory picker).
fun summarize(ctx: Context) {
    try {
        val dir = File(ctx.filesDir, "litert-demo").apply { mkdirs() }
        val soFile = File(dir, SO_NAME)
        val modelFile = File(dir, MODEL_NAME)

        copyFromDownloadsTest(ctx, SO_NAME, soFile)
        System.load(soFile.absolutePath)
        Log.i(TAG, "loaded ${soFile.absolutePath}")

        copyFromDownloadsTest(ctx, MODEL_NAME, modelFile)

        val tLoad = SystemClock.elapsedRealtime()
        Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 1024,
                cacheDir = File(ctx.cacheDir, "litert-demo").apply { mkdirs() }.absolutePath,
            )
        ).use { engine ->
            engine.initialize()
            Log.i(TAG, "model load ${SystemClock.elapsedRealtime() - tLoad}ms")

            engine.createConversation().use { conversation ->
                val tSum = SystemClock.elapsedRealtime()
                val reply = conversation.sendMessage(
                    "Summarize in one sentence:\n\n" +
                        "Your package is waiting at the depot. Confirm delivery and pay \$1.99 at http://bit.ly/not-a-real-link to release it today."
                )
                Log.i(TAG, "summarize ${SystemClock.elapsedRealtime() - tSum}ms: $reply")
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "summarize failed", t)
    }
}

private fun copyFromDownloadsTest(ctx: Context, filename: String, dest: File) {
    if (dest.exists() && dest.length() > 0L) {
        Log.i(TAG, "using existing $filename (${dest.length()} bytes)")
        return
    }
    dest.parentFile?.mkdirs()
    val partial = File(dest.path + ".partial")
    openDownloadsTest(ctx, filename).use { input ->
        partial.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    if (dest.exists()) dest.delete()
    if (!partial.renameTo(dest)) {
        partial.copyTo(dest, overwrite = true)
        partial.delete()
    }
    dest.setReadable(true, true)
    dest.setExecutable(true, true)
    Log.i(TAG, "copied $filename (${dest.length()} bytes)")
}

private fun openDownloadsTest(ctx: Context, filename: String): InputStream {
    val direct = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "test/$filename",
    )
    if (direct.canRead()) {
        Log.i(TAG, "reading ${direct.absolutePath}")
        return FileInputStream(direct)
    }

    for (perm in ctx.contentResolver.persistedUriPermissions) {
        if (!perm.isReadPermission) continue
        val uri = findInTree(ctx, perm.uri, filename) ?: continue
        val stream = ctx.contentResolver.openInputStream(uri)
        if (stream != null) {
            Log.i(TAG, "reading $uri")
            return stream
        }
    }
    throw FileNotFoundException(
        "$filename not found in Downloads/test. Grant that folder with the Auto Backup workflow."
    )
}

private fun findInTree(ctx: Context, treeUri: Uri, filename: String): Uri? {
    val treeId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Exception) {
        return null
    }
    return findNamed(ctx, treeUri, treeId, filename, lookInTest = true)
}

private fun findNamed(
    ctx: Context,
    treeUri: Uri,
    parentId: String,
    filename: String,
    lookInTest: Boolean,
): Uri? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
    var testDirId: String? = null
    ctx.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIdx)
            val id = cursor.getString(idIdx)
            val mime = cursor.getString(mimeIdx)
            if (name == filename) {
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            }
            if (lookInTest && name == "test" && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                testDirId = id
            }
        }
    }
    val nested = testDirId ?: return null
    return findNamed(ctx, treeUri, nested, filename, lookInTest = false)
}
