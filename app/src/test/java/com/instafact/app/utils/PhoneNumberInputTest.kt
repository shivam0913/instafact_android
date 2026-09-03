package com.instafact.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Keeps the on-device rules in step with phone_validation.py on the server.
 *
 * The server already accepted "+919876500011" and friends; the client stripped only
 * non-digits, so a typed country code became a 12-digit number and was rejected here
 * before it could reach the server that understood it. That is exactly what happens when
 * someone is handed a number written with its country code - a Play Store reviewer, or
 * anyone pasting from their contacts.
 */
class PhoneNumberInputTest {

    private val india = Countries.byIso("IN")!!

    private fun normalize(raw: String) = PhoneNumberInput.normalize(raw, india)

    @Test
    fun `plain national number is unchanged`() {
        assertEquals("9876500011", normalize("9876500011"))
    }

    @Test
    fun `leading plus and country code are stripped`() {
        assertEquals("9876500011", normalize("+919876500011"))
    }

    @Test
    fun `country code without a plus is stripped`() {
        assertEquals("9876500011", normalize("919876500011"))
    }

    @Test
    fun `separators are ignored`() {
        assertEquals("9876500011", normalize("+91 98765 00011"))
        assertEquals("9876500011", normalize("+91-98765-00011"))
        assertEquals("9876500011", normalize("(+91) 98765 00011"))
    }

    @Test
    fun `domestic trunk prefix is stripped`() {
        assertEquals("9876500011", normalize("09876500011"))
    }

    @Test
    fun `a national number beginning with the dial code digits is left alone`() {
        // 9198765000 is a real 10-digit number starting "91". Stripping the dial code
        // here would silently mangle it, so the length has to gate the strip.
        assertEquals("9198765000", normalize("9198765000"))
    }

    @Test
    fun `every form a person might type is accepted`() {
        for (raw in listOf(
            "9876500011",
            "+919876500011",
            "919876500011",
            "+91 98765 00011",
            "09876500011",
        )) {
            assertNull("expected $raw to be accepted", PhoneNumberInput.validate(raw, india))
        }
    }

    @Test
    fun `genuinely wrong numbers are still rejected`() {
        assertEquals("Enter your mobile number.", PhoneNumberInput.validate("", india))
        assertEquals(
            "Enter a valid 10-digit number for ${india.name}.",
            PhoneNumberInput.validate("98765", india),
        )
        assertEquals(
            "Indian mobile numbers start with 6, 7, 8 or 9.",
            PhoneNumberInput.validate("5876500011", india),
        )
        assertEquals("Enter a real mobile number.", PhoneNumberInput.validate("9999999999", india))
        assertEquals("Enter a real mobile number.", PhoneNumberInput.validate("9876543210", india))
    }
}
