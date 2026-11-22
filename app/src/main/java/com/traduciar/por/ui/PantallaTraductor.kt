package com.traduciar.por.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traduciar.por.EstadoModelo
import com.traduciar.por.EstadoPantalla
import com.traduciar.por.Traduccion
import com.traduciar.por.TraductorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla principal. En Compose, una pantalla es una función marcada con @Composable
 * que "describe" lo que se ve. Cuando cambia el estado, Compose la vuelve a dibujar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTraductor(viewModel: TraductorViewModel) {
    // Leemos el estado del ViewModel. "by" hace que se actualice solo.
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()

    val contexto = LocalContext.current
    val avisos = remember { SnackbarHostState() }

    // ---------------- Permiso de micrófono ----------------
    var tienePermiso by remember { mutableStateOf(tienePermisoMicrofono(contexto)) }
    // "rememberSaveable" sobrevive a girar el celular
    var permisoRechazado by rememberSaveable { mutableStateOf(false) }
    var rechazoPermanente by rememberSaveable { mutableStateOf(false) }

    // Cuando el usuario vuelve a la app (por ejemplo, desde Ajustes), revisamos el permiso otra vez
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tienePermiso = tienePermisoMicrofono(contexto)
        if (tienePermiso) permisoRechazado = false
    }

    // Esto abre el cuadro del sistema "¿Permitir que TraduciAr-Por grabe audio?"
    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        tienePermiso = concedido
        if (concedido) {
            permisoRechazado = false
            viewModel.empezarAEscuchar()
        } else {
            permisoRechazado = true
            // Si Android ya no deja volver a preguntar, solo queda ir a Ajustes
            val actividad = contexto.buscarActivity()
            rechazoPermanente = actividad != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        actividad, Manifest.permission.RECORD_AUDIO
                    )
        }
    }

    // Lo que pasa al tocar el micrófono
    val empezar = {
        if (tienePermisoMicrofono(contexto)) viewModel.empezarAEscuchar()
        else pedirPermiso.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Mostrar mensajes cortos (Snackbar) cuando el ViewModel lo pide
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            avisos.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    // ---------------- Estructura de la pantalla ----------------
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TraduciAr-Por  🇦🇷→🇧🇷", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        // Abajo de todo, siempre visible: el botón del micrófono
        bottomBar = {
            ZonaMicrofono(
                estado = estado,
                habilitado = estado.estadoModelo == EstadoModelo.LISTO,
                onEmpezar = empezar,
                onTerminar = viewModel::dejarDeEscuchar
            )
        }
    ) { margenes ->
        // LazyColumn = lista que se puede desplazar (scroll)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Aviso del modelo (solo si no está listo)
            if (estado.estadoModelo != EstadoModelo.LISTO) {
                item { AvisoModelo(estado.estadoModelo, onReintentar = viewModel::prepararModelo) }
            }

            // Aviso de permiso rechazado
            if (permisoRechazado && !tienePermiso) {
                item {
                    AvisoPermiso(
                        permanente = rechazoPermanente,
                        onPedirDeNuevo = { pedirPermiso.launch(Manifest.permission.RECORD_AUDIO) },
                        onAbrirAjustes = { abrirAjustesDeLaApp(contexto) }
                    )
                }
            }

            // Tarjeta con lo que dijiste en español
            item {
                TarjetaTexto(
                    titulo = "🇦🇷 Español",
                    texto = estado.textoOriginal.ifBlank {
                        if (estado.escuchando) "Escuchando…" else "Tocá el micrófono y hablá en español."
                    },
                    esPista = estado.textoOriginal.isBlank()
                )
            }

            // Tarjeta con la traducción al portugués + botones
            item {
                TarjetaTraduccion(
                    texto = estado.textoTraducido,
                    traduciendo = estado.traduciendo,
                    onRepetir = viewModel::repetir,
                    onCopiar = { viewModel.copiar(estado.textoTraducido) }
                )
            }

            // Historial
            if (historial.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Historial",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::borrarHistorial) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Borrar")
                        }
                    }
                }
                items(historial, key = { it.fecha }) { traduccion ->
                    ItemHistorial(
                        traduccion = traduccion,
                        onEscuchar = { viewModel.hablar(traduccion.traducido) },
                        onCopiar = { viewModel.copiar(traduccion.traducido) }
                    )
                }
            }
        }
    }
}

// =========================================================================
// Componentes (pedacitos reutilizables de la pantalla)
// =========================================================================

/**
 * Botón grande del micrófono.
 *  - Tocar una vez: empieza a escuchar. Tocar otra vez: termina.
 *  - Mantener presionado: escucha mientras lo tenés apretado y termina al soltar.
 */
