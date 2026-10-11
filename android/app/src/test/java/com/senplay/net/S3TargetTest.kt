package com.senplay.net

import com.senplay.core.ServerConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Address parsing, checked against the same fixture the Swift test reads.
 *
 * The address field is the one place users type whatever their provider's console
 * showed them, so it is a place two implementations drift easily — and it did: the
 * iOS side used to fold the host into the prefix when no `@` was present.
 */
class S3TargetTest {

    private fun fixture(): JSONObject =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("endpoints.json")) {
            "endpoints.json is not on the test classpath"
        }.bufferedReader().use { JSONObject(it.readText()) }

    private fun server(address: String, user: String, password: String) =
        ServerConfig(
            id = "test",
            type = "s3",
            address = address,
            username = user,
            password = password
        )

    @Test
    fun addressesParseAsDocumented() {
        val cases = fixture().getJSONArray("s3")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val expected = case.getJSONObject("expect")
            val target = S3Target.of(
                server(case.getString("address"), case.getString("username"), case.getString("password"))
            )
            assertNotNull("failed to parse ${case.getString("address")}", target)
            val actual = target!!
            assertEquals(case.getString("address"), expected.getString("endpoint"), actual.endpoint)
            assertEquals(case.getString("address"), expected.getString("bucket"), actual.bucket)
            assertEquals(case.getString("address"), expected.getString("prefix"), actual.prefix)
            assertEquals(case.getString("address"), expected.getString("region"), actual.region)
        }
    }

    @Test
    fun incompleteCredentialsAreRejected() {
        val rejects = fixture().getJSONArray("rejects")
        for (index in 0 until rejects.length()) {
            val case = rejects.getJSONObject(index)
            val target = S3Target.of(
                server(case.getString("address"), case.getString("username"), case.getString("password"))
            )
            assertNull(
                "should have been rejected: ${case.getString("address")}",
                target
            )
        }
    }
}
