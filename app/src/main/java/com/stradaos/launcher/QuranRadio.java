package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/**
 * Built-in lightweight Quran Radio stream player (Phase 3 of Afnon, zero third-party SDKs).
 * Uses Android's MediaPlayer with async buffering, audio focus management, and HTTP fallback for Android 4.x.
 */
public class QuranRadio {

    public static class Station {
        public final String name, subtitle, url;
        public Station(String name, String subtitle, String url) {
            this.name = name;
            this.subtitle = subtitle;
            this.url = url;
        }
    }

    public static final Station[] STATIONS = {
        new Station("Cairo Quran Radio", "إذاعة القرآن الكريم · القاهرة", "https://stream.radiojar.com/8s5u5tpdtwzuv"),
        new Station("Makkah Live Quran", "المسجد الحرام · مكة", "https://backup.qurango.net/radio/makkah"),
        new Station("Madinah Live Quran", "المسجد النبوي · المدينة", "https://backup.qurango.net/radio/madinah"),
        new Station("Mishary Alafasy", "مشاري راشد العفاسي", "https://backup.qurango.net/radio/mishary"),
        new Station("Abdulbasit Abdulsamad", "عبد الباسط عبد الصمد", "https://backup.qurango.net/radio/abdulbasit_warsh"),
        new Station("Mahmoud Al-Husary", "محمود خليل الحصري", "https://backup.qurango.net/radio/mahmoud_khalil_alhussary"),
        new Station("Al-Minshawi", "محمد صديق المنشاوي", "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi"),
        new Station("Abdulrahman Al-Sudais", "عبد الرحمن السديس", "https://backup.qurango.net/radio/abdulrahman_alsudaes"),
        new Station("Maher Al-Muaiqly", "ماهر المعيقلي", "https://backup.qurango.net/radio/maher"),
        new Station("Saad Al-Ghamdi", "سعد الغامدي", "https://backup.qurango.net/radio/saad_alghamdi"),
        new Station("Yasser Al-Dosari", "ياسر الدوسري", "https://backup.qurango.net/radio/yasser_aldosari"),
        new Station("Nasser Al-Qatami", "ناصر القطامي", "https://backup.qurango.net/radio/nasser_alqatami"),
        new Station("Quran · English Translation", "Meanings of the Quran", "https://backup.qurango.net/radio/translation_quran_english"),
        new Station("Quran · Urdu Translation", "قرآن ترجمہ اردو", "https://backup.qurango.net/radio/translation_quran_urdu"),
        new Station("Quran · French Translation", "Traduction française", "https://backup.qurango.net/radio/translation_quran_french"),
        new Station("Quran · Spanish Translation", "Traducción al español", "https://backup.qurango.net/radio/translation_quran_spanish")
    };

    public interface Listener {
        void onStateChanged();
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static MediaPlayer mp;
    private static AudioManager am;
    private static boolean playing, buffering, active;
    private static int currentIdx;
    private static String lastError;
    private static Listener listener;
    private static Context appCtx;
    private static boolean triedHttpFallback;