@Composable
private fun ZonaMicrofono(
    estado: EstadoPantalla,
    habilitado: Boolean,
    onEmpezar: () -> Unit,
    onTerminar: () -> Unit
) {
    // "rememberUpdatedState" evita usar valores viejos dentro del detector de toques
    val escuchandoActual by rememberUpdatedState(estado.escuchando)
    val empezarActual by rememberUpdatedState(onEmpezar)
    val terminarActual by rememberUpdatedState(onTerminar)
    val habilitadoActual by rememberUpdatedState(habilitado)

    // El botón "late" según el volumen de tu voz
    val escala by animateFloatAsState(
        targetValue = if (estado.escuchando) 1f + estado.nivelVoz * 0.15f else 1f,
        label = "escalaMicrofono"
    )

    val colorFondo = when {
        !habilitado -> MaterialTheme.colorScheme.surfaceVariant
        estado.escuchando -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val colorIcono = when {
        !habilitado -> MaterialTheme.colorScheme.onSurfaceVariant
        estado.escuchando -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val textoAyuda = when {
        !habilitado -> "Esperando el traductor…"
        estado.escuchando -> "Escuchando… tocá para terminar"
        else -> "Tocá para hablar (o mantené presionado)"
    }

    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .scale(escala)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colorFondo)
                    // Accesibilidad: para TalkBack, esto es un botón
                    .semantics {
                        role = Role.Button
                        contentDescription = if (estado.escuchando) "Terminar de escuchar" else "Hablar"
                        onClick {
                            if (habilitadoActual) {
                                if (escuchandoActual) terminarActual() else empezarActual()
                            }
                            true
                        }
                    }
                    // Detecta toque corto y toque largo
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (!habilitadoActual) {
                                // Todavía no está listo el traductor: no hacemos nada
                            } else if (escuchandoActual) {
                                // Ya estaba escuchando: este toque lo termina
                                terminarActual()
                            } else {
                                val inicio = SystemClock.uptimeMillis()
                                empezarActual()
                                val soltado = tryAwaitRelease() // espera a que levantes el dedo
                                val duracion = SystemClock.uptimeMillis() - inicio
                                // Si lo mantuviste apretado más de medio segundo, al soltar termina
                                if (soltado && duracion > 600) terminarActual()
                            }
                        })
                    }
            ) {
                Icon(
                    imageVector = when {
                        !habilitado -> Icons.Filled.MicOff
                        estado.escuchando -> Icons.Filled.Stop
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = null,
                    tint = colorIcono,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                textoAyuda,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tarjeta simple con un título y un texto. */
@Composable
private fun TarjetaTexto(titulo: String, texto: String, esPista: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                texto,
                style = MaterialTheme.typography.bodyLarge,
                color = if (esPista) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Tarjeta destacada con la traducción y los botones Repetir / Copiar. */
@Composable
private fun TarjetaTraduccion(
    texto: String,
    traduciendo: Boolean,
    onRepetir: () -> Unit,
    onCopiar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🇧🇷 Português (Brasil)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            when {
                traduciendo -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                texto.isBlank() -> Text("La traducción va a aparecer acá.", style = MaterialTheme.typography.bodyLarge)
                else -> Text(texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRepetir, enabled = texto.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Repetir")
                }
                OutlinedButton(onClick = onCopiar, enabled = texto.isNotBlank()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar")
                }
            }
        }
    }
}

/** Una fila del historial. Tocarla la lee en voz alta. */
@Composable
private fun ItemHistorial(traduccion: Traduccion, onEscuchar: () -> Unit, onCopiar: () -> Unit) {
    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Escuchar", onClick = onEscuchar)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    traduccion.original,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    traduccion.traducido,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Text(
                    formato.format(Date(traduccion.fecha)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCopiar) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar")
            }
        }
    }
}

/** Aviso mientras se descarga (o falló) el modelo de traducción. */
@Composable
private fun AvisoModelo(estadoModelo: EstadoModelo, onReintentar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            when (estadoModelo) {
                EstadoModelo.VERIFICANDO -> Text("Preparando el traductor…")
                EstadoModelo.DESCARGANDO -> {
                    Text(
                        "Descargando el traductor español → portugués (unos 60 MB). " +
                                "Solo se hace una vez; después funciona sin internet."
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                EstadoModelo.ERROR -> {
                    Text("No se pudo descargar el traductor. Conectate a internet y reintentá.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReintentar) { Text("Reintentar") }
                }
                EstadoModelo.LISTO -> Unit
            }
        }
    }
}

/** Aviso cuando el usuario no dio permiso de micrófono. */
@Composable
private fun AvisoPermiso(permanente: Boolean, onPedirDeNuevo: () -> Unit, onAbrirAjustes: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Sin permiso de micrófono no puedo escucharte.",
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (permanente) "Activalo desde Ajustes > Permisos > Micrófono."
                else "Tocá \"Dar permiso\" y elegí \"Permitir\".",
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(8.dp))
            if (permanente) {
                Button(onClick = onAbrirAjustes) { Text("Abrir ajustes") }
            } else {
                Button(onClick = onPedirDeNuevo) { Text("Dar permiso") }
            }
        }
    }
}

// =========================================================================
// Funciones de ayuda
// =========================================================================

private fun tienePermisoMicrofono(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

/** Abre la pantalla de Ajustes de esta app (para activar el permiso a mano). */
private fun abrirAjustesDeLaApp(contexto: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", contexto.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(intent)
}

/** Busca la Activity a partir del contexto (lo necesita el sistema de permisos). */
private fun Context.buscarActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
