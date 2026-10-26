package mx.dibujos.kiosko;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Laberinto de celdas (backtracking: siempre hay exactamente un camino entre dos celdas).
 * Se coloca en un rectángulo de pantalla; responde si un movimiento cruza una pared
 * (para el camino dibujado con el dedo y para la canica) y qué celdas ya alcanzó el niño.
 * La entrada es la celda de arriba a la izquierda y la meta la de abajo a la derecha.
 */
final class Maze {
    final int cols, rows;
    /** Pared a la derecha de (c, r) y abajo de (c, r). */
    private final boolean[][] right, down;
    float left, top, cell;
    private final boolean[][] reached;
    private boolean won;

    Maze(int cols, int rows, long seed) {
        this.cols = cols;
        this.rows = rows;
        right = new boolean[cols][rows];
        down = new boolean[cols][rows];
        reached = new boolean[cols][rows];
        for (int c = 0; c < cols; c++)
            for (int r = 0; r < rows; r++) { right[c][r] = true; down[c][r] = true; }
        carve(new Random(seed));
        reached[0][0] = true;
    }

    private void carve(Random rnd) {
        boolean[][] seen = new boolean[cols][rows];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, 0});
        seen[0][0] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] at = stack.peek();
            List<int[]> next = new ArrayList<>();
            for (int[] d : dirs) {
                int c = at[0] + d[0], r = at[1] + d[1];
                if (c >= 0 && r >= 0 && c < cols && r < rows && !seen[c][r]) next.add(new int[]{c, r});
            }
            if (next.isEmpty()) { stack.pop(); continue; }
            int[] n = next.get(rnd.nextInt(next.size()));
            if (n[0] != at[0]) right[Math.min(n[0], at[0])][at[1]] = false;
            else down[at[0]][Math.min(n[1], at[1])] = false;
            seen[n[0]][n[1]] = true;
            stack.push(n);
        }
    }

    /** Centra el laberinto con celdas cuadradas dentro de w×h (con margen). */
    void layout(float w, float h, float margin) {
        cell = Math.min((w - 2 * margin) / cols, (h - 2 * margin) / rows);
        left = (w - cell * cols) / 2;
        top = (h - cell * rows) / 2;
    }

    boolean wallRight(int c, int r) { return c == cols - 1 || right[c][r]; }
    boolean wallDown(int c, int r) { return r == rows - 1 || down[c][r]; }

    /** ¿Se puede pasar directo entre dos celdas vecinas? */
    boolean open(int c0, int r0, int c1, int r1) {
        if (!inside(c0, r0) || !inside(c1, r1)) return false;
        if (r0 == r1 && Math.abs(c0 - c1) == 1) return !right[Math.min(c0, c1)][r0];
        if (c0 == c1 && Math.abs(r0 - r1) == 1) return !down[c0][Math.min(r0, r1)];
        return c0 == c1 && r0 == r1;
    }

    boolean inside(int c, int r) { return c >= 0 && r >= 0 && c < cols && r < rows; }
    int col(float x) { return (int) Math.floor((x - left) / cell); }
    int row(float y) { return (int) Math.floor((y - top) / cell); }

    /**
     * ¿El paso de (x0,y0) a (x1,y1) cruza una pared? Fuera del laberinto se puede dibujar libre,
     * pero no entrar ni salir por los lados. Si el dedo salta varias celdas, se revisa el trayecto.
     */
    boolean crossesWall(float x0, float y0, float x1, float y1) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0) / (cell / 3)));
        int c = col(x0), r = row(y0);
        for (int i = 1; i <= steps; i++) {
            float x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            int nc = col(x), nr = row(y);
            if (nc == c && nr == r) continue;
            boolean in0 = inside(c, r), in1 = inside(nc, nr);
            if (in0 != in1) return true; // los bordes del laberinto son pared
            if (in0) {
                boolean ok = open(c, r, nc, nr)
                        || (Math.abs(nc - c) == 1 && Math.abs(nr - r) == 1 // en diagonal: por alguna de las dos esquinas
                        && ((open(c, r, nc, r) && open(nc, r, nc, nr)) || (open(c, r, c, nr) && open(c, nr, nc, nr))));
                if (!ok) return true;
            }
            c = nc;
            r = nr;
        }
        return false;
    }

    /** El camino avanza: marca la celda si llegó desde una ya alcanzada. Devuelve true si es la meta por primera vez. */
    boolean reach(float fromX, float fromY, float x, float y) {
        int c0 = col(fromX), r0 = row(fromY), c = col(x), r = row(y);
        if (!inside(c, r) || !inside(c0, r0) || !reached[c0][r0]) return false;
        reached[c][r] = true;
        if (c == cols - 1 && r == rows - 1 && !won) { won = true; return true; }
        return false;
    }

    boolean isReached(float x, float y) { int c = col(x), r = row(y); return inside(c, r) && reached[c][r]; }
    boolean won() { return won; }

    float centerX(int c) { return left + (c + .5f) * cell; }
    float centerY(int r) { return top + (r + .5f) * cell; }

    /**
     * Mueve una canica de radio rad de (x, y) a (nx, ny) sin atravesar paredes: avanza en pasos
     * cortos y la empuja fuera de cada segmento de pared que toque (así se desliza por ellas y
     * no se atora en las esquinas). Devuelve {x, y, chocóX(1/0), chocóY(1/0)}.
     */
    float[] slide(float x, float y, float nx, float ny, float rad) {
        float hitX = 0, hitY = 0;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(nx - x, ny - y) / (rad / 2)));
        float dx = (nx - x) / steps, dy = (ny - y) / steps;
        for (int i = 0; i < steps; i++) {
            x += dx;
            y += dy;
            int c0 = col(x), r0 = row(y);
            for (int c = c0 - 1; c <= c0 + 1; c++)
                for (int r = r0 - 1; r <= r0 + 1; r++) {
                    if (!inside(c, r)) continue;
                    float cl = left + c * cell, ct = top + r * cell;
                    float[][] segs = {
                            wallRight(c, r) ? new float[]{cl + cell, ct, cl + cell, ct + cell} : null,
                            wallDown(c, r) ? new float[]{cl, ct + cell, cl + cell, ct + cell} : null,
                            c == 0 ? new float[]{cl, ct, cl, ct + cell} : null,
                            r == 0 ? new float[]{cl, ct, cl + cell, ct} : null};
                    for (float[] g : segs) {
                        if (g == null) continue;
                        float px = Math.max(Math.min(g[0], g[2]), Math.min(Math.max(g[0], g[2]), x));
                        float py = Math.max(Math.min(g[1], g[3]), Math.min(Math.max(g[1], g[3]), y));
                        float ox = x - px, oy = y - py, d = (float) Math.hypot(ox, oy);
                        if (d >= rad) continue;
                        if (d < 1e-4f) { ox = -dx; oy = -dy; d = (float) Math.hypot(ox, oy); if (d < 1e-4f) continue; }
                        x = px + ox / d * rad;
                        y = py + oy / d * rad;
                        if (Math.abs(ox) > Math.abs(oy)) hitX = 1; else hitY = 1;
                    }
                }
        }
        return new float[]{x, y, hitX, hitY};
    }

    /** Camino de la entrada a la meta (para pruebas y para la canica). */
    int pathLength() {
        int[][] dist = new int[cols][rows];
        for (int[] d : dist) java.util.Arrays.fill(d, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{0, 0});
        dist[0][0] = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] a = q.poll();
            for (int[] d : dirs) {
                int c = a[0] + d[0], r = a[1] + d[1];
                if (inside(c, r) && dist[c][r] < 0 && open(a[0], a[1], c, r)) { dist[c][r] = dist[a[0]][a[1]] + 1; q.add(new int[]{c, r}); }
            }
        }
        return dist[cols - 1][rows - 1];
    }

    /** Centros de las celdas del camino de la entrada a la meta, x0,y0,x1,y1… (pruebas por adb). */
    float[] solution() {
        int[][] prev = new int[cols * rows][];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{0, 0});
        prev[0] = new int[]{-1, -1};
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] a = q.poll();
            for (int[] d : dirs) {
                int c = a[0] + d[0], r = a[1] + d[1];
                if (inside(c, r) && prev[r * cols + c] == null && open(a[0], a[1], c, r)) { prev[r * cols + c] = a; q.add(new int[]{c, r}); }
            }
        }
        List<int[]> route = new ArrayList<>();
        for (int[] at = {cols - 1, rows - 1}; at[0] >= 0; at = prev[at[1] * cols + at[0]]) route.add(0, at);
        float[] xy = new float[route.size() * 2];
        for (int i = 0; i < route.size(); i++) { xy[i * 2] = centerX(route.get(i)[0]); xy[i * 2 + 1] = centerY(route.get(i)[1]); }
        return xy;
    }

    /** Cuántas celdas se pueden visitar desde la entrada (en un laberinto perfecto: todas). */
    int connected() {
        int n = 0;
        boolean[][] seen = new boolean[cols][rows];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{0, 0});
        seen[0][0] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] a = q.poll();
            n++;
            for (int[] d : dirs) {
                int c = a[0] + d[0], r = a[1] + d[1];
                if (inside(c, r) && !seen[c][r] && open(a[0], a[1], c, r)) { seen[c][r] = true; q.add(new int[]{c, r}); }
            }
        }
        return n;
    }
}
