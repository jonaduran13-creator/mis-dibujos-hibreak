package mx.dibujos.kiosko;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Superficies de tinta: sin sombras, degradados ni animación. */
final class Ui {
    static GradientDrawable surface(int color, int radius, int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);
        d.setStroke(stroke,Color.BLACK);return d;
    }
}
