package br.com.openmonetis.companion.domain.parser

/**
 * Best-effort text extraction from the HTML page a NFC-e QR Code points to.
 * Each Brazilian state (Sefaz) runs its own "consulta" portal with its own
 * markup, so this works over the page's plain text using generic labels
 * instead of a fixed DOM structure. Never throws; returns null when a value
 * isn't found.
 */
object NfceInvoiceHtmlParser {

    fun stripHtml(html: String): String {
        val withoutNoise = html
            .replace(SCRIPT_OR_STYLE_REGEX, " ")
            .replace(BLOCK_TAG_REGEX, "\n")
            .replace(TAG_REGEX, " ")

        return decodeHtmlEntities(withoutNoise)
            .lines()
            .map { it.trim().replace(EXTRA_SPACE_REGEX, " ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun parseAmount(text: String): Double? {
        for (pattern in AMOUNT_LABEL_PATTERNS) {
            pattern.find(text)?.let { match ->
                toDouble(match.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    fun parseMerchantName(text: String): String? {
        for (pattern in MERCHANT_LABEL_PATTERNS) {
            pattern.find(text)?.let { match ->
                val candidate = match.groupValues[1].trim()
                if (candidate.length >= 2) return candidate.take(80)
            }
        }

        // Fallback: the emitter's name is almost always the line right before
        // its CNPJ on a NFC-e receipt, even when there's no explicit label.
        val cnpjMatch = CNPJ_REGEX.find(text) ?: return null
        val before = text.substring(0, cnpjMatch.range.first)
        val candidate = before.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }
        return candidate?.takeIf { it.length in 2..80 }
    }

    private fun toDouble(rawValue: String): Double? =
        rawValue.replace(".", "").replace(",", ".").toDoubleOrNull()

    private fun decodeHtmlEntities(text: String): String {
        var result = text
        NAMED_ENTITIES.forEach { (entity, replacement) -> result = result.replace(entity, replacement) }
        return NUMERIC_ENTITY_REGEX.replace(result) { match ->
            match.groupValues[1].toIntOrNull()
                ?.let { codePoint -> runCatching { String(Character.toChars(codePoint)) }.getOrNull() }
                ?: match.value
        }
    }

    private val SCRIPT_OR_STYLE_REGEX = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val BLOCK_TAG_REGEX = Regex("(?is)</?\\s*(br|p|div|tr|li|h[1-6])[^>]*>")
    private val TAG_REGEX = Regex("(?is)<[^>]+>")
    private val EXTRA_SPACE_REGEX = Regex("[ \\t\\x0B\\f\\r]+")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);")
    private val CNPJ_REGEX = Regex("""\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}""")

    private val NAMED_ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'"
    )

    private val AMOUNT_LABEL_PATTERNS = listOf(
        Regex("""Valor\s+a\s+pagar\D{0,20}?([\d.]+,\d{2})""", RegexOption.IGNORE_CASE),
        Regex("""Valor\s+total\s+da\s+nota\D{0,20}?([\d.]+,\d{2})""", RegexOption.IGNORE_CASE),
        Regex("""Valor\s+Total\D{0,20}?([\d.]+,\d{2})""", RegexOption.IGNORE_CASE),
        Regex("""Valor\s+da\s+compra\D{0,20}?([\d.]+,\d{2})""", RegexOption.IGNORE_CASE)
    )

    private val MERCHANT_LABEL_PATTERNS = listOf(
        Regex("""Nome\s*/\s*Raz[aã]o\s+Social\s*:?\s*([^\n]+)""", RegexOption.IGNORE_CASE),
        Regex("""Raz[aã]o\s+Social\s*:?\s*([^\n]+)""", RegexOption.IGNORE_CASE),
        Regex("""Nome\s+Fantasia\s*:?\s*([^\n]+)""", RegexOption.IGNORE_CASE)
    )
}