    private static final AudioManager.OnAudioFocusChangeListener FOCUS_LS = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (mp == null) return;
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                stop();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                try { if (mp.isPlaying()) { mp.pause(); playing = false; notifyChanged(); } } catch (Throwable ignored) { }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                try { mp.setVolume(0.25f, 0.25f); } catch (Throwable ignored) { }
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                try {
                    mp.setVolume(1f, 1f);
                    if (active && !playing && !buffering) { mp.start(); playing = true; notifyChanged(); }
                } catch (Throwable ignored) { }
            }
        }
    };

    public static void setListener(Listener l) {
        listener = l;
    }

    public static boolean isActive() { return active; }
    public static boolean isPlaying() { return playing; }
    public static boolean isBuffering() { return buffering; }
    public static String lastError() { return lastError; }

    public static int currentIndex(Context c) {
        if (c != null) {
            SharedPreferences p = c.getSharedPreferences("launcher", 0);
            int saved = p.getInt("quran_station", 0);
            if (saved >= 0 && saved < STATIONS.length) currentIdx = saved;
        }
        return currentIdx;
    }

    public static Station currentStation(Context c) {
        int idx = currentIndex(c);
        if (c != null) {
            String custom = c.getSharedPreferences("launcher", 0).getString("quran_custom_url", "").trim();
            if (custom.length() > 0 && idx == -1) return new Station("Custom Quran Stream", custom, custom);
        }
        return STATIONS[Math.max(0, Math.min(STATIONS.length - 1, idx))];
    }

    public static void play(Context c, int idx) {
        if (c == null) return;
        appCtx = c.getApplicationContext();
        currentIdx = ((idx % STATIONS.length) + STATIONS.length) % STATIONS.length;
        appCtx.getSharedPreferences("launcher", 0).edit().putInt("quran_station", currentIdx).apply();
        triedHttpFallback = false;
        startUrl(STATIONS[currentIdx].url);
    }

    public static void playCustom(Context c, String url) {
        if (c == null || url == null || url.trim().length() == 0) return;
        appCtx = c.getApplicationContext();
        appCtx.getSharedPreferences("launcher", 0).edit().putString("quran_custom_url", url.trim()).apply();
        triedHttpFallback = false;
        startUrl(url.trim());
    }

    private static void startUrl(final String url) {
        releasePlayer();
        active = true;
        playing = false;
        buffering = true;
        lastError = null;
        notifyChanged();

        try {
            if (am == null && appCtx != null) am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.requestAudioFocus(FOCUS_LS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Throwable ignored) { }

        try {
            final MediaPlayer p = new MediaPlayer();
            mp = p;
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer player) {
                    if (mp != player) return;
                    buffering = false;
                    playing = true;
                    try { player.start(); } catch (Throwable ignored) { playing = false; }
                    notifyChanged();
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer player, int what, int extra) {
                    if (mp != player) return true;
                    if (!triedHttpFallback && url.startsWith("https://")) {
                        triedHttpFallback = true;
                        startUrl("http://" + url.substring(8));
                        return true;
                    }
                    buffering = false;
                    playing = false;
                    lastError = "Error " + what;
                    notifyChanged();
                    return true;
                }
            });
            p.setDataSource(url);
            p.prepareAsync();
        } catch (Throwable e) {
            buffering = false;
            playing = false;
            lastError = e.getMessage();
            notifyChanged();
        }
    }

    public static void toggle(Context c) {
        if (c == null) return;
        if (!active) {
            play(c, currentIndex(c));
            return;
        }
        if (buffering) {
            stop();
            return;
        }
        if (mp != null) {
            try {
                if (playing) {
                    mp.pause();
                    playing = false;
                } else {
                    if (am != null) am.requestAudioFocus(FOCUS_LS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                    mp.start();
                    playing = true;
                }
                notifyChanged();
                return;
            } catch (Throwable ignored) { }
        }
        play(c, currentIndex(c));
    }

    public static void next(Context c) {
        play(c, currentIndex(c) + 1);
    }

    public static void prev(Context c) {
        play(c, currentIndex(c) - 1);
    }

    public static void stop() {
        active = false;
        playing = false;
        buffering = false;
        releasePlayer();
        try { if (am != null) am.abandonAudioFocus(FOCUS_LS); } catch (Throwable ignored) { }
        notifyChanged();
    }

    private static void releasePlayer() {
        MediaPlayer old = mp;
        mp = null;
        if (old != null) {
            try { old.stop(); } catch (Throwable ignored) { }
            try { old.release(); } catch (Throwable ignored) { }
        }
    }

    private static void notifyChanged() {
        if (listener == null) return;
        UI.post(new Runnable() {
            public void run() {
                if (listener != null) listener.onStateChanged();
            }
        });
    }
}
