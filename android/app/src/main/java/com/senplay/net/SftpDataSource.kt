package com.senplay.net

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.senplay.core.ServerConfig
import java.io.IOException
import java.io.InputStream

/**
 * Reads media over SFTP.
 *
 * jsch's `get` supports a byte offset through a plain InputStream read loop, which is
 * what `read` drives — so seeking is a ranged pull rather than a re-download.
 * Host key verification is deliberately skipped: the user is connecting to their own
 * box and there is no trust store UI, which is stated in the coverage doc rather than
 * quietly assumed.
 */
class SftpDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = SftpDataSource()
}

class SftpDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val parsed = parse(dataSpec.uri)
            ?: throw IOException("SFTP 地址无效：${dataSpec.uri}")

        return try {
            // See the class comment: no trust store UI, so this is explicit.
            JSch.setConfig("StrictHostKeyChecking", "no")
            val jsch = JSch()
            val newSession = jsch.getSession(parsed.user, parsed.host, parsed.port).apply {
                setPassword(parsed.password)
                timeout = 20_000
                connect(15_000)
            }
            val newChannel = newSession.openChannel("sftp") as ChannelSftp
            newChannel.connect(15_000)

            val attributes = newChannel.stat(parsed.path)
            val size = attributes.size

            val input = newChannel.get(parsed.path)
            var skipped = 0L
            while (skipped < dataSpec.position) {
                val step = input.skip(dataSpec.position - skipped)
                if (step <= 0) break
                skipped += step
            }

            session = newSession
            channel = newChannel
            stream = input

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                (size - dataSpec.position).coerceAtLeast(0)
            }

            transferStarted(dataSpec)
            bytesRemaining
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("SFTP 打开失败：${error.message}", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val input = stream ?: return C.RESULT_END_OF_INPUT
        return try {
            val want = minOf(length.toLong(), bytesRemaining).toInt()
            val read = input.read(buffer, offset, want)
            if (read == -1) {
                C.RESULT_END_OF_INPUT
            } else {
                bytesRemaining -= read
                bytesTransferred(read)
                read
            }
        } catch (error: IOException) {
            throw PlaybackException("SFTP 读取失败", error, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        transferEnded()
        try {
            stream?.close()
        } catch (_: IOException) {
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
            stream = null
            channel = null
            session = null
            uri = null
        }
    }

    private data class Parsed(
        val host: String,
        val port: Int,
        val user: String,
        val password: String,
        val path: String
    )

    private fun parse(uri: Uri): Parsed? {
        val host = uri.host ?: return null
        val userInfo = uri.userInfo
        return Parsed(
            host = host,
            port = if (uri.port > 0) uri.port else 22,
            user = userInfo?.substringBefore(':') ?: "root",
            password = userInfo?.substringAfter(':', "") ?: "",
            path = uri.path ?: "/"
        )
    }
}

/** Directory listing for the browser screen. */
fun listSftpDirectory(server: ServerConfig, path: String): List<SmbListEntry> {
    val raw = server.address
        .removePrefix("sftp://")
        .removePrefix("SFTP://")
    val hostPart = raw.substringBefore('/')
    val host = hostPart.substringBefore(':')
    val port = hostPart.substringAfter(':', "22").toIntOrNull() ?: 22

    JSch.setConfig("StrictHostKeyChecking", "no")
    val jsch = JSch()
    val session = jsch.getSession(server.username.ifBlank { "root" }, host, port).apply {
        setPassword(server.password)
        timeout = 20_000
        connect(15_000)
    }
    val channel = session.openChannel("sftp") as ChannelSftp
    channel.connect(15_000)

    return try {
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(path.ifBlank { "/" }) as List<ChannelSftp.LsEntry>
        entries.filter { it.filename != "." && it.filename != ".." && !it.filename.startsWith(".") }
            .map { entry ->
                val base = if (path.endsWith("/")) path else "$path/"
                SmbListEntry(
                    name = entry.filename,
                    isDirectory = entry.attrs.isDir,
                    size = entry.attrs.size,
                    url = base + entry.filename
                )
            }
            .sortedWith(compareByDescending<SmbListEntry> { it.isDirectory }.thenBy { it.name })
    } finally {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}
