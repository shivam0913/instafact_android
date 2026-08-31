package com.instafact.app.utils

/**
 * @param nationalLength expected digits after the dial code, or null when it varies by operator.
 */
data class Country(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val nationalLength: Int?,
) {
    /** Regional-indicator pair derived from the ISO code, so no flag assets are needed. */
    val flag: String
        get() = isoCode.uppercase().map { char ->
            String(Character.toChars(FLAG_OFFSET + (char - 'A')))
        }.joinToString("")

    companion object {
        private const val FLAG_OFFSET = 0x1F1E6
    }
}

object Countries {

    const val DEFAULT_ISO = "IN"

    /** Digits allowed when a country has no single fixed national length. */
    val FALLBACK_LENGTH_RANGE = 6..14

    val ALL: List<Country> = listOf(
        Country("IN", "India", "91", 10),
        Country("US", "United States", "1", 10),
        Country("CA", "Canada", "1", 10),
        Country("GB", "United Kingdom", "44", 10),
        Country("AU", "Australia", "61", 9),
        Country("NZ", "New Zealand", "64", null),
        Country("IE", "Ireland", "353", null),
        Country("AE", "United Arab Emirates", "971", 9),
        Country("SA", "Saudi Arabia", "966", 9),
        Country("QA", "Qatar", "974", 8),
        Country("KW", "Kuwait", "965", 8),
        Country("BH", "Bahrain", "973", 8),
        Country("OM", "Oman", "968", 8),
        Country("SG", "Singapore", "65", 8),
        Country("MY", "Malaysia", "60", null),
        Country("ID", "Indonesia", "62", null),
        Country("TH", "Thailand", "66", 9),
        Country("VN", "Vietnam", "84", 9),
        Country("PH", "Philippines", "63", 10),
        Country("HK", "Hong Kong", "852", 8),
        Country("CN", "China", "86", 11),
        Country("JP", "Japan", "81", null),
        Country("KR", "South Korea", "82", null),
        Country("TW", "Taiwan", "886", 9),
        Country("PK", "Pakistan", "92", 10),
        Country("BD", "Bangladesh", "880", 10),
        Country("LK", "Sri Lanka", "94", 9),
        Country("NP", "Nepal", "977", 10),
        Country("BT", "Bhutan", "975", 8),
        Country("MV", "Maldives", "960", 7),
        Country("AF", "Afghanistan", "93", 9),
        Country("DE", "Germany", "49", null),
        Country("FR", "France", "33", 9),
        Country("ES", "Spain", "34", 9),
        Country("IT", "Italy", "39", null),
        Country("PT", "Portugal", "351", 9),
        Country("NL", "Netherlands", "31", 9),
        Country("BE", "Belgium", "32", null),
        Country("CH", "Switzerland", "41", 9),
        Country("AT", "Austria", "43", null),
        Country("SE", "Sweden", "46", null),
        Country("NO", "Norway", "47", 8),
        Country("DK", "Denmark", "45", 8),
        Country("FI", "Finland", "358", null),
        Country("PL", "Poland", "48", 9),
        Country("CZ", "Czechia", "420", 9),
        Country("GR", "Greece", "30", 10),
        Country("RO", "Romania", "40", 9),
        Country("HU", "Hungary", "36", 9),
        Country("RU", "Russia", "7", 10),
        Country("UA", "Ukraine", "380", 9),
        Country("TR", "Türkiye", "90", 10),
        Country("IL", "Israel", "972", 9),
        Country("EG", "Egypt", "20", 10),
        Country("ZA", "South Africa", "27", 9),
        Country("NG", "Nigeria", "234", 10),
        Country("KE", "Kenya", "254", 9),
        Country("TZ", "Tanzania", "255", 9),
        Country("UG", "Uganda", "256", 9),
        Country("GH", "Ghana", "233", 9),
        Country("ET", "Ethiopia", "251", 9),
        Country("MA", "Morocco", "212", 9),
        Country("BR", "Brazil", "55", 11),
        Country("AR", "Argentina", "54", 10),
        Country("CL", "Chile", "56", 9),
        Country("CO", "Colombia", "57", 10),
        Country("PE", "Peru", "51", 9),
        Country("MX", "Mexico", "52", 10),
    ).sortedBy { it.name }

    fun default(): Country = byIso(DEFAULT_ISO) ?: ALL.first()

    fun byIso(isoCode: String): Country? = ALL.firstOrNull { it.isoCode.equals(isoCode, true) }

    fun byDialCode(dialCode: String): Country? {
        val normalized = dialCode.removePrefix("+")
        // Several countries share a dial code (+1, +7); prefer the default when it matches.
        return ALL.firstOrNull { it.dialCode == normalized && it.isoCode == DEFAULT_ISO }
            ?: ALL.firstOrNull { it.dialCode == normalized }
    }

    fun search(query: String): List<Country> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ALL
        val digits = trimmed.removePrefix("+")
        return ALL.filter { country ->
            country.name.contains(trimmed, ignoreCase = true) ||
                country.isoCode.equals(trimmed, ignoreCase = true) ||
                country.dialCode.startsWith(digits)
        }
    }
}
