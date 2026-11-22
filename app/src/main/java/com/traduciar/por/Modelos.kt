package com.traduciar.por

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Los dos idiomas que maneja la app, con todo lo que cada parte necesita saber de ellos.
 */
enum class Idioma(
    val codigo: String,              // Código corto: "es" / "pt"
    val sigla: String,               // Lo que se muestra en pantalla: "ES" / "PT"
    val nombre: String,              // Nombre para mostrar
    val bandera: String,
    val etiquetaVoz: String,         // Idioma para el reconocimiento de voz
    val codigoMlKit: String,         // Idioma para ML Kit
    val localesVoz: List<Locale>     // Acentos preferidos para leer en voz alta (en orden)
) {
    ES(
        codigo = "es", sigla = "ES", nombre = "Español", bandera = "🇦🇷",
        etiquetaVoz = "es-419",
        codigoMlKit = TranslateLanguage.SPANISH,
        // Primero acentos latinos; España queda como última opción
        localesVoz = listOf("es-419", "es-US", "es-MX", "es-AR", "es-CO", "es-ES", "es")
            .map(Locale::forLanguageTag)
    ),
    PT(
        codigo = "pt", sigla = "PT", nombre = "Português", bandera = "🇧🇷",
        etiquetaVoz = "pt-BR",
        codigoMlKit = TranslateLanguage.PORTUGUESE,
        localesVoz = listOf("pt-BR", "pt-PT", "pt").map(Locale::forLanguageTag)
    );

    /** El idioma "contrario": al que hay que traducir. */
    fun otro(): Idioma = if (this == ES) PT else ES

    companion object {
        fun desdeCodigo(codigo: String?): Idioma = entries.firstOrNull { it.codigo == codigo } ?: ES
    }
}

/** Cómo decide la app en qué idioma estás hablando. */
enum class ModoIdioma(val etiqueta: String) {
    AUTO("Automático"),     // Detecta solo si es español o portugués
    ES_A_PT("ES → PT"),     // Siempre de español a portugués
    PT_A_ES("PT → ES");     // Siempre de portugués a español

    /** Idioma de origen fijo (o null si es automático). */
    val origenFijo: Idioma?
        get() = when (this) {
            AUTO -> null
            ES_A_PT -> Idioma.ES
            PT_A_ES -> Idioma.PT
        }
}

/** Voz preferida para leer las traducciones. */
enum class Genero(val etiqueta: String) {
    FEMENINA("Femenina"),
    MASCULINA("Masculina")
}
