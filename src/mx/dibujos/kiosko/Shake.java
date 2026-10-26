package mx.dibujos.kiosko;

/**
 * Reconoce "agitar" (como el juguete de las perillas) con el acelerómetro: tres sacudidas
 * fuertes, en sentidos alternados, en poco más de un segundo. Caminar, dejarlo en la mesa
 * o girarlo no llegan a eso.
 */
final class Shake {
    static final float STRONG = 11f; // m/s² por encima de la gravedad
    static final int NEEDED = 3;
    static final long WINDOW = 1300, QUIET = 1500;

    private final long[] peaks = new long[NEEDED];
    private int count;
    private float lastSign;
    private long lastShake = -QUIET;
    private float gx, gy, gz;
    private boolean primed;

    /** Lectura del acelerómetro (incluye gravedad). Devuelve true al reconocer una agitada. */
    boolean feed(float ax, float ay, float az, long now) {
        if (!primed) { gx = ax; gy = ay; gz = az; primed = true; return false; }
        // Filtro sencillo: la gravedad cambia despacio; lo que queda es el movimiento.
        gx = gx * .85f + ax * .15f; gy = gy * .85f + ay * .15f; gz = gz * .85f + az * .15f;
        float lx = ax - gx, ly = ay - gy, lz = az - gz;
        float mag = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (mag < STRONG || now - lastShake < QUIET) return false;
        float main = Math.abs(lx) >= Math.abs(ly) && Math.abs(lx) >= Math.abs(lz) ? lx : Math.abs(ly) >= Math.abs(lz) ? ly : lz;
        float sign = Math.signum(main);
        if (count > 0 && (sign == lastSign || now - peaks[count - 1] < 90)) return false; // misma sacudida
        if (count > 0 && now - peaks[0] > WINDOW) count = 0;
        peaks[count++] = now;
        lastSign = sign;
        if (count < NEEDED) return false;
        count = 0;
        lastShake = now;
        return true;
    }
}
