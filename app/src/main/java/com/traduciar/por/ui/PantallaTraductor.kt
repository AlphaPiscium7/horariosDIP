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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traduciar.por.EstadoModelo
import com.traduciar.por.EstadoPantalla
import com.traduciar.por.Idioma
import com.traduciar.por.ModoIdioma
import com.traduciar.por.Traduccion
import com.traduciar.por.TraductorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla principal. En Compose, una pantalla es una función @Composable
 * que "describe" lo que se ve. Cuando cambia el estado, Compose la vuelve a dibujar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTraductor(viewModel: TraductorViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()
    val ajustes by viewModel.ajustes.collectAsStateWithLifecycle()

    val contexto = LocalContext.current
    val avisos = remember { SnackbarHostState() }
    var mostrarAjustes by rememberSaveable { mutableStateOf(false) }

    // ---------------- Permiso de micrófono ----------------
    var tienePermiso by remember { mutableStateOf(tienePermisoMicrofono(contexto)) }
    var permisoRechazado by rememberSaveable { mutableStateOf(false) }
    var rechazoPermanente by rememberSaveable { mutableStateOf(false) }

    // Al volver a la app (por ejemplo, desde Ajustes del sistema) revisamos permiso y voces
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tienePermiso = tienePermisoMicrofono(contexto)
        if (tienePermiso) permisoRechazado = false
        viewModel.refrescarVoces()
    }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        tienePermiso = concedido
        if (concedido) {
            permisoRechazado = false
            viewModel.empezarAEscuchar()
        } else {
            permisoRechazado = true
            val actividad = contexto.buscarActivity()
            rechazoPermanente = actividad != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(actividad, Manifest.permission.RECORD_AUDIO)
        }
    }

    val empezar = {
        if (tienePermisoMicrofono(contexto)) viewModel.empezarAEscuchar()
        else pedirPermiso.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            avisos.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TraduciAr-Por  🇦🇷⇄🇧🇷", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { mostrarAjustes = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        bottomBar = {
            ZonaMicrofono(
                estado = estado,
                modo = ajustes.modo,
                habilitado = estado.estadoModelo == EstadoModelo.LISTO,
                onEmpezar = empezar,
                onTerminar = viewModel::dejarDeEscuchar
            )
        }
    ) { margenes ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.estadoModelo != EstadoModelo.LISTO) {
                item { AvisoModelo(estado, onReintentar = viewModel::prepararModelos) }
            }

            if (permisoRechazado && !tienePermiso) {
                item {
                    AvisoPermiso(
                        permanente = rechazoPermanente,
                        onPedirDeNuevo = { pedirPermiso.launch(Manifest.permission.RECORD_AUDIO) },
                        onAbrirAjustes = { abrirAjustesDeLaApp(contexto) }
                    )
                }
            }

            // ¿En qué idioma vas a hablar?
            item { SelectorModo(ajustes.modo, onCambiar = viewModel::cambiarModo) }

            // Indicador grande "ES → PT" de la última traducción
            estado.origen?.let { origen ->
                item { IndicadorDireccion(origen, estado.detectadoAuto, estado.confianza) }
            }

            // Confianza baja: preguntamos el idioma
            if (estado.dudaIdioma && !estado.traduciendo) {
                item {
                    AvisoDuda(
                        confianza = estado.confianza,
                        onElegir = viewModel::corregirIdioma
                    )
                }
            }

            // Texto original
            item {
                val origen = estado.origen
                TarjetaTexto(
                    titulo = if (origen != null) "${origen.bandera} ${origen.nombre} (original)" else "Lo que dijiste",
                    texto = estado.textoOriginal.ifBlank {
                        if (estado.escuchando) "Escuchando…" else "Tocá el micrófono y hablá en español o en portugués."
                    },
                    esPista = estado.textoOriginal.isBlank()
                )
            }

            // Traducción
            item {
                TarjetaTraduccion(
                    destino = estado.origen?.otro(),
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
                        Text("Historial", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                        onEscuchar = { viewModel.hablar(traduccion.traducido, traduccion.destino) },
                        onCopiar = { viewModel.copiar(traduccion.traducido) }
                    )
                }
            }
        }
    }

    // Panel de ajustes (se desliza desde abajo)
    if (mostrarAjustes) {
        HojaAjustes(
            genero = ajustes.genero,
            voces = estado.voces,
            vocesListas = estado.vocesListas,
            onCambiarGenero = viewModel::cambiarGenero,
            onProbar = viewModel::probarVoz,
            onInstalarVoces = { abrirAjustesDeVoz(contexto) },
            onCerrar = { mostrarAjustes = false }
        )
    }
}

// =========================================================================
// Componentes
// =========================================================================

/** Tres botones grandes: Automático / ES → PT / PT → ES. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorModo(modo: ModoIdioma, onCambiar: (ModoIdioma) -> Unit) {
    Column {
        Text(
            "¿En qué idioma vas a hablar?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val opciones = ModoIdioma.entries
            opciones.forEachIndexed { i, opcion ->
                SegmentedButton(
                    selected = opcion == modo,
                    onClick = { onCambiar(opcion) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = opciones.size),
                    icon = {}, // sin tilde, para que entre el texto
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        when (opcion) {
                            ModoIdioma.AUTO -> "Detectar"
                            ModoIdioma.ES_A_PT -> "ES → PT"
                            ModoIdioma.PT_A_ES -> "PT → ES"
                        },
                        maxLines = 1,
                        fontWeight = if (opcion == modo) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Muestra en grande la dirección de la traducción, por ejemplo "🇦🇷 ES → PT 🇧🇷". */
