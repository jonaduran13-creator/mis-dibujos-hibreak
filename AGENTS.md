# Mis Dibujos — app kiosco de dibujo para Bigme HiBreak (tinta electrónica)

App Android para que un niño dibuje y tome fotos en un teléfono de tinta electrónica
Bigme HiBreak. Corre en **modo kiosco**: es la única app del aparato.

Comentarios y textos de la app están en español; mantén ese estilo.

## El aparato

- Bigme HiBreak, código BW (blanco y negro), Android 11 (API 30), MediaTek MT6765,
  6 GB RAM, pantalla nativa 720×1440, app horizontal 1440×720 (densidad 278). El aparato se elige con `-Serial` o `ANDROID_SERIAL` (ver `tools/comun.ps1`).
- Sin lápiz digitalizador: se dibuja con el dedo (sensor táctil FocalTech `fts_ts`, 2 dedos).
- El panel físico está **girado 270°** respecto a la pantalla (está "acostado").
- Bootloader bloqueado, sin root. La app es **dueña del dispositivo** (device owner),
  activada con `adb shell dpm set-device-owner mx.dibujos.kiosko/.AdminReceiver`.
- 36 apps del sistema desactivadas con `pm disable-user` (se reactivan con `pm enable`).

## Compilar e instalar

No hay Gradle: `build.ps1` llama directo a aapt2, javac, d8, zipalign y apksigner.

```
powershell -ExecutionPolicy Bypass -File build.ps1            # compila → build/MisDibujos.apk
powershell -ExecutionPolicy Bypass -File build.ps1 -Install   # compila e instala por adb
```

- Herramientas (JDK 17, build-tools 30.0.3, platform android-30) están en `C:\tmp\hb`.
  Si se mueven, define `$env:HIBREAK_TOOLS` con la nueva carpeta.
- adb: `%LOCALAPPDATA%\Android\platform-tools\adb.exe`.
- **Firma**: `keystore/dibujos.keystore` (alias `dibujos`; contraseña en `keystore/keystore.properties`, ambos fuera de git).
  Como la app es device owner, **toda actualización debe firmarse con esta misma llave**;
  si se pierde, hay que restablecer el aparato de fábrica para instalar otra.
- Sube `android:versionCode` en `AndroidManifest.xml` en cada versión.
- `stubs/` son firmas de clases del sistema de Bigme, solo para compilar (no se empaquetan).

## Arquitectura (src/mx/dibujos/kiosko)

| Archivo | Qué hace |
|---|---|
| `MainActivity` | Kiosco (lock task, home persistente, sin barra de estado), pantalla de inicio (Dibujar / Fotos / Piano), barra de herramientas, guardado automático, galería, menú de papá/mamá con PIN, limpieza de sombras, pantalla negra al apagar. |
| `Lienzo` | Interfaz común de lienzo. |
| `XrzLienzo` | Lienzo principal sobre el **motor de escritura de Bigme** (ver abajo). Incluye "figura perfecta". |
| `DrawView` | Lienzo de respaldo en Android normal, si el motor de Bigme no existe. |
| `ShapeSnap` | Reconoce línea, círculo, óvalo, triángulo, rectángulo, pentágono, hexágono… y genera la versión perfecta. |
| `CameraPanel` | Cámara Camera1 con búfer NV21 y ruta única de orientación/encuadre para vista previa y filtros. |
| `PhotoMode` | Modo fotografía: tomar fotos (color, `Pictures/Fotos`), galería, visor, "pintar sobre esta foto". |
| `InkPhoto` | Convierte una foto en hoja para colorear (contornos Sobel, negro sobre blanco). |
| `GalleryPanel` | Cuadrícula de miniaturas de una carpeta. |
| `ToolButton` | Botones con iconos dibujados a mano (sin recursos). |
| `Sounds` | Sonidos de campanita sintetizados (pentatónica), sin archivos. |
| `Eink` | Acceso por reflexión al control de refresco de Bigme (`xrz.framework.manager`). |

Dibujos: `Pictures/Dibujos/*.png`. Fotos: `Pictures/Fotos/*.jpg` (MediaStore).

