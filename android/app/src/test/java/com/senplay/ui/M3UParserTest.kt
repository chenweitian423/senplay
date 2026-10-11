package com.senplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The playlist parser is fed by whatever the user happens to have, so the fixtures
 * are deliberately awkward: BOM, CRLF, missing attributes, stray lines.
 *
 * The same files are read by the iOS test, which is what makes "both builds agree"
 * a checked claim instead of a documented hope.
 */
class M3UParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "fixture $name is not on the test classpath"
        }.bufferedReader().use { it.readText() }

    @Test
    fun basicListKeepsEveryAttribute() {
        val channels = M3UParser.parse(fixture("iptv-basic.m3u"))
        assertEquals(4, channels.size)

        val first = channels[0]
        assertEquals("CCTV-1 综合", first.name)
        assertEquals("http://example.test/live/cctv1.m3u8", first.url)
        assertEquals("央视", first.group)
        assertEquals("http://logo/1.png", first.logo)
        assertEquals("cctv1", first.tvgId)

        assertEquals("卫视", channels[2].group)
        // A channel with no group attribute lands in the default bucket rather than
        // an empty string, which is what the UI groups on.
        assertEquals("未分组", channels[3].group)
    }

    @Test
    fun messyListSkipsWhatItCannotClassify() {
        val channels = M3UParser.parse(fixture("iptv-messy.m3u"))
        val urls = channels.map { it.url }
        assertEquals(
            listOf(
                "http://example.test/a.m3u8",
                "http://example.test/b.m3u8",
                "http://example.test/c.m3u8",
                "http://example.test/d.m3u8"
            ),
            urls
        )
        assertTrue(urls.none { it.contains("not-a-url-line") })
    }

    @Test
    fun bomAndCrlfDoNotLeakIntoNames() {
        val channels = M3UParser.parse(fixture("iptv-crlf.m3u"))
        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("CRLF 频道", channel.name)
        assertEquals("CRLF 组", channel.group)
        assertTrue("name has no control characters", channel.name.none { it.code < 32 })
        assertTrue("group has no control characters", channel.group.none { it.code < 32 })
    }
}
