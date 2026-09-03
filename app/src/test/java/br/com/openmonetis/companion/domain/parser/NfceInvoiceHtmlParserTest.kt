package br.com.openmonetis.companion.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfceInvoiceHtmlParserTest {

    @Test
    fun `extracts amount labeled as Valor a pagar`() {
        val html = """
            <html><body>
                <div>Emitente</div>
                <div>Valor a pagar R$: <strong>31,49</strong></div>
            </body></html>
        """.trimIndent()

        val text = NfceInvoiceHtmlParser.stripHtml(html)

        assertEquals(31.49, NfceInvoiceHtmlParser.parseAmount(text)!!, 0.001)
    }

    @Test
    fun `extracts amount labeled as Valor Total with thousands separator`() {
        val html = "<span>Valor Total</span><span>R$ 1.234,56</span>"

        val text = NfceInvoiceHtmlParser.stripHtml(html)

        assertEquals(1234.56, NfceInvoiceHtmlParser.parseAmount(text)!!, 0.001)
    }

    @Test
    fun `returns null amount when no known label is present`() {
        val text = NfceInvoiceHtmlParser.stripHtml("<p>Item 1 R\$ 10,00</p><p>Item 2 R\$ 5,00</p>")

        assertNull(NfceInvoiceHtmlParser.parseAmount(text))
    }

    @Test
    fun `extracts merchant name from Razao Social label`() {
        val html = "<div>Razão Social: DOCE ROMA PADARIA E CO LTDA</div><div>CNPJ: 12.345.678/0001-99</div>"

        val text = NfceInvoiceHtmlParser.stripHtml(html)

        assertEquals("DOCE ROMA PADARIA E CO LTDA", NfceInvoiceHtmlParser.parseMerchantName(text))
    }

    @Test
    fun `falls back to the line before the CNPJ when there is no label`() {
        val html = """
            <html><body>
                <div>DOCE ROMA PADARIA E CO</div>
                <div>12.345.678/0001-99</div>
                <div>Valor Total R$ 31,49</div>
            </body></html>
        """.trimIndent()

        val text = NfceInvoiceHtmlParser.stripHtml(html)

        assertEquals("DOCE ROMA PADARIA E CO", NfceInvoiceHtmlParser.parseMerchantName(text))
    }

    @Test
    fun `returns null merchant name when there is no label and no CNPJ`() {
        val text = NfceInvoiceHtmlParser.stripHtml("<p>Sem dados suficientes aqui.</p>")

        assertNull(NfceInvoiceHtmlParser.parseMerchantName(text))
    }

    @Test
    fun `decodes html entities and strips tags into plain lines`() {
        val html = "<div>Padaria &amp; Cia</div><br><div>Valor&nbsp;Total R\$ 9,90</div>"

        val text = NfceInvoiceHtmlParser.stripHtml(html)

        assertEquals("Padaria & Cia\nValor Total R$ 9,90", text)
    }
}
