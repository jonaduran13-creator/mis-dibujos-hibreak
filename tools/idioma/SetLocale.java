import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Cambia el idioma del sistema del HiBreak (como Ajustes → Idioma), corriendo como shell:
 *   adb push setlocale.dex /data/local/tmp/
 *   adb shell CLASSPATH=/data/local/tmp/setlocale.dex app_process / SetLocale es-MX
 * El usuario shell tiene CHANGE_CONFIGURATION; la app no.
 */
public class SetLocale {
    public static void main(String[] args) throws Exception {
        String tag = args.length > 0 ? args[0] : "es-MX";
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Configuration config = (Configuration) am.getClass().getMethod("getConfiguration").invoke(am);
        config.setLocales(new LocaleList(Locale.forLanguageTag(tag)));
        Configuration.class.getField("userSetLocale").setBoolean(config, true);
        am.getClass().getMethod("updatePersistentConfiguration", Configuration.class).invoke(am, config);
        System.out.println("Idioma del sistema: " + tag);
    }
}
