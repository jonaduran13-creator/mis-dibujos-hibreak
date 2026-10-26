package mx.dibujos.kiosko;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * Sellos: dibujitos listos que el niño pone con un toque (y del tamaño que arrastre).
 * Cada uno son unos cuantos trazos en una caja de -1 a 1; se dibujan con la punta elegida.
 */
final class Stamps {
    static final int STAR = 0, HEART = 1, FLOWER = 2, SUN = 3, CLOUD = 4, HOUSE = 5, CAT = 6, FISH = 7, BUTTERFLY = 8, COUNT = 9;
    static final String[] NAMES = {"Estrella", "Corazón", "Flor", "Sol", "Nube", "Casita", "Gato", "Pez", "Mariposa"};

    private Stamps() {}

    /** Los trazos del sello con centro (cx, cy) y radio r, en pixeles. */
    static List<List<PointF>> make(int kind, float cx, float cy, float r) {
        List<List<PointF>> unit = unit(kind);
        List<List<PointF>> out = new ArrayList<>();
        for (List<PointF> s : unit) {
            List<PointF> t = new ArrayList<>(s.size());
            for (PointF p : s) t.add(new PointF(cx + p.x * r, cy + p.y * r));
            out.add(t);
        }
        return out;
    }

    static List<List<PointF>> unit(int kind) {
        List<List<PointF>> s = new ArrayList<>();
        switch (kind) {
            case STAR: {
                List<PointF> l = new ArrayList<>();
                for (int i = 0; i <= 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5, q = i % 2 == 0 ? 1 : 0.42;
                    l.add(pt(Math.cos(a) * q, Math.sin(a) * q + 0.08));
                }
                s.add(l);
                break;
            }
            case HEART: {
                List<PointF> l = new ArrayList<>();
                for (int i = 0; i <= 72; i++) {
                    double t = i * 2 * Math.PI / 72, x = 16 * Math.pow(Math.sin(t), 3);
                    double y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
                    l.add(pt(x / 17, (y - 2.5) / 17));
                }
                s.add(l);
                break;
            }
            case FLOWER: {
                List<PointF> petals = new ArrayList<>();
                for (int i = 0; i <= 150; i++) { // rosa de 5 pétalos
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 150, q = 0.25 + 0.75 * Math.abs(Math.cos(2.5 * (a + Math.PI / 2)));
                    petals.add(pt(Math.cos(a) * q, Math.sin(a) * q));
                }
                s.add(petals);
                s.add(circle(0, 0, 0.22, 0.22));
                break;
            }
            case SUN: {
                s.add(circle(0, 0, 0.48, 0.48));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    s.add(line(Math.cos(a) * 0.65, Math.sin(a) * 0.65, Math.cos(a), Math.sin(a)));
                }
                break;
            }
            case CLOUD: {
                double[][] bumps = {{-0.6, 0.12, 0.3}, {-0.2, -0.12, 0.4}, {0.25, -0.2, 0.42}, {0.66, 0.1, 0.3}};
                List<PointF> l = new ArrayList<>();
                double bottom = 0.42;
                for (int i = 0; i <= 60; i++) {
                    double x = -0.9 + i * 1.86 / 60, y = bottom;
                    for (double[] b : bumps) {
                        double d = b[2] * b[2] - (x - b[0]) * (x - b[0]);
                        if (d > 0) y = Math.min(y, b[1] - Math.sqrt(d));
                    }
                    l.add(pt(x, y));
                }
                l.add(pt(-0.9, bottom));
                l.add(l.get(0));
                s.add(l);
                break;
            }
            case HOUSE:
                s.add(poly(-0.62, -0.05, -0.62, 0.85, 0.62, 0.85, 0.62, -0.05));
                s.add(poly(-0.82, 0.02, 0, -0.82, 0.82, 0.02));
                s.add(poly(-0.16, 0.85, -0.16, 0.38, 0.16, 0.38, 0.16, 0.85));
                s.add(poly(0.28, 0.12, 0.5, 0.12, 0.5, 0.32, 0.28, 0.32, 0.28, 0.12));
                break;
            case CAT:
                s.add(circle(0, 0.15, 0.68, 0.6));
                s.add(poly(-0.6, -0.12, -0.55, -0.8, -0.18, -0.42));
                s.add(poly(0.6, -0.12, 0.55, -0.8, 0.18, -0.42));
                s.add(circle(-0.26, 0.05, 0.08, 0.1));
                s.add(circle(0.26, 0.05, 0.08, 0.1));
                s.add(poly(-0.07, 0.25, 0.07, 0.25, 0, 0.34, -0.07, 0.25));
                s.add(poly(-0.16, 0.42, 0, 0.34, 0.16, 0.42));
                s.add(line(-0.2, 0.3, -0.85, 0.2));
                s.add(line(-0.2, 0.38, -0.85, 0.45));
                s.add(line(0.2, 0.3, 0.85, 0.2));
                s.add(line(0.2, 0.38, 0.85, 0.45));
                break;
            case FISH:
                s.add(circle(-0.15, 0, 0.68, 0.42));
                s.add(poly(0.5, 0, 0.95, -0.38, 0.95, 0.38, 0.5, 0));
                s.add(circle(-0.5, -0.08, 0.07, 0.07));
                s.add(poly(-0.82, 0.12, -0.7, 0.16));
                break;
            case BUTTERFLY:
                s.add(circle(-0.45, -0.28, 0.42, 0.36));
                s.add(circle(0.45, -0.28, 0.42, 0.36));
                s.add(circle(-0.36, 0.36, 0.3, 0.28));
                s.add(circle(0.36, 0.36, 0.3, 0.28));
                s.add(line(0, -0.5, 0, 0.62));
                s.add(poly(-0.04, -0.5, -0.3, -0.92));
                s.add(poly(0.04, -0.5, 0.3, -0.92));
                break;
        }
        return s;
    }

    private static PointF pt(double x, double y) { return new PointF((float) x, (float) y); }

    private static List<PointF> line(double x0, double y0, double x1, double y1) { return poly(x0, y0, x1, y1); }

    private static List<PointF> poly(double... xy) {
        List<PointF> l = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) l.add(pt(xy[i], xy[i + 1]));
        return l;
    }

    private static List<PointF> circle(double cx, double cy, double rx, double ry) {
        List<PointF> l = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 48;
            l.add(pt(cx + Math.cos(a) * rx, cy + Math.sin(a) * ry));
        }
        return l;
    }
}