## Motor de escritura rápida de Bigme (lo más delicado)

- Está en `/system/framework/handwritten.jar` (en el BOOTCLASSPATH): `com.xrz.NoteView`
  (extiende `android.view.HandwrittenView`), `SimplePen`, `Rubber`, `BasePen`.
  Pinta en un búfer compartido y lo manda directo al panel: mucho menos retraso.
- El servicio `handwrittenservice` **solo lee el lápiz digitalizador**, que el HiBreak no
  tiene. Por eso `XrzLienzo` pasa los toques del dedo a mano con
  `note.onInputTouch(acción, x, y, presión, herramienta)` — acción DOWN=1 MOVE=2 UP=3,
  herramienta DEDO=2 — en coordenadas del panel según la rotación: en landscape ROTATION_90 son `(rawX, rawY)`; en portrait ROTATION_0 son `(rawY, anchoPantalla − rawX)`.
- `setStrokeWidthRange(min, max)` exige **max > min**, o ignora el grosor.
- `getBlendBitmap()` viene acostado: usar `getBlendBitmapAfterRotation()` para guardar.
- `setBackground()` espera la imagen en orientación del panel: se gira según el display, usando `panelAngle()` (`XrzLienzo.fit`); en landscape ROTATION_90 no se gira.
- El motor registra trazos en su propio hilo: tras soltar un trazo hay que esperar
  (~180 ms) antes de `revertPrev()`, o se borra el trazo anterior.
- adb **no puede** inyectar toques sobre esta capa. Para probar desde la PC:
  `adb shell am start -n mx.dibujos.kiosko/.MainActivity --efa testPath x0,y0,x1,y1,… --ez testHold true`
  (testHold = quedarse quieto al final para la figura perfecta).
- Para forzar el lienzo normal: `--ez motorNormal true`.

## Tinta electrónica

- Modos de refresco (`xrz.framework.manager.EinkRefreshMode`): DU=2, A2=16, CLEAN=176,
  HD=177, NORMAL=178, FAST=179, HANDWRITE=1029. La ventana usa DU por defecto.
- `Eink.cleanRefresh()` = refresco completo con parpadeo; se hace al cambiar de pantalla y
  tras pausas de 3 s si hubo 12+ trazos, borrador o deshacer (contra sombras/ghosting).
- Tras repintados del motor (deshacer, figura perfecta) se repinta la barra de botones
  (`onRedrawn`), porque la tinta a veces la deja sin dibujar.

## Probar sin molestar al niño

- Capturas: `adb exec-out screencap -p > captura.png` (muestra la composición, no la tinta física).
- La cámara frontal puede captar personas: borra capturas y fotos de prueba al terminar.
- Los dibujos del niño están en `Pictures/Dibujos`; no los borres al limpiar pruebas.

## Pendiente / ideas

- Confirmar en el aparato que la pantalla negra al apagar se ve negra en la tinta física.
- Confirmar en el aparato que los botones ya no desaparecen tras deshacer.
- El PIN de papá/mamá se fija con `--es setPin NNNN` solo si no existe; se cambia desde el menú
  (mantener 5 s el botón de galería).

## Actualización 1.4 (25 de octubre de 2026)

- Interfaz horizontal. Solo cámara usa FULL_SENSOR y soporta vertical/horizontal. Al cerrar vuelve a landscape y restaura el lienzo previo. No hacer cambios de orientación al elegir filtros.
- `CameraPanel`: Camera1 + NV21. TODOS los modos, también Normal, pasan por `decode → rotación → espejo frontal → recorte proporcional → filtro → ImageView`. Guardar copia exactamente el fotograma mostrado. No volver a usar getBitmap de TextureView para efectos: no conserva la misma transformación que se veía y causaba giros/estiramientos.
- `FunFilters`: Ojotes/Globo localizan ojos con FaceDetector, suavizan posiciones y toleran pérdidas breves. Espejo es simétrico y Píxeles promedia bloques de cuatro tonos.
- `PhotoToDrawPanel`: revisión de foto antes de pintar, Contornos / Foto clara, sin recortar la foto original.
- `PianoAudio/PianoMode`: sintetizador streaming con cinco timbres e iconos, dos dedos, apagado y foco de audio.
- Mandala usa `DrawView` con seis ejes y reflexión (doce copias), no el búfer nativo de Bigme. El resto del dibujo sigue usando el motor nativo. No reflejar el bitmap de fondo, solo los trazos nuevos.
- `SleepLogo` registra el callback nativo `XrzEinkManager.SuspendCallback`. Entrega un bitmap al firmware ANTES del reposo; ACTION_SCREEN_OFF por sí solo llega tarde. Siete fondos, o negro en preferencias. Los stubs de XrzEinkManager y ISuspendCallback son solo de compilación.
- El administrador declara force-lock. En un aparato ya provisionado, hay que refrescarlo con `adb shell dpm set-active-admin mx.dibujos.kiosko/.AdminReceiver`; build.ps1 -Install lo hace.
- Ver docs/USO.md. No borrar dibujos o fotos del niño al probar.

