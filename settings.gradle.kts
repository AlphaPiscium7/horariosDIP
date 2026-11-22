// settings.gradle.kts
// Este archivo le dice a Gradle (la herramienta que compila la app)
// dónde buscar las librerías y qué módulos forman el proyecto.

pluginManagement {
    repositories {
        google()            // Repositorio de Google (Android, ML Kit, etc.)
        mavenCentral()      // Repositorio general de librerías Java/Kotlin
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TraduciAr-Por"

// Nuestro proyecto tiene un solo módulo: la app.
include(":app")
