package com.senplay.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

data class DlnaDevice(
    val name: String,
    val location: String,
    val controlUrl: String,
    val host: String
)

/**
 * UPnP / DLNA discovery and transport control.
 *
 * Discovery is SSDP: an M-SEARCH multicast for MediaRenderer devices, then each
 * responder's description document for its friendly name and AVTransport control
 * URL. Control is SOAP over HTTP.
 *
 * Android has no multicast restriction here, unlike iOS where the same thing needs
 * an Apple-issued entitlement — which is why casting is Android-only, same as SMB
 * and SFTP are.
 */
object Dlna {

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

    suspend fun discover(timeoutMs: Int = 3500): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DlnaDevice>()
        val socket = DatagramSocket().apply {
            soTimeout = 1200
            broadcast = true
        }

        try {
            val message = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: $RENDERER\r\n")
                append("\r\n")
            }
            val payload = message.toByteArray()
            val group = InetAddress.getByName(SSDP_ADDRESS)
            // Repeat a few times: UDP is lossy and some renderers answer late.
            repeat(3) {
                socket.send(DatagramPacket(payload, payload.size, group, SSDP_PORT))
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(8192)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    val location = response.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                        ?: continue
                    if (found.containsKey(location)) continue

                    describe(location)?.let { found[location] = it }
                } catch (_: java.net.SocketTimeoutException) {
                    // Nothing more is arriving; keep the deadline honest and continue.
                }
            }
        } finally {
            runCatching { socket.close() }
        }

        found.values.sortedBy { it.name }
    }

    private fun describe(location: String): DlnaDevice? = runCatching {
        val url = URL(location)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 4000
        }
        val xml = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        fun firstText(tag: String): String? {
            val nodes = document.getElementsByTagName(tag)
            return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
        }

        val name = firstText("friendlyName") ?: url.host
        val serviceType = document.getElementsByTagName("serviceType")
        val controlUrl = (0 until serviceType.length)
            .mapNotNull { serviceType.item(it) as? Element }
            .firstOrNull { it.textContent.contains("AVTransport") }
            ?.let { element ->
                var parent = element.parentNode
                while (parent != null && parent !is Element) parent = parent.parentNode
                val siblings = (parent as? Element)?.getElementsByTagName("controlURL")
                if (siblings != null && siblings.length > 0) siblings.item(0).textContent?.trim() else null
            }

        if (controlUrl.isNullOrBlank()) return@runCatching null

        val resolved = if (controlUrl.startsWith("http")) {
            controlUrl
        } else {
            val base = "${url.protocol}://${url.host}:${if (url.port > 0) url.port else 80}"
            base + (if (controlUrl.startsWith("/")) controlUrl else "/$controlUrl")
        }
        DlnaDevice(name = name, location = location, controlUrl = resolved, host = url.host)
    }.getOrNull()

    suspend fun play(device: DlnaDevice, mediaUrl: String, title: String): Boolean =
        withContext(Dispatchers.IO) {
            setTransportUri(device, mediaUrl, title) && transport(device, "Play")
        }

    suspend fun pause(device: DlnaDevice): Boolean = withContext(Dispatchers.IO) {
        transport(device, "Pause")
    }

    suspend fun stop(device: DlnaDevice): Boolean = withContext(Dispatchers.IO) {
        transport(device, "Stop")
    }

    suspend fun seek(device: DlnaDevice, milliseconds: Long): Boolean = withContext(Dispatchers.IO) {
        val seconds = milliseconds / 1000
        val stamp = String.format(
            "%02d:%02d:%02d",
            seconds / 3600,
            (seconds % 3600) / 60,
            seconds % 60
        )
        soap(
            device,
            "Seek",
            """
            <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <Unit>REL_TIME</Unit>
              <Target>$stamp</Target>
            </u:Seek>
            """.trimIndent()
        )
    }

    private fun setTransportUri(device: DlnaDevice, mediaUrl: String, title: String): Boolean {
        val escaped = mediaUrl.replace("&", "&amp;").replace("<", "&lt;")
        val safeTitle = title.replace("&", "&amp;").replace("<", "&lt;")
        val metadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                       xmlns:dc="http://purl.org/dc/elements/1.1/"
                       xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="0" parentID="-1" restricted="1">
                <dc:title>$safeTitle</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        val escapedMetadata = metadata
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return soap(
            device,
            "SetAVTransportURI",
            """
            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <CurrentURI>$escaped</CurrentURI>
              <CurrentURIMetaData>$escapedMetadata</CurrentURIMetaData>
            </u:SetAVTransportURI>
            """.trimIndent()
        )
    }

    private fun transport(device: DlnaDevice, action: String): Boolean = soap(
        device,
        action,
        """
        <u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
          <InstanceID>0</InstanceID>
          <Speed>1</Speed>
        </u:$action>
        """.trimIndent()
    )

    private fun soap(device: DlnaDevice, action: String, body: String): Boolean = runCatching {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>$body</s:Body>
            </s:Envelope>
        """.trimIndent()

        val connection = (URL(device.controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 6000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
        }
        connection.outputStream.use { it.write(envelope.toByteArray()) }
        val code = connection.responseCode
        connection.disconnect()
        code in 200..299
    }.getOrDefault(false)

    /**
     * Local address a renderer can reach. A phone usually has several interfaces;
     * this picks the first non-loopback IPv4 one with a real prefix.
     */
    fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 }
            ?.hostAddress
    }.getOrNull()

    /** Rewrites a loopback URL into one the renderer can fetch. */
    fun reachableUrl(path: String, port: Int): String? {
        val host = localAddress() ?: return null
        return "http://$host:$port$path"
    }
}
