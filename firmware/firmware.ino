#include <WiFi.h>
#include <WebServer.h>
#include <errno.h>
#include "mbedtls/md.h"

// Clave compartida para HMAC-SHA256.
// En produccion o entornos reales, define firmware/secrets.h (ignorado por Git).
#if __has_include("secrets.h")
#include "secrets.h"
#else
const char *CLAVE = "riego-hmac-demo-2026";
#endif

// Pines adaptados de EVA1 (Arduino UNO -> ESP32)
// Nota: No se usa DHT11 porque en ESP32 emulado por QEMU provoca watchdog reset.
// Se reemplaza por sensor analogico de humedad de suelo (GPIO 34) + respaldo simulado.
const int PIN_SENSOR_HUMEDAD = 34;
const int PIN_ACTUADOR = 4; // Rele SPDT bomba de agua (logica inversa EVA1: LOW = encendido)

WebServer server(80);

uint64_t ultimoContador = 0;     // Proteccion anti-repeticion (anti-replay)
float umbral = 35.0f;             // Humedad minima (%) para activar riego automatico
bool manual = false;              // false = automatico (EVA1), true = control manual
bool actuadorOn = false;          // Estado de la bomba
bool servidorIniciado = false;

float humedadSimulada = 32.0f;
unsigned long ultimaActualizacion = 0;
const unsigned long INTERVALO_SIM_MS = 2000;

String hmac(const String &msg) {
  byte resultado[32];
  mbedtls_md_context_t ctx;
  mbedtls_md_init(&ctx);
  const mbedtls_md_info_t *info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  mbedtls_md_setup(&ctx, info, 1);
  mbedtls_md_hmac_starts(&ctx, (const unsigned char *)CLAVE, strlen(CLAVE));
  mbedtls_md_hmac_update(&ctx, (const unsigned char *)msg.c_str(), msg.length());
  mbedtls_md_hmac_finish(&ctx, resultado);
  mbedtls_md_free(&ctx);

  char hex[65];
  for (int i = 0; i < 32; i++) {
    sprintf(hex + (i * 2), "%02x", resultado[i]);
  }
  hex[64] = '\0';
  return String(hex);
}

bool compararSeguro(const String &a, const String &b) {
  if (a.length() != b.length()) return false;
  byte diff = 0;
  for (unsigned int i = 0; i < a.length(); i++) {
    diff |= (byte)(tolower(a[i]) ^ tolower(b[i]));
  }
  return diff == 0;
}

int checksum(const String &s) {
  int c = 0;
  for (unsigned int i = 0; i < s.length(); i++) {
    c = (c + (unsigned char)s[i]) & 0xFF;
  }
  return c;
}

float leerSensor() {
  int raw = analogRead(PIN_SENSOR_HUMEDAD);
  // Si hay señal analogica real conectada al ADC (0..4095), mapear a 0..100%.
  // En QEMU sin hardware analogico conectado, raw suele ser 0 o ruido minimo:
  // en ese caso usamos un modelo de humedad de suelo realista para la demo.
  if (raw > 50 && raw < 4050) {
    return constrain(map(raw, 4095, 0, 0, 1000) / 10.0f, 0.0f, 100.0f);
  }
  return humedadSimulada;
}

void actualizarModeloHumedad() {
  unsigned long ahora = millis();
  if (ahora - ultimaActualizacion < INTERVALO_SIM_MS) return;
  ultimaActualizacion = ahora;

  if (actuadorOn) {
    humedadSimulada += 1.5f;
  } else {
    humedadSimulada -= 0.4f;
  }
  if (humedadSimulada < 15.0f) humedadSimulada = 15.0f;
  if (humedadSimulada > 85.0f) humedadSimulada = 85.0f;
}

void aplicarActuador() {
  // Logica inversa de EVA1 para rele SPDT optoacoplado (LOW = bomba encendida)
  digitalWrite(PIN_ACTUADOR, actuadorOn ? LOW : HIGH);
}

void handleLectura() {
  float valor = leerSensor();
  String datos = String(valor, 1) + ";" + String(umbral, 1) + ";" + (actuadorOn ? "ON" : "OFF");
  String payload = "LEC;" + datos + ";" + String(checksum(datos)) + "\n";
  Serial.printf("http GET /lectura from %s -> %s",
                server.client().remoteIP().toString().c_str(),
                payload.c_str());
  server.send(200, "text/plain", payload);
}

void handleCmd() {
  String accion = server.arg("accion");
  String nStr = server.arg("n");
  String sig = server.arg("sig");

  if (accion.length() == 0 || nStr.length() == 0 || sig.length() == 0) {
    Serial.println("seguridad RECHAZADO parametros incompletos");
    server.send(400, "text/plain", "parametros incompletos\n");
    return;
  }

  if (nStr[0] == '0') {
    server.send(400, "text/plain", "contador invalido\n");
    return;
  }
  for (unsigned int i = 0; i < nStr.length(); i++) {
    if (nStr[i] < '0' || nStr[i] > '9') {
      server.send(400, "text/plain", "contador invalido\n");
      return;
    }
  }
  errno = 0;
  uint64_t n = strtoull(nStr.c_str(), nullptr, 10);
  if (errno == ERANGE) {
    server.send(400, "text/plain", "contador invalido\n");
    return;
  }
  String msg = "CMD;" + accion + ";" + nStr;

  if (!compararSeguro(sig, hmac(msg))) {
    Serial.println("seguridad RECHAZADO firma " + msg);
    server.send(401, "text/plain", "firma invalida\n");
    return;
  }

  if (n == 0 || n <= ultimoContador) {
    Serial.println("seguridad RECHAZADO repeticion " + msg);
    server.send(409, "text/plain", "repetido\n");
    return;
  }

  ultimoContador = n;

  if (accion.startsWith("UMBRAL:")) {
    float nuevoUmbral = accion.substring(7).toFloat();
    if (nuevoUmbral < 5.0f) nuevoUmbral = 5.0f;
    if (nuevoUmbral > 95.0f) nuevoUmbral = 95.0f;
    umbral = nuevoUmbral;
  } else if (accion == "ACT:AUTO") {
    manual = false;
  } else if (accion == "ACT:ON") {
    manual = true;
    actuadorOn = true;
  } else if (accion == "ACT:OFF") {
    manual = true;
    actuadorOn = false;
  } else {
    Serial.println("cmd RECHAZADO accion desconocida " + accion);
    server.send(400, "text/plain", "accion invalida\n");
    return;
  }

  aplicarActuador();
  Serial.println("cmd OK " + accion);
  server.send(200, "text/plain", "ACK;" + accion + ";OK\n");
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_ACTUADOR, OUTPUT);
  actuadorOn = false;
  aplicarActuador();

  // Hertzbench fija SSID "Espressif" (abierta) y canal 5
  WiFi.begin("Espressif", "", 5);
  server.on("/lectura", handleLectura);
  server.on("/cmd", handleCmd);
}

void loop() {
  if (WiFi.status() == WL_CONNECTED && !servidorIniciado) {
    Serial.printf("wifi up ip=%s\n", WiFi.localIP().toString().c_str());
    server.begin();
    servidorIniciado = true;
    Serial.println("http listening on 80");
  }
  if (servidorIniciado) server.handleClient();
  actualizarModeloHumedad();

  // Modo automatico de EVA1: activa riego si humedad < umbral aunque no haya app conectada
  if (!manual) {
    actuadorOn = (leerSensor() < umbral);
  }
  aplicarActuador();
}
