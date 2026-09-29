# Verificación · Balonmano Live 1.0.0

Fecha: 9 de septiembre de 2026. Identificador Android: `org.balonmano.live`.

## Completado

- **Compilación release:** APK compilado y optimizado con R8. Android mínimo 26 (Android 8), objetivo 37. JDK 17, Gradle 9.6.1 y Android Gradle Plugin 9.2.1.
- **Firma:** verificada con `apksigner`; firma APK v2 válida y clave RSA de 3072 bits. Certificado SHA-256: `cabbba8c7dcf66b2b7551687d9608e90291b95d33302bf659285ea4b20c6e802`.
- **24 pruebas JVM:** 18 del motor de partido, 4 de validación del servidor/clave y 2 de serialización y recuperación; cero errores y cero fallos.
- **Simulación de 90 minutos:** reloj monotónico, dos tiempos, descanso y prórrogas sin acumulación de error por conteo de ticks. Es una simulación acelerada del motor, no una transmisión de 90 minutos.
- **3 pruebas Android en emulador API 35:** cifrado y borrado con Android Keystore, marcador con nombres largos y exclusiones, y codificación local de cámara más marcador a 1280×720 con pista AAC. Cero fallos.
- **Actualización del video:** el clip local refleja el cambio de 12 a 13 goles mientras se codifica. El fotograma se inspeccionó visualmente; se ve el marcador sobre la imagen sintética de la cámara.
- **Permisos denegados:** se comprobó el aviso de cámara/micrófono y que el marcador permanece disponible.
- **Uso del APK release instalado:** cámara, suma de goles de ambos equipos, inicio/pausa, alta de exclusión, tiempo muerto y cierre forzado. Al volver a abrir se recuperaron los goles, la exclusión y el contador de tiempos muertos, con reloj pausado.
- **Análisis Android release:** cero errores; siete advertencias relacionadas con orientación, accesibilidad de gestos y clases TLS incluidas en dependencias. La conexión de la app exige YouTube, RTMPS y verificación del hostname. No usa gestores de confianza personalizados.
- **Privacidad por construcción:** el filtro recibe exclusivamente `MatchState`. No recibe vistas del operador, claves, diálogos ni datos de conexión. No se usa captura de pantalla.

## Pendiente en un teléfono físico

No había teléfono Android físico conectado ni acceso a la clave del canal. Por eso **no se completó la prueba privada en YouTube de al menos 90 minutos**. No se ha validado la estabilidad térmica, la batería, la audibilidad real, el comportamiento del fabricante ante llamadas/bloqueo ni la reconexión real Wi-Fi/datos en tu modelo.

La existencia de un APK compilado y las pruebas del emulador no equivalen a una certificación para todos los teléfonos Android 8 o posteriores.

### Registro para completar la prueba

| Dato | Resultado |
|---|---|
| Marca y modelo | Pendiente |
| Versión Android | Pendiente |
| Red y ubicación | Pendiente |
| Inicio y final de la prueba (mínimo 90 minutos) | Pendiente |
| Audio audible y sincronizado | Pendiente |
| Marcador legible y actualización de goles | Pendiente |
| Reloj, pausas, exclusiones y tiempos muertos | Pendiente |
| Desconexión breve y reconexión | Pendiente |
| Cambio de app, llamada y bloqueo | Pendiente |
| Temperatura y batería | Pendiente |
| Confirmación de finalización en YouTube Studio | Pendiente |

Usar un directo privado. La guía explica la preparación del canal y los controles de la app.
