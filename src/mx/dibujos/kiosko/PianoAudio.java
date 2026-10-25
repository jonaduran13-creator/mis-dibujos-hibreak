package mx.dibujos.kiosko;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Sintetizador local: sin red, muestras externas ni retraso de carga por nota. */
final class PianoAudio implements AutoCloseable {
    private static final int RATE = 22050;
    private final boolean[] held = new boolean[13];
    private volatile boolean running = true;
    private volatile int sound;
    private final Thread worker;

    PianoAudio() {
        worker = new Thread(this::render, "pianito-audio");
        worker.start();
    }

    synchronized void notes(boolean[] notes) { System.arraycopy(notes, 0, held, 0, 13); }
    synchronized void silence() { java.util.Arrays.fill(held, false); }
    void sound(int value) { silence(); sound = value; }
    public void close() { silence(); running = false; }

    private void render() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        AudioTrack track = null;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(min, 1024)).setTransferMode(AudioTrack.MODE_STREAM).build();
            double[] phase = new double[13], age = new double[13], env = new double[13];
            boolean[] previous = new boolean[13], active = new boolean[13];
            short[] pcm = new short[256];
            track.play();
            while (running) {
                synchronized (this) { System.arraycopy(held, 0, active, 0, 13); }
                int timbre = sound;
                for (int n = 0; n < 13; n++) {
                    if (active[n] && !previous[n]) { age[n] = 0; phase[n] = 0; }
                    previous[n] = active[n];
                }
                for (int i = 0; i < pcm.length; i++) {
                    double sum = 0;
                    for (int n = 0; n < 13; n++) {
                        env[n] += ((active[n] ? 1 : 0) - env[n]) * (active[n] ? .014 : .0025);
                        if (env[n] < .0001) continue;
                        double p = phase[n];
                        double sample;
                        if (timbre == 1) sample = (Math.sin(p) + .32 * Math.sin(2.76*p) * Math.exp(-age[n]*3)) * Math.exp(-age[n]*2);
                        else if (timbre == 2) sample = .65*Math.sin(p) + .23*Math.sin(2*p) + .12*Math.sin(4*p);
                        else if(timbre==3) sample = (.88*Math.sin(p)+.1*Math.sin(2*p)+.025*Math.sin(3*p))*(1+.008*Math.sin(2*Math.PI*4.5*age[n]));
                        else if(timbre==4) sample = (.58*Math.sin(p)+.24*Math.sin(2*p)+.12*Math.sin(3*p)+.06*Math.sin(4*p))*(1+.02*Math.sin(2*Math.PI*5*age[n]));
                        else sample = (Math.sin(p) + .3*Math.sin(2*p)*Math.exp(-age[n]*3) + .12*Math.sin(3*p)) * Math.exp(-age[n]*.9);
                        sum += sample * env[n];
                        phase[n] = (p + 2*Math.PI*261.625565*Math.pow(2,n/12.0)/RATE) % (2*Math.PI);
                        age[n] += 1.0/RATE;
                    }
                    // El volumen del sistema ya está al máximo (15/15); antes una nota sonaba a ~20%
                    // (×0.16). Limitador suave (tanh): una nota llega a ~75% y dos dedos a ~95% sin
                    // recortarse en seco, así la bocina chica suena fuerte pero no áspera.
                    pcm[i] = (short)(Math.tanh(sum * .7) * 32767);
                }
                if (track.write(pcm, 0, pcm.length) < 0) break;
            }
        } catch (Exception e) { android.util.Log.w("Pianito", "Audio no disponible", e); }
        finally { if (track != null) { try { track.stop(); } catch (Exception ignored) {} track.release(); } }
    }
}
