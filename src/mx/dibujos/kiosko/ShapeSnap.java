package mx.dibujos.kiosko;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconoce figuras dibujadas a mano y devuelve su versión perfecta: línea recta,
 * círculo, óvalo, triángulo, rectángulo, rombo, polígonos regulares de 5 a 8 lados,
 * corazón, estrella (de contorno o de un solo trazo) y luna. Devuelve null si el
 * trazo no se parece lo suficiente a ninguna, para dejarlo tal cual.
 *
 * Cómo decide: genera cada figura candidata ajustada al trazo y mide la distancia
 * entre ambos en los dos sentidos (del trazo a la figura y de la figura al
 * trazo; así una figura con partes de más o de menos sale mal calificada), mitad
 * promedio y mitad percentil 90 (las peores partes: esquinas, puntas, pétalos). Gana la
 * de menor distancia, con una pequeña penalización a las figuras más complejas para
 * que, en caso de duda, se prefiera la más simple. Como en modo Asistido esto se
 * intenta en cada trazo, el umbral es estricto: un dibujo libre se queda libre. Con
 * ajuste regular solo se acepta si la figura gana por mucho a todas las demás.
 * Calibrado con cientos de trazos sintéticos temblorosos (ver tests/test_algorithms.py).
 */
public final class ShapeSnap {
    private static final float MIN_SIZE = 32f;      // trazos más chicos se dejan a mano
    private static final float LINE_TOLERANCE = 0.12f;
    private static final float CLOSED_GAP = 0.38f;  // hueco entre inicio y fin, relativo al tamaño
    private static final float STEP = 4f;           // separación de los puntos generados
    /** Calificación aceptada (mitad distancia media, mitad percentil 90), relativa a la diagonal del trazo. */
    private static final float ACCEPT = 0.036f;
    /** Hasta aquí se acepta solo si la figura gana claramente (menos de CLEAR_MARGIN × la segunda). */
    private static final float ACCEPT_LOOSE = 0.08f, CLEAR_MARGIN = 0.6f;
    private static final int SAMPLES = 96;          // puntos con que se comparan trazo y figura

    /** Resultado: el nombre de la figura (para pruebas y registros) y sus puntos perfectos. */
    public static final class Result {
        public final String kind;
        public final List<PointF> points;
        final float score;
        Result(String kind, List<PointF> points, float score) {
            this.kind = kind;
            this.points = points;
            this.score = score;
        }
    }

    private ShapeSnap() {}

    private static volatile boolean warmed;

