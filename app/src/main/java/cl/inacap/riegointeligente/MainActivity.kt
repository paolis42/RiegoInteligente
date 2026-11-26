package cl.inacap.riegointeligente

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.inacap.riegointeligente.data.ComandoPendiente
import cl.inacap.riegointeligente.data.Lectura
import cl.inacap.riegointeligente.data.RepositorioUsuarios
import cl.inacap.riegointeligente.ui.theme.RiegoInteligenteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Verde = Color(0xFF52796F)
private val VerdeSuave = Color(0xFFE5F0E8)
private val Fondo = Color(0xFFFAF9F6)
private val AlertaBg = Color(0xFFFFF3CD)
private val AlertaTexto = Color(0xFF8A5A00)
private val RojoError = Color(0xFFB3261E)

class MainActivity : ComponentActivity() {
    private val riegoViewModel: RiegoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val boardUrlExtra = intent.getStringExtra("board_url")?.trim()?.takeIf { it.isNotEmpty() }
        val usuarioExtra = intent.getStringExtra("usuario")?.trim()?.takeIf { it.isNotEmpty() }

        if (BuildConfig.DEBUG && boardUrlExtra != null && usuarioExtra != null) {
            riegoViewModel.configurarYComenzar(boardUrlExtra, autoPruebaProtocolo = true)
        }

