package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/**
 * Unified lightweight devotional & Quran radio streamer for all Faith Companion modes.
 * Shares a single MediaPlayer & AudioFocus listener so zero extra resources are used.
 */
public class FaithRadio {

    public static class Station {
        public final String name, subtitle, url;
        public Station(String name, String subtitle, String url) {
            this.name = name;
            this.subtitle = subtitle;
            this.url = url;
        }
    }

    private static final Station[] CHRISTIAN_STATIONS = {
        new Station("Relevant Radio", "Catholic Talk & Prayer", "https://relevantradio.leanstream.co/relevantradio-aac"),
        new Station("Moody Radio", "Christian Teaching & Music", "https://moodyradio.streamguys1.com/wmbifm.aac"),
        new Station("Ancient Faith Radio", "Orthodox Hymns & Chants", "https://ancientfaith.streamguys1.com/music"),
        new Station("EWTN Radio", "Global Catholic Radio", "https://ewtn.streamguys1.com/ewtn-english")
    };

    private static final Station[] JEWISH_STATIONS = {
        new Station("JM in the AM", "Jewish Music & Torah", "https://stream.jewishmusicstream.com:8000/stream"),
        new Station("Kol Chai Music", "Israeli & Jewish Music", "https://media2.93fm.co.il/livemusic"),
        new Station("Radio Breslev", "Torah & Niggunim", "https://stream.radiojar.com/breslev")
    };

    private static final Station[] HINDU_STATIONS = {
        new Station("Radio Sai Global", "Bhajans & Devotional", "https://stream.radiosai.org:8002/"),
        new Station("ISKCON Desire Tree", "Hare Krishna Kirtan", "https://stream.iskcondesiretree.com/audio/kirtan"),
        new Station("Bhakti Radio", "Vedic Mantras & Aarti", "https://stream.zeno.fm/49f7104e84zuv")
    };

    private static final Station[] SIKH_STATIONS = {
        new Station("Harmandir Sahib Live", "Golden Temple · Amritsar", "https://live.sgpc.net:8443/;nocache=1"),
        new Station("SikhNet Radio", "Gurbani Kirtan · Chardi Kala", "https://radio.sikhnet.com/live"),
        new Station("Sukhmani Sahib Radio", "24/7 Gurbani & Simran", "https://stream.zeno.fm/r6a82d93d78uv")
    };

    public static Station[] stationsFor(String mode) {
        if (Faith.CHRISTIAN.equals(mode)) return CHRISTIAN_STATIONS;
        if (Faith.JUDAISM.equals(mode)) return JEWISH_STATIONS;
        if (Faith.HINDU.equals(mode)) return HINDU_STATIONS;
        if (Faith.SIKH.equals(mode)) return SIKH_STATIONS;
        Station[] out = new Station[QuranRadio.STATIONS.length];
        for (int i = 0; i < QuranRadio.STATIONS.length; i++) {
            out[i] = new Station(QuranRadio.STATIONS[i].name, QuranRadio.STATIONS[i].subtitle, QuranRadio.STATIONS[i].url);
        }
        return out;
    }

    public static String radioTitle(String mode) {
        if (Faith.CHRISTIAN.equals(mode)) return "Radio Cristiana";
        if (Faith.JUDAISM.equals(mode)) return "Radio Judía";
        if (Faith.HINDU.equals(mode)) return "Radio Devocional";
        if (Faith.SIKH.equals(mode)) return "Radio Gurbani";
        return "Radio Corán";
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static MediaPlayer mp;
    private static AudioManager am;
    private static boolean playing, buffering, active;
    private static int currentIdx;
    private static String activeMode = Faith.ISLAM;
    private static String lastError;
    private static QuranRadio.Listener listener;
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

    public static void setListener(QuranRadio.Listener l) { listener = l; }
    public static boolean isActive() { return active; }
    public static boolean isPlaying() { return playing; }
    public static boolean isBuffering() { return buffering; }
    public static String lastError() { return lastError; }
    public static String activeMode() { return activeMode; }

    public static int currentIndex(Context c, String mode) {
        Station[] list = stationsFor(mode);
        if (c != null) {
            int saved = c.getSharedPreferences("launcher", 0).getInt("faith_station_" + mode, 0);
            if (saved >= 0 && saved < list.length) return saved;
        }
        return Math.max(0, Math.min(list.length - 1, currentIdx));
    }

    public static Station currentStation(Context c) {
        String m = activeMode != null ? activeMode : Faith.ISLAM;
        Station[] list = stationsFor(m);
        int idx = currentIndex(c, m);
        return list[Math.max(0, Math.min(list.length - 1, idx))];
    }

    public static void play(Context c, String mode, int idx) {
        if (c == null) return;
        QuranRadio.stop();
        appCtx = c.getApplicationContext();
        activeMode = Faith.OFF.equals(mode) ? Faith.ISLAM : mode;
        Station[] list = stationsFor(activeMode);
        currentIdx = ((idx % list.length) + list.length) % list.length;
        appCtx.getSharedPreferences("launcher", 0).edit().putInt("faith_station_" + activeMode, currentIdx).apply();
        triedHttpFallback = false;
        startUrl(list[currentIdx].url);
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
        String m = activeMode != null ? activeMode : Faith.mode(c.getSharedPreferences("launcher", 0));
        if (!active) {
            play(c, m, currentIndex(c, m));
            return;
        }
        if (buffering) { stop(); return; }
        if (mp != null) {
            try {
                if (playing) { mp.pause(); playing = false; }
                else {
                    if (am != null) am.requestAudioFocus(FOCUS_LS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                    mp.start();
                    playing = true;
                }
                notifyChanged();
                return;
            } catch (Throwable ignored) { }
        }
        play(c, m, currentIndex(c, m));
    }

    public static void next(Context c) {
        String m = activeMode != null ? activeMode : Faith.ISLAM;
        play(c, m, currentIndex(c, m) + 1);
    }

    public static void prev(Context c) {
        String m = activeMode != null ? activeMode : Faith.ISLAM;
        play(c, m, currentIndex(c, m) - 1);
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
