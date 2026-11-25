
package cl.inacap.riegointeligente

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import cl.inacap.riegointeligente.data.RepositorioUsuarios
import cl.inacap.riegointeligente.ui.theme.RiegoInteligenteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val Verde = Color(0xFF52796F)
private val Fondo = Color(0xFFFAF9F6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RiegoInteligenteTheme {
                var correoSesion by remember {
                    mutableStateOf<String?>(null)
                }

                if (correoSesion == null) {
                    PantallaLogin(
                        onLoginCorrecto = { correo ->
                            correoSesion = correo
                        }
                    )
                } else {
                    PantallaDashboard(
                        correo = correoSesion!!,
                        onCerrarSesion = {
                            correoSesion = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaLogin(
    onLoginCorrecto: (String) -> Unit
) {
    val context = LocalContext.current
    val repositorio = remember {
        RepositorioUsuarios(context.applicationContext)
    }
    val scope = rememberCoroutineScope()

    var correo by rememberSaveable {
        mutableStateOf("")
    }
    var contrasena by rememberSaveable {
        mutableStateOf("")
    }
    var modoRegistro by rememberSaveable {
        mutableStateOf(false)
    }
    var cargando by remember {
        mutableStateOf(false)
    }
    var mensaje by remember {
        mutableStateOf("")
    }
    var esError by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Fondo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🌱",
                fontSize = 64.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Riego Inteligente",
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold,
                color = Verde,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Cuida tus plantas de forma inteligente",
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(35.dp))

            Text(
                text = if (modoRegistro) {
                    "Crear cuenta"
                } else {
                    "Bienvenido"
                },
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = Verde
            )

            Spacer(Modifier.height(22.dp))

            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo electrónico") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !cargando,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(15.dp))

            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !cargando,
                singleLine = true,
                visualTransformation =
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                shape = RoundedCornerShape(16.dp)
            )

            if (modoRegistro) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Usa al menos 8 caracteres.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    val correoValido = Patterns.EMAIL_ADDRESS
                        .matcher(correo.trim()).matches()

                    when {
                        correo.isBlank() ||
                                contrasena.isBlank() -> {
                            mensaje = "Completa todos los campos"
                            esError = true
                        }

                        !correoValido -> {
                            mensaje = "Ingresa un correo válido"
                            esError = true
                        }

                        modoRegistro &&
                                contrasena.length < 8 -> {
                            mensaje = "La contraseña debe tener al menos 8 caracteres"
                            esError = true
                        }

                        else -> {
                            cargando = true
                            mensaje = ""

                            scope.launch {
                                try {
                                    if (modoRegistro) {
                                        val registrado =
                                            repositorio.registrar(
                                                correo,
                                                contrasena
                                            )

                                        if (registrado) {
                                            mensaje = "Cuenta creada. Ahora inicia sesión."
                                            esError = false
                                            contrasena = ""
                                            modoRegistro = false
                                        } else {
                                            mensaje = "No se pudo registrar. Revisa los datos o usa otro correo."
                                            esError = true
                                        }
                                    } else {
                                        val usuario =
                                            repositorio.iniciarSesion(
                                                correo,
                                                contrasena
                                            )

                                        if (usuario != null) {
                                            contrasena = ""
                                            onLoginCorrecto(usuario.correo)
                                        } else {
                                            mensaje = "Correo o contraseña incorrectos"
                                            esError = true
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    mensaje = "No se pudo completar la operación."
                                    esError = true
                                } finally {
                                    cargando = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !cargando,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Verde
                )
            ) {
                Text(
                    if (cargando) "Procesando..."
                    else if (modoRegistro) "Registrarme"
                    else "Iniciar sesión"
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                enabled = !cargando,
                onClick = {
                    modoRegistro = !modoRegistro
                    mensaje = ""
                    contrasena = ""
                }
            ) {
                Text(
                    text = if (modoRegistro) {
                        "¿Ya tienes una cuenta? Inicia sesión"
                    } else {
                        "¿No tienes cuenta? Regístrate"
                    },
                    color = Verde
                )
            }

            if (mensaje.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = mensaje,
                    color = if (esError) {
                        Color(0xFFB3261E)
                    } else Verde,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(25.dp))

            Text(
                text = "Sistema de monitoreo y control de riego IoT",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PantallaDashboard(
    correo: String,
    onCerrarSesion: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Fondo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            var estadoServidor by remember {
                mutableStateOf("Comprobando conexión...")
            }

            LaunchedEffect(Unit) {
                estadoServidor = try {
                    cl.inacap.riegointeligente.data.ClienteHertzbench
                        .consultarEstado()
                    "Conectado"
                } catch (e: Exception) {
                    "Desconectado"
                }
            }

            Text(
                text = "📡 Servidor IoT: $estadoServidor",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("🌱", fontSize = 52.sp)

            Spacer(Modifier.height(12.dp))

            Text(
                "Panel de Riego Inteligente",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Verde,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Sesión iniciada: $correo",
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(25.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE5F0E8)
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "💧 Humedad del suelo",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Sin lecturas todavía")

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "🚰 Estado de la bomba",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Estado desconocido")

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "📡 Conexión ESP32",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Aún sin configurar")
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Próximamente conectaremos este panel con el ESP32 emulado mediante Wi-Fi.",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(30.dp))

            Button(
                onClick = onCerrarSesion,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Verde
                )
            ) {
                Text("Cerrar sesión")
            }
        }
    }
}