        setContent {
            RiegoInteligenteTheme {
                SolicitarPermisoRedLocalSiAplica()

                var correoSesion by rememberSaveable { mutableStateOf<String?>(null) }

                if (correoSesion == null) {
                    PantallaLogin(
                        onLoginCorrecto = { correo ->
                            correoSesion = correo
                        }
                    )
                } else {
                    PantallaDashboard(
                        correo = correoSesion!!,
                        boardUrlExtra = boardUrlExtra,
                        autoPruebaProtocolo = false,
                        onCerrarSesion = {
                            riegoViewModel.detenerSondeo()
                            correoSesion = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SolicitarPermisoRedLocalSiAplica() {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current
    val permiso = "android.permission.ACCESS_LOCAL_NETWORK"
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permiso) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(permiso)
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

    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    var modoRegistro by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var esError by remember { mutableStateOf(false) }

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
            Text(text = "🌱", fontSize = 64.sp)

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
                text = if (modoRegistro) "Crear cuenta" else "Bienvenido",
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                        correo.isBlank() || contrasena.isBlank() -> {
                            mensaje = "Completa todos los campos"
                            esError = true
                        }

                        !correoValido -> {
                            mensaje = "Ingresa un correo válido"
                            esError = true
                        }

                        modoRegistro && contrasena.length < 8 -> {
                            mensaje = "La contraseña debe tener al menos 8 caracteres"
                            esError = true
                        }

                        else -> {
                            cargando = true
                            mensaje = ""

                            scope.launch {
                                try {
                                    if (modoRegistro) {
                                        val registrado = repositorio.registrar(correo, contrasena)
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
                                        val usuario = repositorio.iniciarSesion(correo, contrasena)
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
                colors = ButtonDefaults.buttonColors(containerColor = Verde)
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
                    color = if (esError) RojoError else Verde,
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
    boardUrlExtra: String?,
    autoPruebaProtocolo: Boolean,
    onCerrarSesion: () -> Unit,
    vm: RiegoViewModel = viewModel()
) {
    LaunchedEffect(boardUrlExtra, autoPruebaProtocolo) {
        vm.configurarYComenzar(boardUrlExtra, autoPruebaProtocolo)
    }

    val estadoConexion by vm.estadoConexion.collectAsState()
    val boardUrl by vm.boardUrl.collectAsState()
    val lecturas by vm.lecturas.collectAsState()
    val comandos by vm.historialComandos.collectAsState()
    val pendientes by vm.pendientesCount.collectAsState()
    val ultimoResultado by vm.ultimoResultado.collectAsState()

    val ultima = lecturas.firstOrNull()
    var umbralSlider by remember(ultima?.umbral) {
        mutableFloatStateOf(ultima?.umbral ?: 35f)
    }
    var urlInput by remember(boardUrl) { mutableStateOf(boardUrl) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Fondo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🌱 Riego Inteligente",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Verde
                    )
                    Text(
                        text = "Sesión: $correo",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                OutlinedButton(onClick = onCerrarSesion) {
                    Text("Salir", fontSize = 12.sp)
                }
            }

            val conectado = estadoConexion == "Conectado"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (conectado) VerdeSuave else AlertaBg,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = if (conectado) "📡 ESP32: Conectado" else "⚠️ ESP32: $estadoConexion",
                        fontWeight = FontWeight.SemiBold,
                        color = if (conectado) Verde else AlertaTexto,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Enlace: $boardUrl" + if (pendientes > 0) " · Pendientes: $pendientes" else "",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VerdeSuave),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Telemetría actual",
                        fontWeight = FontWeight.Bold,
                        color = Verde,
                        fontSize = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DatoMetrica(
                            titulo = "💧 Humedad",
                            valor = ultima?.let { "${String.format(Locale.US, "%.1f", it.valor)} %" } ?: "--"
                        )
                        DatoMetrica(
                            titulo = "🎯 Umbral",
                            valor = ultima?.let { "${String.format(Locale.US, "%.1f", it.umbral)} %" } ?: "--"
                        )
                        DatoMetrica(
                            titulo = "🚰 Bomba",
                            valor = ultima?.let { if (it.actuador) "ENCENDIDA" else "APAGADA" } ?: "--"
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📈 Historial de humedad (Room / SQLite)",
                        fontWeight = FontWeight.Bold,
                        color = Verde,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    GraficoHumedad(
                        lecturas = lecturas,
                        umbral = ultima?.umbral ?: umbralSlider,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎛️ Control remoto firmado (HMAC-SHA256)",
                        fontWeight = FontWeight.Bold,
                        color = Verde,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Umbral objetivo: ${umbralSlider.roundToInt()}%",
                        fontSize = 14.sp
                    )
                    Slider(
                        value = umbralSlider,
                        onValueChange = { umbralSlider = it },
                        valueRange = 10f..90f
                    )

                    Button(
                        onClick = {
                            vm.enviarComando("UMBRAL:${umbralSlider.roundToInt()}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Verde)
                    ) {
                        Text("Aplicar umbral (${umbralSlider.roundToInt()}%)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { vm.enviarComando("ACT:ON") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Verde)
                        ) {
                            Text("Encender", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { vm.enviarComando("ACT:OFF") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apagar", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { vm.enviarComando("ACT:AUTO") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Auto", fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "🔐 Pruebas de seguridad (ISO 27400)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.probarFirmaAlterada() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Firma inválida (401)", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                        OutlinedButton(
                            onClick = { vm.probarAntiRepeticion() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Repetido (409)", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Text(
                        text = "Última respuesta: $ultimoResultado",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🗂️ Registro local (Room)",
                        fontWeight = FontWeight.Bold,
                        color = Verde,
                        fontSize = 15.sp
                    )
                    if (lecturas.isEmpty()) {
                        Text("Sin lecturas guardadas todavía.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        lecturas.take(5).forEach { lec ->
                            Text(
                                text = "${formatearHora(lec.ts)} · Humedad ${lec.valor}% · Umbral ${lec.umbral}% · Bomba ${if (lec.actuador) "ON" else "OFF"}",
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (comandos.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Comandos recientes:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        comandos.take(5).forEach { cmd ->
                            ItemComando(cmd)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚙️ Dirección del ESP32",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("URL placa") }
                        )
                        Button(
                            onClick = { vm.actualizarUrlPlaca(urlInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = Verde)
                        ) {
                            Text("Usar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoMetrica(titulo: String, valor: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = titulo, fontSize = 12.sp, color = Color.DarkGray)
        Spacer(Modifier.height(4.dp))
        Text(text = valor, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Verde)
    }
}

@Composable
private fun ItemComando(cmd: ComandoPendiente) {
    val estado = if (cmd.sincronizada) "✓" else "⏳ Pendiente"
    Text(
        text = "${formatearHora(cmd.creadoEn)} · ${cmd.accion} [$estado] ${cmd.respuesta ?: ""}",
        fontSize = 12.sp,
        color = if (cmd.sincronizada) Color.DarkGray else AlertaTexto
    )
}

@Composable
private fun GraficoHumedad(
    lecturas: List<Lectura>,
    umbral: Float,
    modifier: Modifier = Modifier
) {
    val ordenadas = remember(lecturas) { lecturas.reversed() }

    if (ordenadas.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Esperando lecturas para graficar...", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 12f
        val usableW = (w - 2 * pad).coerceAtLeast(1f)
        val usableH = (h - 2 * pad).coerceAtLeast(1f)

        // Linea guia del umbral
        val yUmbral = pad + usableH * (1f - (umbral.coerceIn(0f, 100f) / 100f))
        drawLine(
            color = Color(0xFFD97706),
            start = Offset(pad, yUmbral),
            end = Offset(w - pad, yUmbral),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )

        val pasoX = if (ordenadas.size > 1) usableW / (ordenadas.size - 1) else 0f
        val path = Path()
        ordenadas.forEachIndexed { idx, lec ->
            val x = if (ordenadas.size == 1) w / 2f else pad + idx * pasoX
            val y = pad + usableH * (1f - (lec.valor.coerceIn(0f, 100f) / 100f))
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(
                color = if (lec.actuador) Color(0xFF1D4ED8) else Verde,
                radius = 5f,
                center = Offset(x, y)
            )
        }
        if (ordenadas.size > 1) {
            drawPath(
                path = path,
                color = Verde,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun formatearHora(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ts))
}
