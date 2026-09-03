package com.instafact.app.utils

/**
 * Turns whatever someone types into a national number, and says whether it is usable.
 *
 * Mirrors phone_validation.py on the server. Checking here saves a round trip and, more
 * importantly, saves sending a real SMS to a number that cannot exist - every OTP costs
 * money whether or not it can be delivered.
 *
 * Kept out of the ViewModel because none of it depends on ViewModel state, and because
 * logic that has to stay in step with the backend is worth being able to test directly.
 */
object PhoneNumberInput {

    private const val INDIA_DIAL_CODE = "91"
    private const val ASCENDING = "01234567890123456789"
    private const val DESCENDING = "98765432109876543210"

    /**
     * Reduces the input to the plain national number.
     *
     * People write numbers in every shape - "+91 98765 00011", "919876500011",
     * "09876500011" - and the server accepts all of them. Stripping only non-digits meant
     * a typed country code became a 12-digit number that was rejected on the device, so
     * the request never reached the server that would have understood it.
     *
     * A dial code is removed only when what remains is exactly the right length for the
     * country, so a genuine national number that happens to begin with those digits is
     * left alone.
     */
    fun normalize(rawPhoneNumber: String, country: Country): String {
        var digits = rawPhoneNumber.filter { it.isDigit() }
        val expected = country.nationalLength ?: return digits

        val dialCode = country.dialCode.filter { it.isDigit() }
        if (dialCode.isNotEmpty() &&
            digits.length == dialCode.length + expected &&
            digits.startsWith(dialCode)
        ) {
            digits = digits.removePrefix(dialCode)
        }

        // Domestic trunk prefix: dialled inside the country, never part of the number.
        if (digits.length == expected + 1 && digits.startsWith("0")) {
            digits = digits.drop(1)
        }
        return digits
    }

    /** Null when the number is acceptable for [country], otherwise the reason. */
    fun validate(rawPhoneNumber: String, country: Country): String? {
        val digits = normalize(rawPhoneNumber, country)
        val expected = country.nationalLength
        return when {
            digits.isEmpty() -> "Enter your mobile number."
            expected != null && digits.length != expected ->
                "Enter a valid $expected-digit number for ${country.name}."
            expected == null && digits.length !in Countries.FALLBACK_LENGTH_RANGE ->
                "Enter a valid mobile number for ${country.name}."
            // Indian mobile numbers always start 6-9; 0-5 are landline and service
            // ranges that can never receive an SMS.
            country.dialCode == INDIA_DIAL_CODE && digits.first() !in '6'..'9' ->
                "Indian mobile numbers start with 6, 7, 8 or 9."
            isObviouslyFake(digits) -> "Enter a real mobile number."
            else -> null
        }
    }

    /** All-same digits or a perfect run, e.g. 5555555555 and 9876543210. */
    private fun isObviouslyFake(digits: String): Boolean {
        if (digits.all { it == digits.first() }) return true
        return ASCENDING.contains(digits) || DESCENDING.contains(digits)
    }
}