## Revisión 1.5

- No hay botones Dormir en la UI: solo se usa el botón físico y SleepLogo sigue activo.
- DrawingHistory mantiene hasta cuarenta estados por hoja, compartidos entre los dos motores. Cada onStrokeStarted captura el estado anterior; se comprime en PNG fuera del hilo de UI. Cambiar Mandala, abrir/cerrar cámara o cancelar una foto NO debe vaciar este historial. Solo se limpia en una hoja nueva, al abrir otro dibujo o al aceptar una foto como nueva hoja.
- MainActivity.undoStroke restaura el estado anterior con loadPhoto para marcarlo como cambio pendiente de guardar. No volver a depender de la pila interna de cada motor al cambiar de modo.
- XrzLienzo usa un contador generation para cancelar correcciones de figura pendientes al cargar, desactivar o deshacer. Así una corrección retrasada no pinta encima de un estado restaurado.

## Revisión 1.6

- Botón Asistido / Libre. Pref assisted=true por defecto. Se aplica a ambos motores en createLienzo y al cambiar la preferencia. XrzLienzo cancela también el temporizador y cualquier corrección pendiente al desactivarlo. No modificar trazos ya hechos ni limpiar el historial al cambiar esta opción.

## Revisión 1.7 — contenido editable

- DrawView.load importa un PNG como contenido editable sobre papel blanco. loadEditable(content,paper,modified) distingue la composición de dibujo del papel que el borrador debe recuperar. Nunca usar loadPhoto para restaurar un dibujo, un estado de deshacer o una vista previa de cámara; loadPhoto declara una foto nueva como papel.
- MainActivity mantiene pagePaper separado. DrawingProjects guarda un ZIP privado `.dibujo` por URI (antes `.bernabe`; se migra solo), con drawing.png, paper.png opcional y document.json. AtomicFile conserva la versión previa si falla la escritura. El PNG público sigue disponible y se usa como contenido visible más reciente; el proyecto aporta la capa de papel y recuperación si ese PNG no se puede leer.
- ensureEditableCanvas migra al lienzo DrawView al reabrir, borrar o restaurar una imagen. El motor Bigme se conserva para hojas nuevas sin estas operaciones. La migración no limpia DrawingHistory ni cambia la herramienta elegida.
- El borrador Mandala transforma la geometría de los trazos, no el Canvas con su shader de papel. Girar ese shader reflejaba/giraba la foto al borrar.
- El papel se conserva en cambios de modo, deshacer y cámara. Solo se elimina al crear una hoja nueva, se sustituye al abrir otro proyecto, o se define al aceptar una foto nueva.
- tests/android contiene instrumentación autocontenida contra su propio paquete mx.dibujos.editchecks. No usa los datos de la app real ni abre una actividad. Tras ejecutar, desinstalar solo ese paquete de pruebas.

## Auditoría 1.7.1 (versionCode 27, 25 de octubre de 2026)

- **Cierre en bucle corregido**: `NoteView.getBlendBitmapAfterRotation()` lanza NullPointerException
  (en `Painter.blend`) si la superficie del motor aún no existe — p. ej. `reopenLast` al arrancar,
  vía `ensureEditableCanvas`. Como la app es el inicio del kiosco, eso la reabría y volvía a cerrar.
  `XrzLienzo.snapshot()` ahora atrapa la excepción y devuelve null; todos los que lo llaman aceptan null.
  No quitar ese try/catch.
