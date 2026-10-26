package mx.dibujos.kiosko;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Linterna: el flash LED trasero en modo antorcha (no necesita permiso de cámara).
 * Se apaga sola a los 5 minutos, al dormir el aparato y al abrir la cámara, para
 * cuidar la batería y que el LED no se caliente. Los botones se enteran de cada cambio,
 * también cuando la apaga el sistema (por ejemplo, la cámara al abrirse).
 */
final class Linterna {
    private static final long AUTO_OFF = 5 * 60 * 1000;
    private static Linterna instance;

    private final CameraManager manager;
    private final String cameraId;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean on;
    private final Runnable autoOff = () -> set(false);

    static Linterna get(Context c) {
        if (instance == null) instance = new Linterna(c.getApplicationContext());
        return instance;
    }

    private Linterna(Context c) {
        manager = c.getSystemService(CameraManager.class);
        cameraId = findFlash();
        if (manager != null && cameraId != null) {
            manager.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String id, boolean enabled) {
                    if (id.equals(cameraId)) changed(enabled);
                }

                @Override
                public void onTorchModeUnavailable(String id) {
                    if (id.equals(cameraId)) changed(false); // la cámara la está usando
                }
            }, ui);
        }
    }

    /** La cámara trasera con flash; si no hay, cualquier cámara con flash. */
    private String findFlash() {
        if (manager == null) return null;
        try {
            String any = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics ch = manager.getCameraCharacteristics(id);
                Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash == null || !flash) continue;
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
                if (any == null) any = id;
            }
            return any;
        } catch (Exception e) {
            Log.w("MisDibujos", "Sin linterna", e);
            return null;
        }
    }

    boolean available() { return cameraId != null; }
    boolean isOn() { return on; }

    void toggle() { set(!on); }

    void set(boolean value) {
        if (cameraId == null || (!value && !on)) return; // apagar algo ya apagado: nada que hacer
        try {
            manager.setTorchMode(cameraId, value);
        } catch (Exception e) {
            // Ocupada por la cámara u otro motivo: se queda apagada.
            Log.w("MisDibujos", "Linterna no disponible", e);
            changed(false);
        }
    }

    private void changed(boolean enabled) {
        on = enabled;
        ui.removeCallbacks(autoOff);
        if (enabled) ui.postDelayed(autoOff, AUTO_OFF);
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    void addListener(Runnable r) { listeners.add(r); }
    void removeListener(Runnable r) { listeners.remove(r); }

    /** Botón de linterna listo para poner en una barra; se mantiene al día solo. */
    static ToolButton button(Context c) {
        final Linterna lamp = get(c);
        final ToolButton b = new ToolButton(c, ToolButton.LAMP) {
            private final Runnable sync = () -> setChosen(lamp.isOn());
            @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); lamp.addListener(sync); sync.run(); }
            @Override protected void onDetachedFromWindow() { lamp.removeListener(sync); super.onDetachedFromWindow(); }
        };
        b.setOnClickListener(v -> { Sounds.play(Sounds.TAP); lamp.toggle(); });
        if (!lamp.available()) b.setVisibility(android.view.View.GONE);
        return b;
    }
}
