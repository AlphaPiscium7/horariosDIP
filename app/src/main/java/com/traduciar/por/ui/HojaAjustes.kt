package com.traduciar.por.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traduciar.por.Genero
import com.traduciar.por.Idioma
import com.traduciar.por.InfoVoz

/**
 * Panel de ajustes que se desliza desde abajo:
 *  - Elegir voz femenina o masculina (queda guardado).
 *  - Ver qué voz se usa en cada idioma y probarla.
 *  - Abrir los ajustes del sistema para instalar más voces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaAjustes(
    genero: Genero,
    voces: Map<Idioma, InfoVoz>,
    vocesListas: Boolean,
    onCambiarGenero: (Genero) -> Unit,
    onProbar: (Idioma) -> Unit,
    onInstalarVoces: () -> Unit,
    onCerrar: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // ---------------- Género de la voz ----------------
            Text("Voz para leer las traducciones", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Genero.entries.forEachIndexed { i, opcion ->
                    SegmentedButton(
                        selected = opcion == genero,
                        onClick = { onCambiarGenero(opcion) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = Genero.entries.size),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(
                            (if (opcion == Genero.FEMENINA) "👩 " else "👨 ") + opcion.etiqueta,
                            fontWeight = if (opcion == genero) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------------- Estado de cada idioma ----------------
            if (!vocesListas) {
                Text("Cargando las voces del celular…")
            } else {
                Idioma.entries.forEach { idioma ->
                    FilaVoz(idioma, genero, voces[idioma], onProbar = { onProbar(idioma) })
                    Spacer(Modifier.height(10.dp))
                }
            }

            Text(
                "Android no informa si una voz es de hombre o de mujer: la app lo deduce por el nombre de la voz. " +
                        "Tocá \"Probar\" para escucharla.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ---------------- Instalar voces ----------------
            Button(
                onClick = onInstalarVoces,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Instalar más voces")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Se abren los ajustes de \"Texto a voz\" del celular. Tocá el engranaje ⚙️ del motor de Google → " +
                        "\"Instalar datos de voz\" → elegí Español (Estados Unidos o España) y Português (Brasil). " +
                        "Cada idioma trae varias voces de hombre y de mujer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Una tarjeta por idioma: qué voz se usa + botón para probarla. */
@Composable
private fun FilaVoz(idioma: Idioma, genero: Genero, info: InfoVoz?, onProbar: () -> Unit) {
    val (texto, ok) = when {
        info == null -> "Buscando voces…" to true
        !info.hayVoz -> "⚠️ No hay voz instalada. Instalala con el botón de abajo." to false
        info.generoEncontrado -> "✅ Voz ${genero.etiqueta.lowercase()}" to true
        info.generoReal != null ->
            "⚠️ No hay voz ${genero.etiqueta.lowercase()}: se usa una voz ${info.generoReal.etiqueta.lowercase()}." to false
        else -> "⚠️ No encontré voz ${genero.etiqueta.lowercase()}: se usa la disponible." to false
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("${idioma.bandera} ${idioma.nombre}", fontWeight = FontWeight.SemiBold)
                Text(
                    texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
            }
            FilledTonalButton(
                onClick = onProbar,
                enabled = info?.hayVoz == true,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Probar")
            }
        }
    }
}

/**
 * Abre los ajustes de "Texto a voz" del sistema. Como cada marca de celular
 * los tiene en un lugar distinto, probamos varias opciones en orden.
 */
fun abrirAjustesDeVoz(contexto: Context) {
    val opciones = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),          // Pantalla "Salida de texto a voz"
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),  // Instalar datos de voz del motor
        Intent(Settings.ACTION_SETTINGS)                      // Último recurso: Ajustes generales
    )
    for (intent in opciones) {
        try {
            contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Esta opción no existe en este celular: probamos la siguiente
        }
    }
}