    /**
     * Prepara las plantillas y "calienta" el código en segundo plano al abrir la app: la
     * primera clasificación en el HiBreak tardaba ~120 ms (las siguientes ~18 ms).
     */
    public static void warmUp() {
        if (warmed) return;
        warmed = true;
        Thread t = new Thread(() -> {
            List<PointF> ring = new ArrayList<>();
            for (int i = 0; i <= 120; i++) {
                double a = 2 * Math.PI * i / 120;
                ring.add(new PointF((float) (300 + 150 * Math.cos(a) + 6 * Math.sin(a * 5)), (float) (300 + 140 * Math.sin(a))));
            }
            for (int i = 0; i < 3; i++) classify(ring);
        }, "figuras");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public static List<PointF> recognize(List<PointF> raw) {
        Result r = classify(raw);
        return r == null ? null : r.points;
    }

    public static Result classify(List<PointF> raw) {
        if (raw.size() < 5) return null;
        List<PointF> pts = resample(raw, STEP);
        if (pts.size() < 5) return null;
        float size = size(pts);
        if (size < MIN_SIZE) return null;

        pts = trimOvershoot(pts, size);
        PointF a = pts.get(0), b = pts.get(pts.size() - 1);
        boolean closed = dist(a, b) < CLOSED_GAP * size;

        if (!closed) {
            float len = dist(a, b);
            if (len < MIN_SIZE) return null;
            float worst = 0;
            for (PointF p : pts) worst = Math.max(worst, distToSegment(p, a, b));
            float traveled = length(pts);
            return worst < LINE_TOLERANCE * len && traveled < len * 1.65f ? new Result("línea", line(a, b), 0) : null;
        }

        float length = length(pts);
        // Un garabato con vueltas superpuestas no debe convertirse en nada.
        if (length > size * 5.3f || length < size * 1.8f) return null;

        // El trazo se muestrea una sola vez; cada figura se compara contra estos puntos.
        List<PointF> sampled = evenly(pts, SAMPLES);
        float diag = diagonal(pts);
        List<Result> candidates = new ArrayList<>();
        float[] e = ellipseParams(pts);
        boolean round = Math.min(e[2], e[3]) / Math.max(e[2], e[3]) > 0.76f;
        candidates.add(scored(round ? "círculo" : "óvalo", ellipse(pts), sampled, diag, 0f));
        List<PointF> corners = corners(pts, size);
        if (corners != null) {
            int n = corners.size();
            String name = n == 3 ? "triángulo" : n == 4 ? "rectángulo" : n == 5 ? "pentágono"
                    : n == 6 ? "hexágono" : n == 7 ? "heptágono" : "octágono";
            // Un óvalo también se puede cortar en 5–8 esquinas: esos polígonos pagan más.
            candidates.add(scored(name, polygon(corners), sampled, diag, n <= 4 ? 0f : 0.002f * (n - 4)));
        }
        // Si un óvalo o un polígono ya ajusta excelente, no hace falta probar las plantillas
        // (en el HiBreak cada comparación cuesta: esto corre al levantar el dedo).
        float basic = Float.MAX_VALUE;
        for (Result r : candidates) basic = Math.min(basic, r.score);
        if (basic > 0.02f) {
            for (Template t : TEMPLATES) {
                float bestScore = Float.MAX_VALUE, bestAngle = 0;
                for (int deg = -15; deg <= 15; deg += 15) {
                    float angle = (float) Math.toRadians(deg);
                    float s = chamferSampled(sampled, placeTemplate(t.sampled, pts, angle)) / diag + t.penalty;
                    if (s < bestScore) { bestScore = s; bestAngle = angle; }
                }
                // La versión fina (para dibujar) solo se genera para el mejor giro.
                List<PointF> dense = placeTemplate(t.points, pts, bestAngle);
                candidates.add(new Result(t.name, evenly(dense, Math.max(48, (int) (length(dense) / STEP))), bestScore));
            }
        }
        Result best = null;
        for (Result r : candidates) if (best == null || r.score < best.score) best = r;
        // Hexágono contra círculo: se parecen mucho. Si el polígono ajusta casi igual de bien,
        // gana (en un círculo de verdad el polígono ajusta mucho peor, p. ej. el doble).
        // Al revés: casi nadie dibuja heptágonos u octágonos, y un círculo tembloroso se les
        // parece. Solo ganan al círculo si ajustan claramente mejor.
        if ((best.kind.equals("heptágono") || best.kind.equals("octágono")) && candidates.get(0).score * 0.8f <= best.score)
            best = candidates.get(0);
        // Solo pentágono y hexágono: un círculo tembloroso se parece a un heptágono u octágono.
        if (best == candidates.get(0) && candidates.size() > 1 && corners != null && (corners.size() == 5 || corners.size() == 6)
                && candidates.get(1).score <= best.score * 1.3f) best = candidates.get(1);
        if (best == null) return null;
        // Segunda mejor de otra figura: si el trazo es tembloroso, todas califican peor, pero
        // una figura real sigue ganando por mucho; un dibujo libre queda parecido a varias.
        float second = Float.MAX_VALUE;
        for (Result r : candidates) if (!family(r.kind).equals(family(best.kind))) second = Math.min(second, r.score);
        // Círculo y óvalo no tienen "rival" cercano (una mancha o una nube redonda tampoco):
        // para ellos solo vale el ajuste muy bueno.
        boolean roundish = best.kind.equals("círculo") || best.kind.equals("óvalo");
        boolean clear = !roundish && best.score < ACCEPT_LOOSE && best.score < CLEAR_MARGIN * second;
        return best.score < ACCEPT || clear ? best : null;
    }

    /** Las dos estrellas son la misma familia: no deben competir entre sí por el margen. */
    private static String family(String kind) {
        return kind.startsWith("estrella") ? "estrella" : kind;
    }

    private static Result scored(String kind, List<PointF> shape, List<PointF> sampled, float diag, float penalty) {
        return new Result(kind, shape, chamferSampled(sampled, evenly(shape, SAMPLES)) / diag + penalty);
    }

    /**
     * Distancia simétrica entre dos trazos ya muestreados (de cada punto de uno al más
     * cercano del otro, en ambos sentidos), sin importar dónde empiezan ni en qué
     * dirección van.
     */
    private static float chamferSampled(List<PointF> pa, List<PointF> pb) {
        return (nearest(pa, pb) + nearest(pb, pa)) / 2;
    }

    /**
     * Mitad promedio y mitad percentil 90 de las distancias al punto más cercano: el
     * promedio solo "perdona" demasiado (una casita parece círculo); el percentil 90
     * mide las peores partes, donde se notan esquinas, puntas y pétalos de más o de menos.
     */
    private static float nearest(List<PointF> from, List<PointF> to) {
        float[] d = new float[from.size()];
        float sum = 0;
        for (int i = 0; i < d.length; i++) {
            PointF p = from.get(i);
            float best = Float.MAX_VALUE;
            for (PointF q : to) {
                float dx = p.x - q.x, dy = p.y - q.y, dd = dx * dx + dy * dy;
                if (dd < best) best = dd;
            }
            d[i] = (float) Math.sqrt(best);
            sum += d[i];
        }
        java.util.Arrays.sort(d);
        return 0.5f * (sum / d.length) + 0.5f * d[(int) (d.length * 0.9f)];
    }

    /**
     * Si el niño cierra la figura y se pasa del punto de inicio, se recorta lo que sobra
     * (el cierre se busca en el último 40% del trazo y lo recortado no debe pasar del 25%).
     */
    private static List<PointF> trimOvershoot(List<PointF> pts, float size) {
        int n = pts.size();
        PointF start = pts.get(0);
        int bestIdx = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = (int) (n * 0.6f); i < n; i++) {
            float d = dist(start, pts.get(i));
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        if (bestIdx > 0 && bestIdx < n - 1 && bestDist < 0.12f * size && n - 1 - bestIdx < n * 0.25f
                && dist(start, pts.get(n - 1)) > bestDist * 1.5f) {
            return new ArrayList<>(pts.subList(0, bestIdx + 1));
        }
        return pts;
    }

    // ---------- Figuras con plantilla: corazón, estrellas, rombo, luna ----------

    private static final class Template {
        final String name;
        final List<PointF> points;  // contorno cerrado dentro del cuadrado [0,1]×[0,1]
        final List<PointF> sampled; // el mismo contorno con SAMPLES puntos parejos, para comparar
        final float penalty;
        Template(String name, List<PointF> points, float penalty) {
            this.name = name;
            this.points = points;
            this.sampled = evenly(points, SAMPLES);
            this.penalty = penalty;
        }
    }

    private static final List<Template> TEMPLATES = new ArrayList<>();
    static {
        List<PointF> heart = new ArrayList<>();
        for (int i = 0; i <= 240; i++) {
            double t = 2 * Math.PI * i / 240;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
            heart.add(new PointF((float) x, (float) y));
        }
        TEMPLATES.add(new Template("corazón", unit(heart), 0.006f));

        List<PointF> star = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(-90 + 36 * i), r = i % 2 == 0 ? 1 : 0.42;
            star.add(new PointF((float) (r * Math.cos(a)), (float) (r * Math.sin(a))));
        }
        TEMPLATES.add(new Template("estrella", unit(densify(star)), 0.008f));

        // La estrella "de un trazo" que dibujan los niños: cinco líneas que se cruzan.
        List<PointF> pentagram = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            double a = Math.toRadians(-90 + 144 * i);
            pentagram.add(new PointF((float) Math.cos(a), (float) Math.sin(a)));
        }
        TEMPLATES.add(new Template("estrella de un trazo", unit(densify(pentagram)), 0.008f));

