
package cl.inacap.riegointeligente.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class RepositorioUsuarios(context: Context) {

    private val usuarioDao =
        AppDatabase.obtener(context).usuarioDao()

    // Registrar un nuevo usuario
    suspend fun registrar(
        correo: String,
        contrasena: String
    ): Boolean {

        val correoLimpio = correo.trim()
            .lowercase(Locale.ROOT)

        if (correoLimpio.isBlank() ||
            contrasena.length < 8
        ) {
            return false
        }

        val existente =
            usuarioDao.buscarPorCorreo(correoLimpio)

        if (existente != null) {
            return false
        }

        val salt = SeguridadContrasena.crearSalt()

        val hash = withContext(Dispatchers.Default) {
            SeguridadContrasena.generarHash(
                contrasena,
                salt
            )
        }

        val nuevoUsuario = Usuario(
            correo = correoLimpio,
            passwordHash = hash,
            salt = salt
        )

        usuarioDao.insertarUsuario(nuevoUsuario)

        return true
    }

    // Verificar credenciales al iniciar sesión
    suspend fun iniciarSesion(
        correo: String,
        contrasena: String
    ): Usuario? {

        val correoLimpio = correo.trim()
            .lowercase(Locale.ROOT)

        val usuario =
            usuarioDao.buscarPorCorreo(correoLimpio)
                ?: return null

        val correcta = withContext(Dispatchers.Default) {
            SeguridadContrasena.verificar(
                contrasena,
                usuario.salt,
                usuario.passwordHash
            )
        }

        return if (correcta) usuario else null
    }
}
