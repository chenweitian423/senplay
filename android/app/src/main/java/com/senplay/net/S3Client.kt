package com.senplay.net

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.senplay.core.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S3-compatible object storage.
 *
 * Users supply an endpoint, a bucket, an access key and a secret; no OAuth and no
 * per-provider SDK, which is what lets one implementation cover the providers that
 * would otherwise each need their own integration.
 */
data class S3Target(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val prefix: String
) {
    companion object {
        /** `bucket@host/prefix` keeps bucket and prefix in the single address field. */
        fun of(server: ServerConfig): S3Target? {
            var raw = server.address.trim()
            val scheme = if (raw.lowercase().startsWith("https")) "https://" else "http://"
            raw = raw.removePrefix("https://").removePrefix("http://")
            if (raw.isBlank()) return null

            var bucket = ""
            val at = raw.indexOf('@')
            if (at > 0) {
                bucket = raw.substring(0, at)
                raw = raw.substring(at + 1)
            }
            val queryStart = raw.indexOf('?')
            val host = raw.substringBefore('/').substringBefore('?')
            if (host.isBlank()) return null

            val path = raw.substringAfter('/', "")
            val prefix = path.substringBefore('?').trim('/')
            val bucketName = bucket.ifBlank { path.substringBefore('/') }
            if (bucketName.isBlank()) return null

            val explicitRegion = if (queryStart >= 0) {
                raw.substring(queryStart + 1)
                    .split('&')
                    .firstOrNull { it.startsWith("region=") }
                    ?.substringAfter('=')
            } else null

            val region = explicitRegion ?: when {
                host.contains("oss-") -> host.substringAfter("oss-").substringBefore('.')
                host.contains("cos.") -> host.substringAfter("cos.").substringBefore('.')
                host.contains("s3.") && host.contains("amazonaws") ->
                    host.substringAfter("s3.").substringBefore('.')
                else -> "us-east-1"
            }

            if (server.username.isBlank() || server.password.isBlank()) return null
            return S3Target(
                endpoint = scheme + host,
                region = region,
                accessKey = server.username,
                secretKey = server.password,
                bucket = bucketName,
                prefix = if (bucket.isBlank()) path.substringAfter('/', "").trim('/') else prefix
            )
        }
    }

    val host: String get() = endpoint.removePrefix("https://").removePrefix("http://")
}

data class S3Object(
    val key: String,
    val size: Long,
    val isDirectory: Boolean
) {
    val name: String get() = key.trimEnd('/').substringAfterLast('/')
}

/** Signature V4, written out rather than pulled in. */
object S3Signer {

    private fun hmac(key: ByteArray, message: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun deriveKey(secret: String, day: String, region: String): ByteArray {
        val kDate = hmac(("AWS4" + secret).toByteArray(Charsets.UTF_8), day)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, "s3")
        return hmac(kService, "aws4_request")
    }

    /** Encodes the way S3 expects: unreserved characters only, spaces as %20. */
    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("*", "%2A")

    /**
     * Signs in place. `queryPairs` must already be in their final form: the caller
     * controls ordering because the canonical query is order-sensitive.
     */
    fun sign(
        method: String,
        target: S3Target,
        canonicalPath: String,
        canonicalQuery: String,
        now: Date = Date()
    ): Map<String, String> {
        val stampFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        stampFormat.timeZone = TimeZone.getTimeZone("UTC")
        val stamp = stampFormat.format(now)
        val day = stamp.take(8)

        val payloadHash = sha256Hex("")
        val canonicalHeaders = "host:${target.host}\n" +
            "x-amz-content-sha256:$payloadHash\n" +
            "x-amz-date:$stamp\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest = listOf(
            method,
            canonicalPath,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")

        val scope = "$day/${target.region}/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            stamp,
            scope,
            sha256Hex(canonicalRequest)
        ).joinToString("\n")

        val signature = deriveKey(target.secretKey, day, target.region)
            .let { key ->
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(key, "HmacSHA256"))
                mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
            }
            .joinToString("") { "%02x".format(it) }

        return mapOf(
            "Host" to target.host,
            "x-amz-date" to stamp,
            "x-amz-content-sha256" to payloadHash,
            "Authorization" to
                "AWS4-HMAC-SHA256 Credential=${target.accessKey}/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"
        )
    }

    /**
     * Exposed so a unit test can drive the derivation with an arbitrary service; the
     * client itself always signs for "s3".
     */
    internal fun signStringToSignForTest(
        secret: String,
        day: String,
        region: String,
        service: String,
        stringToSign: String
    ): String {
        val kDate = hmac(("AWS4" + secret).toByteArray(Charsets.UTF_8), day)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, service)
        val kSigning = hmac(kService, "aws4_request")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(kSigning, "HmacSHA256"))
        return mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Builds the sorted canonical query from name/value pairs. */
    fun canonicalQuery(pairs: List<Pair<String, String>>): String =
        pairs
            .map { (name, value) -> encode(name) to encode(value) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
}

object S3 {

