# Riego Inteligente — EVA2

## Integrantes y problema

- Integrantes: **completar los nombres de la dupla**.
- Problema: monitorear la humedad del suelo y controlar una bomba de riego,
  manteniendo un modo automático independiente de la aplicación.
- Antes de entregar, contrastar el firmware con el prototipo y el código de la
  EVA1; en este repositorio no se incluye el diseño original.

## Herramientas y enlace

Android Studio, Kotlin, Jetpack Compose, Room/SQLite, ESP32 con Arduino y
Hertzbench. Se usa Wi-Fi con HTTP porque es el enlace recomendado para la
emulación de la guía; Bluetooth clásico no está soportado por Hertzbench.
El firmware utiliza el ADC o, si no detecta una señal adecuada, una humedad
simulada para la demostración. La bomba usa un relé de lógica inversa.

## Uso de la aplicación

Al abrirla normalmente, registrar una cuenta local e iniciar sesión. La
pantalla principal muestra humedad, umbral, estado de bomba, gráfico e historial;
permite cambiar el umbral, encender, apagar o volver al modo automático. La
app guarda lecturas y encola comandos cuando falla la red. Al reconectar intenta
enviarlos nuevamente.

Para automatizar el escenario Hertzbench, la **versión debug**, cuando recibe
los extras `board_url` y `usuario`, ejecuta en segundo plano un cambio de
umbral, un encendido, una repetición y una firma alterada. Esta automatización
puede enviar comandos sin iniciar sesión y es exclusivamente para la prueba
emulada; no habilita los controles de la pantalla ni se ejecuta en release.
No usar la versión debug con un actuador real.

## Protocolo y seguridad

- `GET /lectura` responde `LEC;valor;umbral;ON|OFF;checksum`. El checksum es
  la suma de bytes de `valor;umbral;estado`, módulo 256.
- `GET /cmd?accion=...&n=...&sig=...` firma `CMD;accion;n` con HMAC-SHA256
  en hexadecimal. Acciones: `UMBRAL:<valor>`, `ACT:ON`, `ACT:OFF`, `ACT:AUTO`.
- El ESP32 responde `ACK;accion;OK` si acepta, 401 si la firma es inválida y
  409 si se repite el contador. El contador aceptado se guarda solo en memoria
  y se pierde al reiniciar el ESP32.
- Las contraseñas de las cuentas locales se guardan con salt y PBKDF2. El
  campo `usuario` del escenario no autentica a nadie.

La clave predeterminada es **pública y solo sirve para demostración**. Para
cambiarla, copiar `firmware/secrets.h.example` como `firmware/secrets.h` y
configurar la misma clave como propiedad Gradle o variable de entorno
`RIEGO_HMAC_KEY` antes de compilar la app. No subir la clave privada al
repositorio. La clave queda dentro del APK y HTTP no cifra el tráfico: este
prototipo no es adecuado para proteger un sistema real.

## Compilación y pruebas

Compilar el APK debug con el Gradle Wrapper desde la raíz:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

Android Studio debe tener configurado el SDK, incluida la plataforma Android
37 y las herramientas necesarias. Los escenarios usan el APK en
`app/build/outputs/apk/debug/app-debug.apk`.

Hertzbench requiere además `libqemu-xtensa` compatible y sus ROM, el compilador
para ESP32 y los dos AVD de los escenarios. Consultar la
[guía oficial del ESP32](https://github.com/Rixmerz/hertzbench/blob/master/hertzbench/docs/e2e-esp32.md).
En Windows, la guía de la evaluación advierte que la ejecución completa no
está validada. Si no se consigue la biblioteca para Windows, usar un entorno
Linux preparado o el laboratorio. Configurar `QEMU_ESP32_LIB` con la ruta de
la biblioteca compatible. En API 37 puede ser necesario conceder el permiso
de red local al iniciar la app.

Desde la raíz, reemplazando la ruta del ejecutable por la instalada:

```powershell
& "$env:USERPROFILE\Downloads\hertzbench-windows-x64.exe" validate .\eva2.yaml
& "$env:USERPROFILE\Downloads\hertzbench-windows-x64.exe" run .\eva2.yaml --out .\out\pixel8
& "$env:USERPROFILE\Downloads\hertzbench-windows-x64.exe" run .\eva2-dispositivo2.yaml --out .\out\dispositivo2
```

Guardar la salida de consola y los archivos generados como evidencia. La
carpeta `out` está ignorada por Git: para entregar evidencia en el repositorio,
incorporarla deliberadamente en una carpeta apropiada, sin incluir secretos.

## Tabla de pruebas

Ejecutadas el 2026-11-26 en Linux con KVM, Hertzbench v0.4.4, ESP32 emulado
en QEMU y AVD `pixel8` (Android 16, API 36). La evidencia de cada corrida
(salida de consola, serial de cada nodo y `metrics.json`) está en `evidencia/`.

| Prueba | Cómo se probó | Resultado |
| --- | --- | --- |
| Compilación y pruebas unitarias | `./gradlew assembleDebug testDebugUnitTest` | Aprobado: 6 de 6 tests |
| Lecturas | `eva2.yaml`: la app recibe y guarda cada lectura con checksum válido | Aprobado |
| Umbral y bomba | `eva2.yaml`: `UMBRAL:40` y `ACT:ON` responden `200 ACK` | Aprobado |
| Firma adulterada y repetición | `eva2.yaml`: 401 y 409, ambos registrados en el serial del ESP32 | Aprobado: `RESULT: PASS`, 8 de 8 aserciones |
| Contingencia | `eva2-contingencia.yaml`: interferencia corta el Wi-Fi entre 30 s y 60 s | Aprobado: muestra "Reconectando", reintenta a 1, 2, 4 y 8 s y se recupera sola a los 73 s |
| Login | `eva2-login.yaml` corre `LoginTest` (androidTest) dentro de Hertzbench | Aprobado: 4 de 4 tests |
| Dos dispositivos | `eva2-dispositivo2.yaml` con un AVD de API 37 | Pendiente: requiere instalar ese AVD |

Para repetir los tests de login: `./gradlew assembleDebug assembleDebugAndroidTest`
y luego `hertzbench run eva2-login.yaml --out out/login`.

También queda pendiente mostrar al docente el mockup y el método de login,
realizar la demostración en clase y subir el enlace del repositorio público a
Moodle según la guía.
