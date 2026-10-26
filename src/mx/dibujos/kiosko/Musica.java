package mx.dibujos.kiosko;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

/**
 * Dibujo musical: mientras el dedo dibuja, suena la nota de la altura en la que va
 * (arriba agudo, abajo grave). Solo notas de la escala pentatónica, así que cualquier
 * garabato suena bonito. Cada nota es un pulso corto tipo marimba, ya preparado en memoria.
 */
final class Musica {
    // Pentatónica de Do: de Do4 a Mi6 (12 notas), de grave a agudo.
    private static final float[] NOTES = {261.63f, 293.66f, 329.63f, 392f, 440f, 523.25f, 587.33f, 659.25f, 783.99f, 880f, 1046.5f, 1318.5f};
    private static final int RATE = 22050;
    private static final float VOLUME = 0.38f;
    private static final long MIN_GAP = 85; // ms entre notas: un trazo rápido no se vuelve ruido

    private static final AudioTrack[] tracks = new AudioTrack[NOTES.length];
    private static boolean ready;
    private static int lastNote = -1;
    private static long lastAt;

    private Musica() {}

    static void init() {
        if (ready) return;
        ready = true;
        new Thread(() -> {
            try {
                for (int i = 0; i < NOTES.length; i++) tracks[i] = make(pluck(NOTES[i]));
            } catch (Throwable t) {
                Log.w("MisDibujos", "música", t);
            }
        }, "musica").start();
    }

    /** Nota para una altura (0 = arriba) dentro de un lienzo de alto h. */
    static int noteFor(float y, float h) {
        if (h <= 0) return 0;
        int n = (int) ((1 - Math.max(0, Math.min(.999f, y / h))) * NOTES.length);
        return Math.max(0, Math.min(NOTES.length - 1, n));
    }

    /** Apoyar el dedo siempre suena; al moverse, solo al cambiar de nota. */
    static void touch(float y, float h, boolean start) {
        int n = noteFor(y, h);
        long now = SystemClock.uptimeMillis();
        if (!start && (n == lastNote || now - lastAt < MIN_GAP)) return;
        lastNote = n;
        lastAt = now;
        AudioTrack t = tracks[n];
        if (t == null) return;
        try {
            t.pause();
            t.setPlaybackHeadPosition(0);
            t.play();
        } catch (Throwable ignored) {
        }
    }

    private static short[] pluck(float f) {
        int n = (int) (RATE * .5f);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float attack = Math.min(1, t / .004f);
            float env = attack * (float) Math.exp(-t * 7);
            double s = Math.sin(2 * Math.PI * f * t) + .35 * Math.sin(4 * Math.PI * f * t) * Math.exp(-t * 14)
                    + .12 * Math.sin(6 * Math.PI * f * t) * Math.exp(-t * 20);
            out[i] = (short) (Math.tanh(s * .8) * env * VOLUME * 32767);
        }
        return out;
    }

    private static AudioTrack make(short[] pcm) {
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
        t.write(pcm, 0, pcm.length);
        return t;
    }
}