- **Cámara**: el cuadro mostrado anterior se recicla al reemplazarlo (unas 5 imágenes de ~3 MB por
  segundo), nunca durante una captura; la foto guardada es siempre una copia (`latest.copy`).
- **Medido**: 5 ciclos dibujo→cámara→dibujo sin fugas: ~83–86 MB en dibujo, ~72–78 MB con cámara,
  hilos estables (31–33), la cámara se libera siempre. CPU de la app: 0% dibujando, ~20% de un
  núcleo con la cámara.
- **Sistema**: desactivados (reversible con `pm enable`) gms, gsf, gms.location.history,
  onetimeinitializer, partnersetup, syncadapters.contacts, apps.restore y tts de Google: ~150 MB y
  despertares de CPU sin uso (no hay cuenta ni Play Store; FaceDetector y Camera1 no dependen de GMS).
  Memoria disponible tras arrancar: ~4.8 GB. NO desactivar com.xrz.standby / com.xrz.settings /
  com.xrz.sys.control (reposo e imagen de descanso de Bigme).
- **Fallo del firmware visto una vez**: `system_server` se cayó en `DisplayPowerController.hideBackLogo`
  ("View not attached to window manager") 19 s después de una instalación. Es código de Bigme al
  quitar la imagen de reposo; no se puede corregir desde la app. Si se repite con frecuencia,
  probar con la opción de reposo negro o sin `SleepLogo`.
- La carga ("load average") de ~19 es normal en este aparato: la cuentan los hilos del controlador
  de tinta en espera (estado D), no el uso real de CPU (~90% libre).

## Versión 1.8 (versionCode 30)

- **Dibujo rápido otra vez**: en 1.7 `ensureEditableCanvas` pasaba al `DrawView` (lento) al reabrir,
  borrar o deshacer, y `rasterEditing` impedía volver al motor de Bigme. Ahora `XrzLienzo.loadEditable`
  pone la tinta guardada en la capa de primer plano del motor (`setForegroundAndBackgroundTogether`;
  la goma la borra) y el papel/foto en el fondo (la goma no lo toca). `inkOnly` separa la tinta:
  transparente donde la imagen coincide con el papel o es blanca. `ensureEditableCanvas` ya no hace nada.
  Solo Mandala usa `DrawView`, con refresco A2 en su vista. Probado en el aparato: reabrir, goma y
  deshacer siguen en `com.xrz.NoteView`.
- Lo cargado antes de que el lienzo tenga tamaño se aplica en `flushPending` (layout u onResume).
- Filtro de cámara 4: **Invertido** (negativo) en lugar de Píxeles.
- Volumen: el del sistema ya estaba al máximo; el piano mezclaba ×0.16. Ahora `tanh(suma×0.7)`
  (una nota ~75%, dos dedos ~95%). Campanitas de botones a 0.45.
- Pantalla se apaga a 1 minuto sin uso (`dpm.setSystemSetting SCREEN_OFF_TIMEOUT 60000` en `setupKiosk`).
- Idioma del sistema: español (México). Estaba en zh-CN tras el restablecimiento (mensajes del sistema y
  nombre del almacenamiento por USB en chino). Herramienta: `tools/idioma/SetLocale.java`
  (`app_process` como shell; la app no tiene permiso CHANGE_CONFIGURATION).
- 72 paquetes desinstalados para el usuario 0: lista en `tools/apps-quitadas.txt`;
  `tools/restaurar-apps.ps1` los reinstala (o `-Quitar` para volver a quitarlos).

## Versión 2.0 (versionCode 34) — figuras

- `ShapeSnap.classify` genera cada candidata (óvalo, polígono por esquinas, y plantillas: corazón,
  estrella, estrella de un trazo, rombo, luna ×2) y la califica con distancia simétrica trazo↔figura
  (mitad promedio, mitad percentil 90) / diagonal. Acepta si < 0.036, o si < 0.08 y < 0.6 × la mejor
  de otra familia (no aplica a círculo/óvalo). Reglas extra: hexágono/pentágono ganan al círculo hasta
  ×1.3; heptágono/octágono solo si ajustan 20% mejor que el círculo.
