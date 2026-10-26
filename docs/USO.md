# Mis Dibujos · guía de uso

La actualización conserva la firma y el modo kiosco. No requiere internet.

## Uso

- Inicio: tres ilustraciones grandes para Dibujar, Fotos y Piano.
- Dibujo: herramientas con etiquetas, tres grosores, goma, deshacer, hoja nueva,
  cámara, dibujos guardados, Mandala. Mandala repite cada trazo en doce
  posiciones simétricas. Se activa y desactiva con la flor; los dibujos se guardan
  en la misma carpeta de siempre. Sus guías punteadas no se guardan en la imagen.
- Punta (el botón después de la goma): cada toque cambia entre Lápiz, Grafito, Pincel y
  Puntos, con el grosor elegido. Se recuerda al apagar.
- Sellos: abre una tira con nueve dibujitos. Elige uno y toca la hoja para ponerlo; si
  arrastras el dedo antes de soltar, sale más grande (se ve su contorno punteado). Usa la
  punta elegida, funciona en Mandala (sale 12 veces) y Deshacer lo quita entero. Para volver
  a dibujar, toca un grosor.
- Figuras: el reconocimiento intenta corregir al levantar el dedo o tras una
  pausa de 350 ms. Reconoce línea, círculo, óvalo, triángulo, rectángulo, rombo,
  pentágono, hexágono, corazón, estrella (de contorno o de un trazo) y luna, aunque
  estén temblorosos, algo inclinados o se pasen del punto de inicio. Si un dibujo
  libre no se parece claramente a una figura, se deja como está.
  Deshacer permite quitar una corrección que no te guste. El historial compartido conserva hasta cuarenta estados al entrar o salir de Mandala.
- Piano: una octava, dos dedos, Piano, Campanas, Órgano, Flauta y Cuerdas.
  Cada sonido tiene su icono. Se silencia al salir, al dormir o al perder el foco de audio.
- Cámara: se adapta a vertical y horizontal. Cambiar de efecto conserva la misma
  orientación y proporciones. Tiene Normal, Ojotes, Globo, Espejo e Invertido. Abre con la cámara selfie.
  El cambio de cámara usa una cámara con flechas; no hay giro manual.
  Ojotes y Globo intentan localizar la cara; sin detección usan el centro del encuadre.
  Conviene centrar la cara y tener buena luz. Todo se procesa localmente.
- Invertido: colores en negativo (blanco↔negro).
- Foto para dibujar: la foto se revisa antes de usarla. Elige Contornos o Foto
  clara, Otra foto o Dibujar. Se mantiene completa, sin recortar las caras.
- Descanso con el botón físico de bloqueo: siete escenas cristianas alternan entre descansos. Se ajustan para
  cubrir el panel. El menú de papá/mamá permite elegir negro completo.

El saludo dice "¡Hola!" o "Hola, {nombre}": el nombre se pone en el menú de papá/mamá → Nombre en el saludo.

Para el menú de papá/mamá, mantén cinco segundos el saludo del inicio o el botón
de dibujos guardados. El PIN existente se conserva.

## Compilar y actualizar

En la carpeta original del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 -Install
```

Conserva la carpeta `keystore` original y las herramientas de `C:\tmp\hb`.
El ZIP de código entregado no incluye la llave privada. El script actualiza la
declaración del administrador existente.

## Comprobaciones

- APK compilado con JDK 17 / Android 30 y firmado con la llave existente.
- 22 comprobaciones de reconocimiento geométrico, transformaciones de filtros e historial de deshacer.
  Las pruebas de algoritmos usan reemplazos mínimos de gráficos de Android y
  no validan la detección facial real ni el aspecto físico de la tinta.
- El registro nativo de reposo de Bigme se comprobó en el HiBreak; una prueba
  confirmó el estado Asleep / pantalla OFF.
- Verificar en el aparato el negro físico, la respuesta de la tinta, la orientación
  con ambas cámaras y la detección de caras reales. Las capturas digitales no
  demuestran el contraste ni las sombras del panel físico.

## Fondos

Jesús y los niños · Arca de Noé · Buen pastor · Nacimiento · Jesús en el jardín ·
Paloma y arcoíris · Jesús calma el mar.

El botón Asistido / Libre permite desactivar por completo la corrección de figuras. La preferencia se conserva al reiniciar y cambiar Mandala. En Libre no hay corrección ni al pausar ni al levantar el dedo.

## Dibujos editables (1.7)

Al reabrir un dibujo se puede borrar la tinta que ya estaba guardada, dibujar encima y deshacer las nuevas ediciones. Los PNG anteriores se importan sobre papel blanco. No se puede reconstruir el historial de trazos de sesiones que no lo guardaban.

Las nuevas ediciones conservan un proyecto `.dibujo` dentro de los archivos privados de la app, con el contenido y el papel/foto separados. Se sigue guardando el PNG en Pictures/Dibujos para ver y compartir. Si la hoja se creó con una foto desde esta versión, el borrador recupera esa foto debajo de los trazos. La información original de una foto antigua ya aplanada en un PNG no se puede recuperar automáticamente.

El dibujo nuevo usa el motor rápido cuando es posible. Al reabrir, borrar o restaurar una imagen se usa el lienzo editable para que la tinta guardada no se convierta en un fondo imborrable. Mandala conserva el papel en su posición original al borrar.

Se verificaron 19 casos reales de Android sobre dibujos sintéticos en un paquete aislado: borrado de PNG antiguo, trazos vecinos, deshacer, edición nueva, foto protegida, borrado simétrico, carga antes del layout, cambio de tamaño, nueva hoja, finalización al suspender y guardado/reapertura con recuperación ante fallos. Los dibujos del niño no se usaron como pruebas.
