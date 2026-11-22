package com.traduciar.por

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué situación están los modelos de traducción (los "diccionarios" que se descargan). */
enum class EstadoModelo { VERIFICANDO, DESCARGANDO, LISTO, SIN_CONEXION, ERROR }

/**
 * Todo lo que la pantalla necesita saber para dibujarse.
 * Cada vez que algo cambia, se crea una copia nueva y la pantalla se redibuja sola.
 */
data class EstadoPantalla(
    // Descarga de modelos
    val estadoModelo: EstadoModelo = EstadoModelo.VERIFICANDO,
    val progresoDescarga: Float = 0f,        // 0..1
    val textoDescarga: String = "",

    // Escucha y traducción
    val escuchando: Boolean = false,
    val traduciendo: Boolean = false,
    val textoOriginal: String = "",
    val textoTraducido: String = "",
    val origen: Idioma? = null,              // Idioma en que hablaste (null = todavía nada)
    val detectadoAuto: Boolean = false,      // true = lo detectó la app sola
    val confianza: Float? = null,            // Confianza de la detección (0..1)
    val dudaIdioma: Boolean = false,         // true = confianza baja: preguntar al usuario
    val nivelVoz: Float = 0f,                // 0..1, para animar el botón mientras hablás

    // Voces
    val vocesListas: Boolean = false,
    val voces: Map<Idioma, InfoVoz> = emptyMap(),

    val mensaje: String? = null              // Aviso corto que se muestra abajo (Snackbar)
)

/**
 * El "cerebro" de la pantalla. Sobrevive cuando girás el celular y coordina:
 *   1) Escuchar tu voz (SpeechRecognizer)
 *   2) Detectar si hablaste en español o portugués (ML Kit Language ID)
 *   3) Traducir al otro idioma (ML Kit Translation, sin internet)
 *   4) Leer la traducción con voz femenina o masculina (TextToSpeech)
 *   5) Guardar historial y ajustes
 */
class TraductorViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    private val _estado = MutableStateFlow(EstadoPantalla())
    val estado: StateFlow<EstadoPantalla> = _estado.asStateFlow()

    // ---------------- Ajustes e historial (guardados en el celular) ----------------
    private val repoAjustes = AjustesRepository(application)
    val ajustes: StateFlow<Ajustes> = repoAjustes.ajustes
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ajustes())

    private val repoHistorial = HistorialRepository(application)
    val historial: StateFlow<List<Traduccion>> = repoHistorial.historial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- Motores ----------------
    private val motor = MotorTraduccion()
    private val voz = MotorVoz(application) { ok ->
        _estado.update { it.copy(vocesListas = ok) }
        if (ok) actualizarVoces() else mostrarMensaje("No se pudo iniciar la lectura en voz alta.")
    }

    private var reconocedor: SpeechRecognizer? = null
    private var idiomaEspanolVoz = "es-419"   // si el celular no lo soporta, pasamos a "es-US"
    private var ultimaFueGuardada = false     // para corregir el historial si cambian el idioma
    private val avisosDeVozDados = mutableSetOf<Idioma>()

    // Si se recupera internet y faltaba descargar, reintenta solo
    private val vigilanteRed = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val e = _estado.value.estadoModelo
            if (e == EstadoModelo.SIN_CONEXION || e == EstadoModelo.ERROR) prepararModelos()
        }
    }

    init {
        prepararModelos()
        conectividad().registerDefaultNetworkCallback(vigilanteRed)
        // Cuando se cargan (o cambian) los ajustes guardados, recalculamos las voces
        viewModelScope.launch {
            ajustes.collect { if (voz.listo) actualizarVoces() }
        }
    }

    // =====================================================================
    // 1) MODELOS DE TRADUCCIÓN
    // =====================================================================

    /**
     * Revisa qué modelos faltan (español y/o portugués) y los descarga de a uno,
     * mostrando el avance. Solo hace falta internet esta primera vez.
     */
    fun prepararModelos() {
        viewModelScope.launch {
            if (_estado.value.estadoModelo == EstadoModelo.DESCARGANDO) return@launch
            _estado.update { it.copy(estadoModelo = EstadoModelo.VERIFICANDO) }
            try {
                val faltan = motor.idiomasFaltantes()
                if (faltan.isEmpty()) {
                    _estado.update { it.copy(estadoModelo = EstadoModelo.LISTO, progresoDescarga = 1f) }
                    return@launch
                }
                if (!hayInternet()) {
                    _estado.update { it.copy(estadoModelo = EstadoModelo.SIN_CONEXION) }
                    return@launch
                }
                faltan.forEachIndexed { i, idioma ->
                    _estado.update {
                        it.copy(
                            estadoModelo = EstadoModelo.DESCARGANDO,
                            progresoDescarga = i.toFloat() / faltan.size,
                            textoDescarga = "Descargando ${idioma.nombre} (${i + 1} de ${faltan.size})…"
                        )
                    }
                    motor.descargar(idioma)
                }
                _estado.update {
                    it.copy(
                        estadoModelo = EstadoModelo.LISTO,
                        progresoDescarga = 1f,
                        mensaje = "¡Listo! Ya podés traducir sin internet."
                    )
                }
            } catch (e: Exception) {
                _estado.update {
                    it.copy(estadoModelo = if (hayInternet()) EstadoModelo.ERROR else EstadoModelo.SIN_CONEXION)
                }
            }
        }
    }

    // =====================================================================
    // 2) RECONOCIMIENTO DE VOZ
    // =====================================================================

    /** Empieza a escuchar. La pantalla llama a esto solo si ya hay permiso de micrófono. */
    fun empezarAEscuchar() {
        if (_estado.value.escuchando) return
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            mostrarMensaje("Este celular no tiene reconocimiento de voz. Instalá o actualizá la app \"Google\".")
            return
        }
        voz.callar() // que no se escuche a sí misma

        val r = reconocedor ?: SpeechRecognizer.createSpeechRecognizer(app).also {
            it.setRecognitionListener(escuchador)
            reconocedor = it
        }

        // En modo automático escuchamos en español y le avisamos al reconocedor que
        // también puede venir portugués. En modo fijo, escuchamos en ese idioma.
        val modo = ajustes.value.modo
        val idiomaPrincipal = when (modo.origenFijo) {
            Idioma.PT -> Idioma.PT.etiquetaVoz
            else -> idiomaEspanolVoz
        }

        val pedido = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idiomaPrincipal)
            if (modo == ModoIdioma.AUTO) {
                // Extra que entiende el reconocedor de Google: idiomas adicionales posibles
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(Idioma.PT.etiquetaVoz))
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !hayInternet())
        }

        _estado.update {
            it.copy(
                escuchando = true, textoOriginal = "", textoTraducido = "",
                origen = null, dudaIdioma = false, confianza = null, nivelVoz = 0f
            )
        }
        r.startListening(pedido)
    }

    fun dejarDeEscuchar() {
        reconocedor?.stopListening()
    }

    fun cancelarEscucha() {
        if (_estado.value.escuchando) {
            reconocedor?.cancel()
            _estado.update { it.copy(escuchando = false, nivelVoz = 0f) }
        }
    }

    private val escuchador = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            _estado.update { it.copy(nivelVoz = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texto = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!texto.isNullOrBlank()) _estado.update { it.copy(textoOriginal = texto) }
        }

        override fun onResults(results: Bundle?) {
            val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            _estado.update { it.copy(escuchando = false, nivelVoz = 0f, textoOriginal = texto) }
            if (texto.isBlank()) mostrarMensaje("No te escuché bien. Probá de nuevo.")
            else procesar(texto)
        }

        override fun onError(error: Int) {
            _estado.update { it.copy(escuchando = false, nivelVoz = 0f) }

            val idiomaNoDisponible = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            // Si "es-419" no está disponible, reintentamos con "es-US" (también latino)
            if (idiomaNoDisponible && idiomaEspanolVoz == "es-419" && ajustes.value.modo.origenFijo != Idioma.PT) {
                idiomaEspanolVoz = "es-US"
                empezarAEscuchar()
                return
            }

            val texto = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché bien. Probá de nuevo."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER ->
                    "Sin internet: descargá el idioma \"sin conexión\" en los ajustes de voz de Google."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado. Esperá un segundo."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    "Este celular no reconoce voz en ese idioma. Instalalo en los ajustes de voz de Google."
                SpeechRecognizer.ERROR_CLIENT -> null // pasa al cancelar nosotros: no es un error real
                else -> "Error de reconocimiento de voz (código $error)."
            }
            texto?.let { mostrarMensaje(it) }
        }
    }

    // =====================================================================
    // 3) DETECCIÓN + TRADUCCIÓN
    // =====================================================================

    /** Decide el idioma de origen (detectándolo o según el modo elegido) y traduce. */
    private fun procesar(texto: String) {
        if (_estado.value.estadoModelo != EstadoModelo.LISTO) {
            mostrarMensaje("El traductor todavía no terminó de descargarse.")
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(traduciendo = true) }
            val fijo = ajustes.value.modo.origenFijo
            if (fijo != null) {
                traducirYMostrar(texto, fijo, auto = false, confianza = null, duda = false, corrigiendo = false)
            } else {
                val d = motor.detectar(texto)
                traducirYMostrar(texto, d.idioma, auto = true, confianza = d.confianza, duda = !d.segura, corrigiendo = false)
            }
        }
    }

    /**
     * Botón "Era español" / "Era português": el usuario corrige el idioma detectado.
     * Volvemos a traducir y reemplazamos la entrada del historial.
     */
    fun corregirIdioma(idioma: Idioma) {
        val texto = _estado.value.textoOriginal
        if (texto.isBlank() || _estado.value.traduciendo) return
        viewModelScope.launch {
            _estado.update { it.copy(traduciendo = true) }
            traducirYMostrar(texto, idioma, auto = false, confianza = null, duda = false, corrigiendo = true)
        }
    }

    private suspend fun traducirYMostrar(
        texto: String, origen: Idioma, auto: Boolean, confianza: Float?, duda: Boolean, corrigiendo: Boolean
    ) {
        try {
            val resultado = motor.traducir(texto, origen)
            _estado.update {
                it.copy(
                    textoTraducido = resultado, origen = origen,
                    detectadoAuto = auto, confianza = confianza, dudaIdioma = duda
                )
            }
            val entrada = Traduccion(original = texto, traducido = resultado, origen = origen)
            if (corrigiendo && ultimaFueGuardada) repoHistorial.reemplazarUltima(entrada)
            else repoHistorial.agregar(entrada)
            ultimaFueGuardada = true
            hablar(resultado, origen.otro())
        } catch (e: Exception) {
            ultimaFueGuardada = false
            mostrarMensaje("No se pudo traducir: ${e.localizedMessage}")
        } finally {
            _estado.update { it.copy(traduciendo = false) }
        }
    }

    // =====================================================================
    // 4) VOZ
    // =====================================================================

    /** Elige las voces según el género guardado. Si [avisar], informa si falta alguna. */
    private fun actualizarVoces(avisar: Boolean = false) {
        val genero = ajustes.value.genero
        val info = voz.elegirVoces(genero)
        _estado.update { it.copy(voces = info) }
        if (avisar) {
            val sinGenero = info.filter { it.value.hayVoz && !it.value.generoEncontrado }.keys
            if (sinGenero.isNotEmpty()) {
                mostrarMensaje(
                    "No hay voz ${genero.etiqueta.lowercase()} en " +
                            sinGenero.joinToString(" ni ") { it.nombre } +
                            ". Uso la que hay; podés instalar más voces desde Ajustes."
                )
            }
        }
    }

    /** Se llama al volver a la app (por si instalaste voces nuevas en los ajustes del sistema). */
    fun refrescarVoces() {
        if (voz.listo) actualizarVoces()
    }

    fun hablar(texto: String, idioma: Idioma) {
        if (texto.isBlank()) return
        if (!voz.listo) {
            mostrarMensaje("La lectura en voz alta todavía no está lista.")
            return
        }
        val info = _estado.value.voces[idioma]
        if (info != null && !info.hayVoz) {
            mostrarMensaje("No hay voz en ${idioma.nombre} instalada. Instalala desde Ajustes ⚙️.")
            return
        }
        // Aviso (una vez por sesión) si no hay voz del género elegido
        if (info != null && !info.generoEncontrado && avisosDeVozDados.add(idioma)) {
            mostrarMensaje(
                "No encontré voz ${ajustes.value.genero.etiqueta.lowercase()} en ${idioma.nombre}; uso la disponible."
            )
        }
        if (!voz.hablar(texto, idioma)) {
            mostrarMensaje("No se pudo leer en ${idioma.nombre}. Revisá las voces en Ajustes ⚙️.")
        }
    }

    /** Botón "Repetir". */
    fun repetir() {
        val e = _estado.value
        e.origen?.let { hablar(e.textoTraducido, it.otro()) }
    }

    /** Botón "Probar" de Ajustes. */
    fun probarVoz(idioma: Idioma) {
        val frase = if (idioma == Idioma.ES) "Hola, así suena la voz en español."
        else "Olá, assim soa a voz em português."
        hablar(frase, idioma)
    }

    // =====================================================================
    // 5) AJUSTES Y OTRAS ACCIONES
    // =====================================================================

    fun cambiarGenero(genero: Genero) {
        viewModelScope.launch {
            repoAjustes.guardarGenero(genero)
            ajustes.first { it.genero == genero } // esperar a que quede guardado
            avisosDeVozDados.clear()
            if (voz.listo) actualizarVoces(avisar = true)
        }
    }

    fun cambiarModo(modo: ModoIdioma) {
        viewModelScope.launch { repoAjustes.guardarModo(modo) }
    }

    fun copiar(texto: String) {
        if (texto.isBlank()) return
        val portapapeles = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        portapapeles.setPrimaryClip(ClipData.newPlainText("Traducción", texto))
        // Desde Android 13 el sistema ya muestra su propio aviso de "copiado"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) mostrarMensaje("Traducción copiada")
    }

    fun borrarHistorial() {
        viewModelScope.launch { repoHistorial.borrarTodo() }
        ultimaFueGuardada = false
    }

    private fun mostrarMensaje(texto: String) {
        _estado.update { it.copy(mensaje = texto) }
    }

    fun mensajeMostrado() {
        _estado.update { it.copy(mensaje = null) }
    }

    private fun conectividad() = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun hayInternet(): Boolean {
        val cm = conectividad()
        val red = cm.activeNetwork ?: return false
        val capacidades = cm.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        runCatching { conectividad().unregisterNetworkCallback(vigilanteRed) }
        reconocedor?.destroy()
        voz.cerrar()
        motor.cerrar()
        super.onCleared()
    }
}
