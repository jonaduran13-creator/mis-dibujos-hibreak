package mx.dibujos.kiosko;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Superficies de tinta: sin sombras, degradados ni animación. */
final class Ui {
    /** Botones de texto con las mismas superficies y estados que las herramientas. */
    static void action(android.widget.Button button) {
        float d=button.getResources().getDisplayMetrics().density;
        android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed},surface(Color.BLACK,Math.round(10*d),Math.round(d)));
        bg.addState(new int[]{android.R.attr.state_focused},surface(Color.WHITE,Math.round(10*d),Math.round(3*d)));
        bg.addState(new int[]{},surface(Color.WHITE,Math.round(10*d),Math.round(d)));
        button.setBackground(bg);button.setStateListAnimator(null);button.setAllCaps(false);
        button.setTextSize(15);button.setTypeface(null,android.graphics.Typeface.BOLD);
        button.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{0xff666666,Color.WHITE,Color.BLACK}));
    }
    static GradientDrawable surface(int color, int radius, int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);
        d.setStroke(stroke,Color.BLACK);return d;
    }
}
