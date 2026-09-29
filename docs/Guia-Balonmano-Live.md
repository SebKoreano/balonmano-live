# Balonmano Live · Guía rápida

Una app gratuita para controlar el marcador y transmitir balonmano a YouTube desde un solo Android, en horizontal. El público recibe cámara, sonido y marcador; los botones quedan en tu teléfono.

## 1. Instalar

1. Descarga **Balonmano-Live-1.0.0.apk** desde [Releases](https://github.com/SebKoreano/balonmano-live/releases) y ábrelo en tu teléfono desde Archivos o Descargas.
2. Si Android lo solicita, permite que esa aplicación instale apps de esta fuente. Instala Balonmano Live; después puedes desactivar ese permiso de instalación.
3. Abre la app, coloca el teléfono horizontal y activa cámara y micrófono cuando los solicite.

Objetivo de compatibilidad: **Android 8 o posterior**, con cámara trasera, micrófono y capacidad de codificar video 720p. La compatibilidad de cada modelo debe comprobarse en una prueba real. El APK se puede compartir con tu equipo; cada teléfono configura su propia conexión a YouTube.

## 2. Preparar YouTube

Haz la configuración antes del partido. Puedes usar la computadora o el navegador del teléfono; si la página móvil no muestra la sala de control, solicita la versión para computadora.

1. Entra en [YouTube Studio](https://studio.youtube.com), selecciona **Crear → Emitir en directo** y prepara una emisión con codificador. Define título y privacidad; usa **Privada** para las pruebas.
2. Copia la **URL del servidor** y la **clave de emisión**. En Balonmano Live abre **YouTube**, pega esos datos y guarda.
3. Activa **inicio automático** para poder iniciar el envío desde la app. Recomendamos dejar desactivada la finalización automática para tener margen de reconexión si se corta internet; en ese caso termina el directo manualmente en Studio después del partido.
4. Vuelve a Balonmano Live y toca **Transmitir → Confirmar** cuando estés listo.

La app transforma el servidor RTMP de YouTube en una conexión RTMPS cifrada. La clave queda cifrada en el almacenamiento privado del teléfono; no se incluye en el APK que compartes. Para eliminarla: **YouTube → Borrar conexión**.

**«Enviando a YouTube» indica que el servidor acepta el video.** Comprueba en Studio que el directo está publicado con la privacidad correcta. Si usas una emisión programada sin inicio automático, deberás pulsar también el botón de emisión de Studio.

Referencias oficiales: [transmitir con un codificador](https://support.google.com/youtube/answer/2907883?hl=es), [inicio y finalización automáticos](https://support.google.com/youtube/answer/9854503?hl=es).

## 3. Manejar el partido

| Control | Qué hace |
|---|---|
| **Partido** | Cambia nombres, colores y duraciones. La duración de los tiempos se configura antes de empezar. |
| **+ GOL / −** | Suma o corrige un gol del equipo correspondiente. Nunca baja de cero. |
| **Iniciar / Pausar reloj** | Controla el tiempo de juego. Por defecto, 00:00–30:00 y 30:00–60:00. |
| **Tocar el reloj grande** | Corrige minutos y segundos dentro del período actual y deja el reloj en pausa. No cambia las exclusiones. |
| **+ 2 min** | Añade una exclusión; el dorsal es opcional. Solo descuenta tiempo cuando corre el reloj del partido. |
| **Gestionar exclusiones** | Muestra todas las exclusiones y permite corregir dorsal, tiempo pendiente o eliminarlas. |
| **T. muerto** | Pausa el juego y comienza una cuenta de 60 segundos, ajustable. Aumenta el contador del equipo. El partido se reanuda manualmente. |
| **Mantener pulsado T. muerto** | Corrige el contador de tiempos muertos. También está disponible desde Partido. |
| **2.º tiempo / + Prórroga** | Se habilita al terminar el período. Conserva goles y exclusiones; el nuevo período empieza pausado. Las prórrogas duran 5 minutos por defecto. |
| **Pellizcar la imagen** | Ajusta el zoom si la cámara del teléfono lo admite. |
| **Partido → Nuevo partido** | Tras confirmar, borra goles, reloj, tiempos muertos y exclusiones. Conserva equipos, duraciones y YouTube. |

El marcador muestra hasta tres exclusiones de cada equipo; si hay más, indica cuántas adicionales quedan. Todas siguen contando y pueden gestionarse. La app registra las decisiones de la mesa y permite corregirlas; no arbitra las reglas del torneo.

Los colores se escriben como `#RRGGBB`. Ejemplos: azul `#369EE8`, naranja `#E87848`, verde `#36B77D` y rojo `#E34E61`.

## 4. Interrupciones y final del partido

- **Sin internet:** el marcador sigue funcionando. La app reintenta la conexión con esperas de 2, 4, 8, 16 y hasta 30 segundos. No puede recuperar imágenes que no llegaron a YouTube.
- **Cambio de aplicación:** durante una transmisión, el servicio mantiene cámara y micrófono con una notificación visible. El comportamiento frente a llamadas, bloqueo y ahorro de batería debe probarse en tu modelo.
- **Cierre inesperado:** vuelve a abrir la app. Recupera el último estado guardado **en pausa**, incluso si había un tiempo muerto. Revisa el reloj y reanuda manualmente. El guardado periódico se realiza aproximadamente cada segundo mientras cambia el partido.
- **Sin permiso de cámara/micrófono:** puedes usar el marcador, pero debes habilitar ambos permisos para transmitir.
- **Para terminar:** toca **Detener envío → Confirmar**. Después comprueba y termina el directo en YouTube Studio, especialmente si desactivaste su finalización automática.

Usa conexión estable y prueba con la misma red y posición que usarás durante el partido. La app no cobra suscripción; la conexión de internet o los datos de tu operador siguen siendo necesarios. La configuración inicial envía aproximadamente 4 Mb/s de video más audio.

## 5. Prueba real pendiente antes del primer partido

La entrega incluye pruebas automáticas y verificaciones locales. **No se ha completado una transmisión privada de 90 minutos desde un teléfono físico a tu canal.** No había un Android físico conectado ni credenciales del canal disponibles.

Prueba el APK durante al menos 90 minutos en un directo privado: verifica audio, legibilidad del marcador, goles, reloj, sanciones, orientación, reconexión, temperatura y batería. Observa el directo desde otro dispositivo cuando sea posible. Anota modelo, versión de Android, red y cualquier incidencia en `Verificacion.md`.

## 6. Archivos y actualizaciones

- **Balonmano-Live-1.0.0.apk:** app para instalar y compartir.
- **Código fuente:** disponible en el repositorio; usa **Code → Download ZIP** o clona el proyecto para abrirlo en Android Studio.
- **Vista-app.png:** captura de la interfaz en el emulador; la cámara muestra una imagen sintética de prueba.
- **Marcador.png:** ejemplo del marcador generado por la app.
- **Verificacion.md:** comprobaciones realizadas y prueba real pendiente.

El almacén privado de firma y las contraseñas no se publican. No son necesarios para instalar la app. Para compilar una distribución propia, configura tu propia firma siguiendo el README.

Las actualizaciones deben usar la misma firma y un número de versión superior para conservar los datos. Desinstalar la app elimina el partido y la configuración de YouTube guardados en ese teléfono.
