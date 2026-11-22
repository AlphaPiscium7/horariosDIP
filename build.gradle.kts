// build.gradle.kts (raíz del proyecto)
// Acá solo declaramos los "plugins" (herramientas) y sus versiones.
// "apply false" significa: los declaro acá, pero los activa cada módulo.

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Plugin necesario para usar Jetpack Compose con Kotlin 2.x
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
