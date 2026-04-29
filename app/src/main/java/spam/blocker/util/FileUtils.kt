package spam.blocker.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException

object FileUtils {

    fun readDataFromUri(ctx: Context, uri: Uri): ByteArray? {
        return runBlocking {
            withContext(IO) {
                try {
                    // Use coroutine to avoid accessing network on main thread,
                    //   for importing backup directly from cloud file.
                    ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.buffered().readBytes()
                    }
                } catch (_: IOException) {
                    null
                }
            }
        }
    }

    fun writeDataToUri(ctx: Context, uri: Uri, dataToWrite: ByteArray): Boolean {
        return try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(dataToWrite)
                outputStream.flush()
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    fun readFileFromTree(ctx: Context, treeUri: Uri, filename: String): ByteArray? {
        val resolver = ctx.contentResolver

        // 1. Build the URI to look at the children of the selected folder
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        var fileUri: Uri? = null

        // 2. Query the folder for the specific filename
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == filename) {
                    val foundId = cursor.getString(idIndex)
                    // 3. Build the Document URI for the specific file found
                    fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId)
                    break
                }
            }
        }

        // 4. If we found the file, read its bytes
        return fileUri?.let { uri ->
            try {
                resolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    fun writeFileInTree(ctx: Context, treeUri: Uri, filename: String, data: ByteArray) {
        val resolver = ctx.contentResolver

        // 1. Build the children URI for the folder
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        var existingFileUri: Uri? = null

        // 2. Query the folder to see if the filename exists
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == filename) {
                    val foundId = cursor.getString(idIndex)
                    existingFileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId)
                    break
                }
            }
        }

        // 3. Get the URI (either the existing one or a newly created one)
        val targetUri = existingFileUri ?: DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
            "application/octet-stream",
            filename
        )

        // 4. Write data (using "wt" mode to truncate/overwrite existing content)
        targetUri?.let { uri ->
            resolver.openOutputStream(uri, "wt")?.use { it.write(data) }
        }
    }

    fun getFilename(ctx: Context, uri: Uri): String? {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        var filename: String? = null

        cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)?.let { nameIndex ->
            cursor.moveToFirst()

            filename = cursor.getString(nameIndex)
            cursor.close()
        }

        return filename
    }

}