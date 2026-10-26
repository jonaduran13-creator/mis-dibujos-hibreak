package mx.dibujos.kiosko;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * La "película" de una hoja: cada trazo con su punta, grosor y los puntos con su tiempo,
 * para volver a dibujarlos después en el motor. Los trazos se agrupan por acción
 * (un trazo, un sello completo) para que Deshacer quite lo mismo que quita en la hoja.
 */
final class Recording {
    /** Límite de puntos guardados (~2 MB en el archivo); al pasarlo se empieza desde la imagen de ese momento. */
    static final int MAX_POINTS = 150_000;
    private static final int MAGIC = 0x50454c49; // "PELI"

    static final class Stroke {
        final int brush;
        final float width;
        final boolean eraser;
        /** x, y (coordenadas de la vista), presión y tiempo en ms desde el inicio del trazo, por punto. */
        float[] xy = new float[64];
        int[] pressure = new int[32];
        int[] time = new int[32];
        int count;
        /** Pausa antes de este trazo, en ms. */
        int gap;

        Stroke(int brush, float width, boolean eraser) {
            this.brush = brush;
            this.width = width;
            this.eraser = eraser;
        }

        void add(float x, float y, int p, int t) {
            if (count == time.length) {
                xy = java.util.Arrays.copyOf(xy, xy.length * 2);
                pressure = java.util.Arrays.copyOf(pressure, pressure.length * 2);
                time = java.util.Arrays.copyOf(time, time.length * 2);
            }
            xy[count * 2] = x;
            xy[count * 2 + 1] = y;
            pressure[count] = p;
            time[count] = t;
            count++;
        }

        int duration() { return count == 0 ? 0 : time[count - 1]; }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    /** Índice del primer trazo de cada acción. */
    private final List<Integer> groups = new ArrayList<>();
    private Stroke open;
    private long openedAt, lastEnd;
    private int points;
    /** Se pasó del límite: hay que empezar desde la imagen actual. */
    private boolean overflow;

    List<Stroke> strokes() { return strokes; }
    boolean isEmpty() { return strokes.isEmpty(); }
    int points() { return points; }

    void clear() {
        strokes.clear();
        groups.clear();
        open = null;
        points = 0;
        overflow = false;
        lastEnd = 0;
    }

    /** Empieza una acción (el niño apoya el dedo o pone un sello). Devuelve false si se llenó. */
    boolean beginGroup() {
        if (points >= MAX_POINTS) overflow = true;
        if (overflow) return false;
        groups.add(strokes.size());
        return true;
    }

    /** Se llenó en esta acción: quien llama debe guardar la imagen actual como inicio y limpiar. */
    boolean overflowed() { return overflow; }

    void begin(int brush, float width, boolean eraser, long now) {
        if (overflow) return;
        if (groups.isEmpty()) groups.add(strokes.size());
        open = new Stroke(brush, width, eraser);
        open.gap = lastEnd == 0 ? 0 : (int) Math.min(60_000, Math.max(0, now - lastEnd));
        openedAt = now;
        strokes.add(open);
    }

    void point(float x, float y, int pressure, long now) {
        if (open == null || overflow) return;
        open.add(x, y, pressure, (int) Math.max(0, now - openedAt));
        points++;
    }

    void end(long now) {
        if (open == null) return;
        if (open.count == 0) strokes.remove(strokes.size() - 1);
        open = null;
        lastEnd = now;
    }

    /** La figura perfecta reemplaza al último trazo a mano. */
    void dropLast() {
        if (strokes.isEmpty()) return;
        Stroke s = strokes.remove(strokes.size() - 1);
        points -= s.count;
        if (!groups.isEmpty() && groups.get(groups.size() - 1) > strokes.size()) groups.remove(groups.size() - 1);
    }

    /** Deshacer: quita la última acción completa. */
    void undoGroup() {
        if (groups.isEmpty()) return;
        int from = groups.remove(groups.size() - 1);
        while (strokes.size() > from) points -= strokes.remove(strokes.size() - 1).count;
        open = null;
    }

    boolean hasGroups() { return !groups.isEmpty(); }

    // ---------- Archivo ----------

    byte[] toBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeInt(1);
        out.writeInt(groups.size());
        for (int g : groups) out.writeInt(g);
        out.writeInt(strokes.size());
        for (Stroke s : strokes) {
            out.writeByte(s.brush);
            out.writeFloat(s.width);
            out.writeBoolean(s.eraser);
            out.writeInt(s.gap);
            out.writeInt(s.count);
            for (int i = 0; i < s.count; i++) {
                out.writeFloat(s.xy[i * 2]);
                out.writeFloat(s.xy[i * 2 + 1]);
                out.writeShort(s.pressure[i]);
                out.writeInt(s.time[i]);
            }
        }
        out.flush();
        return bytes.toByteArray();
    }

    static Recording fromBytes(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != MAGIC || in.readInt() != 1) throw new IOException("Película desconocida");
        Recording r = new Recording();
        int g = in.readInt();
        if (g < 0 || g > MAX_POINTS) throw new IOException("Película dañada");
        for (int i = 0; i < g; i++) r.groups.add(in.readInt());
        int n = in.readInt();
        if (n < 0 || n > MAX_POINTS) throw new IOException("Película dañada");
        for (int k = 0; k < n; k++) {
            Stroke s = new Stroke(in.readByte(), in.readFloat(), in.readBoolean());
            s.gap = in.readInt();
            int count = in.readInt();
            if (count < 0 || r.points + count > MAX_POINTS * 2) throw new IOException("Película dañada");
            for (int i = 0; i < count; i++) s.add(in.readFloat(), in.readFloat(), in.readShort() & 0xffff, in.readInt());
            r.points += count;
            r.strokes.add(s);
        }
        return r;
    }

    // ---------- Reproducción ----------

    /**
     * Tiempos de reproducción: los trazos a doble velocidad y las pausas entre ellos de
     * máximo 0.3 s; si aun así dura más de maxMs, se acelera todo para que quepa.
     * Devuelve, por trazo, el momento (ms) en que empieza y el factor de velocidad en [0].
     */
    static float[] schedule(List<Stroke> strokes, int maxMs) {
        float[] start = new float[strokes.size() + 1];
        float t = 0;
        for (int i = 0; i < strokes.size(); i++) {
            Stroke s = strokes.get(i);
            t += Math.min(300, s.gap / 2f);
            start[i + 1] = t;
            t += s.duration() / 2f;
        }
        float scale = t > maxMs ? maxMs / t : 1f;
        float[] out = new float[strokes.size() + 1];
        out[0] = 0.5f * scale; // ms de reproducción por ms grabado
        for (int i = 0; i < strokes.size(); i++) out[i + 1] = start[i + 1] * scale;
        return out;
    }
}
