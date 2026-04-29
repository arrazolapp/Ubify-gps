# 📡 ArrazolApp GPS Tracker

App Android nativa para tracking GPS en tiempo real de vendedores y transportistas.

## Características
- **Foreground Service**: GPS activo 24/7 incluso con la app cerrada
- **Auto-inicio**: se activa al encender el celular
- **Firebase Realtime Database**: envía ubicación cada 10 segundos
- **Notificación permanente**: muestra velocidad y estado en la barra del celular
- **Optimización de batería**: solicita exclusión automáticamente

## Compilar
El APK se compila automáticamente con GitHub Actions. 
Ve a la pestaña **Actions** → último build → descargá el artifact **ArrazolApp-GPS-Tracker**.

## Configuración
Al instalar por primera vez, ingresá:
- **Empresa**: ID de la empresa (ej: `demo_corp`)
- **ID Usuario**: el que genera el Admin Panel
- **Nombre, Rol, Placa, WhatsApp**

## Stack
- Kotlin
- Firebase Realtime Database
- Google Play Services Location (FusedLocationProvider)
- Foreground Service + BootReceiver
