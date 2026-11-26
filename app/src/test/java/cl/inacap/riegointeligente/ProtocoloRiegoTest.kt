package cl.inacap.riegointeligente

import cl.inacap.riegointeligente.data.ProtocoloRiego
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloRiegoTest {

    @Test
    fun checksumValidoParseaLecturaCorrectamente() {
        val datos = "23.4;40.0;OFF"
        val cs = ProtocoloRiego.calcularChecksum(datos)
        assertEquals(218, cs)

        val lectura = ProtocoloRiego.parsearLectura("LEC;$datos;$cs", ahoraMs = 1234L)
        assertEquals(23.4f, lectura.valor, 0.01f)
        assertEquals(40.0f, lectura.umbral, 0.01f)
        assertFalse(lectura.actuador)
        assertEquals(1234L, lectura.ts)
        assertTrue(lectura.sincronizada)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checksumAlteradoLanzaExcepcion() {
        ProtocoloRiego.parsearLectura("LEC;23.4;40.0;OFF;99")
    }

    @Test(expected = IllegalArgumentException::class)
    fun estadoDesconocidoEsRechazado() {
        val datos = "23.4;40.0;UNKNOWN"
        ProtocoloRiego.parsearLectura("LEC;$datos;${ProtocoloRiego.calcularChecksum(datos)}")
    }

    @Test
    fun hmacSha256EsDeterministaYHex64() {
        val h1 = ProtocoloRiego.firmarHmacSha256("CMD;ACT:ON;1", "riego-hmac-demo-2026")
        val h2 = ProtocoloRiego.firmarHmacSha256("CMD;ACT:ON;1", "riego-hmac-demo-2026")
        val h3 = ProtocoloRiego.firmarHmacSha256("CMD;ACT:OFF;1", "riego-hmac-demo-2026")
        assertEquals(64, h1.length)
        assertEquals(h1, h2)
        assertFalse(h1 == h3)
    }

    @Test
    fun hmacSha256CoincideConVectorConocido() {
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            ProtocoloRiego.firmarHmacSha256(
                "The quick brown fox jumps over the lazy dog",
                "key"
            )
        )
    }
}
