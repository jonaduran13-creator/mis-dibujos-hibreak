package com.xrz;
import android.graphics.Bitmap;
public class NoteView extends android.view.HandwrittenView {
    public NoteView(android.content.Context c) { super(c); }
    public int setPen(BasePen p) { return 0; }
    public BasePen getPen() { return null; }
    public void clear(boolean b) {}
    public void clear(boolean a, boolean b) {}
    public boolean canRevertPrev() { return false; }
    public void revertPrev() {}
    public Bitmap getHandwrittenBitmap() { return null; }
    public Bitmap getBackgroundBitmap() { return null; }
    public Bitmap getBlendBitmap() { return null; }
    public Bitmap getBlendBitmapAfterRotation() { return null; }
    public final void setBackground(Bitmap b) {}
    public final void setBackground(Bitmap b, boolean f) {}
    public final void setForegroundAndBackgroundTogether(Bitmap fg, Bitmap bg, boolean f) {}
    public void setBackgroundEnable(boolean b) {}
    public final void setBackgroundRefreshMode(int m) {}
    public void setInputEnabled(boolean b) {}
    public void setOverlayEnabled(boolean b) {}
    public void enableCompatibleMode(boolean b) {}
    public void setFocusCheckEnabled(boolean b) {}
    public void setDrawGroundMode(int m) {}
    public String getVersion() { return null; }
    public void onRestart() {}
    public void onDestroy() {}
    public int onInputTouch(int a, int b, int c, int d, int e) { return 0; }
}
