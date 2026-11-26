package cl.inacap.riegointeligente

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.inacap.riegointeligente.data.AppDatabase
import cl.inacap.riegointeligente.data.ComandoPendiente
import cl.inacap.riegointeligente.data.Enlace
import cl.inacap.riegointeligente.data.EnlaceHttp
import cl.inacap.riegointeligente.data.Lectura
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RiegoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.obtener(application)
    private val lecturaDao = db.lecturaDao()
    private val comandoDao = db.comandoDao()

    private val _boardUrl = MutableStateFlow("http://192.168.4.11/")
    val boardUrl: StateFlow<String> = _boardUrl.asStateFlow()

    private var enlace: Enlace = EnlaceHttp(_boardUrl.value, application)

    private val _estadoConexion = MutableStateFlow("Conectando...")
    val estadoConexion: StateFlow<String> = _estadoConexion.asStateFlow()

    private val _ultimoResultado = MutableStateFlow("Sin comandos enviados aún")
    val ultimoResultado: StateFlow<String> = _ultimoResultado.asStateFlow()

    val lecturas: StateFlow<List<Lectura>> = lecturaDao.observarRecientes(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historialComandos: StateFlow<List<ComandoPendiente>> = comandoDao.observarHistorial(15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendientesCount: StateFlow<Int> = comandoDao.contarPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var monitorJob: Job? = null
    private var pruebaInicialEjecutada = false

    fun configurarYComenzar(urlInicial: String?, autoPruebaProtocolo: Boolean) {
        val urlLimpia = urlInicial?.trim()?.takeIf { it.isNotEmpty() } ?: "http://192.168.4.11/"
        if (_boardUrl.value != urlLimpia || monitorJob == null) {
            _boardUrl.value = urlLimpia
            enlace = EnlaceHttp(urlLimpia, getApplication())
            iniciarSondeo(autoPruebaProtocolo)
        }
    }

    fun actualizarUrlPlaca(nuevaUrl: String) {
        val limpia = nuevaUrl.trim()
        if (limpia.isEmpty()) return
        _boardUrl.value = limpia
        enlace = EnlaceHttp(limpia, getApplication())
        _estadoConexion.value = "Reconectando..."
        iniciarSondeo(autoPruebaProtocolo = false)
    }

    fun detenerSondeo() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun iniciarSondeo(autoPruebaProtocolo: Boolean) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            var espera = 1_000L
            while (isActive) {
                val lectura = enlace.leer()
                if (lectura != null) {
                    lecturaDao.insertar(lectura)
                    espera = 1_000L
                    _estadoConexion.value = "Conectado"
                    reenviarPendientes()

                    if (autoPruebaProtocolo && !pruebaInicialEjecutada) {
                        pruebaInicialEjecutada = true
                        ejecutarSecuenciaProtocolo()
                    }
                } else {
                    _estadoConexion.value = "Reconectando..."
                    Log.w("HZB", "Reconectando... siguiente intento en ${espera}ms")
                    espera = minOf(espera * 2, 30_000L)
                }
                delay(if (lectura != null) 2_000L else espera)
            }
        }
    }

    private suspend fun reenviarPendientes() {
        val pendientes = comandoDao.obtenerPendientes()
        for (cmd in pendientes) {
            val res = enlace.enviar(cmd.accion)
            if (res.startsWith("ACK;")) {
                comandoDao.marcarSincronizado(cmd.id, res)
                _ultimoResultado.value = "Reenviado al reconectar: $res"
            } else if (res.startsWith("ERROR_RED:")) {
                break
            } else {
                comandoDao.marcarSincronizado(cmd.id, res)
            }
        }
    }

    fun enviarComando(accion: String) {
        viewModelScope.launch {
            enviarComandoSuspend(accion)
        }
    }

    private suspend fun enviarComandoSuspend(accion: String) {
        val res = enlace.enviar(accion)
        if (res.startsWith("ERROR_RED:")) {
            comandoDao.insertar(
                ComandoPendiente(
                    accion = accion,
                    sincronizada = false,
                    respuesta = "En cola local (sin conexión)"
                )
            )
            _estadoConexion.value = "Reconectando..."
            _ultimoResultado.value = "Sin conexión: '$accion' guardado en Room para reenviar"
        } else {
            comandoDao.insertar(
                ComandoPendiente(
                    accion = accion,
                    sincronizada = true,
                    respuesta = res
                )
            )
            _ultimoResultado.value = res
            enlace.leer()?.let { lecturaDao.insertar(it) }
        }
    }

    fun probarFirmaAlterada() {
        viewModelScope.launch {
            val res = enlace.enviarConFirmaAlterada("ACT:OFF")
            comandoDao.insertar(
                ComandoPendiente(
                    accion = "TEST_FIRMA_ALTERADA",
                    sincronizada = true,
                    respuesta = res
                )
            )
            _ultimoResultado.value = "Prueba firma alterada -> $res"
        }
    }

    fun probarAntiRepeticion() {
        viewModelScope.launch {
            val res = enlace.enviarUltimoRepetido()
            comandoDao.insertar(
                ComandoPendiente(
                    accion = "TEST_REPETICION",
                    sincronizada = true,
                    respuesta = res
                )
            )
            _ultimoResultado.value = "Prueba anti-repetición -> $res"
        }
    }

    private suspend fun ejecutarSecuenciaProtocolo() {
        Log.i("HZB", "Iniciando secuencia de verificacion EVA2")
        enviarComandoSuspend("UMBRAL:40")
        delay(500L)
        enviarComandoSuspend("ACT:ON")
        delay(500L)
        val resRepetido = enlace.enviarUltimoRepetido()
        delay(500L)
        val resAlt = enlace.enviarConFirmaAlterada("ACT:OFF")
        _ultimoResultado.value = "Pruebas EVA2: repetición $resRepetido; firma $resAlt"
    }

    override fun onCleared() {
        monitorJob?.cancel()
        super.onCleared()
    }
}
