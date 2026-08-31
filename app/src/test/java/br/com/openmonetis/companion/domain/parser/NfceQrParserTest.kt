package br.com.openmonetis.companion.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfceQrParserTest {

    private val parser = NfceQrParser()

    @Test
    fun `extracts access key from SP NFC-e QR code`() {
        val raw = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=35250812345678000199650010000000011000000010|2|1|1|A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0"

        val result = parser.parse(raw)

        assertEquals("35250812345678000199650010000000011000000010", result!!.accessKey)
        assertEquals(raw, result.rawContent)
    }

    @Test
    fun `extracts access key from PR NFC-e QR code`() {
        val raw = "http://www.fazenda.pr.gov.br/nfce/qrcode?p=41250812345678000199650010000000011000000010|2|1|1|B1C2D3E4F5A6B7C8D9E0F1A2B3C4D5E6F7A8B9C0"

        val result = parser.parse(raw)

        assertEquals("41250812345678000199650010000000011000000010", result!!.accessKey)
    }

    @Test
    fun `extracts access key from MG NFC-e QR code`() {
        val raw = "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p=31250812345678000199650010000000011000000010|2|1|1|C1D2E3F4A5B6C7D8E9F0A1B2C3D4E5F6A7B8C9D0"

        val result = parser.parse(raw)

        assertEquals("31250812345678000199650010000000011000000010", result!!.accessKey)
    }

    @Test
    fun `extracts access key from legacy chNFe query param`() {
        val raw = "https://nfe.sefaz.ba.gov.br/servicos/nfce/qrcode.aspx?chNFe=29250812345678000199650010000000011000000010&nVersao=100&tpAmb=1"

        val result = parser.parse(raw)

        assertEquals("29250812345678000199650010000000011000000010", result!!.accessKey)
    }

    @Test
    fun `extracts access key when pipe is url-encoded`() {
        val raw = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=35250812345678000199650010000000011000000010%7C2%7C1%7C1%7CA1B2C3D4"

        val result = parser.parse(raw)

        assertEquals("35250812345678000199650010000000011000000010", result!!.accessKey)
    }

    @Test
    fun `rejects pix payload`() {
        val raw = "00020126580014br.gov.bcb.pix0136a629534e-7693-4846-b028-f142082d7b0752040000530398654041.005802BR5913Fulano de Tal6008Brasilia62070503***6304ABCD"

        assertNull(parser.parse(raw))
    }

    @Test
    fun `rejects arbitrary text`() {
        assertNull(parser.parse("https://example.com/not-an-nfce"))
    }

    @Test
    fun `rejects access key shorter than 44 digits`() {
        val raw = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=123456789|2|1|1|hash"

        assertNull(parser.parse(raw))
    }
}
