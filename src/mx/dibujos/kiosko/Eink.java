package mx.dibujos.kiosko;

import android.content.Context;
import android.util.Log;
import android.view.Window;

/**
 * Acceso por reflexión al motor de tinta de Bigme (xrz.framework.manager).
 * Las constantes salen de xrz.framework.manager.EinkRefreshMode en el
 * framework.jar del HiBreak. Si algo no existe, simplemente no hace nada.
 */
public final class Eink {
    private static final String TAG = "MisDibujosEink";

    public static final int DU = 2;
    public static final int A2 = 16;
    public static final int CLEAN = 176;
    public static final int NORMAL = 178;
    public static final int FAST = 179;
    public static final int HANDWRITE = 1029;

    private Eink() {}

    private static Object manager(Context c) throws Exception {
        Class<?> k = Class.forName("xrz.framework.manager.XrzEinkManager");
        return k.getConstructor(Context.class).newInstance(c);
    }

    /** Modo de refresco de la ventana de dibujo y política guardada de la app. */
    public static void apply(Context c, Window w, int mode) {
        try {
            Object m = manager(c);
            m.getClass().getMethod("setRefreshModeByWindow", Window.class, int.class).invoke(m, w, mode);
            Log.i(TAG, "setRefreshModeByWindow " + mode + " ok");
        } catch (Throwable t) {
            Log.w(TAG, "setRefreshModeByWindow", t);
        }
        try {
            Object dpm = c.getSystemService("xrz_display_policy_service");
            if (dpm != null) {
                Object r = dpm.getClass().getMethod("setRefreshModeForPackage", String.class, int.class)
                        .invoke(dpm, c.getPackageName(), mode);
                Log.i(TAG, "setRefreshModeForPackage " + mode + " -> " + r);
            }
        } catch (Throwable t) {
            Log.w(TAG, "setRefreshModeForPackage", t);
        }
    }

    /** Modo de refresco solo para una vista (por ejemplo, la barra de botones). */
    public static void applyView(Context c, android.view.View v, int mode) {
        try {
            Object m = manager(c);
            m.getClass().getMethod("setRefreshModeByView", android.view.View.class, int.class).invoke(m, v, mode);
        } catch (Throwable t) {
            Log.w(TAG, "setRefreshModeByView", t);
        }
    }

    /** Refresco completo con parpadeo para borrar los fantasmas de la tinta. */
    public static void cleanRefresh(Context c) {
        try {
            Object m = manager(c);
            m.getClass().getMethod("forceGlobalRefresh", int.class).invoke(m, CLEAN);
            Log.i(TAG, "forceGlobalRefresh ok");
        } catch (Throwable t) {
            Log.w(TAG, "forceGlobalRefresh", t);
        }
    }
}
