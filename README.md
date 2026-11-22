# TraduciAr-Por 🇦🇷 ⇄ 🇧🇷

App Android que **escucha español latino o portugués de Brasil**, **detecta sola el idioma**,
lo **traduce al otro** y lo **lee en voz alta** (con voz femenina o masculina).
Funciona **sin internet** después de la primera descarga de los traductores.

| Qué hace | Con qué |
|---|---|
| Escucha tu voz (`es-419` / `pt-BR`) | `SpeechRecognizer` de Android |
| Detecta si hablaste español o portugués | Google ML Kit Language Identification |
| Traduce ES → PT y PT → ES sin internet | Google ML Kit Translation |
| Lee el resultado con voz femenina o masculina | `TextToSpeech` de Android |
| Guarda historial y ajustes | DataStore |
| Pantalla, modo oscuro | Jetpack Compose + Material 3 |

---

## 📁 Qué hay en cada archivo

```
app/src/main/java/com/traduciar/por/
├── MainActivity.kt          ← punto de entrada: arranca la pantalla
├── TraductorViewModel.kt    ← el "cerebro": voz → detección → traducción → lectura
├── Modelos.kt               ← idiomas (ES/PT), modos (Detectar / ES→PT / PT→ES), género de voz
├── MotorTraduccion.kt       ← ML Kit: descarga de modelos, traducción y detección de idioma
├── MotorVoz.kt              ← TextToSpeech: elige voz femenina/masculina por idioma
├── AjustesRepository.kt     ← guarda la voz y el modo elegidos
├── HistorialRepository.kt   ← guarda las últimas 30 traducciones
└── ui/
    ├── PantallaTraductor.kt ← pantalla principal
    ├── HojaAjustes.kt       ← panel de Ajustes ⚙️ (voz, probar, instalar voces)
    └── theme/Theme.kt       ← colores (claro / oscuro)
```

---

## 📱 Compilar y obtener el APK SIN PC (solo con el celular)

El repo tiene un "robot" de GitHub Actions (`.github/workflows/build-apk.yml`) que compila la app en los
servidores de GitHub, gratis. No necesitás Android Studio.

1. **Hacé un cambio y subilo a `main`** (por ejemplo, editando un archivo desde github.com en el navegador
   del celular → ✏️ → *Commit changes*, o aceptando un Pull Request con *Merge*).
2. Andá a la pestaña **Actions** del repo: vas a ver "Compilar APK" con un círculo amarillo 🟡 (compilando).
   Tarda unos 3–4 minutos.
3. Cuando aparece el tilde verde ✅, andá a la página principal del repo → **Releases** (a la derecha, o abajo
   en la versión móvil) → la versión más nueva `v1.0.N` → tocá **TraductorESPT.apk** para descargarlo.
4. Si sale una ❌ roja: tocala para ver qué paso falló (o pedile ayuda a Claude pegando el error).

**Compilar sin cambiar nada:** Actions → "Compilar APK" → **Run workflow** → elegí la rama `main` → Run.

> En otras ramas también compila (para probar), pero el APK queda solo como *artifact* dentro de
> Actions (hay que iniciar sesión para bajarlo). La **Release** pública se crea únicamente desde `main`.

### Instalar el APK en el celular
1. Abrí el `TraductorESPT.apk` descargado (notificación de descarga o app **Archivos → Descargas**).
2. Si dice "no se pueden instalar apps de esta fuente": **Configuración → Permitir desde esta fuente** → volver.
3. **Instalar** (si Play Protect avisa: *Más detalles → Instalar de todas formas*).
4. Las versiones nuevas se instalan **encima** de la anterior, sin perder historial ni ajustes.

---

## 🗣️ Cómo usarla

- **Selector de idioma** arriba:
  - **Detectar**: hablás en cualquiera de los dos y la app decide sola. Arriba aparece por ejemplo
    **🇦🇷 ES → PT 🇧🇷** con el porcentaje de seguridad. Si la seguridad es baja, aparece
    "⚠️ No estoy seguro del idioma" con dos botones grandes para elegirlo a mano.
  - **ES → PT** / **PT → ES**: fija la dirección (el reconocimiento de voz es más preciso así).
- **Micrófono**: tocar = empieza / tocar de nuevo = termina. Mantener presionado = habla mientras lo apretás.
- **Repetir** / **Copiar** la traducción. Tocar un ítem del **historial** lo vuelve a leer.
- **⚙️ Ajustes**: voz **femenina o masculina** (queda guardada), botón **Probar** para cada idioma y
  **Instalar más voces** (abre los ajustes de texto a voz del sistema).

> Sobre la detección automática: el reconocedor de voz de Android escucha en un idioma principal
> (español) y se le indica que también puede venir portugués. Con el reconocedor de Google suele andar bien,
> pero si hablás mucho en portugués, elegí **PT → ES** para que transcriba mejor.

---

## 💻 (Opcional) Con una PC y Android Studio

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

1. **Los traductores ES y PT** → los descarga la app sola la primera vez (barra de avance).
2. **Las voces en español y portugués** (para leer en voz alta): en la app, ⚙️ Ajustes →
   **Instalar más voces**, o Ajustes del celular → Sistema → Idiomas → **Salida de texto a voz** →
   ⚙️ del motor de Google → **Instalar datos de voz** → *Español (Estados Unidos)* y *Português (Brasil)*.
3. **El reconocimiento de voz sin conexión en los dos idiomas**:
   abrí la app **Google** → tu foto → Configuración → Voz → **Reconocimiento de voz sin conexión** →
   descargá *Español (Latinoamérica)* o *Español (Estados Unidos)* **y** *Português (Brasil)*.
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
