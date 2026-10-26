package mx.dibujos.kiosko;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Sonidos suaves de campanita, sintetizados en la app (sin archivos ni licencias).
 * Todas las notas son de la escala pentatónica de Do, que siempre suena armoniosa,
 * con ataque suave y caída lenta, a volumen bajo.
 */
public final class Sounds {
    public static final int TAP = 0, PEN_S = 1, PEN_M = 2, PEN_L = 3, ERASER = 4, UNDO = 5,
            NEW_PAGE = 6, CAMERA = 7, SHUTTER = 8, SNAP = 9, GALLERY = 10, BACK = 11, OPEN = 12,
            BRUSH = 13, STAMP = 14, WALL = 15, WIN = 16;

    private static final int RATE = 22050;
    private static final float VOLUME = 0.45f; // campanitas suaves pero audibles en la bocina del HiBreak
    // Do5 Re5 Mi5 Sol5 La5 Do6 Mi6
    private static final float C5 = 523.25f, D5 = 587.33f, E5 = 659.25f, G5 = 783.99f, A5 = 880f, C6 = 1046.5f, E6 = 1318.5f;
    private static final float A4 = 440f, G4 = 392f;

    private static final AudioTrack[] tracks = new AudioTrack[17];

    private Sounds() {}

    /** Prepara todos los sonidos (unos milisegundos); llamar una vez al iniciar. */
    public static void init() {
        new Thread(() -> {
            try {
                tracks[TAP] = make(chord(new float[]{E5}, new float[]{0}, 0.35f));
                tracks[PEN_S] = make(chord(new float[]{A5}, new float[]{0}, 0.4f));
                tracks[PEN_M] = make(chord(new float[]{E5}, new float[]{0}, 0.45f));
                tracks[PEN_L] = make(chord(new float[]{C5}, new float[]{0}, 0.5f));
                tracks[ERASER] = make(soft(chord(new float[]{G4, D5}, new float[]{0, 0.05f}, 0.5f), 0.7f));
                tracks[UNDO] = make(chord(new float[]{G5, E5, C5}, new float[]{0, 0.09f, 0.18f}, 0.6f));
                tracks[NEW_PAGE] = make(chord(new float[]{C5, E5, G5, C6}, new float[]{0, 0.08f, 0.16f, 0.24f}, 0.9f));
                tracks[CAMERA] = make(chord(new float[]{D5, A5}, new float[]{0, 0.1f}, 0.6f));
                tracks[SHUTTER] = make(shutter());
                tracks[SNAP] = make(chord(new float[]{G5, C6, E6}, new float[]{0, 0.06f, 0.12f}, 0.8f));
                tracks[GALLERY] = make(chord(new float[]{C5, G5}, new float[]{0, 0.1f}, 0.6f));
                tracks[BACK] = make(chord(new float[]{G5, C5}, new float[]{0, 0.1f}, 0.6f));
                tracks[BRUSH] = make(chord(new float[]{A5, C6}, new float[]{0, 0.07f}, 0.5f));
                tracks[STAMP] = make(chord(new float[]{C6, G5}, new float[]{0, 0.05f}, 0.45f));
                tracks[WALL] = make(soft(chord(new float[]{G4}, new float[]{0}, 0.25f), 0.6f));
                tracks[WIN] = make(chord(new float[]{C5, E5, G5, C6, E6}, new float[]{0, 0.1f, 0.2f, 0.3f, 0.42f}, 1.4f));
                tracks[OPEN] = make(chord(new float[]{C5, E5, G5}, new float[]{0, 0.12f, 0.24f}, 1.0f));
            } catch (Throwable t) {
                Log.w("MisDibujos", "sonidos", t);
            }
        }, "sonidos").start();
    }

    public static void play(int id) {
        AudioTrack t = tracks[id];
        if (t == null) return;
        try {
            t.pause();
            t.setPlaybackHeadPosition(0);
            t.play();
        } catch (Throwable ignored) {
        }
    }

    /** Notas tipo campana (fundamental + armónicos suaves), cada una empezando en su momento. */
    private static float[] chord(float[] freqs, float[] starts, float seconds) {
        int n = (int) (RATE * seconds);
        float[] out = new float[n];
        for (int k = 0; k < freqs.length; k++) {
            int s0 = (int) (starts[k] * RATE);
            float f = freqs[k];
            for (int i = s0; i < n; i++) {
                float t = (i - s0) / (float) RATE;
                float attack = Math.min(1f, t / 0.008f);
                float env = attack * (float) Math.exp(-t * 6.5);
                double ph = 2 * Math.PI * f * t;
                out[i] += env * (float) (Math.sin(ph) + 0.25 * Math.sin(2 * ph) * Math.exp(-t * 10)
                        + 0.08 * Math.sin(3.01 * ph) * Math.exp(-t * 14));
            }
        }
        return out;
    }

    private static float[] soft(float[] a, float gain) {
        for (int i = 0; i < a.length; i++) a[i] *= gain;
        return a;
    }

    /** Clic muy suave (ruido filtrado de 20 ms) seguido de una campanita. */
    private static float[] shutter() {
        float[] bell = chord(new float[]{G5, C6}, new float[]{0.03f, 0.1f}, 0.7f);
        java.util.Random r = new java.util.Random(7);
        float lp = 0;
        int clickLen = (int) (RATE * 0.02f);
        for (int i = 0; i < clickLen; i++) {
            lp += 0.25f * ((r.nextFloat() * 2 - 1) - lp);
            bell[i] += lp * 1.2f * (1 - i / (float) clickLen);
        }
        return bell;
    }

    private static AudioTrack make(float[] samples) {
        float peak = 0;
        for (float s : samples) peak = Math.max(peak, Math.abs(s));
        float g = peak > 0 ? VOLUME / peak : 0;
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            // Desvanecido final para que no haga "clic" al terminar.
            float fade = Math.min(1f, (samples.length - i) / (RATE * 0.03f));
            pcm[i] = (short) (samples[i] * g * fade * 32767);
        }
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        t.write(pcm, 0, pcm.length);
        return t;
    }
}
