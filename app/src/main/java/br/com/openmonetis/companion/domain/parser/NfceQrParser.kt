package br.com.openmonetis.companion.domain.parser

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

data class NfceQrResult(
    val accessKey: String,
    val rawContent: String
)

/**
 * Parser for NFC-e (Nota Fiscal de Consumidor Eletrônica) QR codes.
 * The query URL format varies by state, but always carries a 44-digit
 * access key either as `p=<chave>|<versao>|<tpAmb>|<idToken>|<hash>`
 * (current layout) or as the legacy `chNFe=<chave>`.
 */
@Singleton
class NfceQrParser @Inject constructor() {

    fun parse(rawValue: String): NfceQrResult? {
        val decoded = try {
            URLDecoder.decode(rawValue, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            rawValue
        } catch (e: IllegalArgumentException) {
            rawValue
        }

        P_PARAM_REGEX.find(decoded)?.let { return NfceQrResult(it.groupValues[1], rawValue) }
        CHNFE_PARAM_REGEX.find(decoded)?.let { return NfceQrResult(it.groupValues[1], rawValue) }

        return null
    }

    fun formatAccessKey(accessKey: String): String = accessKey.chunked(4).joinToString(" ")

    companion object {
        const val NFCE_SOURCE_APP = "openmonetis:nfce-scanner"

        private val P_PARAM_REGEX = Regex("""[?&]p=(\d{44})(?:\||&|$)""")
        private val CHNFE_PARAM_REGEX = Regex("""[?&]chNFe=(\d{44})(?:&|$)""")
    }
}
