package com.traduciar.por

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** En qué situación está el modelo de traducción (el "diccionario" que se descarga). */
enum class EstadoModelo { VERIFICANDO, DESCARGANDO, LISTO, ERROR }

/**
 * Todo lo que la pantalla necesita saber para dibujarse.
 * Cada vez que algo cambia, se crea una copia nueva y la pantalla se redibuja sola.
 */
data class EstadoPantalla(
    val estadoModelo: EstadoModelo = EstadoModelo.VERIFICANDO,
    val escuchando: Boolean = false,
    val traduciendo: Boolean = false,
    val textoOriginal: String = "",
    val textoTraducido: String = "",
    val nivelVoz: Float = 0f,          // 0..1, para animar el botón mientras hablás
    val mensaje: String? = null        // Aviso corto que se muestra abajo (Snackbar)
)

/**
 * El ViewModel es el "cerebro" de la pantalla. Sobrevive cuando girás el celular
 * y se encarga de:
 *   1) Escuchar tu voz (SpeechRecognizer)
 *   2) Traducir de español a portugués (ML Kit, sin internet)
 *   3) Leer la traducción en voz alta (TextToSpeech)
 *   4) Guardar el historial
 */
class TraductorViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    // "_estado" se modifica acá adentro; "estado" es la versión de solo lectura para la pantalla.
    private val _estado = MutableStateFlow(EstadoPantalla())
    val estado: StateFlow<EstadoPantalla> = _estado.asStateFlow()

    // ---------------- Historial ----------------
    private val repositorio = HistorialRepository(application)
    val historial: StateFlow<List<Traduccion>> = repositorio.historial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- Traductor (ML Kit) ----------------
    private val traductor = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.PORTUGUESE) // ML Kit usa portugués de Brasil
            .build()
    )

    // ---------------- Lectura en voz alta (TTS) ----------------
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    // ---------------- Reconocimiento de voz ----------------
    private var reconocedor: SpeechRecognizer? = null
    // Idioma con el que escuchamos. Si el celular no soporta "es-419", probamos "es-US".
    private var idiomaVoz = "es-419"

    init {
        prepararModelo()
        prepararVozPortugues()
    }

    // =====================================================================
    // 1) MODELO DE TRADUCCIÓN
    // =====================================================================

    /**
     * Revisa si el modelo español→portugués ya está en el celular.
     * Si no está, lo descarga (solo esta vez hace falta internet, unos 60 MB).
     */
    fun prepararModelo() {
        viewModelScope.launch {
            _estado.update { it.copy(estadoModelo = EstadoModelo.VERIFICANDO) }
            try {
                val gestor = RemoteModelManager.getInstance()
                val espanol = TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()
                val portugues = TranslateRemoteModel.Builder(TranslateLanguage.PORTUGUESE).build()
                // "await()" espera el resultado sin congelar la pantalla
                val yaEsta = gestor.isModelDownloaded(espanol).await() &&
                        gestor.isModelDownloaded(portugues).await()

                if (!yaEsta) {
                    if (!hayInternet()) {
                        _estado.update {
                            it.copy(
                                estadoModelo = EstadoModelo.ERROR,
                                mensaje = "Conectate a internet una sola vez para descargar el traductor."
                            )
                        }
                        return@launch
                    }
                    _estado.update {
                        it.copy(
                            estadoModelo = EstadoModelo.DESCARGANDO,
                            mensaje = "Descargando el traductor (una sola vez)…"
                        )
                    }
                    // Descarga con cualquier red (datos o WiFi). Si querés solo WiFi: .requireWifi()
                    traductor.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                    _estado.update {
                        it.copy(mensaje = "¡Listo! Ya podés traducir sin internet.")
                    }
                }
                _estado.update { it.copy(estadoModelo = EstadoModelo.LISTO) }
            } catch (e: Exception) {
                _estado.update {
                    it.copy(
                        estadoModelo = EstadoModelo.ERROR,
                        mensaje = "No se pudo descargar el traductor. Revisá la conexión y reintentá."
                    )
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

        tts?.stop() // Si estaba hablando, que se calle para no escucharse a sí mismo

        // El reconocedor se crea una sola vez y se reutiliza
        val r = reconocedor ?: SpeechRecognizer.createSpeechRecognizer(app).also {
            it.setRecognitionListener(escuchador)
            reconocedor = it
        }

        // El "Intent" es el pedido que le hacemos al servicio de voz de Android
        val pedido = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idiomaVoz)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // ver el texto mientras hablás
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Sin internet, le pedimos que use el reconocimiento dentro del celular
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !hayInternet())
        }

        _estado.update {
            it.copy(escuchando = true, textoOriginal = "", textoTraducido = "", nivelVoz = 0f)
        }
        r.startListening(pedido)
    }

    /** Termina de escuchar y procesa lo que se dijo hasta ahora. */
    fun dejarDeEscuchar() {
        reconocedor?.stopListening()
    }

    /** Cancela todo (por ejemplo, si la app pasa a segundo plano). */
    fun cancelarEscucha() {
        if (_estado.value.escuchando) {
            reconocedor?.cancel()
            _estado.update { it.copy(escuchando = false, nivelVoz = 0f) }
        }
    }

    /** Recibe los "eventos" del reconocedor de voz: empezó, oyó algo, terminó, error… */
    private val escuchador = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        // Volumen de la voz (aprox. -2 a 10). Lo pasamos a 0..1 para animar el botón.
        override fun onRmsChanged(rmsdB: Float) {
            _estado.update { it.copy(nivelVoz = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }

        // Texto parcial: aparece en pantalla mientras todavía estás hablando
        override fun onPartialResults(partialResults: Bundle?) {
            val texto = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!texto.isNullOrBlank()) {
                _estado.update { it.copy(textoOriginal = texto) }
            }
        }

        // Resultado final: lo traducimos
        override fun onResults(results: Bundle?) {
            val texto = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _estado.update { it.copy(escuchando = false, nivelVoz = 0f, textoOriginal = texto) }
            if (texto.isBlank()) {
                mostrarMensaje("No te escuché bien. Probá de nuevo.")
            } else {
                traducir(texto)
            }
        }

        override fun onError(error: Int) {
            _estado.update { it.copy(escuchando = false, nivelVoz = 0f) }

            // Si el idioma "es-419" no está disponible, reintentamos con "es-US" (también latino)
            val idiomaNoDisponible = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            if (idiomaNoDisponible && idiomaVoz == "es-419") {
                idiomaVoz = "es-US"
                empezarAEscuchar()
                return
            }

            val texto = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché bien. Probá de nuevo."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER ->
                    "Sin internet: descargá el idioma español \"sin conexión\" en los ajustes de voz de Google."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado. Esperá un segundo."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    "El reconocimiento de voz en español no está disponible en este celular."
                SpeechRecognizer.ERROR_CLIENT -> null // pasa cuando cancelamos nosotros: no es un error real
                else -> "Error de reconocimiento de voz (código $error)."
            }
            texto?.let { mostrarMensaje(it) }
        }
    }

    // =====================================================================
    // 3) TRADUCCIÓN
    // =====================================================================

    private fun traducir(texto: String) {
        if (_estado.value.estadoModelo != EstadoModelo.LISTO) {
            mostrarMensaje("El traductor todavía no terminó de descargarse.")
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(traduciendo = true) }
            try {
                val resultado = traductor.translate(texto).await()
                _estado.update { it.copy(textoTraducido = resultado) }
                repositorio.agregar(Traduccion(original = texto, traducido = resultado))
                hablar(resultado)
            } catch (e: Exception) {
                mostrarMensaje("No se pudo traducir: ${e.localizedMessage}")
            } finally {
                _estado.update { it.copy(traduciendo = false) }
            }
        }
    }

    // =====================================================================
    // 4) LECTURA EN VOZ ALTA
    // =====================================================================

    private fun prepararVozPortugues() {
        tts = TextToSpeech(app) { estadoTts ->
            if (estadoTts == TextToSpeech.SUCCESS) {
                val resultado = tts?.setLanguage(Locale.forLanguageTag("pt-BR"))
                ttsListo = resultado != TextToSpeech.LANG_MISSING_DATA &&
                        resultado != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsListo) {
                    mostrarMensaje(
                        "Falta la voz en portugués. Instalala en Ajustes > Sistema > Idioma > Salida de texto a voz."
                    )
                }
            } else {
                mostrarMensaje("No se pudo iniciar la lectura en voz alta.")
            }
        }
    }

    /** Lee un texto en portugués de Brasil. */
    fun hablar(texto: String) {
        if (texto.isBlank()) return
        if (!ttsListo) {
            mostrarMensaje("La voz en portugués no está disponible.")
            return
        }
        // QUEUE_FLUSH = si estaba leyendo otra cosa, la corta y lee esta
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "traduccion")
    }

    /** Botón "Repetir": vuelve a leer la última traducción. */
    fun repetir() = hablar(_estado.value.textoTraducido)

    // =====================================================================
    // OTRAS ACCIONES
    // =====================================================================

    /** Copia un texto al portapapeles para pegarlo en WhatsApp, etc. */
    fun copiar(texto: String) {
        if (texto.isBlank()) return
        val portapapeles = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        portapapeles.setPrimaryClip(ClipData.newPlainText("Traducción", texto))
        // Desde Android 13 el sistema ya muestra su propio aviso de "copiado"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            mostrarMensaje("Traducción copiada")
        }
    }

    fun borrarHistorial() {
        viewModelScope.launch { repositorio.borrarTodo() }
    }

    private fun mostrarMensaje(texto: String) {
        _estado.update { it.copy(mensaje = texto) }
    }

    /** La pantalla avisa que ya mostró el mensaje, así no se repite. */
    fun mensajeMostrado() {
        _estado.update { it.copy(mensaje = null) }
    }

    /** ¿Hay conexión a internet en este momento? */
    private fun hayInternet(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val red = cm.activeNetwork ?: return false
        val capacidades = cm.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Se llama cuando se cierra la pantalla: liberamos micrófono, voz y traductor. */
    override fun onCleared() {
        reconocedor?.destroy()
        tts?.stop()
        tts?.shutdown()
        traductor.close()
        super.onCleared()
    }
}
