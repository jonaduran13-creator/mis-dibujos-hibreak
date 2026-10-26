package mx.dibujos.kiosko;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * "Presión" con el dedo: el panel del HiBreak reporta qué tanto dedo toca (ABS_MT_TOUCH_MAJOR,
 * 0–255). Se aprende el rango real de este niño con el promedio de cada trazo (un toque raro
 * no lo desajusta) y, cuando hay diferencia suficiente entre trazos suaves y apoyados, se
 * convierte en presión para el motor de Bigme (1000–4096). Mientras no, todo queda igual.
 */
final class FingerSize {
    private static final float MIN_SPREAD = 6f; // unidades del panel entre el trazo más fino y el más apoyado
    private static final int MIN_STROKES = 8;
    private final SharedPreferences prefs;
    private final Runnable onActivated;
    private float low, high;
    private int strokes;
    private float smooth, sum;
    private int samples;

    FingerSize(SharedPreferences prefs, Runnable onActivated) {
        this.prefs = prefs;
        this.onActivated = onActivated;
        low = prefs.getFloat("touchLow", Float.MAX_VALUE);
        high = prefs.getFloat("touchHigh", 0);
        strokes = prefs.getInt("touchStrokes", 0);
    }

    boolean active() { return strokes >= MIN_STROKES && high - low >= MIN_SPREAD; }

    /** Primer punto del trazo. */
    int begin(float major) {
        smooth = major;
        sum = 0;
        samples = 0;
        return pressure(major);
    }

    int pressure(float major) {
        if (major > 0) {
            smooth = smooth * .7f + major * .3f;
            sum += major;
            samples++;
        }
        if (!active()) return Lienzo.PRESSURE_MID;
        float n = (smooth - low) / (high - low);
        return 1000 + Math.round(3096 * Math.max(0, Math.min(1, n)));
    }

    /** Fin del trazo: el promedio ajusta el rango aprendido. */
    void endStroke() {
        int n = samples;
        samples = 0; // trazos sin dedo (sellos, pruebas) no repiten el último
        if (n < 3) return;
        float mean = sum / n;
        boolean was = active();
        low = Math.min(low, mean);
        high = Math.max(high, mean);
        strokes++;
        prefs.edit().putFloat("touchLow", low).putFloat("touchHigh", high).putInt("touchStrokes", strokes).apply();
        Log.i("MisDibujosDedo", "trazo: tamaño " + Math.round(mean) + " (rango " + Math.round(low) + "–" + Math.round(high)
                + ", " + strokes + " trazos)" + (active() ? " presión activa" : ""));
        if (!was && active()) onActivated.run();
    }
}
