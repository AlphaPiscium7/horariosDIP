# TraduciAr-Por 🇦🇷 → 🇧🇷

App Android que **escucha español latino**, lo **traduce al portugués de Brasil** y lo **lee en voz alta**.
Funciona **sin internet** después de la primera descarga del traductor.

| Qué hace | Con qué |
|---|---|
| Escucha tu voz (`es-419`) | `SpeechRecognizer` de Android |
| Traduce ES → PT sin internet | Google ML Kit Translation |
| Lee el resultado (`pt-BR`) | `TextToSpeech` de Android |
| Guarda las últimas 30 traducciones | DataStore |
| Pantalla, modo oscuro | Jetpack Compose + Material 3 |

---

## 📁 Qué hay en cada archivo

```
app/src/main/
├── AndroidManifest.xml          ← permisos (micrófono) y nombre de la app
├── java/com/traduciar/por/
│   ├── MainActivity.kt          ← punto de entrada: arranca la pantalla
│   ├── TraductorViewModel.kt    ← el "cerebro": voz → traducción → lectura
│   ├── HistorialRepository.kt   ← guarda/lee el historial en el celular
│   └── ui/
│       ├── PantallaTraductor.kt ← todo lo que se ve: botones, tarjetas, historial
│       └── theme/Theme.kt       ← colores (claro / oscuro) y tamaño de letra
└── res/                         ← ícono, nombre, colores de arranque
```

---

## 🧑‍💻 Paso a paso (para principiantes)

### Paso 1 — Instalar Android Studio
1. Entrá a **developer.android.com/studio** y descargá Android Studio.
2. Instalalo con las opciones por defecto (“Standard”). La primera vez descarga el SDK de Android:
   puede tardar un rato y ocupa varios GB. Dejalo terminar.

### Paso 2 — Bajar el proyecto
**Opción A (sin Git):** en GitHub, en la página del repo, elegí la rama del proyecto,
tocá el botón verde **Code → Download ZIP** y descomprimilo en una carpeta (por ejemplo `Documentos/TraduciAr-Por`).

**Opción B (con Git):** en Android Studio, pantalla de bienvenida → **Clone Repository** → pegá la URL del repo.

### Paso 3 — Abrir el proyecto
1. En Android Studio: **File → Open…** (o “Open” en la pantalla de bienvenida).
2. Elegí **la carpeta que contiene `settings.gradle.kts`** (no la carpeta `app`).
3. Si pregunta “Trust project?” → **Trust Project**.
4. Abajo a la derecha vas a ver una barra de progreso: es **Gradle sincronizando** (descarga librerías).
   La primera vez tarda varios minutos. Esperá a que termine.
   - Si Android Studio te ofrece **actualizar el “Android Gradle Plugin”** podés aceptar (“Upgrade”), o ignorarlo: funciona igual.
   - Si aparece un error de “SDK 35 not installed”, hacé clic en el enlace azul **Install** que aparece en el mensaje.

### Paso 4 — Preparar el celular
1. En el celular: **Ajustes → Acerca del teléfono** y tocá **7 veces** “Número de compilación”.
   Aparece “Ya sos desarrollador”.
2. Volvé a Ajustes → **Sistema → Opciones de desarrollador** → activá **Depuración por USB**.
3. Conectá el celular a la compu con un cable USB (que sea de datos, no solo de carga).
4. En el celular aparece “¿Permitir depuración USB?” → marcá “Permitir siempre” y **Aceptar**.
5. (Windows) Si el celular no aparece, puede faltar el driver USB del fabricante (Samsung, Motorola, etc.).

> **Alternativa sin cable (Android 11+):** Opciones de desarrollador → *Depuración inalámbrica*,
> y en Android Studio el menú de dispositivos → *Pair Devices Using Wi-Fi* (escaneás un QR).

### Paso 5 — Compilar e instalar
1. Arriba, en la barra de herramientas, elegí tu celular en la lista de dispositivos.
2. Tocá el botón verde **▶ Run 'app'** (o `Shift + F10`).
3. Android Studio compila, instala la app y la abre sola en el celular. 🎉

### Paso 6 — Primer uso
1. **Con internet**, abrí la app: aparece el aviso “Descargando el traductor…” (unos 60 MB, una sola vez).
2. Cuando termina, tocá el **micrófono**. La primera vez te pide permiso → **Permitir**.
3. Hablá en español. Al terminar, ves la traducción y la escuchás en portugués.
   - **Tocar** el micrófono = empieza; tocar de nuevo = termina (o termina solo al hacer silencio).
   - **Mantener presionado** = escucha mientras lo apretás y termina al soltar.
4. Botones: **Repetir** (vuelve a leer) y **Copiar** (para pegar en WhatsApp, etc.).
5. Tocá cualquier ítem del **historial** para volver a escucharlo.

---

## ✈️ Para que funcione 100 % sin internet

La app necesita tres cosas descargadas en el celular:

1. **El traductor** → lo descarga la app sola la primera vez (Paso 6).
2. **La voz en portugués** (para leer en voz alta):
   Ajustes → Sistema → Idiomas → **Salida de texto a voz** → ⚙️ del motor de Google →
   **Instalar datos de voz** → *Português (Brasil)*.
3. **El reconocimiento de voz en español sin conexión**:
   abrí la app **Google** → tu foto → Configuración → Voz → **Reconocimiento de voz sin conexión** →
   descargá *Español (Latinoamérica)* o *Español (Estados Unidos)*.
   (En algunos celulares está en Ajustes → Sistema → Idioma → Voz.)

Probalo después en **modo avión** ✈️.

---

## 🛠️ Problemas comunes

| Problema | Solución |
|---|---|
| “Este celular no tiene reconocimiento de voz” | Instalá/actualizá la app **Google** desde Play Store. |
| “Falta la voz en portugués” | Instalá la voz pt-BR (ver arriba, punto 2). |
| Sin internet no reconoce lo que digo | Descargá el español sin conexión (punto 3). |
| Rechacé el permiso del micrófono | La app muestra un botón **Abrir ajustes** → Permisos → Micrófono → Permitir. |
| Gradle falla al sincronizar | Revisá la conexión y hacé **File → Sync Project with Gradle Files**. |
| “Unsupported Java version” | **File → Settings → Build Tools → Gradle → Gradle JDK** → elegí la que viene con Android Studio (“jbr-21” o similar). |

---

## 📦 Generar un APK para pasarle a alguien
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Cuando termine, hacé clic en **locate**:
el archivo `app-debug.apk` se puede mandar e instalar en otro Android (hay que permitir “instalar apps desconocidas”).

Requisitos técnicos: minSdk 24 (Android 7.0), targetSdk 35 (Android 15), Kotlin 2.0, Gradle Kotlin DSL.