- Calibrado con un banco sintético (figuras y dibujos libres con temblor, giro ±15°, inicio y sentido
  al azar): ~97% aciertos y ~1% falsos con poco temblor; ~85% / ~1% con temblor medio.
- En el HiBreak tarda ~18 ms por trazo (antes de optimizar: 137 ms). `ShapeSnap.warmUp()` en
  `onCreate` prepara las plantillas en segundo plano para que el primer trazo no se trabe.
- `XrzLienzo.trySnap` registra en logcat (`MisDibujosXrz`) la figura detectada y el tiempo.
- `tools/comun.ps1` NUNCA elige solo un aparato por Wi-Fi (una vez instaló la app en una tele de la
  casa); si pasa, `--ez quitarAdmin true` (solo adb, solo si no es dueña) le quita el permiso de
  administrador para poder desinstalarla.

## Versión 2.1 (versionCode 36) — linterna

- `Linterna`: `CameraManager.setTorchMode` sobre el flash trasero (no requiere permiso de cámara). Botón en el
  inicio y en el piano (`Linterna.button`). Se sincroniza con `TorchCallback`; se apaga a los 5 min, en
  `ACTION_SCREEN_OFF` y antes de abrir la cámara (`CameraPanel.open`).

## Versión 2.2 (versionCode 37) — lo aprendido al decompilar Bigme (JADX + dexdump)

Herramientas: JADX 1.5.6 en `C:\tmp\hb\jadx` (oficial, sha256 545ea2be…); código decompilado de
`handwritten.jar`, `xrz.framework.server.jar` y `DisplayPowerController` en `C:\tmp\hb\sys\out-*`.

- **Motor de escritura (`com.xrz.NoteView`)**: `onInputTouch` mete el punto en una cola; el hilo
  `OverlayThreadLoop` lo pinta y llama `Handwritten.inValidate(rect, modo)`, que pide el envío al
  servicio nativo `handwrittenservice` (agrupa rectángulos; solo espera si se mezclan modos con un envío
  pendiente). `DrawThreadLoop` copia a la ventana normal cada 100–150 ms (solo afecta capturas). Modos:
  HANDWRITTEN=1029, RUBBER=1030, GU16 rect=1156, GC16 rect=1028. El camino ya es eficiente.
- **Bloqueo de 1 s tras un fondo**: `drawContent` guarda `mLastUpdateBgTime` y `onInputTouch` ignora
  toques 1 s ("Is busy for showing"). Pasaba en cada Deshacer, hoja nueva, reabrir o foto.
  `XrzLienzo.shortenInputBlock()` lo recorta a 250 ms por reflexión tras cada `setBackground` /
  `setForegroundAndBackgroundTogether`. Probado: trazo dibujado justo después de Deshacer, 0 avisos.
- **Caída del sistema al despertar** (`DisplayPowerController.hideBackLogo`): si `onPrepareSuspendLogo`
  falla o devuelve null (p. ej. la app se está actualizando), Bigme no pone imagen pero queda
  `isLogoShow=true` y al despertar hace `removeView` de una vista vieja que nunca limpia → excepción en
  system_server. Defensa: `SleepLogo` deja la imagen preparada, nunca falla ni devuelve null, y
  `build.ps1 -Install` reabre la app al terminar para que vuelva a registrarse.
- **Recommit** (`NoteView.enableRecommitMode(delay, modo)` → repinta zonas con un modo limpio) existe pero
  ni la app de imágenes de Bigme lo usa; no se activó porque su efecto solo se ve en la tinta física.
- `--ei testDelay N` (solo adb): espera antes de `testPath`, para probar trazos justo tras una acción.

## Versión 2.3 (versionCode 38) — puntas y sellos

