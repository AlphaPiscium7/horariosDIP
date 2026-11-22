// app/build.gradle.kts
// Configuración del módulo de la app: versiones de Android y librerías que usa.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Paquete único que identifica a la app en el celular y en Play Store
    namespace = "com.traduciar.por"
    // Compilamos con el SDK de Android 15 (API 35)
    compileSdk = 35

    defaultConfig {
        applicationId = "com.traduciar.por"
        minSdk = 24          // Android 7.0 como mínimo
        targetSdk = 35       // Optimizada para Android 15
        // GitHub Actions pasa el número de compilación (-PnumeroVersion=N) para que
        // cada APK nuevo tenga una versión mayor y se pueda instalar encima del anterior.
        val numero = (project.findProperty("numeroVersion") as String?)?.toIntOrNull() ?: 1
        versionCode = numero
        versionName = "1.0.$numero"
    }

    // Firma del APK. Android solo instala apps firmadas, y para poder ACTUALIZAR la app
    // sin desinstalarla, todas las versiones tienen que estar firmadas con la misma clave.
    // Esta clave está guardada en el repo: sirve para uso personal, NO para Play Store.
    signingConfigs {
        create("personal") {
            storeFile = file("firma/traduciar.jks")
            storePassword = "traduciar123"
            keyAlias = "traduciar"
            keyPassword = "traduciar123"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("personal")
            // Reduce el tamaño del APK final eliminando código que no se usa
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true   // Activamos Jetpack Compose (la forma moderna de hacer pantallas)
    }
}

dependencies {
    // --- Básicos de Android ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // --- Jetpack Compose (la "BOM" fija versiones compatibles entre sí) ---
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // íconos: micrófono, parlante...
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Traducción sin internet (Google ML Kit) ---
    implementation("com.google.mlkit:translate:17.0.3")
    // Permite usar "await()" con las tareas de ML Kit dentro de corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // --- Guardado local del historial ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
