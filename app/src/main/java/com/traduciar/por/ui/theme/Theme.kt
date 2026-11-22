package com.traduciar.por.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

// Paleta para modo CLARO (verde, como la bandera de Brasil 🇧🇷)
private val ColoresClaros = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F2D0),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF1F5FA8),
    secondaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFBA1A1A),
)

// Paleta para modo OSCURO
private val ColoresOscuros = darkColorScheme(
    primary = Color(0xFF89D6B5),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA5F2D0),
    secondary = Color(0xFFA7C8FF),
    secondaryContainer = Color(0xFF00468A),
    error = Color(0xFFFFB4AB),
)

// Letras un poco más grandes de lo normal para que se lea fácil
private val TipografiaLegible = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodyMedium = bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        titleLarge = titleLarge.copy(fontSize = 24.sp),
    )
}

/**
 * Tema de la app. Elige automáticamente modo claro u oscuro
 * según la configuración del celular.
 * En Android 12+ usa los colores del fondo de pantalla del usuario ("Material You").
 */
@Composable
fun TraduciArTheme(
    modoOscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colores = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contexto = LocalContext.current
            if (modoOscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        }
        modoOscuro -> ColoresOscuros
        else -> ColoresClaros
    }

    MaterialTheme(
        colorScheme = colores,
        typography = TipografiaLegible,
        content = content
    )
}