- **Puntas nativas** (`Lienzo.setBrush`, `BRUSH_*`): `handwritten.jar` trae `Pencil` (textura de grafito,
  `PencilPaint`), `BrushPen` (tipo 103, ancho según velocidad) y `DashPen` (tipo 104) que ninguna app de
  Bigme usa. `XrzLienzo.setPen` crea la punta elegida; todas aceptan `setStrokeWidthRange(w-1, w)`.
  `BrushPen` va con ancho automático entre w/3 y 2w; `DashPen` con `DashPathEffect` proporcional al
  grosor (el 5,5 de fábrica se ve como línea sólida con el dedo). Stubs en `stubs/com/xrz`.
  `DrawView` (Mandala) solo imita el punteado (`Stroke.dashed`; se ve punteado al soltar).
  Preferencia `brush`. Probado en el aparato: las cuatro se pintan con el motor rápido.
- **Sellos** (`Stamps`): 9 dibujos en caja -1..1 como listas de trazos. `Lienzo.stamp(trazos)` en
  coordenadas de la vista: `XrzLienzo` los mete al motor con `onInputTouch` (puntos cada 6 px, en
  coordenadas de pantalla vía `getLocationOnScreen`); `DrawView` los agrega como trazos. Antes llama
  `onStrokeStarted` → el historial guarda la imagen y Deshacer quita el sello entero.
  En `MainActivity`: botón Sellos (grupo de `penButtons`) abre `stampPicker` (tira sobre la barra; tocar
  fuera la cierra); `StampPad` es una capa transparente sobre el lienzo que solo existe con un sello
  elegido (toque = radio 55 dp; arrastrar = más grande, con contorno punteado de guía). Elegir grosor o
  goma, o cambiar Mandala, sale de sellos (`stopStamps`). Pruebas: `tests/test_algorithms.py` (57).

## Versión 2.4 (versionCode 39) — película, música, laberintos, presión, limpieza suave

- **Eventos de tinta**: `Lienzo.Listener.onInk(acción, x, y, presión)` en coordenadas de la vista para cada
  punto pintado (dedo, figura perfecta, sello), `onInkReplaced()` cuando la figura perfecta reemplaza el
  trazo y `onBlocked()` cuando una `Gate` corta el trazo. `Lienzo.inject` pinta puntos sin avisar ni corregir.
- **Película** (`Recording`, `Pelicula`): `MainActivity.onInk` graba trazos (punta, grosor, goma, puntos con
  presión y tiempo), agrupados por acción como el historial (Deshacer → `undoGroup`). Se guarda en el
  `.dibujo` (`pelicula.bin` + `inicio.png` si la hoja no empezó en blanco). Máx. 150 000 puntos; al llenarse
  empieza otra desde la imagen de ese momento. Reproducción: trazos a 2×, pausas ≤ 0.3 s, todo en ≤ 25 s;
  escudo transparente: tocar la detiene y recarga la imagen final. Dibujos viejos: la película empieza
  desde la imagen abierta. Mandala o foto nueva reinician la película.
- **Música** (`Musica`): 12 notas pentatónicas (Do4–Mi6) por altura, pulso tipo marimba, mín. 85 ms entre
  notas. Preferencia `music`.
- **Presión** (`FingerSize`): `getevent -lp` muestra `ABS_MT_TOUCH_MAJOR 0–255`. Se aprende el rango con el
  promedio de cada trazo (log `MisDibujosDedo`); con ≥ 8 trazos y ≥ 6 unidades de diferencia, lápiz y
  grafito pasan a ancho automático (0.6–1.5×) con presión 1000–4096. **Sin verificar con dedo real.**
- **Laberinto** (`Maze`, `MazeArt`): backtracking, entrada arriba-izquierda, meta abajo-derecha; papel de la
  hoja (la goma no lo borra). `Gate` corta el trazo antes de cruzar una pared; `reach` solo cuenta celdas
  alcanzadas desde la entrada. Niveles 6×3…14×6 (`mazeLevel`). Figuras perfectas apagadas en el laberinto.
- **Canica** (`MarbleView`): sensor de gravedad (ejes según la rotación), colisión por segmentos de pared,
  vista en A2 con repintado del cuadrito; `setKeepScreenOn` mientras se juega.
