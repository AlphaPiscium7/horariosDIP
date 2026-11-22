package com.traduciar.por

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Las preferencias del usuario que se guardan en el celular. */
data class Ajustes(
    val genero: Genero = Genero.FEMENINA,
    val modo: ModoIdioma = ModoIdioma.AUTO
)

// Archivo de preferencias separado del historial
private val Context.ajustesStore by preferencesDataStore(name = "ajustes")

/**
 * Guarda y lee los ajustes (voz y modo de idioma) para que queden
 * recordados aunque cierres la app o reinicies el celular.
 */
class AjustesRepository(private val context: Context) {

    private val claveGenero = stringPreferencesKey("genero_voz")
    private val claveModo = stringPreferencesKey("modo_idioma")

    val ajustes: Flow<Ajustes> = context.ajustesStore.data.map { p ->
        Ajustes(
            // runCatching: si el valor guardado no es válido, usamos el de fábrica
            genero = runCatching { Genero.valueOf(p[claveGenero]!!) }.getOrDefault(Genero.FEMENINA),
            modo = runCatching { ModoIdioma.valueOf(p[claveModo]!!) }.getOrDefault(ModoIdioma.AUTO)
        )
    }

    suspend fun guardarGenero(genero: Genero) {
        context.ajustesStore.edit { it[claveGenero] = genero.name }
    }

    suspend fun guardarModo(modo: ModoIdioma) {
        context.ajustesStore.edit { it[claveModo] = modo.name }
    }
}
