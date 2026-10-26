package mx.dibujos.kiosko;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Teclado de una octava, dos dedos y cinco sonidos. La tinta solo cambia al tocar. */
final class PianoMode extends LinearLayout {
    private PianoAudio audio;
    private Keys keys;
    private final Activity activity;
    private android.media.AudioFocusRequest focus;
    private int soundId;

    PianoMode(Activity a, Runnable home) {
        super(a); activity = a;
        setOrientation(VERTICAL); setBackgroundColor(Color.WHITE); setClickable(true);
        int pad = dp(12); setPadding(pad, pad, pad, pad);
        LinearLayout header = new LinearLayout(a); header.setGravity(Gravity.CENTER_VERTICAL);
        ToolButton back = new ToolButton(a, ToolButton.HOME);
        back.setOnClickListener(v -> home.run());
        header.addView(back, new LayoutParams(dp(60), dp(56)));
        TextView title = text("Mi pianito", 26); title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(12), 0, 0, 0); header.addView(title,new LayoutParams(0,-2,1));
        addView(header);
        TextView hint = text("Elige un sonido y toca", 15); hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(8),0,0,0); header.addView(hint);
        header.addView(Linterna.button(a), new LayoutParams(dp(72), dp(56)));
        LinearLayout sounds = new LinearLayout(a);
        String[] names = {"Piano", "Campanas", "Órgano", "Flauta", "Cuerdas"};
        int[] icons={ToolButton.PIANO,ToolButton.BELL,ToolButton.ORGAN,ToolButton.FLUTE,ToolButton.STRINGS};
        for (int i=0;i<names.length;i++) {
            final int id=i;
            ToolButton b = new ToolButton(a,icons[i]);
            LayoutParams soundLayout=new LayoutParams(0,dp(56),1); if(i>0)soundLayout.leftMargin=dp(8);
            b.setStateListAnimator(null); sounds.addView(b,soundLayout);
            b.setOnClickListener(v -> {
                soundId=id;
                if (audio != null) audio.sound(id);
                keys.clearNotes();
                for(int j=0;j<sounds.getChildCount();j++) {
                    ((ToolButton)sounds.getChildAt(j)).setChosen(j==id);
                }
            });
        }
        addView(sounds);
        keys = new Keys(a);
        LayoutParams kp=new LayoutParams(-1,0,1); kp.topMargin=dp(10); kp.bottomMargin=dp(4);
        addView(keys,kp);

        sounds.getChildAt(0).performClick();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(Color.BLACK);return t;}
    void pause(){keys.clearNotes();if(audio!=null){audio.close();audio=null;} abandonFocus();}
    void resume(){
        if(audio!=null)return;
        android.media.AudioManager am=activity.getSystemService(android.media.AudioManager.class);
        focus=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME).build())
            .setOnAudioFocusChangeListener(change->{if(change<0) keys.clearNotes();}).build();
        if(am.requestAudioFocus(focus)==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { audio=new PianoAudio(); audio.sound(soundId); }
    }
    private void abandonFocus(){if(focus!=null){activity.getSystemService(android.media.AudioManager.class).abandonAudioFocusRequest(focus);focus=null;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();resume();}
    @Override protected void onDetachedFromWindow(){pause();super.onDetachedFromWindow();}
    private final class Keys extends View {
        private final int[] white={0,2,4,5,7,9,11,12}, black={1,3,6,8,10}, boundaries={1,2,4,5,6};
        private final boolean[] down=new boolean[13];
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Keys(Activity a){super(a);setContentDescription("Teclado de piano de Do a Do; admite dos dedos");setFocusable(true);}
        void clearNotes(){java.util.Arrays.fill(down,false);if(audio!=null)audio.silence();invalidate();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth()/8f,h=getHeight();
            for(int i=0;i<8;i++) {
                paint.setStyle(Paint.Style.FILL);paint.setColor(down[white[i]]?Color.BLACK:Color.WHITE);
                c.drawRoundRect(i*w+2,2,(i+1)*w-2,h-2,8,8,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.BLACK);paint.setStrokeWidth(dp(2));
                c.drawRoundRect(i*w+2,2,(i+1)*w-2,h-2,8,8,paint);
                paint.setStyle(Paint.Style.FILL);paint.setColor(down[white[i]]?Color.WHITE:Color.BLACK);
                paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(new String[]{"Do","Re","Mi","Fa","Sol","La","Si","Do"}[i],(i+.5f)*w,h-dp(24),paint);
            }
            for(int i=0;i<5;i++){
                float x=boundaries[i]*w;
                paint.setColor(Color.BLACK);c.drawRoundRect(x-w*.3f,0,x+w*.3f,h*.55f,7,7,paint);
                if(down[black[i]]){paint.setColor(Color.WHITE);c.drawCircle(x,h*.43f,dp(5),paint);}
            }
        }
        private int note(float x,float y){
            if(x<0||x>=getWidth()||y<0||y>=getHeight())return -1;
            float w=getWidth()/8f;
            if(y<getHeight()*.55f)for(int i=0;i<5;i++)if(Math.abs(x-boundaries[i]*w)<w*.3f)return black[i];
            return white[Math.min(7,(int)(x/w))];
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            boolean[] next=new boolean[13];int action=e.getActionMasked();
            if(action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL){
                for(int i=0;i<e.getPointerCount();i++){
                    if(action==MotionEvent.ACTION_POINTER_UP&&i==e.getActionIndex())continue;
                    int n=note(e.getX(i),e.getY(i));if(n>=0)next[n]=true;
                }
            }
            if(!java.util.Arrays.equals(down,next)){
                System.arraycopy(next,0,down,0,13);if(audio!=null)audio.notes(next);invalidate();
            }
            return true;
        }
    }
}
