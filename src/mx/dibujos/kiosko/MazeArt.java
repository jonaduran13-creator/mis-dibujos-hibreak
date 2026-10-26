package mx.dibujos.kiosko;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import java.util.List;

/** Dibuja el laberinto como papel: paredes negras gruesas, una carita en la entrada y una estrella en la meta. */
final class MazeArt {
    private MazeArt() {}

    static Bitmap render(Maze m, int w, int h, float density) {
        Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        out.eraseColor(Color.WHITE);
        draw(new Canvas(out), m, density);
        return out;
    }

    static void draw(Canvas c, Maze m, float density) {
        Paint p = new Paint();
        p.setAntiAlias(false); // en tinta, bordes nítidos
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.SQUARE);
        p.setStrokeWidth(Math.max(3, m.cell * .07f));
        float l = m.left, t = m.top, s = m.cell;
        // Bordes, con la entrada abierta a la izquierda de la primera celda y la salida a la derecha de la última.
        c.drawLine(l, t, l + s * m.cols, t, p);
        c.drawLine(l, t + s * m.rows, l + s * m.cols, t + s * m.rows, p);
        c.drawLine(l, t + s, l, t + s * m.rows, p);
        c.drawLine(l + s * m.cols, t, l + s * m.cols, t + s * (m.rows - 1), p);
        for (int col = 0; col < m.cols; col++)
            for (int r = 0; r < m.rows; r++) {
                float x = l + col * s, y = t + r * s;
                if (col < m.cols - 1 && m.wallRight(col, r)) c.drawLine(x + s, y, x + s, y + s, p);
                if (r < m.rows - 1 && m.wallDown(col, r)) c.drawLine(x, y + s, x + s, y + s, p);
            }
        // Entrada: carita. Meta: estrella.
        p.setStrokeWidth(Math.max(2, s * .045f));
        p.setStrokeCap(Paint.Cap.ROUND);
        float cx = m.centerX(0), cy = m.centerY(0), r = s * .26f;
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx - r * .38f, cy - r * .2f, r * .11f, p);
        c.drawCircle(cx + r * .38f, cy - r * .2f, r * .11f, p);
        p.setStyle(Paint.Style.STROKE);
        c.drawArc(cx - r * .5f, cy - r * .35f, cx + r * .5f, cy + r * .55f, 20, 140, false, p);
        star(c, p, m.centerX(m.cols - 1), m.centerY(m.rows - 1), s * .32f);
    }

    static void star(Canvas c, Paint p, float cx, float cy, float r) {
        for (List<PointF> stroke : Stamps.make(Stamps.STAR, cx, cy, r)) {
            Path path = new Path();
            for (int i = 0; i < stroke.size(); i++) {
                if (i == 0) path.moveTo(stroke.get(i).x, stroke.get(i).y); else path.lineTo(stroke.get(i).x, stroke.get(i).y);
            }
            c.drawPath(path, p);
        }
    }
}
