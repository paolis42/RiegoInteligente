
package cl.inacap.riegointeligente.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ClienteHertzbench {

    private const val URL_ESTADO =
        "http://10.0.2.2:8080/status"

    suspend fun consultarEstado(): String =
        withContext(Dispatchers.IO) {

            val conexion = URL(URL_ESTADO)
                .openConnection() as HttpURLConnection

            try {
                conexion.requestMethod = "GET"
                conexion.connectTimeout = 5000
                conexion.readTimeout = 5000

                if (conexion.responseCode != 200) {
                    throw Exception(
                        "Error HTTP: ${conexion.responseCode}"
                    )
                }

                conexion.inputStream.bufferedReader().use {
                    it.readText()
                }

            } finally {
                conexion.disconnect()
            }
        }
}
