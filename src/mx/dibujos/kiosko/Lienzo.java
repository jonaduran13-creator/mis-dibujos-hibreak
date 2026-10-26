package mx.dibujos.kiosko;

import android.graphics.Bitmap;
import android.view.View;

/** Lo que la app necesita de un lienzo, sea el motor rápido de Bigme o el normal. */
public interface Lienzo {
    interface Listener {
        default void onStrokeStarted() {}
        void onStrokeFinished();
        /** El lienzo se repintó completo (deshacer, figura perfecta…): la tinta puede borrar los botones. */
        default void onRedrawn() {}
        /** Cada punto de tinta que se pinta (INK_DOWN/MOVE/UP, coordenadas de la vista): película y música. */
        default void onInk(int action, float x, float y, int pressure) {}
        /** La figura perfecta reemplazó al último trazo (sus puntos llegan después por onInk). */
        default void onInkReplaced() {}
        /** El trazo se detuvo porque iba a cruzar una pared (laberinto). */
        default void onBlocked() {}
    }

    int INK_DOWN = 0, INK_MOVE = 1, INK_UP = 2;
    /** Presión cuando no se sabe el tamaño del dedo (el motor usa 1000..4096). */
    int PRESSURE_MID = 2548;

    /** Decide si el trazo puede pasar de un punto a otro (paredes del laberinto). */
    interface Gate { boolean allow(float x0, float y0, float x1, float y1); }
    default void setGate(Gate gate) {}
    /** Pinta un punto como si fuera el dedo, sin avisar al Listener ni corregir figuras (película). */
    void inject(int action, float x, float y, int pressure);
    /** Limpieza suave: tras una pausa se repinta en GU16 solo la zona dibujada (sin parpadeo global). */
    default void setSoftClean(boolean on) {}

    View view();
    void setListener(Listener l);
    default void setShapeAssist(boolean enabled) {}
    /** Puntas del lápiz: normal, grafito con textura, pincel que se adelgaza y punteado. */
    int BRUSH_PEN = 0, BRUSH_PENCIL = 1, BRUSH_BRUSH = 2, BRUSH_DASH = 3, BRUSHES = 4;
    /** Cambia la punta; se aplica en el siguiente setPen. */
    default void setBrush(int brush) {}
    void setPen(float width);
    /** Dibuja un sello (varios trazos, en coordenadas de la vista) con la punta actual, como un trazo más. */
    void stamp(java.util.List<java.util.List<android.graphics.PointF>> strokes);
    void setEraser(float width);
    void undo();
    void clear();
    /** Abre un dibujo guardado para seguir pintando encima. */
    void load(Bitmap b);
    /** Contenido editable y papel/foto independiente; el borrador solo restaura ese papel. */
    default void loadEditable(Bitmap content, Bitmap paper, boolean modified) {
        if(modified)loadPhoto(content);else load(content);
    }
    /** Una foto nueva de fondo; cuenta como cambio sin guardar. */
    void loadPhoto(Bitmap b);
    Bitmap snapshot();
    boolean isDirty();
    /** Hay un dedo dibujando ahora mismo. */
    boolean isDrawing();
    void markSaved();
    default void markDirty() {}
    /** Se apaga mientras hay algo encima (galería, cámara, menús). */
    void setActive(boolean active);
    void onResume();
}
