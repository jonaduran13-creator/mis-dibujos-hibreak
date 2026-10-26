package mx.dibujos.kiosko;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Respuestas táctiles cortas; nunca vibra durante el trazo ni al mantener una tecla. */
final class Feel {
    private static Vibrator motor;
    private static SharedPreferences prefs;
    private static boolean active;
    private static long last;
    private static final AudioAttributes TOUCH=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build();
    static void resume(Context c){
        if(prefs==null){prefs=c.getApplicationContext().getSharedPreferences("kiosko",Context.MODE_PRIVATE);motor=c.getSystemService(Vibrator.class);}
        active=true;
    }
    static void pause(){active=false;if(motor!=null)motor.cancel();}
    static void tick(){play(Sounds.TAP);}
    static void play(int action){
        if(!active||prefs==null||!prefs.getBoolean("haptics",true)||motor==null||!motor.hasVibrator())return;
        long now=SystemClock.uptimeMillis();
        if(action!=Sounds.WIN&&last!=0&&now-last<(action==Sounds.WALL?220:85))return;
        last=now;
        try{
            boolean amplitude=motor.hasAmplitudeControl();int power=amplitude?45:VibrationEffect.DEFAULT_AMPLITUDE;
            if(action==Sounds.WIN)motor.vibrate(VibrationEffect.createWaveform(new long[]{0,12,65,18},new int[]{0,power,0,power},-1),TOUCH);
            else motor.vibrate(VibrationEffect.createOneShot(action==Sounds.SNAP||action==Sounds.SHUTTER?12:amplitude?9:6,power),TOUCH);
        }catch(RuntimeException ignored){}
    }
}