- **Agitar** (`Shake`): 3 picos alternados > 11 m/s² en 1.3 s; pide confirmación (Nueva / Volver).
- **Limpieza suave**: `enableRecommitMode` existe en `NoteView` pero en este firmware (handwrittenservice
  v1.2.10) no hace nada: probado con pausas 300–1000 ms y modos 0/2/1028/1156, el servicio nunca envía el
  repintado (se ve en logcat `mtk_commit … mode=`). En su lugar `XrzLienzo.softCleanNow` llama
  `NoteView.inValidateNoteView(new FlushInfo(rect, true, false, 1156))` 0.8 s después del último trazo, sobre
  el rectángulo dibujado: verificado `mode=0x484` en logcat. El refresco completo pasa de cada 12 a cada 36
  trazos (o tras borrar/deshacer). Menú de papá/mamá → Limpieza de sombras. **Efecto en el panel sin verificar.**
- Órdenes por adb (solo shell): `testFilm`, `testMaze`, `testSolve`, `testMarble`, `testShake`,
  `--ei mazeLevel N`, `--ez softClean`.
- Corrección: `XrzLienzo` descarta cargas pendientes (de antes de tener tamaño) cuando llega otra carga;
  antes, al venir de Mandala, el dibujo anterior tapaba el laberinto recién puesto.
- Pruebas: 104 (`tests/test_algorithms.py`), incluidas película, laberinto, canica y agitar.



## 2.5 (versionCode 40): pulido visual y canica funcional

- Inicio conserva las ilustraciones e incorpora **Juegos → Laberinto / Canica**. Ambos usan el
  mismo laberinto; la segunda entrada activa directamente el control por inclinación.
- Herramientas agrupadas con separadores, sin un recuadro por herramienta; seleccionadas en
  negro. ToolButton adapta icono/texto a controles anchos, limita el tamaño del icono, distingue
  Volver de Deshacer, admite etiqueta por contexto y expone semántica de botón accesible.
- Piano, filtros, galería, hoja para colorear y confirmaciones comparten el mismo sistema.
  Durante Película aparece una indicación visible para detenerla tocando.
- **Bug real del HiBreak:** TYPE_GRAVITY existe y se registra, pero `dumpsys sensorservice`
  mostró eventos constantes (0,0,0) mientras ACCELEROMETER sí cambiaba al moverlo.
  MarbleView usa siempre acelerómetro; Tilt estima gravedad con filtro exponencial de 100 ms
  independiente de frecuencia y adapta los ejes a la rotación. No volver a preferir el sensor
  virtual simplemente porque existe. Registra solo las muestras 1, 100 y 250 para diagnóstico.
- `tests/test_algorithms.py`: 112 comprobaciones, incluyendo cuatro orientaciones, inclinación,
  rechazo de temblor, independencia de frecuencia y lectura dañada de Tilt.
- No se modificaron el motor nativo, los documentos editables, el reconocimiento de figuras,
  la normalización de cámara ni el callback de descanso. La actualización visual no requiere
  volver a activar el administrador del dispositivo.

- En esta sesión también se detectó un estado obsoleto del servicio de sensores: la app estaba
  TOP en la ventana, pero `cmd activity get-uid-state <uid>` devolvía NONEXISTENT y
  `cmd sensorservice get-uid-state mx.dibujos.kiosko` devolvía idle. Una prueba temporal de
  estado active desbloqueó las muestras. Se retiró con reset-uid-state y se reinició el aparato;
  después dio TOP / active de forma natural y recibió muestras reales, sin anulaciones.
  Si reaparece, comprobar ambos estados y reiniciar antes de atribuirlo al código o forzar
  permanentemente el acceso. No dejar overrides de sensorservice como solución.
- Mientras Canica está abierta se usa FLAG_KEEP_SCREEN_ON en la ventana (además de la vista);
  se retira al salir o pausar. Dibujar e inicio conservan su descanso habitual.

- El estado obsoleto de UID reapareció tras reinstalar el APK y no se resolvió con am force-stop
  (la app está protegida por ser device owner). Reiniciar el HiBreak después de la actualización
  restaura TOP / active; considerar este detalle del firmware en futuras instalaciones.
