# Mis Dibujos

App Android que convierte un teléfono de **tinta electrónica Bigme HiBreak** en un aparato
solo para que un niño **dibuje, tome fotos y toque un pianito**, sin internet, sin anuncios
y sin poder salir a otras apps (modo kiosco).

*English summary: an offline kiosk app that turns a Bigme HiBreak e-ink phone into a
drawing / photo / toy-piano device for kids. It uses Bigme's own low-latency handwriting
engine for near real-time finger drawing. Spanish UI.*

> Proyecto independiente, sin relación con Bigme. "Bigme" y "HiBreak" son marcas de sus dueños.

## Qué hace

- **Dibujar** con el dedo casi en tiempo real: usa el motor de escritura rápida que Bigme trae
  en el sistema (`/system/framework/handwritten.jar`), alimentado a mano con los toques del dedo.
- Tres grosores, goma, deshacer (40 pasos), hojas nuevas, galería de dibujos editables.
- **Cuatro puntas**: lápiz, grafito (con textura), pincel (se adelgaza al ir rápido) y punteado.
  Son puntas que Bigme trae en el sistema y no usa en ninguna app; se pintan igual de rápido.
- **Sellos**: estrella, corazón, flor, sol, nube, casita, gato, pez y mariposa. Un toque pone el
  sello; arrastrar antes de soltar lo hace más grande. Se deshacen como cualquier trazo.
- **Figuras perfectas**: si el trazo parece línea (se endereza a 0°, 45° o 90°), círculo, óvalo,
  triángulo (con ángulo recto o isósceles si casi lo es), rectángulo, rombo, pentágono, hexágono,
  **corazón**, **estrella** (de contorno o de un solo trazo) o **luna**, se corrige. Si hay duda,
  el dibujo se queda como está (se puede apagar: Asistido / Libre).
- **Mandala**: cada trazo se repite 12 veces en simetría.
- **Película**: vuelve a dibujar la hoja trazo por trazo, con tinta real (máx. 25 s). Se guarda con el dibujo.
- **Dibujo musical**: cada trazo suena según su altura, en escala pentatónica (botón Música).
- **Juegos → Laberinto / Canica** desde el inicio: se recorren dibujando (el trazo se detiene en las paredes) o con
  una **canica** que rueda inclinando el aparato. Crecen de tamaño al ganar.
- **Agitar** el aparato ofrece una hoja nueva (la anterior queda guardada).
- **Presión con el dedo**: si el panel reporta el tamaño del dedo, apoyarlo más hace la línea más gruesa
  (se calibra solo con los primeros trazos).
- **Limpieza suave**: tras cada pausa se repinta en grises limpios solo la zona dibujada, así casi no hace
  falta el refresco completo que parpadea.
- **Fotos** con la cámara selfie o trasera, filtros (Ojotes, Globo, Espejo, Invertido) y
  "pintar sobre la foto" como hoja para colorear (contornos).
- **Pianito** de una octava con cinco sonidos sintetizados.
- Sonidos de campanita sintetizados (sin archivos de audio).
- Al dormirse muestra una ilustración de descanso (o negro); se duerme solo tras 1 minuto.
- Nivel de batería en la pantalla de inicio (el kiosco oculta la barra de estado).
- **Linterna** (flash LED trasero) en el inicio y en el piano; se apaga sola a los 5 minutos,
  al dormir el aparato y al abrir la cámara.
- Todo se guarda en el aparato: `Pictures/Dibujos` (PNG) y `Pictures/Fotos` (JPG).
- Menú de papá/mamá con PIN (mantener 5 s el saludo): modo de pantalla, salir 15 minutos,
  cambiar PIN, fondo de descanso, nombre del niño en el saludo, quitar el kiosco.

Guía de uso: [docs/USO.md](docs/USO.md). Detalles técnicos: [AGENTS.md](AGENTS.md).

## Aparato probado

Bigme HiBreak blanco y negro (código BW), Android 11, MediaTek MT6765, sin lápiz.
Otros modelos de Bigme podrían funcionar si traen `handwritten.jar`; si no, la app usa un
lienzo normal de Android (más lento). En otros teléfonos funciona, pero sin el motor rápido.

## ⚠️ Antes de instalar

- La app se instala como **dueña del dispositivo (device owner)** para el modo kiosco.
  Android solo lo permite en un aparato **recién restablecido y sin cuentas**:
  **se borra todo el teléfono**. Respalda antes lo que necesites.
- Sin root no hay riesgo de dañar el sistema, pero el kiosco solo se quita desde el menú
  de papá/mamá (o restableciendo de fábrica). **No olvides el PIN.**
- Los pasos opcionales de limpieza (desactivar apps, idioma) son para el HiBreak; revísalos
  antes de usarlos en otro aparato.

## Instalar desde cero

Requisitos en una PC con Windows: [adb (platform-tools)](https://developer.android.com/tools/releases/platform-tools),
JDK 17, Android build-tools 30.0.3 y la plataforma android-30. El script los busca en
`C:\tmp\hb` o en la carpeta de la variable `HIBREAK_TOOLS`
(`jdk-17*`, `sdk\build-tools\30.0.3`, `sdk\platforms\android-30`).

1. Restablece el aparato de fábrica. En la configuración inicial **no inicies sesión con
   Google** ni pongas bloqueo de pantalla.
2. Activa las opciones de desarrollador y la **depuración USB**, conecta el cable y acepta.
3. Compila e instala (la primera vez se crea tu propia llave de firma en `keystore/`;
   **guárdala**: sin ella no podrás actualizar la app):
   ```powershell
   powershell -ExecutionPolicy Bypass -File build.ps1 -Install
   ```
4. Hazla dueña del dispositivo y fija el PIN y el nombre:
   ```powershell
   adb shell dpm set-device-owner mx.dibujos.kiosko/.AdminReceiver
   adb shell am start -n mx.dibujos.kiosko/.MainActivity --es setPin 1234 --es childName "Ana"
   ```
5. Opcional (HiBreak): quitar apps que no se usan y poner el sistema en español:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\restaurar-apps.ps1 -Quitar   # revertir: sin -Quitar
   ```
   Idioma: ver `tools/idioma/SetLocale.java`.

Si hay varios aparatos conectados, agrega `-Serial <serie>` a los scripts o define `ANDROID_SERIAL`.

## Pruebas

```powershell
python tests\test_algorithms.py        # figuras, filtros e historial (necesita JDK en el PATH)
```
Pruebas en el aparato: [tests/android/README.md](tests/android/README.md).

## Privacidad

No usa internet ni cuentas. La detección de cara de los filtros es local (`android.media.FaceDetector`)
y solo ubica los ojos. No hay analíticas.

## Créditos

Ilustraciones generadas para este proyecto; sonidos sintetizados en código. Ver
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Los archivos de `stubs/` solo contienen
firmas de clases del sistema de Bigme para poder compilar; no incluyen código de Bigme.

## Licencia

MIT — ver [LICENSE](LICENSE).
