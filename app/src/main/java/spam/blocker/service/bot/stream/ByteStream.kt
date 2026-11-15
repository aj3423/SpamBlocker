package spam.blocker.service.bot.stream

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * An abstraction over binary data sources (in-memory ByteArray or file/content Uri stream).
 * Callers obtain an InputStream via openStream() and should close it after use.
 */
interface ByteStream {
    fun openStream(ctx: Context): InputStream

    companion object {
        fun fromBytes(bytes: ByteArray): ByteStream = object : ByteStream {
            override fun openStream(ctx: Context): InputStream = ByteArrayInputStream(bytes)
        }

        fun fromUri(uri: Uri): ByteStream = object : ByteStream {
            override fun openStream(ctx: Context): InputStream {
                return ctx.contentResolver.openInputStream(uri)!!
            }
        }
    }
}
