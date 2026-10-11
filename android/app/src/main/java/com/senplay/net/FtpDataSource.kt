package com.senplay.net

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.senplay.core.ServerConfig
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import java.io.InputStream

/**
 * Reads media off an FTP server.
 *
 * Passive mode only — active mode cannot work from a phone — with REST-based
 * resumption, so seeking inside a remote file is native rather than a re-download.
 */
class FtpDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = FtpDataSource()
}

class FtpDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var client: FTPClient? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val parsed = parse(dataSpec.uri)
            ?: throw IOException("FTP 地址无效：${dataSpec.uri}")

        return try {
            val connection = FTPClient().apply {
                connectTimeout = 15_000
                defaultTimeout = 20_000
                dataTimeout = java.time.Duration.ofSeconds(30)
                connect(parsed.host, parsed.port)
                if (!FTPReply.isPositiveCompletion(replyCode)) {
                    throw IOException("FTP 连接被拒绝：${replyString?.trim()}")
                }
                if (!login(parsed.user, parsed.password)) {
                    throw IOException("FTP 登录失败")
                }
                enterLocalPassiveMode()
                setFileType(FTP.BINARY_FILE_TYPE)
                if (dataSpec.position > 0) {
                    setRestartOffset(dataSpec.position)
                }
            }
            val input = connection.retrieveFileStream(parsed.path)
                ?: throw IOException("FTP 无法打开文件：${parsed.path}")

            client = connection
            stream = input

            val size = connection.listFiles(parsed.path)?.firstOrNull()?.size ?: -1L
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size >= 0 -> (size - dataSpec.position).coerceAtLeast(0)
                else -> C.LENGTH_UNSET.toLong()
            }

            transferStarted(dataSpec)
            bytesRemaining
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("FTP 打开失败：${error.message}", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val input = stream ?: return C.RESULT_END_OF_INPUT
        return try {
            val want = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(length.toLong(), bytesRemaining).toInt()
            }
            val read = input.read(buffer, offset, want)
            if (read == -1) {
                C.RESULT_END_OF_INPUT
            } else {
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                bytesTransferred(read)
                read
            }
        } catch (error: IOException) {
            throw PlaybackException("FTP 读取失败", error, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        transferEnded()
        try {
            stream?.close()
            client?.completePendingCommand()
            client?.logout()
        } catch (_: IOException) {
        } finally {
            try {
                client?.disconnect()
            } catch (_: IOException) {
            }
            stream = null
            client = null
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

    /** `ftp://user:pass@host:21/dir/file.mkv`, falling back to stored credentials. */
    private fun parse(uri: Uri): Parsed? {
        val host = uri.host ?: return null
        val stored = SmbCredentials.of(uri)
        val userInfo = uri.userInfo
        val user = stored.first.ifBlank { userInfo?.substringBefore(':') ?: "anonymous" }
        val password = stored.second.ifBlank { userInfo?.substringAfter(':', "") ?: "" }
        return Parsed(
            host = host,
            port = if (uri.port > 0) uri.port else 21,
            user = user,
            password = password,
            path = uri.path ?: "/"
        )
    }
}

/** Directory listing for the browser screen. */
fun listFtpDirectory(server: ServerConfig, path: String): List<SmbListEntry> {
    val raw = server.address.removePrefix("ftp://").removePrefix("FTP://")
    val hostPart = raw.substringBefore('/')
    val host = hostPart.substringBefore(':')
    val port = hostPart.substringAfter(':', "21").toIntOrNull() ?: 21

    val client = FTPClient().apply {
        connectTimeout = 15_000
        defaultTimeout = 20_000
        connect(host, port)
        if (!FTPReply.isPositiveCompletion(replyCode)) error("FTP 连接被拒绝")
        if (!login(server.username.ifBlank { "anonymous" }, server.password)) error("FTP 登录失败")
        enterLocalPassiveMode()
        setFileType(FTP.BINARY_FILE_TYPE)
    }
    return try {
        val target = path.ifBlank { "/" }
        (client.listFiles(target) ?: emptyArray()).map { file ->
            SmbListEntry(
                name = file.name,
                isDirectory = file.isDirectory,
                size = file.size,
                url = if (target.endsWith("/")) target + file.name else "$target/${file.name}"
            )
        }.filter { !it.name.startsWith(".") }
            .sortedWith(compareByDescending<SmbListEntry> { it.isDirectory }.thenBy { it.name })
    } finally {
        runCatching { client.logout() }
        runCatching { client.disconnect() }
    }
}
