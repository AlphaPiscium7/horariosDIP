package com.traduciar.por

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.traduciar.por.ui.PantallaTraductor
import com.traduciar.por.ui.theme.TraduciArTheme

/**
 * Punto de entrada de la app: es lo primero que abre Android al tocar el ícono.
 */
class MainActivity : ComponentActivity() {

    // "by viewModels()" crea el ViewModel una sola vez y lo conserva al girar el celular
    private val viewModel: TraductorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dibuja la app de borde a borde (obligatorio en Android 15)
        enableEdgeToEdge()
        // Acá le decimos qué mostrar: nuestro tema + la pantalla del traductor
        setContent {
            TraduciArTheme {
                PantallaTraductor(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Si la app se va a segundo plano, dejamos de usar el micrófono
        viewModel.cancelarEscucha()
    }
}