        List<PointF> diamond = new ArrayList<>();
        diamond.add(new PointF(0.5f, 0));
        diamond.add(new PointF(1, 0.5f));
        diamond.add(new PointF(0.5f, 1));
        diamond.add(new PointF(0, 0.5f));
        diamond.add(new PointF(0.5f, 0));
        TEMPLATES.add(new Template("rombo", unit(densify(diamond)), 0.004f));

        // Luna creciente: arco exterior por la izquierda y arco interior de regreso.
        List<PointF> moon = new ArrayList<>();
        for (int i = 0; i <= 120; i++) {
            double t = Math.toRadians(60 + 240.0 * i / 120);
            moon.add(new PointF((float) Math.cos(t), (float) Math.sin(t)));
        }
        double cx = 0.55, r2 = Math.hypot(0.5 - cx, Math.sin(Math.toRadians(60)));
        double from = Math.atan2(-Math.sin(Math.toRadians(60)), 0.5 - cx), to = Math.atan2(Math.sin(Math.toRadians(60)), 0.5 - cx);
        if (from < 0) from += 2 * Math.PI;
        for (int i = 0; i <= 100; i++) {
            double t = from - (from - to) * i / 100;
            moon.add(new PointF((float) (cx + r2 * Math.cos(t)), (float) (r2 * Math.sin(t))));
        }
        List<PointF> moonUnit = unit(moon);
        TEMPLATES.add(new Template("luna", moonUnit, 0.008f));
        List<PointF> mirrored = new ArrayList<>();
        for (PointF p : moonUnit) mirrored.add(new PointF(1 - p.x, p.y));
        TEMPLATES.add(new Template("luna", mirrored, 0.008f));
    }

    /**
     * Coloca la plantilla sobre el trazo: se gira el trazo `angle` para enderezarlo, la
     * plantilla ocupa su rectángulo y luego se gira de vuelta (acepta figuras inclinadas).
     */
    private static List<PointF> placeTemplate(List<PointF> template, List<PointF> stroke, float angle) {
        float cx = 0, cy = 0;
        for (PointF p : stroke) { cx += p.x; cy += p.y; }
        cx /= stroke.size(); cy /= stroke.size();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF p : stroke) {
            float x = p.x - cx, y = p.y - cy;
            float u = x * cos + y * sin, v = -x * sin + y * cos;
            minX = Math.min(minX, u); maxX = Math.max(maxX, u);
            minY = Math.min(minY, v); maxY = Math.max(maxY, v);
        }
        List<PointF> out = new ArrayList<>();
        for (PointF t : template) {
            float u = minX + t.x * (maxX - minX), v = minY + t.y * (maxY - minY);
            out.add(new PointF(cx + u * cos - v * sin, cy + u * sin + v * cos));
        }
        return out;
    }

    /** Lleva un contorno al cuadrado [0,1]×[0,1]. */
    private static List<PointF> unit(List<PointF> pts) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF p : pts) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        List<PointF> out = new ArrayList<>();
        for (PointF p : pts) out.add(new PointF((p.x - minX) / (maxX - minX), (p.y - minY) / (maxY - minY)));
        return out;
    }

    private static List<PointF> densify(List<PointF> vertices) {
        List<PointF> out = new ArrayList<>();
        for (int i = 0; i + 1 < vertices.size(); i++) {
            PointF a = vertices.get(i), b = vertices.get(i + 1);
            for (int k = 0; k < 40; k++) out.add(new PointF(a.x + (b.x - a.x) * k / 40, a.y + (b.y - a.y) * k / 40));
        }
        PointF last = vertices.get(vertices.size() - 1);
        out.add(new PointF(last.x, last.y));
        return out;
    }

    // ---------- Líneas ----------

    private static List<PointF> line(PointF a, PointF b) {
        // Casi horizontal, vertical o en diagonal de 45°: se endereza del todo.
        float dx = b.x - a.x, dy = b.y - a.y;
        double ang = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
        PointF end = new PointF(b.x, b.y);
        if (ang < 8) end.y = a.y;
        else if (ang > 82) end.x = a.x;
        else if (Math.abs(ang - 45) < 6) {
            float d = (Math.abs(dx) + Math.abs(dy)) / 2;
            end.x = a.x + Math.signum(dx) * d;
            end.y = a.y + Math.signum(dy) * d;
        }
        List<PointF> out = new ArrayList<>();
        addSegment(out, a, end);
        out.add(end);
        return out;
    }

    // ---------- Elipses y círculos ----------

    private static float[] moments(List<PointF> pts) {
        float cx = 0, cy = 0;
        for (PointF p : pts) { cx += p.x; cy += p.y; }
        cx /= pts.size(); cy /= pts.size();
        float sxx = 0, syy = 0, sxy = 0;
        for (PointF p : pts) {
            float x = p.x - cx, y = p.y - cy;
            sxx += x * x; syy += y * y; sxy += x * y;
        }
        sxx /= pts.size(); syy /= pts.size(); sxy /= pts.size();
        float angle = (float) (0.5 * Math.atan2(2 * sxy, sxx - syy));
        return new float[]{cx, cy, angle};
    }

    /**
     * Centro, ejes y giro del óvalo que mejor envuelve al trazo: el giro sale de la
     * dispersión de los puntos y los ejes de qué tan lejos llega el trazo a lo largo de
     * ellos (un promedio aproximado dejaba óvalos mal ajustados, casi como una nube).
     */
    private static float[] ellipseParams(List<PointF> pts) {
        float[] m = moments(pts);
        float cos = (float) Math.cos(m[2]), sin = (float) Math.sin(m[2]);
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (PointF p : pts) {
            float x = p.x - m[0], y = p.y - m[1];
            float u = x * cos + y * sin, v = -x * sin + y * cos;
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        float cu = (minU + maxU) / 2, cv = (minV + maxV) / 2;
        float cx = m[0] + cu * cos - cv * sin, cy = m[1] + cu * sin + cv * cos;
        return new float[]{cx, cy, (maxU - minU) / 2, (maxV - minV) / 2, m[2]};
    }

    private static List<PointF> ellipse(List<PointF> pts) {
        float[] e = ellipseParams(pts);
        float ra = e[2], rb = e[3], angle = e[4];
        if (Math.min(ra, rb) / Math.max(ra, rb) > 0.76f) {
            ra = rb = (ra + rb) / 2; // casi redondo: círculo perfecto
            angle = 0;
        } else {
            // Óvalo casi acostado o parado: se endereza.
            double deg = Math.toDegrees(angle) % 90;
            if (Math.abs(deg) < 10) angle = (float) Math.toRadians(Math.round(Math.toDegrees(angle) / 90.0) * 90);
        }
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        int n = Math.max(48, (int) (2 * Math.PI * Math.max(ra, rb) / STEP));
        List<PointF> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double t = 2 * Math.PI * i / n;
            float x = (float) (ra * Math.cos(t)), y = (float) (rb * Math.sin(t));
            out.add(new PointF(e[0] + x * cos - y * sin, e[1] + x * sin + y * cos));
        }
        return out;
    }

    // ---------- Polígonos ----------

    /** Esquinas del trazo cerrado (Ramer–Douglas–Peucker), o null si no hay entre 3 y 8. */
    private static List<PointF> corners(List<PointF> pts, float size) {
        // Se empieza a cortar en el punto más lejano del inicio, para no partir una esquina.
        PointF start = pts.get(0);
        int far = 0;
        float best = 0;
        for (int i = 0; i < pts.size(); i++) {
            float d = dist(start, pts.get(i));
            if (d > best) { best = d; far = i; }
        }
        List<PointF> loop = new ArrayList<>(pts.subList(0, far + 1));
        List<PointF> back = new ArrayList<>(pts.subList(far, pts.size()));
        back.add(pts.get(0));
        float eps = 0.07f * size;
        List<PointF> c = new ArrayList<>();
        rdp(loop, 0, loop.size() - 1, eps, c);
        c.add(loop.get(loop.size() - 1));
        List<PointF> c2 = new ArrayList<>();
        rdp(back, 0, back.size() - 1, eps, c2);
        // rdp solo guarda el inicio de cada tramo: c2 empieza en el punto lejano, que ya está en c.
        for (int i = 1; i < c2.size(); i++) c.add(c2.get(i));
        List<PointF> merged = new ArrayList<>();
        for (PointF p : c) {
            if (merged.isEmpty() || dist(merged.get(merged.size() - 1), p) > 0.12f * size) merged.add(p);
        }
        if (merged.size() > 2 && dist(merged.get(0), merged.get(merged.size() - 1)) < 0.12f * size) {
            merged.remove(merged.size() - 1);
        }
        // Quita "esquinas" casi rectas (ángulo de casi 180°).
        boolean changed = true;
        while (changed && merged.size() > 3) {
            changed = false;
            for (int i = 0; i < merged.size(); i++) {
                PointF p = merged.get((i + merged.size() - 1) % merged.size());
                PointF q = merged.get(i);
                PointF r = merged.get((i + 1) % merged.size());
                if (angleAt(p, q, r) > 155) {
                    merged.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return merged.size() >= 3 && merged.size() <= 8 ? merged : null;
    }

    private static void rdp(List<PointF> pts, int s, int e, float eps, List<PointF> out) {
        float worst = 0;
        int idx = -1;
        for (int i = s + 1; i < e; i++) {
            float d = distToSegment(pts.get(i), pts.get(s), pts.get(e));
            if (d > worst) { worst = d; idx = i; }
        }
        if (idx >= 0 && worst > eps) {
            rdp(pts, s, idx, eps, out);
            rdp(pts, idx, e, eps, out);
        } else {
            out.add(pts.get(s));
        }
    }

    private static List<PointF> polygon(List<PointF> corners) {
        List<PointF> v;
        if (corners.size() == 4) v = rectangle(corners);
        else if (corners.size() == 3) v = straighten(corners);
        else v = regular(corners);
        List<PointF> out = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) addSegment(out, v.get(i), v.get((i + 1) % v.size()));
        out.add(new PointF(v.get(0).x, v.get(0).y));
        return out;
    }

    /** Cuatro esquinas: rectángulo de lados paralelos (girado si el niño lo dibujó girado). */
    private static List<PointF> rectangle(List<PointF> c) {
        float cx = 0, cy = 0;
        for (PointF p : c) { cx += p.x; cy += p.y; }
        cx /= 4; cy /= 4;
        // Giro promedio de los lados, llevado a 0–90°.
        double sum = 0;
        for (int i = 0; i < 4; i++) {
            PointF a = c.get(i), b = c.get((i + 1) % 4);
            double ang = Math.atan2(b.y - a.y, b.x - a.x);
            ang = ((ang % (Math.PI / 2)) + Math.PI / 2) % (Math.PI / 2);
            if (ang > Math.PI / 4) ang -= Math.PI / 2;
            sum += ang;
        }
        double rot = sum / 4;
        if (Math.abs(Math.toDegrees(rot)) < 10) rot = 0; // casi derecho: derecho
        float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
        float hu = 0, hv = 0;
        for (PointF p : c) {
            float x = p.x - cx, y = p.y - cy;
            hu += Math.abs(x * cos + y * sin);
            hv += Math.abs(-x * sin + y * cos);
        }
        hu /= 4; hv /= 4;
        if (Math.abs(hu - hv) < 0.12f * Math.max(hu, hv)) hu = hv = (hu + hv) / 2; // casi cuadrado: cuadrado
        float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        List<PointF> out = new ArrayList<>();
        for (float[] s : k) {
            float u = s[0] * hu, w = s[1] * hv;
            out.add(new PointF(cx + u * cos - w * sin, cy + u * sin + w * cos));
        }
        return out;
    }

    /**
     * Triángulo: se respetan las esquinas del niño, con lados rectos. Casi equilátero →
     * equilátero; con una esquina casi recta → ángulo recto exacto; con dos lados casi
     * iguales → isósceles.
     */
    private static List<PointF> straighten(List<PointF> c) {
        float a = dist(c.get(0), c.get(1)), b = dist(c.get(1), c.get(2)), d = dist(c.get(2), c.get(0));
        float mx = Math.max(a, Math.max(b, d)), mn = Math.min(a, Math.min(b, d));
        if (mn / mx > 0.85f) return regular(c);
        List<PointF> out = new ArrayList<>();
        for (PointF p : c) out.add(new PointF(p.x, p.y));
        for (int i = 0; i < 3; i++) {
            PointF prev = out.get((i + 2) % 3), q = out.get(i), next = out.get((i + 1) % 3);
            if (Math.abs(angleAt(prev, q, next) - 90) < 10) {
                // Ángulo recto en q: se gira el lado q→next para que quede perpendicular a q→prev.
                float ux = prev.x - q.x, uy = prev.y - q.y, ul = (float) Math.hypot(ux, uy);
                float len = dist(q, next);
                float px = -uy / ul, py = ux / ul;
                if ((next.x - q.x) * px + (next.y - q.y) * py < 0) { px = -px; py = -py; }
                next.x = q.x + px * len; next.y = q.y + py * len;
                return out;
            }
        }
        for (int i = 0; i < 3; i++) {
            PointF apex = out.get(i), p = out.get((i + 1) % 3), r = out.get((i + 2) % 3);
            float s1 = dist(apex, p), s2 = dist(apex, r);
            if (Math.abs(s1 - s2) < 0.1f * Math.max(s1, s2)) {
                // Isósceles: la punta va sobre la mediatriz de la base, a la misma altura.
                float mx2 = (p.x + r.x) / 2, my2 = (p.y + r.y) / 2;
                float bx = r.x - p.x, by = r.y - p.y, bl = (float) Math.hypot(bx, by);
                float nx = -by / bl, ny = bx / bl;
                float h = (apex.x - mx2) * nx + (apex.y - my2) * ny;
                apex.x = mx2 + nx * h; apex.y = my2 + ny * h;
                return out;
            }
        }
        return out;
    }

    /** Polígono regular (pentágono, hexágono…) centrado donde lo dibujó el niño. */
    private static List<PointF> regular(List<PointF> c) {
        int n = c.size();
        float cx = 0, cy = 0;
        for (PointF p : c) { cx += p.x; cy += p.y; }
        cx /= n; cy /= n;
        float r = 0;
        for (PointF p : c) r += dist(p, new PointF(cx, cy));
        r /= n;
        // Giro de la figura: promedio circular de dónde caen las esquinas (módulo un lado).
        double step = 2 * Math.PI / n, sx = 0, sy = 0;
        for (PointF p : c) {
            double phase = Math.atan2(p.y - cy, p.x - cx) * n; // n·ángulo: esquinas equivalentes coinciden
            sx += Math.cos(phase);
            sy += Math.sin(phase);
        }
        double rot = Math.atan2(sy, sx) / n;
        // Si está casi derecha (un lado plano abajo, como en los libros), se endereza del todo;
        // si el niño la dibujó inclinada, se respeta su inclinación.
        double flat = Math.PI / 2 + step / 2;
        double off = Math.IEEEremainder(rot - flat, step);
        if (Math.abs(off) < Math.toRadians(10)) rot = flat;
        List<PointF> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = rot + i * step;
            out.add(new PointF(cx + (float) (r * Math.cos(t)), cy + (float) (r * Math.sin(t))));
        }
        return out;
    }

    // ---------- Utilidades ----------

    private static float size(List<PointF> pts) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF p : pts) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        return Math.max(maxX - minX, maxY - minY);
    }

    private static float diagonal(List<PointF> pts) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF p : pts) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        return Math.max(1f, (float) Math.hypot(maxX - minX, maxY - minY));
    }

    private static float length(List<PointF> pts) {
        float len = 0;
        for (int i = 1; i < pts.size(); i++) len += dist(pts.get(i - 1), pts.get(i));
        return len;
    }

    /** n puntos repartidos a distancias iguales a lo largo del trazo. */
    private static List<PointF> evenly(List<PointF> pts, int n) {
        float total = length(pts);
        List<PointF> out = new ArrayList<>();
        if (total <= 0 || n < 2) { out.add(new PointF(pts.get(0).x, pts.get(0).y)); return out; }
        float step = total / (n - 1), walked = 0, next = 0;
        int i = 1;
        PointF prev = pts.get(0);
        out.add(new PointF(prev.x, prev.y));
        next = step;
        while (i < pts.size() && out.size() < n) {
            PointF cur = pts.get(i);
            float d = dist(prev, cur);
            if (walked + d >= next && d > 0) {
                float t = (next - walked) / d;
                PointF q = new PointF(prev.x + (cur.x - prev.x) * t, prev.y + (cur.y - prev.y) * t);
                out.add(q);
                walked = next;
                prev = q;
                next += step;
            } else {
                walked += d;
                prev = cur;
                i++;
            }
        }
        PointF last = pts.get(pts.size() - 1);
        while (out.size() < n) out.add(new PointF(last.x, last.y));
        return out;
    }

    private static List<PointF> resample(List<PointF> in, float step) {
        List<PointF> out = new ArrayList<>();
        PointF prev = in.get(0);
        out.add(new PointF(prev.x, prev.y));
        float carry = 0;
        for (int i = 1; i < in.size(); i++) {
            PointF p = in.get(i);
            float d = dist(prev, p);
            while (carry + d >= step) {
                float t = (step - carry) / d;
                PointF q = new PointF(prev.x + (p.x - prev.x) * t, prev.y + (p.y - prev.y) * t);
                out.add(q);
                d -= (step - carry);
                prev = q;
                carry = 0;
            }
            carry += d;
            prev = p;
        }
        PointF last = in.get(in.size() - 1);
        if (dist(out.get(out.size() - 1), last) > 0.5f) out.add(new PointF(last.x, last.y));
        return out;
    }

    private static void addSegment(List<PointF> out, PointF a, PointF b) {
        int n = Math.max(1, (int) (dist(a, b) / STEP));
        for (int i = 0; i < n; i++) out.add(new PointF(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n));
    }

    private static float angleAt(PointF p, PointF q, PointF r) {
        double a1 = Math.atan2(p.y - q.y, p.x - q.x), a2 = Math.atan2(r.y - q.y, r.x - q.x);
        double d = Math.abs(Math.toDegrees(a1 - a2)) % 360;
        return (float) (d > 180 ? 360 - d : d);
    }

    private static float dist(PointF a, PointF b) {
        return (float) Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static float distToSegment(PointF p, PointF a, PointF b) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float l2 = dx * dx + dy * dy;
        if (l2 == 0) return dist(p, a);
        float t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / l2));
        return (float) Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
    }
}
