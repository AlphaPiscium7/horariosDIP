package com.traduciar.por

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Una traducción guardada: texto original, traducción, idioma de origen y cuándo se hizo. */
data class Traduccion(
    val original: String,
    val traducido: String,
    val origen: Idioma = Idioma.ES,
    val fecha: Long = System.currentTimeMillis()
) {
    /** Idioma al que se tradujo (el contrario del origen). */
    val destino: Idioma get() = origen.otro()
}

// Crea (una sola vez) un pequeño "archivo de preferencias" llamado "historial"
// dentro del almacenamiento privado de la app. Nadie más puede leerlo.
private val Context.dataStore by preferencesDataStore(name = "historial")

/**
 * Se encarga de guardar y leer el historial de traducciones.
 * Usa DataStore: guarda la lista como texto JSON dentro del celular.
 */
class HistorialRepository(private val context: Context) {

    private val claveHistorial = stringPreferencesKey("lista_traducciones")

    companion object {
        /** Cuántas traducciones recordamos como máximo. */
        const val MAXIMO = 30
    }

    /**
     * Un "Flow" es como un canal que avisa cada vez que el historial cambia.
     * La pantalla lo escucha y se actualiza sola.
     */
    val historial: Flow<List<Traduccion>> = context.dataStore.data.map { preferencias ->
        desdeJson(preferencias[claveHistorial])
    }

    /** Agrega una traducción al principio de la lista (la más nueva arriba). */
    suspend fun agregar(traduccion: Traduccion) {
        context.dataStore.edit { preferencias ->
            val actual = desdeJson(preferencias[claveHistorial])
            val nueva = (listOf(traduccion) + actual).take(MAXIMO)
            preferencias[claveHistorial] = aJson(nueva)
        }
    }

    /**
     * Reemplaza la traducción más reciente (se usa cuando el usuario corrige
     * a mano el idioma detectado, para no dejar la versión equivocada).
     */
    suspend fun reemplazarUltima(traduccion: Traduccion) {
        context.dataStore.edit { preferencias ->
            val actual = desdeJson(preferencias[claveHistorial])
            preferencias[claveHistorial] = aJson(listOf(traduccion) + actual.drop(1))
        }
    }

    /** Borra todo el historial. */
    suspend fun borrarTodo() {
        context.dataStore.edit { it.remove(claveHistorial) }
    }

    // ---- Conversión entre la lista y texto JSON ----

    private fun aJson(lista: List<Traduccion>): String {
        val array = JSONArray()
        lista.forEach { t ->
            array.put(
                JSONObject()
                    .put("o", t.original)
                    .put("t", t.traducido)
                    .put("i", t.origen.codigo)
                    .put("f", t.fecha)
            )
        }
        return array.toString()
    }

    private fun desdeJson(texto: String?): List<Traduccion> {
        if (texto.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(texto)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Traduccion(
                    original = obj.getString("o"),
                    traducido = obj.getString("t"),
                    // Las traducciones viejas (antes de esta versión) eran todas ES → PT
                    origen = Idioma.desdeCodigo(obj.optString("i", "es")),
                    fecha = obj.getLong("f")
                )
            }
        } catch (e: Exception) {
            emptyList() // Si el dato estuviera dañado, empezamos con la lista vacía
        }
    }
}