    suspend fun list(target: S3Target, prefix: String? = null): List<S3Object> =
        withContext(Dispatchers.IO) {
            val effective = prefix ?: target.prefix
            val key = when {
                effective.isEmpty() -> ""
                effective.endsWith("/") -> effective
                else -> "$effective/"
            }
            val pairs = buildList {
                add("list-type" to "2")
                add("delimiter" to "/")
                if (key.isNotEmpty()) add("prefix" to key)
            }
            val canonicalQuery = S3Signer.canonicalQuery(pairs)
            val path = "/${target.bucket}"
            val headers = S3Signer.sign("GET", target, path, canonicalQuery)

            val url = URL("${target.endpoint}$path?$canonicalQuery")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }

            val xml = try {
                if (connection.responseCode !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val message = Regex("<Message>(.*?)</Message>", RegexOption.DOT_MATCHES_ALL)
                        .find(detail)?.groupValues?.get(1)
                    error(message ?: "S3 返回 ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

            fun texts(tag: String): List<Element> {
                val nodes = document.getElementsByTagName(tag)
                return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            }

            val contents = texts("Contents").mapNotNull { element ->
                val keyNode = element.getElementsByTagName("Key").item(0) ?: return@mapNotNull null
                val sizeNode = element.getElementsByTagName("Size").item(0)
                S3Object(
                    key = keyNode.textContent,
                    size = sizeNode?.textContent?.toLongOrNull() ?: 0L,
                    isDirectory = false
                )
            }
            val folders = texts("CommonPrefixes").mapNotNull { element ->
                val prefixNode = element.getElementsByTagName("Prefix").item(0) ?: return@mapNotNull null
                S3Object(key = prefixNode.textContent, size = 0L, isDirectory = true)
            }

            (folders + contents)
                .filter { it.key.trimEnd('/').isNotEmpty() }
                .sortedWith(compareByDescending<S3Object> { it.isDirectory }.thenBy { it.name })
        }

    /**
     * Presigned GET. The player fetches without our headers, so the signature has to
     * travel in the URL. A one-hour window is plenty for a playback session and keeps
     * the exposure small.
     */
    fun presignedUrl(target: S3Target, key: String, expiresSeconds: Int = 3600): String? {
        val stampFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        stampFormat.timeZone = TimeZone.getTimeZone("UTC")
        val stamp = stampFormat.format(Date())
        val day = stamp.take(8)
        val scope = "$day/${target.region}/s3/aws4_request"

        val path = "/${target.bucket}/" + key.split("/").joinToString("/") { S3Signer.encode(it) }
        val pairs = listOf(
            "X-Amz-Algorithm" to "AWS4-HMAC-SHA256",
            "X-Amz-Credential" to "${target.accessKey}/$scope",
            "X-Amz-Date" to stamp,
            "X-Amz-Expires" to expiresSeconds.toString(),
            "X-Amz-SignedHeaders" to "host"
        )
        val canonicalQuery = S3Signer.canonicalQuery(pairs)

        val canonicalRequest = listOf(
            "GET",
            path,
            canonicalQuery,
            "host:${target.host}\n",
            "host",
            S3Signer.sha256Hex("")
        ).joinToString("\n")

        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            stamp,
            scope,
            S3Signer.sha256Hex(canonicalRequest)
        ).joinToString("\n")

        fun hmac(key: ByteArray, message: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(message.toByteArray(Charsets.UTF_8))
        }
        val signature = hmac(
            hmac(hmac(hmac(("AWS4" + target.secretKey).toByteArray(), day), target.region), "s3"),
            "aws4_request"
        ).let { key ->
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        }.joinToString("") { "%02x".format(it) }

        return "${target.endpoint}$path?$canonicalQuery&X-Amz-Signature=$signature"
    }
}

/**
 * Reads objects directly, signing each request. Used when a URL would expire mid
 * session or when the object needs ranged reads.
 */
class S3DataSourceFactory(private val server: ServerConfig) : DataSource.Factory {
    override fun createDataSource(): DataSource = S3DataSource(server)
}

class S3DataSource(private val server: ServerConfig) : BaseDataSource(/* isNetwork = */ true) {

    private var connection: HttpURLConnection? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val target = S3Target.of(server) ?: throw IOException("S3 配置不完整")
        val key = dataSpec.uri.path?.trimStart('/') ?: throw IOException("S3 对象为空")
        val path = "/${target.bucket}/" + key.split("/").joinToString("/") { S3Signer.encode(it) }

        return try {
            val headers = S3Signer.sign("GET", target, path, "")
            val url = URL("${target.endpoint}$path")
            val http = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 30_000
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (dataSpec.position > 0) {
                    setRequestProperty("Range", "bytes=${dataSpec.position}-")
                }
            }

            val code = http.responseCode
            if (code !in 200..299) {
                throw IOException("S3 返回 $code")
            }

            connection = http
            stream = http.inputStream

            val total = http.contentLengthLong
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                total > 0 && code == 206 -> total
                total > 0 -> (total - dataSpec.position).coerceAtLeast(0)
                else -> C.LENGTH_UNSET.toLong()
            }

            transferStarted(dataSpec)
            bytesRemaining
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("S3 打开失败：${error.message}", error)
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
            throw PlaybackException("S3 读取失败", error, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        transferEnded()
        try {
            stream?.close()
        } catch (_: IOException) {
        } finally {
            connection?.disconnect()
            connection = null
            stream = null
            uri = null
        }
    }
}
