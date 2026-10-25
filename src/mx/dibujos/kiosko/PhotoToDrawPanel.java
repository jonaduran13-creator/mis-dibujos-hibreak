package mx.dibujos.kiosko;

import android.app.Activity;
import android.graphics.*;
import android.view.Gravity;
import android.widget.*;
import java.util.concurrent.Executor;

/** Vista previa de la hoja, antes de empezar a dibujar. */
final class PhotoToDrawPanel extends LinearLayout {
    interface Callback {void onUse(Bitmap page);void onRetake();void onCancel();}
    private final Activity activity;
    private final Bitmap original;
    private final Executor io;
    private final int width,height;
    private final ImageView preview;
    private final TextView progress;
    private final ToolButton accept,outline,soft;
    private final Bitmap[] cache=new Bitmap[2];
    private volatile boolean closed;
    private int generation;
    PhotoToDrawPanel(Activity a,Bitmap photo,int w,int h,Executor executor,Callback callback){
        super(a);activity=a;original=photo;width=Math.max(1,w);height=Math.max(1,h);io=executor;
        setOrientation(VERTICAL);setBackgroundColor(Color.WHITE);setClickable(true);setPadding(dp(10),dp(6),dp(10),dp(6));
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        ToolButton back=new ToolButton(a,ToolButton.BACK);back.setOnClickListener(v->callback.onCancel());
        header.addView(back,new LayoutParams(dp(60),dp(56)));
        TextView title=new TextView(a);title.setText("Mi hoja para dibujar");title.setTextColor(Color.BLACK);title.setTextSize(24);title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(12),0,0,0);
        header.addView(title,new LayoutParams(0,-2,1));
        outline=new ToolButton(a,ToolButton.OUTLINE);soft=new ToolButton(a,ToolButton.LIGHTPHOTO);
        header.addView(outline,new LayoutParams(dp(96),dp(60)));header.addView(soft,new LayoutParams(dp(96),dp(60)));addView(header);
        FrameLayout paper=new FrameLayout(a);paper.setBackground(Ui.surface(Color.WHITE,dp(10),dp(1)));
        preview=new ImageView(a);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);paper.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        progress=new TextView(a);progress.setTextColor(Color.BLACK);progress.setTextSize(20);progress.setGravity(Gravity.CENTER);paper.addView(progress,new FrameLayout.LayoutParams(-1,-1));
        LayoutParams pp=new LayoutParams(-1,0,1);pp.topMargin=dp(6);pp.bottomMargin=dp(6);addView(paper,pp);
        LinearLayout actions=new LinearLayout(a);
        ToolButton retake=new ToolButton(a,ToolButton.RETAKE);retake.setOnClickListener(v->callback.onRetake());actions.addView(retake,new LayoutParams(dp(140),dp(60)));
        accept=new ToolButton(a,ToolButton.PENCIL);accept.setChosen(true);accept.setOnClickListener(v->{Bitmap page=(Bitmap)preview.getTag();if(page!=null)callback.onUse(page.copy(Bitmap.Config.ARGB_8888,false));});
        LayoutParams ap=new LayoutParams(0,dp(60),1);ap.leftMargin=dp(10);actions.addView(accept,ap);addView(actions);
        outline.setOnClickListener(v->choose(0));soft.setOnClickListener(v->choose(1));choose(0);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void choose(int mode){
        int version=++generation;outline.setChosen(mode==0);soft.setChosen(mode==1);accept.setEnabled(false);
        progress.setText("Preparando tu hoja…");preview.setImageDrawable(null);
        io.execute(()->{
            Bitmap page=cache[mode];if(page==null){page=mode==0?InkPhoto.convert(original,width,height):InkPhoto.soft(original,width,height);cache[mode]=page;}
            final Bitmap rendered=page;
            activity.runOnUiThread(()->{if(closed||version!=generation)return;preview.setImageBitmap(rendered);preview.setTag(rendered);progress.setText("");accept.setEnabled(true);
                Eink.apply(activity,activity.getWindow(),mode==0?Eink.DU:Eink.NORMAL);Eink.cleanRefresh(activity);});
        });
    }
    @Override protected void onDetachedFromWindow(){closed=true;super.onDetachedFromWindow();}
}
