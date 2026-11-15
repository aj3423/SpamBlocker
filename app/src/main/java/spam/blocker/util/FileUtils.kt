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
    fun readInternalFile(ctx: Context, fileName: String): ByteArray? {
        val file = ctx.getFileStreamPath(fileName)

        if (!file.exists())
            return null

        return ctx.openFileInput(fileName).use { inputStream ->
            inputStream.readBytes()
        }
    }
    fun writeInternalFile(ctx: Context, fileName: String, modelBytes: ByteArray) {
        ctx.openFileOutput(fileName, Context.MODE_PRIVATE).use { outputStream ->
            outputStream.write(modelBytes)
        }
    }
    fun deleteInternalFile(ctx: Context, fileName: String): Boolean {
        return ctx.deleteFile(fileName)
    }

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

    // Returns the "error message" or "null"
    fun writeDataToUri(ctx: Context, uri: Uri, dataToWrite: ByteArray): String? {
        return try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(dataToWrite)
                outputStream.flush()
            }
            null
        } catch (e: Exception) {
            "$e"
        }
    }

    fun findFileInTree(ctx: Context, treeUri: Uri, filename: String): Uri? {
        val resolver = ctx.contentResolver
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == filename) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                }
            }
        }
        return null
    }

    fun readFileFromTree(ctx: Context, treeUri: Uri, filename: String): ByteArray? {
        return findFileInTree(ctx, treeUri, filename)?.let { uri ->
            try {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
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