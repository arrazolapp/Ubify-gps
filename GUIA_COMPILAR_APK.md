# 📱 ArrazolApp GPS Tracker — Guía para Compilar el APK

## Requisitos

- **Android Studio** (descargalo gratis en developer.android.com/studio)
- **Cuenta de Firebase** (la que ya tenés)

---

## PASO 1: Abrir el proyecto en Android Studio

1. Descargá y descomprimí la carpeta `apk-project`
2. Abrí Android Studio
3. Click en **"Open"** → seleccioná la carpeta `apk-project`
4. Esperá a que Gradle sincronice (puede tardar 2-5 minutos la primera vez)

---

## PASO 2: Agregar google-services.json

Este archivo conecta la app con tu proyecto de Firebase.

1. Andá a [console.firebase.google.com](https://console.firebase.google.com)
2. Abrí tu proyecto **tracking-gps-arrazolapp**
3. Click en ⚙️ → **Configuración del proyecto**
4. En "Tus apps", click en **"Agregar app"** → seleccioná **Android**
5. Nombre del paquete: **`com.arrazolapp.gpstracker`**
6. Click en **"Registrar app"**
7. Descargá el archivo **`google-services.json`**
8. Copiá ese archivo dentro de la carpeta: `apk-project/app/`

La estructura debe quedar así:
```
apk-project/
├── app/
│   ├── google-services.json  ← ACÁ
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/arrazolapp/gpstracker/
│           │   ├── MainActivity.kt
│           │   ├── SetupActivity.kt
│           │   ├── TrackingService.kt
│           │   └── BootReceiver.kt
│           └── res/
│               ├── layout/
│               ├── drawable/
│               ├── values/
│               └── mipmap-*/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## PASO 3: Agregar los íconos

1. En Android Studio, click derecho en la carpeta `app/src/main/res`
2. → **New → Image Asset**
3. Seleccioná tu logo (icon-512.png)
4. Ajustá el padding y click en **Next → Finish**

O manualmente: copiá `icon-192.png` a las carpetas `mipmap-*` como `ic_launcher.png`

---

## PASO 4: Compilar el APK

1. En Android Studio → menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Esperá a que compile (1-2 minutos)
3. Cuando termine, click en **"locate"** en la notificación que aparece abajo
4. El APK está en: `app/build/outputs/apk/debug/app-debug.apk`

---

## PASO 5: Instalar en el celular del vendedor

### Opción A: Por WhatsApp
1. Renombrá el archivo a `GPS-Tracker-ArrazolApp.apk`
2. Enviálo por WhatsApp al vendedor
3. El vendedor lo descarga y toca "Instalar"
4. Si pide permiso de "fuentes desconocidas", activarlo

### Opción B: Por Google Drive
1. Subí el APK a Google Drive
2. Compartí el link con el vendedor
3. El vendedor abre el link, descarga e instala

### Opción C: Por cable USB
1. Conectá el celular por USB
2. Copiá el APK al celular
3. Abrí el archivo desde el explorador del celular

---

## PASO 6: Configurar el agente en la app

Al abrir la app por primera vez, aparece la pantalla de configuración:

1. **Empresa:** `demo_corp` (o el ID de la empresa)
2. **ID de usuario:** el que aparece en el Admin Panel (ej: `user_juan_moa4bp9`)
3. **Nombre:** Juan Carlos Arrazola
4. **Rol:** Vendedor o Transportista
5. **Placa:** (opcional, para transportistas)
6. **WhatsApp:** 50663486904

Click en **"Guardar y Comenzar"** → la app va a la pantalla principal.

---

## Cómo funciona

1. El vendedor abre la app y presiona **"Iniciar Tracking"**
2. La app pide permisos de GPS y batería
3. Aparece una **notificación permanente** en la barra del celular
4. El GPS envía la ubicación a Firebase **cada 10 segundos**
5. **Aunque cierre la app, el tracking sigue funcionando** porque corre como un Foreground Service
6. Si reinicia el celular, el tracking se inicia automáticamente (BootReceiver)
7. Para detener: abrir la app → "Detener" o desde la notificación

---

## Permisos importantes que el vendedor debe aceptar

1. **Ubicación → "Permitir siempre"** (NO "solo cuando uso la app")
2. **Notificaciones → Permitir**
3. **Batería → No optimizar** (para que Android no mate la app)

---

## Troubleshooting

**El tracking se detiene después de un rato:**
- Ir a Configuración del celular → Apps → GPS Tracker → Batería → "Sin restricciones"
- En celulares Xiaomi/Samsung/Huawei: desactivar "Ahorro de batería agresivo"

**No aparece en el mapa del dashboard:**
- Verificar que el ID de empresa sea el mismo en la app y en el dashboard
- Verificar conexión a internet del celular

**Error al compilar:**
- Verificar que `google-services.json` esté en la carpeta `app/`
- Verificar que Android Studio tenga Gradle 8.2+ instalado
