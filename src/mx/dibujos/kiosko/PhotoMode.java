package mx.dibujos.kiosko;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Modo fotografía: solo tomar fotos y verlas. Las fotos se guardan a color en
 * Pictures/Fotos (en la tinta se ven en grises, en la PC a color). Desde el
 * visor, el lápiz convierte la foto en hoja para colorear y abre el modo dibujo.
 */
public class PhotoMode extends FrameLayout {
    public static final String FOLDER = "Pictures/Fotos";

    public interface Callback {
        void onHome();
        void onPaint(Bitmap photo);
    }

    private final Activity activity;
    private final Executor io;
    private final Callback cb;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final int barHeight;
    private CameraPanel camera;
    private ImageView flash;

    public PhotoMode(Activity a, int barHeight, Executor io, Callback cb) {
        super(a);
        this.activity = a;
        this.io = io;
        this.cb = cb;
        this.barHeight = barHeight;
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        showCamera();
    }

    private void showCamera() {
        removeAllViews();
        camera = new CameraPanel(activity, barHeight, new CameraPanel.Callback() {
            @Override
            public void onPhoto(Bitmap photo) { took(photo); }

            @Override
            public void onClose() { cb.onHome(); }
        });
        ToolButton gal = new ToolButton(activity, ToolButton.GALLERY);
        gal.setOnClickListener(v -> showGallery());
        camera.addToBar(gal);
        addView(camera, new LayoutParams(-1, -1));
        // La vista previa se mueve: el modo "video" de Bigme la muestra con grises tramados.
        Eink.apply(activity, activity.getWindow(), Eink.FAST);
    }

    /** Guarda la foto y la deja quieta un momento en pantalla para que el niño vea que salió. */
    private void took(Bitmap photo) {
        flash = new ImageView(activity);
        flash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        flash.setBackgroundColor(Color.WHITE);
        flash.setImageBitmap(photo);
        addView(flash, new LayoutParams(-1, -1));
        io.execute(() -> save(photo));
        ui.postDelayed(() -> {
            if (flash != null) removeView(flash);
            flash = null;
            if (camera != null) camera.resume();
        }, 1200);
    }

    private void save(Bitmap photo) {
        String name = "foto_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".jpg";
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER);
        try {
            Uri u = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return;
            try (OutputStream os = activity.getContentResolver().openOutputStream(u)) {
                photo.compress(Bitmap.CompressFormat.JPEG, 92, os);
            }
        } catch (Exception ignored) {
        }
    }

    private void showGallery() {
        if (camera != null) camera.pause();
        removeAllViews();
        camera = null;
        Eink.apply(activity, activity.getWindow(), Eink.DU);
        addView(new GalleryPanel(activity, FOLDER, "Mis fotos", "Todavía no hay fotos", io,
                new GalleryPanel.Callback() {
                    @Override
                    public void onPick(Uri uri) { showPhoto(uri); }

                    @Override
                    public void onClose() { showCamera(); }
                }), new LayoutParams(-1, -1));
        Eink.cleanRefresh(activity);
    }

    private void showPhoto(Uri uri) {
        Bitmap b;
        try (InputStream is = activity.getContentResolver().openInputStream(uri)) {
            b = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            return;
        }
        if (b == null) return;
        final Bitmap photo = b;
        removeAllViews();
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        ImageView iv = new ImageView(activity);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(Color.WHITE);
        iv.setImageBitmap(photo);
        col.addView(iv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout bar = new LinearLayout(activity);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(4, 8, 4, 8);
        ToolButton back = new ToolButton(activity, ToolButton.BACK);
        back.setOnClickListener(v -> showGallery());
        ToolButton paint = new ToolButton(activity, ToolButton.PENCIL);
        paint.setOnClickListener(v -> cb.onPaint(photo));
        bar.addView(back, new LinearLayout.LayoutParams(0, -1, 1f));
        bar.addView(paint, new LinearLayout.LayoutParams(0, -1, 1f));
        col.addView(bar, new LinearLayout.LayoutParams(-1, barHeight));
        addView(col, new LayoutParams(-1, -1));
        // Foto quieta en grises: el modo normal de Bigme la muestra con más tonos.
        Eink.apply(activity, activity.getWindow(), Eink.NORMAL);
        Eink.cleanRefresh(activity);
    }

    /** Atrás del sistema: regresa un paso. Devuelve false si ya está en la cámara. */
    public boolean back() {
        if (camera != null) return false;
        showCamera();
        return true;
    }

    public void pause() { if (camera != null) camera.pause(); }

    public void resume() { if (camera != null) camera.resume(); }
}
