# Componentes de terceros

## RootEncoder 2.8.0

Copyright Pedro Sánchez (pedroSG94) y colaboradores. Licencia Apache-2.0.
Repositorio: https://github.com/pedroSG94/RootEncoder
Tag utilizado: 2.8.0, commit 073be1db0b7bd69764f9f30e182b7dae4f32cc38.

Se utiliza la biblioteca sin modificar. El motor de marcador, sus controles y su persistencia son código propio. Las clases de composición de la app amplían las interfaces públicas de la biblioteca.

## Otras dependencias

- AndroidX y Android Gradle Plugin: Apache-2.0.
- Kotlin, kotlinx.coroutines y Ktor: Apache-2.0, JetBrains y colaboradores.
- Bouncy Castle: licencia MIT/X11 de Bouncy Castle, disponible en https://www.bouncycastle.org/licence.html.
- Gradle Wrapper: Apache-2.0.
- JUnit (solo pruebas): Eclipse Public License 1.0.
- Robolectric (solo pruebas): MIT.

Las licencias Apache-2.0 y Bouncy Castle se adjuntan en `licenses/`. El grafo de dependencias exacto se puede consultar con `gradlew :app:dependencies`. Las dependencias de prueba no se incluyen en el APK.
