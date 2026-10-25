package mx.dibujos.kiosko;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconoce figuras dibujadas a mano y devuelve su versión perfecta:
 * línea recta, círculo, óvalo, triángulo, rectángulo y polígonos de 5 a 8
 * lados (regulares, como el hexágono). Devuelve null si el trazo no se parece
 * lo suficiente a ninguna, para dejarlo tal cual.
 */
public final class ShapeSnap {
    private static final float MIN_SIZE = 32f;      // trazos más chicos se dejan a mano
    private static final float LINE_TOLERANCE = 0.12f;
    private static final float CLOSED_GAP = 0.38f;  // hueco entre inicio y fin, relativo al tamaño
    private static final float ELLIPSE_TOLERANCE = 0.18f;
    private static final float POLY_TOLERANCE = 0.085f;
    private static final float STEP = 4f;           // separación de los puntos generados

    private ShapeSnap() {}

    public static List<PointF> recognize(List<PointF> raw) {
        if (raw.size() < 5) return null;
        List<PointF> pts = resample(raw, STEP);
        if (pts.size() < 5) return null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF p : pts) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        float size = Math.max(maxX - minX, maxY - minY);
        if (size < MIN_SIZE) return null;

        PointF a = pts.get(0), b = pts.get(pts.size() - 1);
        boolean closed = dist(a, b) < CLOSED_GAP * size;

        if (!closed) {
            float len = dist(a, b);
            if (len < MIN_SIZE) return null;
            float worst = 0;
            for (PointF p : pts) worst = Math.max(worst, distToSegment(p, a, b));
            float traveled=0;
            for(int i=1;i<pts.size();i++)traveled+=dist(pts.get(i-1),pts.get(i));
            return worst < LINE_TOLERANCE * len && traveled < len*1.65f ? line(a, b) : null;
        }

        float length=0;
        for(int i=1;i<pts.size();i++)length+=dist(pts.get(i-1),pts.get(i));
        // Un garabato con vueltas superpuestas no debe convertirse en un círculo.
        if(length>size*5.3f || length<size*1.8f)return null;
        float ellErr = ellipseError(pts);
        List<PointF> corners = corners(pts, size);
        float polyErr = corners == null ? Float.MAX_VALUE : polygonError(pts, corners) / size;

        boolean polyOk = corners != null && polyErr < POLY_TOLERANCE;
        boolean ellOk = ellErr < ELLIPSE_TOLERANCE;
        if (polyOk && corners.size() >= 5) {
            // Un óvalo también se puede cortar en 5–8 esquinas: el polígono regular solo
            // gana si la figura es casi redonda y el óvalo ajusta claramente peor.
            float[] e = ellipseParams(pts);
            boolean round = Math.min(e[2], e[3]) / Math.max(e[2], e[3]) > 0.8f;
            polyOk = round && polyErr * 3 < ellErr;
        }
        if (polyOk && (!ellOk || polyErr * 2 < ellErr)) return polygon(corners);
        if (ellOk) return ellipse(pts);
        return null;
    }

    // ---------- Líneas ----------

    private static List<PointF> line(PointF a, PointF b) {
        // Casi horizontal o vertical: se endereza del todo.
        float dx = b.x - a.x, dy = b.y - a.y;
        double ang = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
        PointF end = new PointF(b.x, b.y);
        if (ang < 8) end.y = a.y;
        else if (ang > 82) end.x = a.x;
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

    /** Centro, ejes y giro del óvalo que mejor envuelve al trazo. */
    private static float[] ellipseParams(List<PointF> pts) {
        float[] m = moments(pts);
        float cos = (float) Math.cos(m[2]), sin = (float) Math.sin(m[2]);
        float ra = 0, rb = 0;
        for (PointF p : pts) {
            float x = p.x - m[0], y = p.y - m[1];
            ra += Math.abs(x * cos + y * sin);
            rb += Math.abs(-x * sin + y * cos);
        }
        // Para una elipse, el promedio de |cos| es 2/π: se corrige para obtener el semieje.
        ra = ra / pts.size() * (float) (Math.PI / 2);
        rb = rb / pts.size() * (float) (Math.PI / 2);
        return new float[]{m[0], m[1], ra, rb, m[2]};
    }

    private static float ellipseError(List<PointF> pts) {
        float[] e = ellipseParams(pts);
        if (e[2] < 1 || e[3] < 1) return Float.MAX_VALUE;
        float cos = (float) Math.cos(e[4]), sin = (float) Math.sin(e[4]);
        float err = 0;
        for (PointF p : pts) {
            float x = p.x - e[0], y = p.y - e[1];
            float u = (x * cos + y * sin) / e[2], v = (-x * sin + y * cos) / e[3];
            err += Math.abs((float) Math.sqrt(u * u + v * v) - 1);
        }
        return err / pts.size();
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

    private static float polygonError(List<PointF> pts, List<PointF> poly) {
        float err = 0;
        for (PointF p : pts) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < poly.size(); i++) {
                best = Math.min(best, distToSegment(p, poly.get(i), poly.get((i + 1) % poly.size())));
            }
            err += best;
        }
        return err / pts.size();
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

    /** Triángulo: se respetan las esquinas del niño, con lados rectos; casi equilátero → equilátero. */
    private static List<PointF> straighten(List<PointF> c) {
        float a = dist(c.get(0), c.get(1)), b = dist(c.get(1), c.get(2)), d = dist(c.get(2), c.get(0));
        float mx = Math.max(a, Math.max(b, d)), mn = Math.min(a, Math.min(b, d));
        return mn / mx > 0.85f ? regular(c) : c;
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
        double start = Math.atan2(c.get(0).y - cy, c.get(0).x - cx);
        // Se acomoda para que quede con un lado plano abajo, como en los libros.
        double step = 2 * Math.PI / n;
        double flat = Math.PI / 2 + step / 2;
        double rot = flat + Math.round((start - flat) / step) * step;
        List<PointF> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = rot + i * step;
            out.add(new PointF(cx + (float) (r * Math.cos(t)), cy + (float) (r * Math.sin(t))));
        }
        return out;
    }

    // ---------- Utilidades ----------

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
