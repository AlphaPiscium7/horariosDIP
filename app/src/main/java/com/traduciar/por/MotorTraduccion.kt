package com.traduciar.por

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Resultado de detectar el idioma de un texto. */
data class Deteccion(
    val idioma: Idioma,      // Idioma más probable (español o portugués)
    val confianza: Float,    // 0..1: qué tan seguro está ML Kit
    val segura: Boolean      // false = conviene preguntarle al usuario
)

/**
 * Todo lo que hace ML Kit, sin internet:
 *  - Descargar los modelos de español y portugués (una sola vez).
 *  - Traducir en las dos direcciones.
 *  - Detectar si un texto está en español o en portugués.
 */
class MotorTraduccion {

    companion object {
        /** Por debajo de esta confianza, le preguntamos al usuario qué idioma habló. */
        const val CONFIANZA_MINIMA = 0.6f
    }

    private val gestor = RemoteModelManager.getInstance()

    // Un traductor para cada dirección. Usan los mismos dos modelos descargados.
    private val esAPt = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(Idioma.ES.codigoMlKit)
            .setTargetLanguage(Idioma.PT.codigoMlKit)
            .build()
    )
    private val ptAEs = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(Idioma.PT.codigoMlKit)
            .setTargetLanguage(Idioma.ES.codigoMlKit)
            .build()
    )

    // Detector de idioma. El modelo viene DENTRO de la app: no hay que descargarlo.
    // Umbral muy bajo para que nos devuelva también idiomas poco probables y poder comparar.
    private val detector = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.01f).build()
    )

    // ------------------------- Modelos -------------------------

    private fun modelo(idioma: Idioma) = TranslateRemoteModel.Builder(idioma.codigoMlKit).build()

    /** Devuelve los idiomas cuyo modelo todavía no está en el celular. */
    suspend fun idiomasFaltantes(): List<Idioma> =
        Idioma.entries.filter { !gestor.isModelDownloaded(modelo(it)).await() }

    /**
     * Descarga UN modelo (unos 30 MB cada uno). Se llama una vez por idioma faltante,
     * así la pantalla puede mostrar el avance: "1 de 2", "2 de 2".
     * Si falla (por ejemplo, sin conexión), lanza una excepción.
     */
    suspend fun descargar(idioma: Idioma) {
        gestor.download(modelo(idioma), DownloadConditions.Builder().build()).await()
    }

    // ------------------------- Traducción -------------------------

    /** Traduce [texto] desde [origen] al otro idioma. */
    suspend fun traducir(texto: String, origen: Idioma): String {
        val traductor = if (origen == Idioma.ES) esAPt else ptAEs
        return traductor.translate(texto).await()
    }

    // ------------------------- Detección -------------------------

    /**
     * Mira el texto y decide si es español o portugués.
     * ML Kit reconoce más de 100 idiomas; acá solo comparamos esos dos.
     */
    suspend fun detectar(texto: String): Deteccion {
        val posibles = try {
            detector.identifyPossibleLanguages(texto).await()
        } catch (e: Exception) {
            emptyList()
        }
        // Puntaje de cada idioma ("und" = indeterminado, no suma)
        val puntajeEs = posibles.filter { it.languageTag == "es" }.sumOf { it.confidence.toDouble() }.toFloat()
        // El gallego es casi igual al portugués escrito: lo sumamos a medias
        val puntajePt = posibles.filter { it.languageTag == "pt" }.sumOf { it.confidence.toDouble() }.toFloat() +
                posibles.filter { it.languageTag == "gl" }.sumOf { it.confidence.toDouble() }.toFloat() * 0.5f

        val idioma = if (puntajePt > puntajeEs) Idioma.PT else Idioma.ES
        val ganador = maxOf(puntajeEs, puntajePt)
        val perdedor = minOf(puntajeEs, puntajePt)
        // Es "segura" si la confianza es alta y hay buena diferencia con el otro idioma
        val segura = ganador >= CONFIANZA_MINIMA && (ganador - perdedor) >= 0.3f
        return Deteccion(idioma, ganador.coerceIn(0f, 1f), segura)
    }

    /** Liberar memoria cuando la app se cierra. */
    fun cerrar() {
        esAPt.close()
        ptAEs.close()
        detector.close()
    }
}
