package cl.inacap.riegointeligente

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Run inside Hertzbench through `instrument:` in eva2-login.yaml, or with
// ./gradlew connectedDebugAndroidTest on any emulator.
@RunWith(AndroidJUnit4::class)
class LoginTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    // A fresh address per test, so the local database never already holds it.
    private val correo = "test${System.nanoTime()}@riego.cl"

    private fun escribirCredenciales(clave: String) {
        rule.onNodeWithText("Correo electrónico").performTextInput(correo)
        rule.onNodeWithText("Contraseña").performTextInput(clave)
    }

    @Test
    fun withoutSessionControlsAreHidden() {
        rule.onNodeWithText("Iniciar sesión").assertIsDisplayed()
        rule.onNodeWithText("Encender").assertDoesNotExist()
        rule.onNodeWithText("Apagar").assertDoesNotExist()
    }

    @Test
    fun unknownCredentialsAreRejected() {
        escribirCredenciales("claveIncorrecta1")
        rule.onNodeWithText("Iniciar sesión").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTextExists("Correo o contraseña incorrectos")
        }
        rule.onNodeWithText("Encender").assertDoesNotExist()
    }

    @Test
    fun wrongPasswordForRegisteredUserIsRejected() {
        registrar("claveCorrecta1")
        escribirContrasenaYEntrar("otraClave99")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTextExists("Correo o contraseña incorrectos")
        }
        rule.onNodeWithText("Encender").assertDoesNotExist()
    }

    @Test
    fun registeredUserReachesDashboard() {
        registrar("claveCorrecta1")
        escribirContrasenaYEntrar("claveCorrecta1")
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Encender") }
        rule.onNodeWithText("Salir").assertIsDisplayed()
    }

    private fun registrar(clave: String) {
        rule.onNodeWithText("¿No tienes cuenta? Regístrate").performClick()
        escribirCredenciales(clave)
        rule.onNodeWithText("Registrarme").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTextExists("Cuenta creada. Ahora inicia sesión.")
        }
    }

    // After registering, the e-mail stays filled in and the password is cleared.
    private fun escribirContrasenaYEntrar(clave: String) {
        rule.onNodeWithText("Contraseña").performTextInput(clave)
        rule.onNodeWithText("Iniciar sesión").performClick()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(
    texto: String
): Boolean = onAllNodes(androidx.compose.ui.test.hasText(texto)).fetchSemanticsNodes().isNotEmpty()
