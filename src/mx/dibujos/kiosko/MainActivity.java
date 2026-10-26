package mx.dibujos.kiosko;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements Lienzo.Listener {

    private static final String FOLDER = "Pictures/Dibujos";
    private static final long AUTOSAVE_DELAY = 1500;
    private static final long PARENT_HOLD = 5000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final DrawingHistory history=new DrawingHistory(io);
    private ToolButton undoButton;
    private Lienzo draw;
    private DrawingProjects projects;
    private Bitmap pagePaper;
    private float selectedPenWidth;
    private int documentRequest;
    private FrameLayout root;
    private View gallery;
    private CameraPanel camera;
    private View startScreen;
    private PhotoMode photoMode;
    private PhotoToDrawPanel photoSheet;
    private Runnable cancelPhotoSheet;
    private int photoRequest;
    private View blackout;
    private boolean mandala;
    private boolean assisted=true;
    private ToolButton assistedButton;
    private ToolButton mandalaButton;
    private Bitmap beforeCamera;
    private boolean paintingPhoto;
    private PianoMode piano;
    private SleepLogo sleepLogo;
    private LinearLayout bar;
    private final ArrayList<ToolButton> penButtons = new ArrayList<>();
    private Uri currentUri;
    private SharedPreferences prefs;
    private long pausedUntil;

    private DevicePolicyManager dpm;
    private ComponentName admin;

    private final Runnable autosave = this::saveNow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("kiosko", MODE_PRIVATE);
        projects=new DrawingProjects(this);
        dpm = getSystemService(DevicePolicyManager.class);
        admin = AdminReceiver.component(this);
        Sounds.init();
        ShapeSnap.warmUp();
        handlePinExtra(getIntent());
        setupKiosk();
        mandala=prefs.getBoolean("mandala",false);
        assisted=prefs.getBoolean("assisted",true);
        buildUi();
        sleepLogo = new SleepLogo(this, prefs);
        sleepLogo.register();
        reopenLast();
        showStart();
        IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, screen);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(screenReceiver);
        if (sleepLogo != null) sleepLogo.close();
        ui.removeCallbacksAndMessages(null);
        if (piano != null) piano.pause();
        history.clear();
        io.shutdown();
        super.onDestroy();
    }

    // ---------- Botón de encendido: pantalla negra ----------

    /**
     * La tinta conserva la última imagen aunque el aparato se duerma. Al apagar con
     * el botón se pinta todo de negro, así se ve "apagado"; al prender se quita.
     */
    private final android.content.BroadcastReceiver screenReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { Linterna.get(c).set(false); showBlackout(); }
            else hideBlackout();
        }
    };

    private void showBlackout() {
        if (blackout != null) return;
        ui.removeCallbacks(autosave);
        saveNow();
        draw.setActive(false);
        ui.removeCallbacks(cleanCheck);
        draw.view().setVisibility(View.INVISIBLE);
        if (piano != null) piano.pause();
        if (photoMode != null) photoMode.pause();
        blackout = sleepView();
        blackout.setClickable(true);
        root.addView(blackout, new FrameLayout.LayoutParams(-1, -1));
        Eink.apply(this, getWindow(), Eink.NORMAL);
        blackout.postDelayed(() -> { if (blackout != null) { blackout.invalidate(); Eink.cleanRefresh(this); } }, 120);
    }

    private void hideBlackout() {
        if (blackout == null) return;
        root.removeView(blackout);
        blackout = null;
        draw.view().setVisibility(View.VISIBLE);
        if (piano != null) piano.resume();
        if (photoMode != null) photoMode.resume();
        if (!overlayOpen()) draw.setActive(true);
        Eink.apply(this, getWindow(), einkMode());
        Eink.cleanRefresh(this);
    }

    private View sleepView() {
        if (prefs.getBoolean("sleepBlack", false)) {
            View v = new View(this); v.setBackgroundColor(Color.BLACK); return v;
        }
        int index = Math.floorMod(prefs.getInt("wallpaper", 0), SleepLogo.IMAGES.length);
        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.WHITE);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageResource(SleepLogo.IMAGES[index]);
        image.setContentDescription("Fondo cristiano de descanso");
        return image;
    }

    private void openPiano() {
        if (piano != null) return;
        if (startScreen != null) { root.removeView(startScreen); startScreen = null; }
        draw.setActive(false);
        piano = new PianoMode(this, this::showStart);
        root.addView(piano, new FrameLayout.LayoutParams(-1,-1));
        Eink.apply(this, getWindow(), Eink.DU);
        Eink.cleanRefresh(this);
    }

    private void closePiano() {
        if (piano == null) return;
        piano.pause(); root.removeView(piano); piano = null;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handlePinExtra(i);
    }

    /**
     * Órdenes desde la PC (adb shell am start …): PIN inicial, nombre, pruebas.
     * La actividad es pública (es el inicio del aparato), así que solo se aceptan si las
     * lanza el shell de adb, nunca otra app.
     */
    private boolean fromAdb() {
        Uri referrer = getReferrer();
        return referrer != null && "com.android.shell".equals(referrer.getHost());
    }

    private void handlePinExtra(Intent i) {
        if (i == null || i.getExtras() == null) return;
        if (!fromAdb()) {
            android.util.Log.w("MisDibujos", "Órdenes ignoradas: no vienen de adb (" + getReferrer() + ")");
            return;
        }
        // Si la app quedó instalada en un aparato equivocado como administradora (no dueña),
        // Android no deja desinstalarla: esta orden le quita ese permiso para poder hacerlo.
        if (i.getBooleanExtra("quitarAdmin", false) && !isOwner() && dpm != null && dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin);
            android.util.Log.i("MisDibujos", "Permiso de administrador quitado; ya se puede desinstalar");
        }
        String childName = i.getStringExtra("childName");
        if (childName != null) {
            prefs.edit().putString("childName", childName.trim()).apply();
            if (startScreen != null) { root.removeView(startScreen); startScreen = null; showStart(); }
        }
        String pin = i.getStringExtra("setPin");
        if (pin != null && pin.matches("\\d{4,8}") && !prefs.contains("pin")) {
            prefs.edit().putString("pin", pin).apply();
        }
        if (i.hasExtra("einkMode")) setEinkMode(i.getIntExtra("einkMode", Eink.A2));
        if (i.hasExtra("motorNormal")) {
            prefs.edit().putBoolean("motorNormal", i.getBooleanExtra("motorNormal", false)).apply();
            recreate();
        }
        // Prueba desde la PC: el motor de Bigme no acepta toques simulados por adb.
        // testPath = x0,y0,x1,y1,… en pantalla; testHold = quedarse quieto al final (figura perfecta).
        if (i.hasExtra("testPath") && draw instanceof XrzLienzo) {
            final float[] s = i.getFloatArrayExtra("testPath");
            final boolean hold = i.getBooleanExtra("testHold", false);
            if (s != null && s.length >= 4) ui.postDelayed(() -> ((XrzLienzo) draw).testPath(s, hold), 800);
        }
        if (i.hasExtra("testScreen")) {
            if (i.getBooleanExtra("testScreen", false)) hideBlackout(); else showBlackout();
        }
    }

    // ---------- Pantalla de tinta ----------

    // El trazo lo pinta el motor de Bigme; este modo es para el resto de la ventana
    // (botones, fotos). DU es nítido y deja pocas sombras.
    private static final String[] MODE_NAMES = {"Nítido (DU), recomendado", "Ultra rápido (A2)", "Escritura de Bigme", "Rápido (video)"};
    private static final int[] MODES = {Eink.DU, Eink.A2, Eink.HANDWRITE, Eink.FAST};

    private int einkMode() {
        return prefs.getInt("einkMode2", Eink.DU);
    }

    private void setEinkMode(int mode) {
        prefs.edit().putInt("einkMode2", mode).apply();
        Eink.apply(this, getWindow(), mode);
        Eink.cleanRefresh(this);
    }

    private void chooseEinkMode() {
        int current = 0;
        for (int k = 0; k < MODES.length; k++) if (MODES[k] == einkMode()) current = k;
        new AlertDialog.Builder(this)
                .setTitle("Modo de pantalla")
                .setSingleChoiceItems(MODE_NAMES, current, (d, which) -> {
                    setEinkMode(MODES[which]);
                    d.dismiss();
                })
                .show();
    }

    // ---------- Modo kiosco ----------

    private boolean isOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(getPackageName());
    }

    private void setupKiosk() {
        if (!isOwner()) return;
        dpm.setLockTaskPackages(admin, new String[]{getPackageName()});
        // Solo dejamos el menú del botón de encendido para poder apagarlo.
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
        IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addCategory(Intent.CATEGORY_DEFAULT);
        dpm.addPersistentPreferredActivity(admin, home, new ComponentName(this, MainActivity.class));
        dpm.setKeyguardDisabled(admin, true);
        dpm.setStatusBarDisabled(admin, true);
        // Tras 1 minuto sin tocar la pantalla se duerme y muestra la imagen de descanso.
        // Como device owner se fija aquí para que sobreviva a reinicios y a cambios en Ajustes.
        try {
            dpm.setSystemSetting(admin, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, "60000");
        } catch (RuntimeException ignored) {
        }
        for (String perm : new String[]{
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA}) {
            dpm.setPermissionGrantState(admin, getPackageName(), perm,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        }
        for (String r : new String[]{
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_MODIFY_ACCOUNTS}) {
            dpm.addUserRestriction(admin, r);
        }
    }

    private void enterLockTask() {
        if (!isOwner() || System.currentTimeMillis() < pausedUntil) return;
        try {
            startLockTask();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        enterLockTask();
        Eink.apply(this, getWindow(), camera != null || photoMode != null ? Eink.FAST : einkMode());
        if (bar != null) Eink.applyView(this, bar, Eink.DU);
        if (camera != null) camera.resume();
        if (photoMode != null) photoMode.resume();
        if (draw != null) { draw.onResume(); draw.setActive(!overlayOpen()); }
        if (piano != null && blackout == null) piano.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(draw!=null)draw.setActive(false);
        if (camera != null) camera.pause();
        if (photoMode != null) photoMode.pause();
        if (piano != null) piano.pause();
        ui.removeCallbacks(autosave);
        saveNow();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onBackPressed() {
        if (blackout != null) return;
        if (camera != null) closeCamera();
        else if (photoSheet != null) { if(cancelPhotoSheet!=null)cancelPhotoSheet.run(); }
        else if (piano != null) showStart();
        else if (gallery != null) closeGallery();
        else if (photoMode != null) { if (!photoMode.back()) showStart(); }
        else if (startScreen == null) showStart();
    }

    private boolean overlayOpen() {
        return photoSheet != null || piano != null || camera != null || gallery != null || photoMode != null || startScreen != null || blackout != null;
    }

    // ---------- Pantalla de inicio: Dibujar o Fotos ----------

    private void showStart() {
        closePhotoSheet();
        if (startScreen != null) return;
        ui.removeCallbacks(autosave); saveNow(); closePhotoMode(); closePiano();
        draw.setActive(false);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL); screen.setBackgroundColor(Color.WHITE);
        screen.setClickable(true); screen.setPadding(dp(12),dp(8),dp(12),dp(10));
        TextView greeting = new TextView(this);
        greeting.setText(greetingText()); greeting.setTextColor(Color.BLACK); greeting.setTextSize(26);
        greeting.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout welcome=new LinearLayout(this);welcome.setOrientation(LinearLayout.HORIZONTAL);welcome.setGravity(Gravity.CENTER_VERTICAL);
        welcome.addView(greeting);attachParentHold(greeting);
        header.addView(welcome,new LinearLayout.LayoutParams(0,-2,1));
        TextView invitation = new TextView(this);
        invitation.setText("¿Qué vamos a crear hoy?"); invitation.setTextColor(Color.BLACK); invitation.setTextSize(18);
        invitation.setPadding(dp(24),0,0,dp(4));welcome.addView(invitation);
        header.addView(Linterna.button(this), new LinearLayout.LayoutParams(dp(72), dp(56)));
        header.addView(new BatteryView(this)); // el kiosco oculta la barra de estado: aquí se ve la batería
        screen.addView(header);
        int[] icons={ToolButton.PENCIL,ToolButton.CAMERA,ToolButton.PIANO};
        String[] names={"Dibujar","Fotos","Piano"};
        View.OnClickListener[] actions={v->{Sounds.play(Sounds.OPEN);hideStart();},
            v->{Sounds.play(Sounds.CAMERA);openPhotoMode();},v->openPiano()};
        LinearLayout choices=new LinearLayout(this);
        screen.addView(choices,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<3;i++) {
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
            if(i>0)lp.leftMargin=dp(12);
            choices.addView(startTile(icons[i],names[i],actions[i]),lp);
        }
        startScreen=screen;root.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        Eink.apply(this,getWindow(),Eink.DU);Eink.cleanRefresh(this);
    }

    private View startTile(int icon, String label, View.OnClickListener click) {
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);tile.setPadding(dp(8),dp(6),dp(8),dp(10));
        tile.setBackground(Ui.surface(Color.WHITE,dp(16),dp(2)));
        ImageView illustration=new ImageView(this);
        illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.setImageResource(icon==ToolButton.PENCIL?R.drawable.menu_dibujar:icon==ToolButton.CAMERA?R.drawable.menu_fotos:R.drawable.menu_piano);
        illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(illustration,new LinearLayout.LayoutParams(-1,0,1));
        TextView text=new TextView(this);text.setText(label);text.setTextSize(26);text.setTextColor(Color.BLACK);
        text.setTypeface(null,android.graphics.Typeface.BOLD);text.setGravity(Gravity.CENTER);
        tile.addView(text,new LinearLayout.LayoutParams(-1,-2));
        tile.setContentDescription(label);tile.setFocusable(true);tile.setOnClickListener(click);
        tile.setOnTouchListener((v,e)->{tile.setBackground(Ui.surface(e.getActionMasked()==MotionEvent.ACTION_DOWN?0xffeeeeee:Color.WHITE,dp(16),dp(2)));return false;});
        return tile;
    }

    private void hideStart() {
        if (startScreen == null) return;
        root.removeView(startScreen);
        startScreen = null;
        draw.setActive(true);
        Eink.apply(this, getWindow(), einkMode());
        Eink.cleanRefresh(this);
    }

    // ---------- Modo fotografía ----------

    private void openPhotoMode() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 2);
            return;
        }
        if (startScreen != null) {
            root.removeView(startScreen);
            startScreen = null;
        }
        photoMode = new PhotoMode(this, dp(64), io, new PhotoMode.Callback() {
            @Override
            public void onHome() { Sounds.play(Sounds.BACK); showStart(); }

            @Override
            public void onPaint(Bitmap photo) {
                Sounds.play(Sounds.OPEN);
                usePhoto(photo.copy(Bitmap.Config.ARGB_8888, false));
            }
        });
        root.addView(photoMode, new FrameLayout.LayoutParams(-1, -1));
        beginCameraOrientation();
    }

    private void closePhotoMode() {
        if (photoMode == null) return;
        photoMode.pause();
        root.removeView(photoMode);
        photoMode = null;
        endCameraOrientation();
    }

    // ---------- Interfaz ----------

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        draw = createLienzo();
        draw.setListener(this);
        col.addView(draw.view(), new LinearLayout.LayoutParams(-1, 0, 1f));

        View line = new View(this);
        line.setBackgroundColor(Color.BLACK);
        col.addView(line, new LinearLayout.LayoutParams(-1, dp(2)));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(6), dp(4), dp(6), dp(2));
        col.addView(bar, new LinearLayout.LayoutParams(-1, dp(68)));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(6),dp(2),dp(6),dp(6));
        bar.setVisibility(View.GONE);
        col.addView(actions,new LinearLayout.LayoutParams(-1,dp(64)));
        bar=actions;

        addTool(actions, ToolButton.HOME).setOnClickListener(v -> { Sounds.play(Sounds.BACK); showStart(); });
        final float[] widths = {dp(2), dp(5), dp(12)};
        final int[] penIcons = {ToolButton.PEN_S, ToolButton.PEN_M, ToolButton.PEN_L};
        final int[] penSounds = {Sounds.PEN_S, Sounds.PEN_M, Sounds.PEN_L};
        for (int i = 0; i < 3; i++) {
            final ToolButton t = addTool(actions, penIcons[i]);
            final float w = widths[i];
            final int snd = penSounds[i];
            t.setOnClickListener(v -> { Sounds.play(snd); draw.setPen(w); selectedPenWidth=w; erasing = false; choose(t); });
            penButtons.add(t);
        }
        final ToolButton eraser = addTool(actions, ToolButton.ERASER);
        eraser.setOnClickListener(v -> { Sounds.play(Sounds.ERASER); ensureEditableCanvas(); draw.setEraser(dp(24)); erasing = true; choose(eraser); });
        penButtons.add(eraser);

        undoButton=addTool(actions,ToolButton.UNDO);undoButton.setEnabled(false);
        undoButton.setOnClickListener(v->undoStroke());
        addTool(actions, ToolButton.NEW_PAGE).setOnClickListener(v -> { Sounds.play(Sounds.NEW_PAGE); newPage(); });
        addTool(actions, ToolButton.CAMERA).setOnClickListener(v -> { Sounds.play(Sounds.CAMERA); openCamera(); });
        ToolButton gal = addTool(actions, ToolButton.GALLERY);
        gal.setOnClickListener(v -> { Sounds.play(Sounds.GALLERY); openGallery(); });
        attachParentHold(gal);

        mandalaButton=addTool(actions,ToolButton.MANDALA);
        mandalaButton.setChosen(mandala);mandalaButton.setOnClickListener(v->toggleMandala());
        assistedButton=addTool(actions,assisted?ToolButton.ASSISTED:ToolButton.FREE);assistedButton.setChosen(assisted);
        assistedButton.setOnClickListener(v->{
            assisted=!assisted;prefs.edit().putBoolean("assisted",assisted).apply();draw.setShapeAssist(assisted);
            assistedButton.setIcon(assisted?ToolButton.ASSISTED:ToolButton.FREE);assistedButton.setChosen(assisted);
        });

        selectedPenWidth=widths[1];draw.setPen(selectedPenWidth);
        choose(penButtons.get(1));

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    /** El motor rápido de Bigme si el sistema lo trae; si no, el lienzo normal de Android. */
    private Lienzo createLienzo(){
        Lienzo canvas=createRawLienzo();canvas.setShapeAssist(assisted);return canvas;
    }

    private Lienzo createRawLienzo() {
        // Solo Mandala necesita el lienzo de Android (repite cada trazo 12 veces). Todo lo demás,
        // incluso reabrir, borrar y deshacer, va en el motor rápido de Bigme (ver XrzLienzo.loadEditable).
        if(mandala){
            DrawView canvas=new DrawView(this);canvas.setMandala(mandala);
            canvas.post(()->Eink.applyView(this,canvas,Eink.A2)); // el refresco más rápido para sus trazos
            return canvas;
        }
        if (!prefs.getBoolean("motorNormal", false)) {
            try {
                return new XrzLienzo(this);
            } catch (Throwable t) {
                android.util.Log.w("MisDibujos", "sin motor de Bigme, uso el normal", t);
            }
        }
        return new DrawView(this);
    }

    private void toggleMandala(){
        draw.setActive(false);ui.removeCallbacks(autosave);saveNow();
        Bitmap picture=draw.snapshot();View previous=draw.view();
        android.view.ViewGroup parent=(android.view.ViewGroup)previous.getParent();
        int index=parent.indexOfChild(previous);android.view.ViewGroup.LayoutParams lp=previous.getLayoutParams();
        parent.removeView(previous);mandala=!mandala;prefs.edit().putBoolean("mandala",mandala).apply();
        draw=createLienzo();draw.setListener(this);selectedPenWidth=dp(5);draw.setPen(selectedPenWidth);
        parent.addView(draw.view(),index,lp);if(picture!=null)draw.loadEditable(picture,pagePaper,false);
        choose(penButtons.get(1));erasing=false;mandalaButton.setChosen(mandala);
        draw.view().post(()->{draw.onResume();Eink.cleanRefresh(this);});
    }

    /**
     * Antes (1.7) esto cambiaba al lienzo lento de Android para poder borrar tinta guardada, y
     * ya no regresaba al motor rápido: dibujar se sentía muy lento. Desde 1.8 los dos lienzos
     * aceptan loadEditable (tinta borrable + papel protegido), así que no hace falta cambiar.
     */
    private void ensureEditableCanvas(){
    }

    private void restorePicture(Bitmap picture,boolean modified){
        ensureEditableCanvas();draw.loadEditable(picture,pagePaper,modified);
    }

    private void beginCameraOrientation(){
        if(beforeCamera==null)beforeCamera=draw.snapshot();
        draw.setActive(false);root.getChildAt(0).setVisibility(View.GONE);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    private void whenLandscape(Runnable action){
        if(root.getWidth()>root.getHeight()){root.post(action);return;}
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
            @Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                if(r-l>b-t){root.removeOnLayoutChangeListener(this);root.post(action);}
            }
        });
    }

    private void endCameraOrientation(){
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        final Bitmap picture=beforeCamera;beforeCamera=null;
        final boolean restore=!paintingPhoto;
        whenLandscape(()->{
            root.getChildAt(0).setVisibility(View.VISIBLE);
            draw.view().post(()->{if(picture!=null&&restore)restorePicture(picture,false);draw.onResume();draw.setActive(!overlayOpen());});
        });
    }

    private ToolButton addTool(LinearLayout bar, int icon) {
        ToolButton t = new ToolButton(this, icon);
        bar.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        return t;
    }

    private void choose(ToolButton t) {
        for (ToolButton b : penButtons) b.setChosen(b == t);
    }

    /** Mantener presionado el botón de galería 5 segundos abre el menú de papá/mamá. */
    private void attachParentHold(View v) {
        final boolean[] fired = {false};
        final Runnable open = () -> { fired[0] = true; askPin(); };
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fired[0] = false;
                    ui.postDelayed(open, PARENT_HOLD);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(open);
                    if (fired[0]) return true; // no abrir la galería después del menú
                    break;
            }
            return false;
        });
    }

    // ---------- Dibujo y guardado ----------

    @Override public void onStrokeStarted(){
        ui.removeCallbacks(autosave);history.push(draw.snapshot());
        if(undoButton!=null)undoButton.setEnabled(history.canUndo());
    }

    private void undoStroke(){
        ensureEditableCanvas();draw.setActive(false);Bitmap previous=history.pop();
        if(previous!=null){
            draw.loadEditable(previous,pagePaper,true);heavyChange=true;Sounds.play(Sounds.UNDO);onStrokeFinished();
        }
        draw.setActive(!overlayOpen());undoButton.setEnabled(history.canUndo());repaintBar();
        Eink.cleanRefresh(this);
    }

    private void clearHistory(){history.clear();if(undoButton!=null)undoButton.setEnabled(false);}

    @Override
    public void onStrokeFinished() {
        ui.removeCallbacks(autosave);
        ui.postDelayed(autosave, AUTOSAVE_DELAY);
        strokesSinceClean++;
        if (erasing) heavyChange = true;
        ui.removeCallbacks(cleanCheck);
        ui.postDelayed(cleanCheck, CLEAN_DELAY);
    }

    /**
     * Cuando el motor de Bigme repinta todo el lienzo (deshacer, figura perfecta),
     * la tinta a veces borra la barra de botones: se vuelve a pintar dos veces.
     */
    @Override
    public void onRedrawn() {
        ui.postDelayed(this::repaintBar, 300);
        ui.postDelayed(this::repaintBar, 900);
    }

    private void repaintBar() {
        if (bar == null) return;
        ((View) bar.getParent()).invalidate();
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).invalidate();
        bar.invalidate();
    }

    // ---------- Limpieza de sombras (ghosting) ----------

    private static final long CLEAN_DELAY = 3000;
    private static final int CLEAN_STROKES = 12;
    private int strokesSinceClean;
    private boolean heavyChange; // borrar y deshacer dejan más sombras
    private boolean erasing;

    /** Cuando el niño hace una pausa, un refresco completo borra las sombras de la tinta. */
    private final Runnable cleanCheck = new Runnable() {
        @Override
        public void run() {
            if (draw.isDrawing() || overlayOpen()) {
                return;
            }
            if (strokesSinceClean >= CLEAN_STROKES || heavyChange) cleanNow();
        }
    };

    private void cleanNow() {
        strokesSinceClean = 0;
        heavyChange = false;
        ui.removeCallbacks(cleanCheck);
        Eink.cleanRefresh(this);
    }

    private void newPage() {
        documentRequest++;
        ui.removeCallbacks(autosave);
        saveNow();
        setCurrent(null);
        clearHistory();pagePaper=null;
        draw.clear();
        cleanNow();
    }

    /** El dibujo abierto se recuerda para retomarlo tras reiniciar el aparato. */
    private void setCurrent(Uri u) {
        currentUri = u;
        if (u == null) prefs.edit().remove("last").apply();
        else prefs.edit().putString("last", u.toString()).apply();
    }

    private DrawingProjects.Page readDrawing(Uri uri){
        DrawingProjects.Page saved=projects.load(uri);
        // Los PNG antiguos se importan como tinta que puede borrarse sobre papel blanco.
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            Bitmap picture=BitmapFactory.decodeStream(stream);
            return picture==null?saved:new DrawingProjects.Page(picture,saved==null?null:saved.paper);
        }catch(Exception e){return saved;}
    }

    private void reopenLast() {
        final String last=prefs.getString("last",null);if(last==null)return;
        final int request=++documentRequest;
        io.execute(()->{
            Uri uri=Uri.parse(last);DrawingProjects.Page saved=readDrawing(uri);
            ui.post(()->{
                if(request!=documentRequest||currentUri!=null||draw.isDirty())return;
                if(saved==null){setCurrent(null);return;}
                setCurrent(uri);pagePaper=saved.paper;restorePicture(saved.picture,false);
            });
        });
    }

    private void saveNow() {
        if(draw==null||!draw.isDirty())return;
        final Bitmap bmp=draw.snapshot();if(bmp==null)return;
        if(currentUri==null)setCurrent(createEntry());
        final Uri target=currentUri;final Bitmap paper=pagePaper;
        if(target==null){bmp.recycle();return;}
        draw.markSaved();
        io.execute(()->{
            Uri stored=target;
            if(!write(target,bmp)){
                Uri fresh=createEntry();
                if(fresh!=null&&write(fresh,bmp)){
                    stored=fresh;ui.post(()->{if(target.equals(currentUri))setCurrent(fresh);});
                }else stored=null;
            }
            if(stored!=null){if(!projects.save(stored,bmp,paper))saveFailed(stored);}
            else saveFailed(target);
            bmp.recycle();
        });
    }

    private void saveFailed(Uri uri){
        ui.post(()->{
            if(uri.equals(currentUri))draw.markDirty();
            android.widget.Toast.makeText(this,"No se pudo guardar la edición completa. Conserva el dibujo abierto.",android.widget.Toast.LENGTH_LONG).show();
        });
    }

    private boolean write(Uri target, Bitmap bmp) {
        try (OutputStream os = getContentResolver().openOutputStream(target, "wt")) {
            return os != null && bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (Exception e) {
            return false;
        }
    }

    private Uri createEntry() {
        String name = "dibujo_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".png";
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER);
        try {
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Cámara ----------

    private void openCamera() {
        if (camera != null) return;
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1);
            return;
        }
        ui.removeCallbacks(autosave);
        saveNow();
        camera = new CameraPanel(this, dp(64), new CameraPanel.Callback() {
            @Override
            public void onPhoto(Bitmap photo) { usePhoto(photo); }

            @Override
            public void onClose() { closeCamera(); }
        });
        draw.setActive(false);
        root.addView(camera, new FrameLayout.LayoutParams(-1, -1));
        beginCameraOrientation();
        // La vista previa se mueve: el modo "video" de Bigme la muestra con grises tramados.
        Eink.apply(this, getWindow(), Eink.FAST);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length == 0 || results[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (code == 2) openPhotoMode(); else openCamera();
    }

    private void closePhotoSheet(){
        photoRequest++;
        if(photoSheet!=null){root.removeView(photoSheet);photoSheet=null;}
        cancelPhotoSheet=null;
    }

    private void usePhoto(Bitmap photo) {
        final Bitmap previous=beforeCamera!=null?beforeCamera:draw.snapshot();
        final int request=++photoRequest;
        paintingPhoto=true;closeCamera();closePhotoMode();
        whenLandscape(()->draw.view().post(()->{
            if(request!=photoRequest){if(previous!=null)restorePicture(previous,false);photo.recycle();paintingPhoto=false;return;}
            paintingPhoto=false;draw.setActive(false);
            cancelPhotoSheet=()->{
                closePhotoSheet();if(previous!=null)restorePicture(previous,false);draw.setActive(!overlayOpen());
                Eink.apply(this,getWindow(),einkMode());Eink.cleanRefresh(this);
            };
            photoSheet=new PhotoToDrawPanel(this,photo,draw.view().getWidth(),draw.view().getHeight(),io,new PhotoToDrawPanel.Callback(){
                public void onUse(Bitmap page){
                    closePhotoSheet();documentRequest++;setCurrent(null);clearHistory();pagePaper=page.copy(Bitmap.Config.ARGB_8888,false);restorePicture(page,true);draw.setActive(!overlayOpen());
                    Eink.apply(MainActivity.this,getWindow(),einkMode());onStrokeFinished();Eink.cleanRefresh(MainActivity.this);
                }
                public void onRetake(){closePhotoSheet();if(previous!=null)restorePicture(previous,false);openCamera();}
                public void onCancel(){if(cancelPhotoSheet!=null)cancelPhotoSheet.run();}
            });
            root.addView(photoSheet,new FrameLayout.LayoutParams(-1,-1));
        }));
    }

    private void closeCamera() {
        if (camera == null) return;
        root.removeView(camera);
        camera = null;
        endCameraOrientation();
        Eink.apply(this, getWindow(), einkMode());
        Eink.cleanRefresh(this);
    }

    // ---------- Galería ----------

    private void openGallery() {
        ui.removeCallbacks(autosave);
        saveNow();
        draw.setActive(false);
        gallery = new GalleryPanel(this, FOLDER, "Mis dibujos", "Todavía no hay dibujos", io,
                new GalleryPanel.Callback() {
                    @Override
                    public void onPick(Uri uri) { Sounds.play(Sounds.TAP); openDrawing(uri); }

                    @Override
                    public void onClose() { Sounds.play(Sounds.BACK); closeGallery(); }
                });
        root.addView(gallery, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openDrawing(Uri uri) {
        final int request=++documentRequest;
        io.execute(()->{
            DrawingProjects.Page saved=readDrawing(uri);
            ui.post(()->{
                if(request!=documentRequest||gallery==null)return;
                if(saved==null){android.widget.Toast.makeText(this,"No se pudo abrir este dibujo",android.widget.Toast.LENGTH_SHORT).show();return;}
                setCurrent(uri);pagePaper=saved.paper;clearHistory();restorePicture(saved.picture,false);closeGallery();
            });
        });
    }

    private void closeGallery() {
        if (gallery == null) return;
        documentRequest++;
        root.removeView(gallery);
        gallery = null;
        draw.setActive(true);
        Eink.cleanRefresh(this);
    }

    // ---------- Menú de papá/mamá ----------

    private void askPin() {
        final String saved = prefs.getString("pin", null);
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setHint(saved == null ? "Crea un PIN (4 a 8 números)" : "PIN");
        new AlertDialog.Builder(this)
                .setTitle("Solo papá o mamá")
                .setView(in)
                .setPositiveButton("Entrar", (d, w) -> {
                    String p = in.getText().toString();
                    if (saved == null) {
                        if (p.matches("\\d{4,8}")) {
                            prefs.edit().putString("pin", p).apply();
                            parentMenu();
                        }
                    } else if (saved.equals(p)) {
                        parentMenu();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void parentMenu() {
        String[] items = {"Modo de pantalla", "Salir 15 minutos", "Cambiar PIN", "Fondo de descanso", "Quitar el modo kiosco para siempre", "Nombre en el saludo"};
        new AlertDialog.Builder(this)
                .setTitle("Menú de papá/mamá")
                .setItems(items, (d, which) -> {
                    if (which == 0) chooseEinkMode();
                    else if (which == 1) pauseKiosk();
                    else if (which == 2) { prefs.edit().remove("pin").apply(); askPin(); }
                    else if (which == 3) new AlertDialog.Builder(this).setTitle("Fondo de descanso")
                        .setSingleChoiceItems(new String[]{"Escenas cristianas que se alternan", "Negro completo"}, prefs.getBoolean("sleepBlack",false)?1:0, (dialog,choice)->{prefs.edit().putBoolean("sleepBlack",choice==1).apply();dialog.dismiss();}).show();
                    else if (which == 4) confirmRemoveKiosk();
                    else askChildName();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /** "¡Hola!" o "Hola, Nombre": el nombre lo pone papá/mamá; no va en el código. */
    private String greetingText() {
        String name = prefs.getString("childName", "").trim();
        return name.isEmpty() ? "¡Hola!" : "Hola, " + name;
    }

    private void askChildName() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        in.setText(prefs.getString("childName", ""));
        new AlertDialog.Builder(this)
                .setTitle("Nombre en el saludo")
                .setView(in)
                .setPositiveButton("Guardar", (d, w) -> {
                    prefs.edit().putString("childName", in.getText().toString().trim()).apply();
                    if (startScreen != null) { root.removeView(startScreen); startScreen = null; showStart(); }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pauseKiosk() {
        pausedUntil = System.currentTimeMillis() + 15 * 60 * 1000;
        if (isOwner()) dpm.setStatusBarDisabled(admin, false);
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        ui.postDelayed(() -> {
            if (isOwner()) dpm.setStatusBarDisabled(admin, true);
        }, 15 * 60 * 1000);
        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void confirmRemoveKiosk() {
        new AlertDialog.Builder(this)
                .setTitle("¿Quitar el modo kiosco?")
                .setMessage("El aparato volverá a funcionar como un teléfono normal. Los dibujos no se borran.")
                .setPositiveButton("Quitar", (d, w) -> removeKiosk())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void removeKiosk() {
        if (!isOwner()) return;
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        dpm.clearPackagePersistentPreferredActivities(admin, getPackageName());
        dpm.setStatusBarDisabled(admin, false);
        dpm.setKeyguardDisabled(admin, false);
        dpm.setLockTaskPackages(admin, new String[0]);
        for (String r : new String[]{
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_MODIFY_ACCOUNTS}) {
            dpm.clearUserRestriction(admin, r);
        }
        dpm.clearDeviceOwnerApp(getPackageName());
        finish();
    }
}
