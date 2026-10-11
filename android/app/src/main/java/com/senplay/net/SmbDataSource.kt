package com.senplay.net

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException
import java.util.Properties

/**
 * Reads media straight off an SMB share.
 *
 * Media3's stock data sources do not speak SMB, so this wraps jcifs-ng's random
 * access file. Because it is a real DataSource, seeking is native — no local
 * download, no proxy hop.
 */
class SmbDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource()
}

class SmbDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var file: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val target = parse(dataSpec.uri)
            ?: throw IOException("SMB 地址无效：${dataSpec.uri}")

        return try {
            val smbFile = SmbFile(target.url, target.context())
            if (!smbFile.exists()) throw IOException("SMB 路径不存在：${target.url}")

            // jcifs-ng exposes random access through the constructor, not a getter.
            val handle = SmbRandomAccessFile(smbFile, "r")
            val length = smbFile.length()
            handle.seek(dataSpec.position)

            file = handle
            position = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                minOf(dataSpec.length, length - dataSpec.position)
            } else {
                (length - dataSpec.position).coerceAtLeast(0)
            }
            opened = true
            transferStarted(dataSpec)
            bytesRemaining
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("SMB 打开失败：${error.message}", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val handle = file ?: return C.RESULT_END_OF_INPUT
        return try {
            val want = minOf(length.toLong(), bytesRemaining).toInt()
            val read = handle.read(buffer, offset, want)
            if (read == -1) {
                C.RESULT_END_OF_INPUT
            } else {
                bytesRemaining -= read
                position += read
                bytesTransferred(read)
                read
            }
        } catch (error: IOException) {
            throw PlaybackException("SMB 读取失败", error, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        transferEnded()
        try {
            file?.close()
        } catch (_: IOException) {
        } finally {
            file = null
            uri = null
            if (opened) {
                opened = false
            }
        }
    }

    /**
     * Accepts `smb://user:pass@host/share/dir/file.mkv` and falls back to the
     * credentials stored with the server when the URI carries none.
     */
    private fun parse(uri: Uri): SmbTarget? {
        val host = uri.host ?: return null
        val segments = uri.pathSegments
        if (segments.isEmpty()) return null
        val stored = SmbCredentials.of(uri)
        return SmbTarget(
            host = host,
            share = segments.first(),
            path = segments.drop(1).joinToString("/"),
            user = stored.first.ifBlank { uri.userInfo?.substringBefore(':') ?: "" },
            password = stored.second.ifBlank { uri.userInfo?.substringAfter(':', "") ?: "" },
            domain = stored.third
        )
    }
}

data class SmbTarget(
    val host: String,
    val share: String,
    val path: String,
    val user: String,
    val password: String,
    val domain: String?
) {
    val url: String
        get() {
            val base = "smb://$host/$share"
            return if (path.isBlank()) "$base/" else base + (if (path.startsWith("/")) path else "/$path")
        }

    fun context(): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "20000")
            setProperty("jcifs.smb.client.soTimeout", "60000")
        }
        val base = BaseContext(PropertyConfiguration(properties))
        return if (user.isBlank()) {
            base.withAnonymousCredentials()
        } else {
            base.withCredentials(jcifs.smb.NtlmPasswordAuthenticator(domain, user, password))
        }
    }
}

/** Lists a directory on an SMB share for the browser screen. */
fun listSmbDirectory(target: SmbTarget): List<SmbListEntry> {
    val smbFile = SmbFile(target.url, target.context())
    return smbFile.listFiles().map { child ->
        SmbListEntry(
            name = child.name.trimEnd('/'),
            isDirectory = child.isDirectory,
            size = child.length(),
            url = child.path
        )
    }.filter { !it.name.startsWith(".") }
        .sortedWith(compareByDescending<SmbListEntry> { it.isDirectory }.thenBy { it.name })
}

data class SmbListEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val url: String
)

/** Credentials recorded when the server was added, keyed by host. */
object SmbCredentials {
    @Volatile
    private var entries: Map<String, Triple<String, String, String?>> = emptyMap()

    fun remember(host: String, user: String, password: String, domain: String? = null) {
        entries = entries + (host to Triple(user, password, domain))
    }

    fun of(uri: Uri): Triple<String, String, String?> {
        val host = uri.host ?: return Triple("", "", null)
        val direct = entries[host]
        if (direct != null) return direct
        val byPrefix = entries.entries.firstOrNull { host.startsWith(it.key) || it.key.startsWith(host) }
        return byPrefix?.value ?: Triple("", "", null)
    }
}


/**
 * Routes playback by scheme: `smb://` goes to the SMB reader, everything else to
 * the HTTP stack. Wrapping at the factory keeps the player unaware of the
 * difference, so a mixed playlist just works.
 */
class SchemeRoutingDataSourceFactory(
    private val http: DataSource.Factory,
    private val smb: DataSource.Factory = SmbDataSourceFactory(),
    private val ftp: DataSource.Factory = FtpDataSourceFactory()
) : DataSource.Factory {

    override fun createDataSource(): DataSource = object : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            val target = when {
                dataSpec.uri.scheme.equals("smb", ignoreCase = true) -> smb
                dataSpec.uri.scheme.equals("ftp", ignoreCase = true) -> ftp
                else -> http
            }
            val created = target.createDataSource()
            listeners.forEach { created.addTransferListener(it) }
            delegate = created
            return created.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = delegate?.uri

        override fun close() {
            delegate?.close()
            delegate = null
        }
    }
}
