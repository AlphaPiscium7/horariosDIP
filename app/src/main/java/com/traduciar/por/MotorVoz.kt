package com.traduciar.por

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

/**
 * Qué voz se va a usar para un idioma, y si coincide con el género elegido.
 */
data class InfoVoz(
    val hayVoz: Boolean,             // ¿Hay alguna voz instalada para este idioma?
    val generoEncontrado: Boolean,   // ¿Es del género que eligió el usuario?
    val generoReal: Genero?,         // Género de la voz elegida (null = no se sabe)
    val nombre: String?              // Nombre técnico de la voz (para curiosos)
)

/**
 * Se encarga de leer en voz alta con el TextToSpeech de Android,
 * eligiendo una voz femenina o masculina para cada idioma.
 *
 * Ojo: Android NO dice oficialmente si una voz es de hombre o de mujer.
 * Lo deducimos del nombre de la voz (ver [generoDe]). Por eso en Ajustes
 * hay un botón "Probar" para escucharla.
 */
class MotorVoz(context: Context, private val alEstarListo: (Boolean) -> Unit) {

    private var tts: TextToSpeech? = null
    var listo = false
        private set

    // Voz elegida para cada idioma (se recalcula al cambiar el género)
    private val vocesElegidas = mutableMapOf<Idioma, Voice?>()

    init {
        tts = TextToSpeech(context.applicationContext) { estado ->
            listo = estado == TextToSpeech.SUCCESS
            alEstarListo(listo)
        }
    }

    /**
     * Elige la mejor voz de cada idioma para el género pedido.
     * Devuelve la información para mostrar avisos en pantalla.
     */
    fun elegirVoces(genero: Genero): Map<Idioma, InfoVoz> {
        val todas = vocesInstaladas()
        return Idioma.entries.associateWith { idioma ->
            val candidatas = todas.filter { it.locale.language == idioma.codigo }
            if (candidatas.isEmpty()) {
                vocesElegidas[idioma] = null
                return@associateWith InfoVoz(false, false, null, null)
            }
            // Ordenamos de mejor a peor: género correcto > acento preferido > sin internet > calidad
            val mejor = candidatas.maxByOrNull { voz ->
                var puntos = 0
                if (generoDe(voz) == genero) puntos += 1000
                val posAcento = idioma.localesVoz.indexOfFirst { coincideAcento(voz, it) }
                if (posAcento >= 0) puntos += (idioma.localesVoz.size - posAcento) * 20
                if (!voz.isNetworkConnectionRequired) puntos += 10
                puntos += voz.quality / 100
                puntos
            }!!
            vocesElegidas[idioma] = mejor
            val generoReal = generoDe(mejor)
            InfoVoz(
                hayVoz = true,
                generoEncontrado = generoReal == genero,
                generoReal = generoReal,
                nombre = mejor.name
            )
        }
    }

    /** Lee [texto] en voz alta con la voz elegida para [idioma]. Devuelve false si no pudo. */
    fun hablar(texto: String, idioma: Idioma): Boolean {
        val motor = tts ?: return false
        if (!listo || texto.isBlank()) return false
        val voz = vocesElegidas[idioma]
        val ok = if (voz != null) {
            motor.setVoice(voz) == TextToSpeech.SUCCESS
        } else {
            // Sin lista de voces: probamos con el idioma directamente
            idioma.localesVoz.any {
                val r = motor.setLanguage(it)
                r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        if (!ok) return false
        // QUEUE_FLUSH = si estaba leyendo otra cosa, la corta
        return motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "traduccion") == TextToSpeech.SUCCESS
    }

    fun callar() {
        tts?.stop()
    }

    fun cerrar() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // ------------------------- Ayudas -------------------------

    /** Voces instaladas de verdad (descarta las que figuran pero no están descargadas). */
    private fun vocesInstaladas(): List<Voice> = try {
        tts?.voices.orEmpty().filter {
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }
    } catch (e: Exception) {
        emptyList() // Algunos motores de voz fallan al listar: seguimos sin lista
    }

    private fun coincideAcento(voz: Voice, deseado: java.util.Locale): Boolean =
        voz.locale.language == deseado.language &&
                (deseado.country.isEmpty() || voz.locale.country.equals(deseado.country, ignoreCase = true))

    /**
     * Intenta averiguar si la voz es femenina o masculina mirando su nombre:
     *  - Google: "es-us-x-sfb#female_1-local", "pt-br-x-afs#male_2-local"
     *  - Samsung: "SMTf00" (f = femenina), "SMTm00" (m = masculina)
     *  - Otros motores: "female" / "male" en el nombre o en sus características
     *  - Voces de Google sin etiqueta: tabla aproximada por código.
     */
    private fun generoDe(voz: Voice): Genero? {
        val texto = (voz.name + " " + voz.features.orEmpty().joinToString(" ")).lowercase()
        if (Regex("(?<![a-z])female").containsMatchIn(texto)) return Genero.FEMENINA
        if (Regex("(?<![a-z])male").containsMatchIn(texto)) return Genero.MASCULINA
        Regex("smt([fm])\\d").find(texto)?.let {
            return if (it.groupValues[1] == "f") Genero.FEMENINA else Genero.MASCULINA
        }
        // Códigos de voces de Google (aproximado; puede variar según la versión)
        val codigo = Regex("-x-([a-z]{3})").find(texto)?.groupValues?.get(1)
        return when (codigo) {
            "sfb", "esf", "eea", "eec", "afs" -> Genero.FEMENINA
            "esc", "esd", "eed", "eef", "ptd" -> Genero.MASCULINA
            else -> null
        }
    }
}
