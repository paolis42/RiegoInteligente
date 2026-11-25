
package cl.inacap.riegointeligente.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SeguridadContrasena {

    private const val ITERACIONES = 600_000
    private const val LONGITUD_HASH = 256

    fun crearSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        return Base64.encodeToString(
            salt,
            Base64.NO_WRAP
        )
    }

    fun generarHash(
        contrasena: String,
        salt: String
    ): String {
        val saltBytes = Base64.decode(
            salt,
            Base64.NO_WRAP
        )

        val especificacion = PBEKeySpec(
            contrasena.toCharArray(),
            saltBytes,
            ITERACIONES,
            LONGITUD_HASH
        )

        return try {
            val clave = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
            ).generateSecret(especificacion)

            Base64.encodeToString(
                clave.encoded,
                Base64.NO_WRAP
            )
        } finally {
            especificacion.clearPassword()
        }
    }

    fun verificar(
        contrasena: String,
        salt: String,
        hashGuardado: String
    ): Boolean {
        return try {
            val hashCalculado = Base64.decode(
                generarHash(contrasena, salt),
                Base64.NO_WRAP
            )

            val hashOriginal = Base64.decode(
                hashGuardado,
                Base64.NO_WRAP
            )

            MessageDigest.isEqual(
                hashCalculado,
                hashOriginal
            )
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
