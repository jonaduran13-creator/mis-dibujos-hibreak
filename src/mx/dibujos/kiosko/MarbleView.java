package mx.dibujos.kiosko;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

/**
 * Canica que rueda por el laberinto inclinando el aparato (acelerómetro filtrado).
 * Se pinta en modo A2 de la tinta, el más rápido, y solo se repinta el cuadrito
 * alrededor de la canica; al salir se hace una limpieza completa.
 */
final class MarbleView extends View implements SensorEventListener {
    interface Callback {
        void onBump();
        void onGoal();
    }

    private static final long TICK = 40; // ~25 cuadros por segundo: lo que A2 alcanza a mostrar
    private final Maze maze;
    private final Callback cb;
    private final SensorManager sensors;
    private final Paint paint = new Paint();
    private final Rect dirty = new Rect();
    private float x, y, vx, vy, gx, gy;
    private final Tilt tilt=new Tilt();
    private long sensorTime;
    private int samples;
    private long last;
    private long lastBump;
    private boolean goal;

    MarbleView(Context c, Maze maze, Callback cb) {
        super(c);
        this.maze = maze;
        this.cb = cb;
        sensors = c.getSystemService(SensorManager.class);
        paint.setAntiAlias(false);
        setKeepScreenOn(true); // se juega inclinando, sin tocar: que no se duerma a medio juego
        x = maze.centerX(0);
        y = maze.centerY(0);
    }

    float radius() { return maze.cell * .27f; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // El sensor virtual GRAVITY existe en el HiBreak, pero devuelve ceros.
        Sensor s=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        boolean registered=s!=null&&sensors.registerListener(this,s,SensorManager.SENSOR_DELAY_GAME);
        setContentDescription(registered?"Canica: inclina suavemente para mover":"No se pudo activar el sensor de movimiento");
        android.util.Log.i("MisDibujosCanica","Acelerómetro activo="+registered);
        sensorTime=0;samples=0;
        last = SystemClock.uptimeMillis();
        post(step);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sensors != null) sensors.unregisterListener(this);
        removeCallbacks(step);
        super.onDetachedFromWindow();
    }

    /** Del sensor (ejes del aparato en vertical) a los ejes de la pantalla girada. */
    @Override
    public void onSensorChanged(SensorEvent e) {
        float seconds=sensorTime==0?.02f:(e.timestamp-sensorTime)/1_000_000_000f;
        sensorTime=e.timestamp;
        int rot=getDisplay()==null?Surface.ROTATION_90:getDisplay().getRotation();
        tilt.update(e.values[0],e.values[1],rot,seconds);
        gx=tilt.x();gy=tilt.y();
        if(++samples==1||samples==100||samples==250)
            android.util.Log.i("MisDibujosCanica","Inclinación="+gx+","+gy+" posición="+x+","+y);

    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(.08f, (now - last) / 1000f);
            last = now;
            // Inclinar poco no mueve (la mano tiembla); luego acelera según la inclinación.
            float k = maze.cell * 2.2f;
            float ax = Math.abs(gx) < .4f ? 0 : gx, ay = Math.abs(gy) < .4f ? 0 : gy;
            vx = (vx + ax * k * dt) * .985f;
            vy = (vy + ay * k * dt) * .985f;
            float max = maze.cell * 6;
            vx = Math.max(-max, Math.min(max, vx));
            vy = Math.max(-max, Math.min(max, vy));
            float ox = x, oy = y, r = radius();
            float[] m = maze.slide(x, y, x + vx * dt, y + vy * dt, r);
            float speed = (float) Math.hypot(vx, vy);
            if (m[2] > 0) vx *= -.25f;
            if (m[3] > 0) vy *= -.25f;
            if ((m[2] > 0 || m[3] > 0) && speed > maze.cell * 1.5f && now - lastBump > 250) { lastBump = now; cb.onBump(); }
            x = m[0];
            y = m[1];
            if (!goal && maze.col(x) == maze.cols - 1 && maze.row(y) == maze.rows - 1) { goal = true; cb.onGoal(); }
            if (Math.abs(x - ox) > .5f || Math.abs(y - oy) > .5f) {
                int pad = (int) (r + 3);
                dirty.set((int) Math.min(ox, x) - pad, (int) Math.min(oy, y) - pad, (int) Math.max(ox, x) + pad, (int) Math.max(oy, y) + pad);
                invalidate(dirty);
            }
            postDelayed(this, TICK);
        }
    };

    @Override
    protected void onDraw(Canvas c) {
        float r = radius();
        paint.setColor(Color.BLACK);
        c.drawCircle(x, y, r, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(x - r * .35f, y - r * .35f, r * .22f, paint); // brillo: se ve redonda
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) { return true; } // con la canica no se dibuja
}