@Composable
private fun IndicadorDireccion(origen: Idioma, auto: Boolean, confianza: Float?) {
    val destino = origen.otro()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "${origen.bandera} ${origen.sigla} → ${destino.sigla} ${destino.bandera}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                auto && confianza != null -> "Detectado: ${(confianza * 100).roundToInt()} % seguro"
                auto -> "Detectado automáticamente"
                else -> "Elegido a mano"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Confianza baja: dos botones grandes para elegir el idioma a mano. */
@Composable
private fun AvisoDuda(confianza: Float?, onElegir: (Idioma) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("⚠️ No estoy seguro del idioma", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("¿En qué idioma hablaste?")
                    if (confianza != null) append(" (seguridad: ${(confianza * 100).roundToInt()} %)")
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Idioma.entries.forEach { idioma ->
                    Button(
                        onClick = { onElegir(idioma) },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("${idioma.bandera} ${idioma.nombre}")
                    }
                }
            }
        }
    }
}

/**
 * Botón grande del micrófono.
 *  - Tocar una vez: empieza a escuchar. Tocar otra vez: termina.
 *  - Mantener presionado: escucha mientras lo tenés apretado y termina al soltar.
 */
@Composable
private fun ZonaMicrofono(
    estado: EstadoPantalla,
    modo: ModoIdioma,
    habilitado: Boolean,
    onEmpezar: () -> Unit,
    onTerminar: () -> Unit
) {
    val escuchandoActual by rememberUpdatedState(estado.escuchando)
    val empezarActual by rememberUpdatedState(onEmpezar)
    val terminarActual by rememberUpdatedState(onTerminar)
    val habilitadoActual by rememberUpdatedState(habilitado)

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
        modo == ModoIdioma.AUTO -> "Tocá y hablá en español o portugués"
        modo == ModoIdioma.ES_A_PT -> "Tocá y hablá en español"
        else -> "Toque e fale em português"
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
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (!habilitadoActual) {
                                // Todavía no está listo el traductor: no hacemos nada
                            } else if (escuchandoActual) {
                                terminarActual()
                            } else {
                                val inicio = SystemClock.uptimeMillis()
                                empezarActual()
                                val soltado = tryAwaitRelease()
                                if (soltado && SystemClock.uptimeMillis() - inicio > 600) terminarActual()
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
            Text(textoAyuda, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TarjetaTexto(titulo: String, texto: String, esPista: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                texto,
                style = MaterialTheme.typography.bodyLarge,
                color = if (esPista) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TarjetaTraduccion(
    destino: Idioma?,
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
            Text(
                if (destino != null) "${destino.bandera} ${destino.nombre} (traducción)" else "Traducción",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(6.dp))
            when {
                traduciendo -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                texto.isBlank() -> Text("La traducción va a aparecer acá.", style = MaterialTheme.typography.bodyLarge)
                else -> Text(texto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onRepetir,
                    enabled = texto.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Repetir")
                }
                OutlinedButton(
                    onClick = onCopiar,
                    enabled = texto.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar")
                }
            }
        }
    }
}

@Composable
private fun ItemHistorial(traduccion: Traduccion, onEscuchar: () -> Unit, onCopiar: () -> Unit) {
    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Escuchar", onClick = onEscuchar)
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${traduccion.origen.sigla} → ${traduccion.destino.sigla} · ${formato.format(Date(traduccion.fecha))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
            }
            TextButton(onClick = onCopiar) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar")
            }
        }
    }
}

/** Aviso mientras se descargan (o fallaron) los modelos de traducción. */
@Composable
private fun AvisoModelo(estado: EstadoPantalla, onReintentar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            when (estado.estadoModelo) {
                EstadoModelo.VERIFICANDO -> {
                    Text("Preparando el traductor…")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                EstadoModelo.DESCARGANDO -> {
                    Text("Descargando los traductores (unos 30 MB cada uno). Solo se hace una vez.", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(estado.textoDescarga, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    // Barra con el avance por idioma (0 %, 50 %, 100 %)
                    LinearProgressIndicator(
                        progress = { estado.progresoDescarga.coerceAtLeast(0.05f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    // Barra animada: indica que se está descargando aunque el porcentaje no cambie
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                EstadoModelo.SIN_CONEXION -> {
                    Text("📶 Sin conexión a internet", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La primera vez hace falta internet para descargar los traductores de español y portugués. " +
                                "Conectate a WiFi o datos: la descarga empieza sola."
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReintentar, modifier = Modifier.height(52.dp)) { Text("Reintentar") }
                }
                EstadoModelo.ERROR -> {
                    Text("No se pudo descargar el traductor. Revisá la conexión y reintentá.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReintentar, modifier = Modifier.height(52.dp)) { Text("Reintentar") }
                }
                EstadoModelo.LISTO -> Unit
            }
        }
    }
}

@Composable
private fun AvisoPermiso(permanente: Boolean, onPedirDeNuevo: () -> Unit, onAbrirAjustes: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Sin permiso de micrófono no puedo escucharte.", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (permanente) "Activalo desde Ajustes > Permisos > Micrófono."
                else "Tocá \"Dar permiso\" y elegí \"Permitir\"."
            )
            Spacer(Modifier.height(8.dp))
            if (permanente) Button(onClick = onAbrirAjustes) { Text("Abrir ajustes") }
            else Button(onClick = onPedirDeNuevo) { Text("Dar permiso") }
        }
    }
}

// =========================================================================
// Funciones de ayuda
// =========================================================================

private fun tienePermisoMicrofono(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

private fun abrirAjustesDeLaApp(contexto: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", contexto.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(intent)
}

private fun Context.buscarActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
