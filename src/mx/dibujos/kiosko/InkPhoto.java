package mx.dibujos.kiosko;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Convierte una foto en una hoja para colorear: solo los contornos en negro
 * sobre fondo blanco. Se ve limpia en la tinta (sin tramas grises) y deja
 * espacio para que el niño pinte encima.
 *
 * Pasos: gris → suavizado (quita el ruido del sensor) → bordes de Sobel →
 * umbral según la propia foto → quitar puntitos sueltos.
 */
public final class InkPhoto {
    /** Fracción de píxeles que pueden ser línea. Más alto = más detalle. */
    private static final float EDGE_FRACTION = 0.07f;
    /** Bordes más débiles que esto nunca son línea (evita rayar fotos lisas). */
    private static final float MIN_EDGE = 18f;

    private InkPhoto() {}

    public static Bitmap convert(Bitmap src, int w, int h) {
        Bitmap fit = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(fit);
        c.drawColor(Color.WHITE);
        float scale = Math.min((float) w / src.getWidth(), (float) h / src.getHeight());
        float dw=src.getWidth()*scale,dh=src.getHeight()*scale;
        c.drawBitmap(src,null,new android.graphics.RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),new Paint(Paint.FILTER_BITMAP_FLAG));

        int n = w * h;
        int[] px = new int[n];
        fit.getPixels(px, 0, w, 0, 0, w, h);
        float[] g = new float[n];
        for (int i = 0; i < n; i++) {
            int p = px[i];
            g[i] = (299 * ((p >> 16) & 255) + 587 * ((p >> 8) & 255) + 114 * (p & 255)) / 1000f;
        }

        float[] tmp = new float[n];
        boxBlur(g, tmp, w, h, 2);
        boxBlur(g, tmp, w, h, 2);

        // Magnitud del gradiente (Sobel).
        float[] mag = tmp;
        float max = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float gx = -g[i - w - 1] - 2 * g[i - 1] - g[i + w - 1] + g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1];
                float gy = -g[i - w - 1] - 2 * g[i - w] - g[i - w + 1] + g[i + w - 1] + 2 * g[i + w] + g[i + w + 1];
                float m = (float) Math.sqrt(gx * gx + gy * gy);
                mag[i] = m;
                if (m > max) max = m;
            }
        }
        for (int x = 0; x < w; x++) { mag[x] = 0; mag[(h - 1) * w + x] = 0; }
        for (int y = 0; y < h; y++) { mag[y * w] = 0; mag[y * w + w - 1] = 0; }

        // Umbral: el valor que deja pasar solo la fracción más fuerte de bordes.
        int bins = 1024;
        int[] hist = new int[bins];
        float k = max > 0 ? (bins - 1) / max : 0;
        for (int i = 0; i < n; i++) hist[(int) (mag[i] * k)]++;
        int keep = Math.round(n * EDGE_FRACTION), acc = 0, b = bins - 1;
        while (b > 0 && (acc += hist[b]) < keep) b--;
        float threshold = Math.max(MIN_EDGE, b / Math.max(k, 1e-6f));

        boolean[] edge = new boolean[n];
        for (int i = 0; i < n; i++) edge[i] = mag[i] >= threshold;

        // Quita puntitos sueltos (ruido): un píxel de línea necesita al menos 2 vecinos.
        for (int i = 0; i < n; i++) px[i] = Color.WHITE;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if (!edge[i]) continue;
                int nb = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if ((dx != 0 || dy != 0) && edge[i + dy * w + dx]) nb++;
                if (nb >= 2) {px[i] = Color.BLACK;px[i+1]=Color.BLACK;}
            }
        }
        fit.setPixels(px, 0, w, 0, 0, w, h);
        return fit;
    }

    /** Foto completa aclarada para dibujar encima sin recortar caras. */
    public static Bitmap soft(Bitmap source,int w,int h){
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);canvas.drawColor(Color.WHITE);
        float scale=Math.min(w/(float)source.getWidth(),h/(float)source.getHeight());
        float width=source.getWidth()*scale,height=source.getHeight()*scale;
        Paint brush=new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.ColorMatrix gray=new android.graphics.ColorMatrix();gray.setSaturation(0);
        android.graphics.ColorMatrix light=new android.graphics.ColorMatrix(new float[]{.58f,0,0,0,107,0,.58f,0,0,107,0,0,.58f,0,107,0,0,0,1,0});
        gray.postConcat(light);brush.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
        canvas.drawBitmap(source,null,new android.graphics.RectF((w-width)/2,(h-height)/2,(w+width)/2,(h+height)/2),brush);return out;
    }

    /** Desenfoque de caja separable, en el mismo arreglo. */
    private static void boxBlur(float[] a, float[] tmp, int w, int h, int r) {
        float d = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float s = 0;
            for (int x = -r; x <= r; x++) s += a[row + Math.min(w - 1, Math.max(0, x))];
            for (int x = 0; x < w; x++) {
                tmp[row + x] = s / d;
                s += a[row + Math.min(w - 1, x + r + 1)] - a[row + Math.max(0, x - r)];
            }
        }
        for (int x = 0; x < w; x++) {
            float s = 0;
            for (int y = -r; y <= r; y++) s += tmp[Math.min(h - 1, Math.max(0, y)) * w + x];
            for (int y = 0; y < h; y++) {
                a[y * w + x] = s / d;
                s += tmp[Math.min(h - 1, y + r + 1) * w + x] - tmp[Math.max(0, y - r) * w + x];
            }
        }
    }
}
