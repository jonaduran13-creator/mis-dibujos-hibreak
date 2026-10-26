package mx.dibujos.kiosko;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/** Cuadrícula de miniaturas de una carpeta de imágenes (dibujos o fotos). */
public class GalleryPanel extends LinearLayout {

    public interface Callback {
        void onPick(Uri uri);
        void onClose();
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LinearLayout list;
    private final String emptyText;
    private volatile boolean closed;
    private Executor io;
    private volatile int page;
    private static final int PAGE_SIZE=12;

    public GalleryPanel(Context c, String folder, String title, String emptyText, Executor io, Callback cb) {
        super(c);
        this.emptyText = emptyText;
        this.io=io;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.WHITE);
        setClickable(true);

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ToolButton back = new ToolButton(c, ToolButton.BACK);
        back.setOnClickListener(v -> cb.onClose());
        header.addView(back, new LayoutParams(dp(80), dp(64)));
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(Color.BLACK);
        t.setTextSize(26);
        t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setPadding(dp(12), 0, 0, 0);
        header.addView(t);
        addView(header, new LayoutParams(-1, dp(72)));

        View line = new View(c);
        line.setBackgroundColor(Color.BLACK);
        addView(line, new LayoutParams(-1, dp(2)));

        ScrollView scroll = new ScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        TextView loading=new TextView(c);loading.setText("Abriendo tu galería…");loading.setTextColor(Color.BLACK);loading.setTextSize(18);loading.setGravity(Gravity.CENTER);loading.setPadding(0,dp(24),0,dp(24));list.addView(loading);
        list.setPadding(dp(6), dp(6), dp(6), dp(6));
        scroll.addView(list);
        addView(scroll, new LayoutParams(-1, 0, 1f));

        io.execute(() -> {
            final ArrayList<Uri> uris = query(c, folder);
            ui.post(() -> fill(uris, cb));
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        closed = true;
        super.onDetachedFromWindow();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static ArrayList<Uri> query(Context c, String folder) {
        ArrayList<Uri> out = new ArrayList<>();
        String[] proj = {MediaStore.Images.Media._ID};
        String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                proj, sel, new String[]{folder + "%"}, MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
            while (cur != null && cur.moveToNext()) {
                out.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(cur.getLong(0))));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void fill(ArrayList<Uri> uris, Callback cb) {
        if (closed) return;
        list.removeAllViews();
        final int generation=page;
        if (uris.isEmpty()) {
            TextView t = new TextView(getContext());
            t.setText(emptyText);
            t.setTextColor(Color.BLACK);
            t.setTextSize(22);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(40), 0, 0);
            list.addView(t);
            return;
        }
        LinearLayout row = null;
        int first=page*PAGE_SIZE, end=Math.min(uris.size(),first+PAGE_SIZE);
        for (int i = first; i < end; i++) {
            if ((i-first) % 4 == 0) {
                row = new LinearLayout(getContext());
                list.addView(row, new LayoutParams(-1, dp(150)));
            }
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(Color.WHITE);
            frame.setStroke(dp(2), Color.BLACK);
            frame.setCornerRadius(dp(10));
            iv.setBackground(frame);
            iv.setPadding(dp(4), dp(4), dp(4), dp(4));
            final Uri u = uris.get(i);
            iv.setContentDescription("Abrir imagen "+(i+1));
            iv.setFocusable(true);
            io.execute(()->{
                if(closed || generation!=page)return;
                Bitmap thumb=null;
                try{thumb=getContext().getContentResolver().loadThumbnail(u,new Size(dp(220),dp(140)),null);}catch(Exception ignored){}
                final Bitmap result=thumb;
                ui.post(()->{if(!closed&&generation==page)iv.setImageBitmap(result);else if(result!=null)result.recycle();});
            });
            iv.setOnClickListener(v -> cb.onPick(u));
            LayoutParams lp = new LayoutParams(0, -1, 1f);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            row.addView(iv, lp);
        }
        int remainder=(end-first)%4;
        if(remainder>0)for(int j=remainder;j<4;j++)row.addView(new View(getContext()),new LayoutParams(0,-1,1));
        if(uris.size()>PAGE_SIZE){
            LinearLayout nav=new LinearLayout(getContext());
            android.widget.Button prev=new android.widget.Button(getContext());prev.setText("Anteriores");prev.setEnabled(page>0);
            Ui.action(prev);
            prev.setOnClickListener(v->{page--;fill(uris,cb);});nav.addView(prev,new LayoutParams(0,dp(52),1));
            TextView position=new TextView(getContext());position.setText((page+1)+" / "+((uris.size()+PAGE_SIZE-1)/PAGE_SIZE));position.setTextColor(Color.BLACK);position.setGravity(Gravity.CENTER);
            nav.addView(position,new LayoutParams(0,dp(52),1));
            android.widget.Button next=new android.widget.Button(getContext());next.setText("Más imágenes");next.setEnabled(end<uris.size());
            Ui.action(next);
            next.setOnClickListener(v->{page++;fill(uris,cb);});nav.addView(next,new LayoutParams(0,dp(52),1));list.addView(nav);
        }
    }
}
