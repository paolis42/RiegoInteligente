package cl.inacap.riegointeligente.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import cl.inacap.riegointeligente.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Interfaz de enlace: permite cambiar el medio (Wi-Fi/HTTP o BLE) sin tocar la UI.
interface Enlace {
    suspend fun leer(): Lectura?
    suspend fun enviar(accion: String): String
    suspend fun enviarConFirmaAlterada(accion: String): String
    suspend fun enviarUltimoRepetido(): String
}

object ProtocoloRiego {
    fun calcularChecksum(datos: String): Int =
        datos.sumOf { it.code } % 256

    fun parsearLectura(linea: String, ahoraMs: Long = System.currentTimeMillis()): Lectura {
        val partes = linea.trim().split(";")
        require(partes.size == 5 && partes[0] == "LEC") { "formato invalido" }
        require(partes[3] == "ON" || partes[3] == "OFF") { "estado invalido" }
        val datos = partes.subList(1, 4).joinToString(";")
        val csEsperado = calcularChecksum(datos)
        require(partes[4].toInt() == csEsperado) { "checksum invalido" }
        return Lectura(
            valor = partes[1].toFloat(),
            umbral = partes[2].toFloat(),
            actuador = partes[3] == "ON",
            ts = ahoraMs,
            sincronizada = true
        )
    }

    fun firmarHmacSha256(mensaje: String, clave: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(clave.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(mensaje.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

class EnlaceHttp(
    baseUrl: String,
    private val context: Context,
    private val clave: String = BuildConfig.HMAC_KEY
) : Enlace {

    private val base: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val comandoMutex = Mutex()
    private var contador: Long = System.currentTimeMillis()
    private var ultimaAccion: String? = null
    private var ultimoContadorUsado: Long? = null
    private var ultimaFirmaUsada: String? = null

    override suspend fun leer(): Lectura? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${base}lectura")
            val con = abrirConexion(url)
            val linea = try {
                val code = con.responseCode
                if (code != 200) throw IOException("HTTP $code")
                con.inputStream.bufferedReader().use { it.readText() }.trim()
            } finally {
                con.disconnect()
            }
            val lectura = ProtocoloRiego.parsearLectura(linea)
            Log.i("HZB", "lectura ok ${lectura.valor}% umbral=${lectura.umbral}% bomba=${if (lectura.actuador) "ON" else "OFF"}")
            lectura
        }.onFailure {
            Log.w("HZB", "lectura fallida: ${it.message}")
        }.getOrNull()
    }

    override suspend fun enviar(accion: String): String = withContext(Dispatchers.IO) {
        comandoMutex.withLock {
            contador++
            val n = contador
            val sig = ProtocoloRiego.firmarHmacSha256("CMD;$accion;$n", clave)
            ultimaAccion = accion
            ultimoContadorUsado = n
            ultimaFirmaUsada = sig
            ejecutarCmd(accion, n, sig, etiqueta = "cmd")
        }
    }

    override suspend fun enviarConFirmaAlterada(accion: String): String = withContext(Dispatchers.IO) {
        comandoMutex.withLock {
            contador++
            val n = contador
            val sigReal = ProtocoloRiego.firmarHmacSha256("CMD;$accion;$n", clave)
            val ultimoChar = if (sigReal.last() == '0') '1' else '0'
            val sigAlterada = sigReal.dropLast(1) + ultimoChar
            ejecutarCmd(accion, n, sigAlterada, etiqueta = "cmd_firma_alterada")
        }
    }

    override suspend fun enviarUltimoRepetido(): String = withContext(Dispatchers.IO) {
        comandoMutex.withLock {
            val acc = ultimaAccion
            val n = ultimoContadorUsado
            val sig = ultimaFirmaUsada
            if (acc == null || n == null || sig == null) {
                "SIN_COMANDO_PREVIO"
            } else {
                ejecutarCmd(acc, n, sig, etiqueta = "cmd_repetido")
            }
        }
    }

    private fun ejecutarCmd(
        accion: String,
        n: Long,
        sig: String,
        etiqueta: String
    ): String {
        return try {
            val url = URL("${base}cmd?accion=$accion&n=$n&sig=$sig")
            val con = abrirConexion(url)
            try {
                val code = con.responseCode
                val stream = if (code < 400) con.inputStream else (con.errorStream ?: con.inputStream)
                val cuerpo = stream.bufferedReader().use { it.readText() }.trim()
                Log.i("HZB", "$etiqueta $accion -> $code $cuerpo")
                if (code < 400) cuerpo else "HTTP $code: $cuerpo"
            } finally {
                con.disconnect()
            }
        } catch (e: IOException) {
            Log.w("HZB", "$etiqueta $accion fallo red: ${e.message}")
            "ERROR_RED: ${e.message ?: "sin conexion"}"
        }
    }

    private fun abrirConexion(url: URL): HttpURLConnection {
        val host = url.host
        val usarLoopbackDirecto = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
        val con = if (usarLoopbackDirecto) {
            url.openConnection() as HttpURLConnection
        } else {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val wifi = cm?.allNetworks?.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            (wifi?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        }
        con.requestMethod = "GET"
        con.connectTimeout = 3000
        con.readTimeout = 3000
        return con
    }
}
