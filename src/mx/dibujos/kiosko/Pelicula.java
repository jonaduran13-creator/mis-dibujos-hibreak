package mx.dibujos.kiosko;

import android.os.Handler;
import android.os.SystemClock;

import java.util.List;

/**
 * Vuelve a dibujar una hoja trazo por trazo en el lienzo, con tinta real del motor:
 * los trazos a doble velocidad, las pausas acortadas y todo dentro de MAX_MS.
 */
final class Pelicula {
    static final int MAX_MS = 25_000;
    private static final long TICK = 16;

    interface Callback {
        /** Antes de cada trazo: poner su punta y grosor (o goma). */
        void pen(Recording.Stroke s);
        /** Cada punto pintado, para la música. */
        void point(Recording.Stroke s, float x, float y, boolean first);
        void done(boolean completed);
    }

    private final Lienzo draw;
    private final Handler ui;
    private final List<Recording.Stroke> strokes;
    private final Callback cb;
    private final float[] plan;
    private long t0;
    private int stroke, point;
    private boolean running;

    Pelicula(Lienzo draw, Handler ui, List<Recording.Stroke> strokes, Callback cb) {
        this.draw = draw;
        this.ui = ui;
        this.strokes = strokes;
        this.cb = cb;
        plan = Recording.schedule(strokes, MAX_MS);
    }

    void start() {
        running = true;
        t0 = SystemClock.uptimeMillis();
        ui.post(tick);
    }

    /** Detener a medias (el niño tocó la pantalla). */
    void stop() {
        if (!running) return;
        running = false;
        ui.removeCallbacks(tick);
        if (point > 0 && stroke < strokes.size()) {
            Recording.Stroke s = strokes.get(stroke);
            draw.inject(Lienzo.INK_UP, s.xy[(point - 1) * 2], s.xy[(point - 1) * 2 + 1], s.pressure[point - 1]);
        }
        cb.done(false);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            float now = SystemClock.uptimeMillis() - t0, speed = plan[0];
            while (stroke < strokes.size() && now >= plan[stroke + 1]) {
                Recording.Stroke s = strokes.get(stroke);
                if (s.count == 0) { stroke++; continue; }
                if (point == 0) cb.pen(s);
                while (point < s.count && plan[stroke + 1] + s.time[point] * speed <= now) {
                    float x = s.xy[point * 2], y = s.xy[point * 2 + 1];
                    draw.inject(point == 0 ? Lienzo.INK_DOWN : Lienzo.INK_MOVE, x, y, s.pressure[point]);
                    cb.point(s, x, y, point == 0);
                    point++;
                }
                if (point < s.count) break; // este trazo sigue en el siguiente cuadro
                draw.inject(Lienzo.INK_UP, s.xy[(s.count - 1) * 2], s.xy[(s.count - 1) * 2 + 1], s.pressure[s.count - 1]);
                stroke++;
                point = 0;
            }
            if (stroke >= strokes.size()) {
                running = false;
                cb.done(true);
                return;
            }
            ui.postDelayed(this, TICK);
        }
    };
}
