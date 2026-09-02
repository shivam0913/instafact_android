package com.instafact.app.data.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.instafact.app.data.model.TokenRefreshRequest
import com.instafact.app.data.model.TokenRefreshResponse
import com.instafact.app.data.model.UserRegisterRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the JSON key names the backend actually requires.
 *
 * These models carry no @SerializedName, so their wire format is produced entirely by
 * Gson's naming policy. Anything that builds its own Gson silently emits camelCase and
 * gets a 422 - which is how token refresh stayed broken without anyone noticing, because
 * the access token only expires after a week.
 *
 * NetworkModule.gson is not referenced directly: that object pulls in Android classes
 * which do not exist in a JVM unit test. The builder below must stay identical to it.
 */
class WireFormatTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private fun keysOf(value: Any) =
        JsonParser.parseString(gson.toJson(value)).asJsonObject

    @Test
    fun `token refresh request uses snake_case keys`() {
        val json = keysOf(TokenRefreshRequest(refreshToken = "abc123"))

        assertEquals("abc123", json.get("refresh_token").asString)
        assertTrue("must not emit camelCase", !json.has("refreshToken"))
    }

    @Test
    fun `token refresh response parses snake_case keys`() {
        val parsed = gson.fromJson(
            """{"token":"new-access","refresh_token":"new-refresh"}""",
            TokenRefreshResponse::class.java,
        )

        assertEquals("new-access", parsed.token)
        assertEquals("new-refresh", parsed.refreshToken)
    }

    @Test
    fun `register request uses snake_case keys`() {
        val json = keysOf(
            UserRegisterRequest(
                phoneNumber = "9000000000",
                countryCode = "91",
                fcmToken = "token",
            ),
        )

        assertEquals("9000000000", json.get("phone_number").asString)
        assertEquals("91", json.get("country_code").asString)
        assertEquals("token", json.get("fcm_token").asString)
    }
}
