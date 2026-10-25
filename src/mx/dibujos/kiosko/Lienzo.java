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
    }

    View view();
    void setListener(Listener l);
    default void setShapeAssist(boolean enabled) {}
    void setPen(float width);
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
