package br.com.openmonetis.companion.domain.parser

import br.com.openmonetis.companion.di.ExternalHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class NfceInvoiceDetails(
    val merchantName: String?,
    val amount: Double?
)

/**
 * Downloads the "consulta" page a NFC-e QR Code links to and extracts the
 * total value and the merchant name from it. Each state runs its own portal
 * with its own markup (and some render content via JavaScript, which a plain
 * HTTP GET can't see), so this is best-effort: it never throws and returns
 * null when the page can't be reached or nothing could be parsed from it.
 */
@Singleton
class NfceInvoiceFetcher @Inject constructor(
    @ExternalHttpClient private val httpClient: OkHttpClient
) {

    suspend fun fetch(url: String): NfceInvoiceDetails? {
        if (!url.startsWith("http", ignoreCase = true)) return null

        val html = downloadHtml(url) ?: return null
        val text = NfceInvoiceHtmlParser.stripHtml(html)

        return NfceInvoiceDetails(
            merchantName = NfceInvoiceHtmlParser.parseMerchantName(text),
            amount = NfceInvoiceHtmlParser.parseAmount(text)
        )
    }

    private suspend fun downloadHtml(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) OpenMonetisCompanion/1.0"
    }
}
