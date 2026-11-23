package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Offline Islamic calculation engine & lightweight Canvas views for StradaOS:
 * - Astronomical prayer times (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) from GPS coordinates
 * - Offline Hijri calendar with moon-sighting adjustment & Ramadan / Jumu'ah / Eid detection
 * - Live Kaaba (Qibla) bearing, distance, and car-relative direction
 * - Travel prayer (Safar: Qasr / Jam' > 80 km) & Du'a al-Safar helpers
 * - Mihrab arch background view & Qibla compass view (pure Android Canvas, API 16+, zero lambdas)
 */
public class Prayer {

    public static final double KAABA_LAT = 21.422487;
    public static final double KAABA_LON = 39.826206;
    public static final double SAFAR_KM = 80.0;

    public static final String[] PRAYER_KEYS = {"Fajr", "Amanecer", "Dhuhr", "Asr", "Maghrib", "Isha"};

    public static final String[] METHOD_NAMES = {
        "Muslim World League (MWL)",
        "ISNA (North America)",
        "Egyptian General Authority",
        "Umm al-Qura (Makkah)",
        "Karachi (Univ. of Islamic Sciences)",
        "Gulf / UAE / Qatar",
        "Tehran"
    };

    // Fajr angle, Isha angle (or negative = fixed minutes after Maghrib), Maghrib angle (0 = sunset)
    private static final double[][] METHOD_PARAMS = {
        {18.0, 17.0, 0.0},   // 0 MWL
        {15.0, 15.0, 0.0},   // 1 ISNA
        {19.5, 17.5, 0.0},   // 2 Egyptian
        {18.5, -90.0, 0.0},  // 3 Umm al-Qura (90 min after Maghrib)
        {18.0, 18.0, 0.0},   // 4 Karachi
        {19.5, -90.0, 0.0},  // 5 Gulf
        {17.7, 14.0, 4.5}    // 6 Tehran
    };

    public static final String[] HIJRI_MONTHS = {
        "Muharram", "Safar", "Rabi' I", "Rabi' II",
        "Jumada I", "Jumada II", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    };

    public static final String[] HIJRI_MONTHS_AR = {
        "محرم", "صفر", "ربيع الأول", "ربيع الثاني",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    };

    public static final String DUA_SAFAR_AR =
        "اللَّهُ أَكْبَرُ، اللَّهُ أَكْبَرُ، اللَّهُ أَكْبَرُ\n"
        + "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ\n"
        + "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى، وَمِنَ الْعَمَلِ مَا تَرْضَى، "
        + "اللَّهُمَّ هَوِّنْ عَلَيْنَا سَفَرَنَا هَذَا وَاطْوِ عَنَّا بُعْدَهُ، اللَّهُمَّ أَنْتَ الصَّاحِبُ فِي السَّفَرِ وَالْخَلِيفَةُ فِي الْأَهْلِ";

    public static final String DUA_SAFAR_TRANSLIT =
        "Allahu Akbar (x3). Subhanalladhi sakh-khara lana hadha wa ma kunna lahu muqrinin, wa inna ila Rabbina lamunqalibun. "
        + "Allahumma inna nas'aluka fi safarina hadhal-birra wat-taqwa, wa minal-'amali ma tarda. "
        + "Allahumma hawwin 'alayna safarana hadha watwi 'anna bu'dah. Allahumma Antas-sahibu fis-safari wal-khalifatu fil-ahl.";

    public static final String DUA_SAFAR_EN =
        "Allah is the Greatest (x3). Glory be to Him who has subjected this to us, and we could never have it (by our efforts), "
        + "and surely to our Lord we are returning. O Allah, we ask You on this journey of ours for righteousness, piety, and deeds that please You. "
        + "O Allah, ease this journey for us and shorten its distance. O Allah, You are the Companion on the journey and the Guardian over the family.";

    public static class DayTimes {
        public int[] mins = new int[6]; // minutes from midnight [Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha]
        public boolean valid;
        public boolean highLat;
    }

    public static class HijriDate {
        public int day, month, year; // month 1..12

        public String monthName() {
            if (month < 1 || month > 12) return "";
            if ("ar".equals(I18n.lang)) return HIJRI_MONTHS_AR[month - 1];
            return HIJRI_MONTHS[month - 1];
        }

        public String formatShort() {
            return day + " " + monthName() + " " + year;
        }

        public boolean isRamadan() { return month == 9; }
        public boolean isEidFitr() { return month == 10 && day >= 1 && day <= 3; }
        public boolean isEidAdha() { return month == 12 && day >= 10 && day <= 13; }
        public boolean isArafah() { return month == 12 && day == 9; }
        public boolean isAshura() { return month == 1 && day == 10; }
        public boolean isWhiteDay() { return day >= 13 && day <= 15; }
    }

    public static class NextInfo {
        public int idx;           // 0..5 (skips sunrise? No, countdown skips Sunrise=1 for prayer countdown unless sunrise is next; we count down to obligatory prayers 0,2,3,4,5)
        public boolean tomorrow;
        public long remSec;
        public int targetMin;
        public boolean isIftar;   // Ramadan + target is Maghrib
        public boolean isSuhoor;  // Ramadan + target is Fajr
    }

    // ───────────────────────── preference helpers ─────────────────────────

    public static boolean isEnabled(SharedPreferences p) {
        return Faith.isEnabled(p);
    }

    /** "both", "top", "card", "off" */
    public static String countdownMode(SharedPreferences p) {
        return Faith.countdownMode(p);
    }

    public static boolean showInTop(SharedPreferences p) {
        return Faith.showInTop(p);
    }

    public static boolean showInCard(SharedPreferences p) {
        return Faith.showInCard(p);
    }

    // ───────────────────────── coordinate storage ─────────────────────────

    public static double[] savedCoord(SharedPreferences p) {
        if (p == null) return null;
        try {
            String la = p.getString("prayer_lat", null);
            String lo = p.getString("prayer_lon", null);
            if (la == null || lo == null) {
                la = p.getString("sun_lat", null);
                lo = p.getString("sun_lon", null);
            }
            if (la != null && lo != null) {
                double lat = Double.parseDouble(la);
                double lon = Double.parseDouble(lo);
                if (!Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 90 && (Math.abs(lat) > 0.001 || Math.abs(lon) > 0.001)) {
                    return new double[]{lat, lon};
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    public static void saveCoord(SharedPreferences p, double lat, double lon, float bearing, boolean hasBearing) {
        if (p == null || Double.isNaN(lat) || Double.isNaN(lon)) return;
        if (Math.abs(lat) > 90 || (Math.abs(lat) < 0.001 && Math.abs(lon) < 0.001)) return;
        SharedPreferences.Editor ed = p.edit();
        boolean writeCoord = true;
        try {
            String oldLa = p.getString("prayer_lat", null), oldLo = p.getString("prayer_lon", null);
            if (oldLa != null && oldLo != null) {
                if (Math.abs(Double.parseDouble(oldLa) - lat) < 0.01 && Math.abs(Double.parseDouble(oldLo) - lon) < 0.01) {
                    writeCoord = false;
                }
            }
        } catch (Throwable ignored) { }
        if (writeCoord) {
            ed.putString("prayer_lat", Double.toString(lat));
            ed.putString("prayer_lon", Double.toString(lon));
        }
        if (hasBearing && !Float.isNaN(bearing)) {
            float prev = p.getFloat("last_heading_deg", -1f);
            if (prev < 0 || Math.abs(prev - bearing) >= 2f) {
                ed.putFloat("last_heading_deg", ((bearing % 360f) + 360f) % 360f);
            }
        }
        if (writeCoord || hasBearing) ed.apply();
    }

    // ───────────────────────── offline astronomical prayer times ─────────────────────────

    private static DayTimes cachedTimes;
    private static String cachedKey;

    public static DayTimes today(SharedPreferences p, double lat, double lon) {
        Calendar cal = Calendar.getInstance();
        int tzMin = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 60000;
        int method = p != null ? p.getInt("prayer_method", 0) : 0;
        boolean hanafi = p != null && p.getBoolean("prayer_hanafi", false);
        int fajrAdj = p != null ? p.getInt("prayer_adj_fajr", 0) : 0;
        String key = cal.get(Calendar.YEAR) + ":" + cal.get(Calendar.DAY_OF_YEAR)
                + ":" + Math.round(lat * 100.0) + ":" + Math.round(lon * 100.0)
                + ":" + tzMin + ":" + method + ":" + (hanafi ? 1 : 0) + ":" + fajrAdj;
        if (cachedTimes != null && key.equals(cachedKey)) return cachedTimes;
        DayTimes dt = compute(lat, lon, tzMin / 60.0, cal, method, hanafi);
        if (dt.valid && fajrAdj != 0) dt.mins[0] = clampMin(dt.mins[0] + fajrAdj);
        cachedTimes = dt;
        cachedKey = key;
        return dt;
    }

    public static DayTimes compute(double lat, double lon, double tzHours, Calendar cal, int method, boolean hanafi) {
        DayTimes out = new DayTimes();
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 89.0) return out;
        int mIdx = Math.max(0, Math.min(METHOD_PARAMS.length - 1, method));
        double[] prm = METHOD_PARAMS[mIdx];

        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH) + 1;
        int d = cal.get(Calendar.DAY_OF_MONTH);
        double jd = julianDate(y, m, d) - lon / (15.0 * 24.0);

        double[] sun = sunPos(jd + 0.5);
        double decl = sun[0];
        double eqt = sun[1];

        double dhuhrH = 12.0 + tzHours - lon / 15.0 - eqt;
        double riseH = sunAngleTime(jd + 0.5, lat, lon, tzHours, 0.833, true);
        double setH = sunAngleTime(jd + 0.5, lat, lon, tzHours, 0.833, false);

        if (Double.isNaN(riseH) || Double.isNaN(setH)) {
            // Polar fallback
            riseH = dhuhrH - 6.0;
            setH = dhuhrH + 6.0;
            out.highLat = true;
        }

        double fajrH = sunAngleTime(jd + 0.2, lat, lon, tzHours, prm[0], true);
        double asrH = asrTime(jd + 0.625, lat, lon, tzHours, hanafi ? 2.0 : 1.0);
        double maghribH = prm[2] > 0 ? sunAngleTime(jd + 0.75, lat, lon, tzHours, prm[2], false) : setH;
        double ishaH = prm[1] < 0 ? maghribH + (-prm[1] / 60.0) : sunAngleTime(jd + 0.85, lat, lon, tzHours, prm[1], false);

        // High-latitude safe fallback (1/7th night rule)
        double night = (riseH + 24.0 - setH);
        if (night < 0) night += 24.0;
        if (Double.isNaN(fajrH) || fajrH > riseH) {
            fajrH = riseH - Math.min(2.5, night / 7.0);
            out.highLat = true;
        }
        if (Double.isNaN(maghribH)) maghribH = setH;
        if (Double.isNaN(ishaH) || ishaH < maghribH) {
            ishaH = maghribH + Math.min(2.5, night / 7.0);
            out.highLat = true;
        }
        if (Double.isNaN(asrH)) asrH = (dhuhrH + setH) * 0.5;

        out.mins[0] = clampMin((int) Math.round(fajrH * 60.0));
        out.mins[1] = clampMin((int) Math.round(riseH * 60.0));
        out.mins[2] = clampMin((int) Math.round(dhuhrH * 60.0));
        out.mins[3] = clampMin((int) Math.round(asrH * 60.0));
        out.mins[4] = clampMin((int) Math.round(maghribH * 60.0));
        out.mins[5] = clampMin((int) Math.round(ishaH * 60.0));
        out.valid = true;
        return out;
    }

    private static int clampMin(int m) {
        return Math.max(0, Math.min(1439, m));
    }

    static double julianDate(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100;
        int b = 2 - a + (a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** Returns {declinationDeg, equationOfTimeHours} */
    static double[] sunPos(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = Math.toDegrees(Math.atan2(dcos(e) * dsin(l), dcos(l))) / 15.0;
        ra = fixHour(ra);
        double eqt = q / 15.0 - ra;
        double decl = Math.toDegrees(Math.asin(dsin(e) * dsin(l)));
        return new double[]{decl, eqt};
    }

    static double sunAngleTime(double jd, double lat, double lon, double tz, double angleDeg, boolean ccw) {
        double[] s = sunPos(jd);
        double decl = s[0], eqt = s[1];
        double noon = 12.0 + tz - lon / 15.0 - eqt;
        double num = -dsin(angleDeg) - dsin(decl) * dsin(lat);
        double den = dcos(decl) * dcos(lat);
        if (Math.abs(den) < 1e-9) return Double.NaN;
        double cosT = num / den;
        if (cosT < -1.0 || cosT > 1.0) return Double.NaN;
        double t = Math.toDegrees(Math.acos(cosT)) / 15.0;
        return noon + (ccw ? -t : t);
    }

    private static double asrTime(double jd, double lat, double lon, double tz, double factor) {
        double[] s = sunPos(jd);
        double decl = s[0], eqt = s[1];
        double noon = 12.0 + tz - lon / 15.0 - eqt;
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        double cosT = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        if (cosT < -1.0 || cosT > 1.0) return Double.NaN;
        return noon + Math.toDegrees(Math.acos(cosT)) / 15.0;
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double fixAngle(double a) { a %= 360.0; return a < 0 ? a + 360.0 : a; }
    private static double fixHour(double h) { h %= 24.0; return h < 0 ? h + 24.0 : h; }

    public static NextInfo nextPrayer(DayTimes dt, Calendar now, HijriDate hd) {
        if (dt == null || !dt.valid) return null;
        int nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        int[] order = {0, 2, 3, 4, 5}; // Fajr, Dhuhr, Asr, Maghrib, Isha
        NextInfo ni = new NextInfo();
        for (int idx : order) {
            int tSec = dt.mins[idx] * 60;
            if (tSec > nowSec) {
                ni.idx = idx;
                ni.tomorrow = false;
                ni.targetMin = dt.mins[idx];
                ni.remSec = tSec - nowSec;
                ni.isIftar = hd != null && hd.isRamadan() && idx == 4;
                ni.isSuhoor = hd != null && hd.isRamadan() && idx == 0;
                return ni;
            }
        }
        ni.idx = 0;
        ni.tomorrow = true;
        ni.targetMin = dt.mins[0];
        ni.remSec = (24 * 3600 - nowSec) + dt.mins[0] * 60L;
        ni.isSuhoor = hd != null && hd.isRamadan();
        return ni;
    }

    public static String formatCountdown(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
    }

    public static String formatTime(Context c, SharedPreferences p, int minutesFromMidnight) {
        String fmt = p != null ? p.getString("clock", "auto") : "auto";
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(c));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, (minutesFromMidnight / 60) % 24);
        cal.set(Calendar.MINUTE, minutesFromMidnight % 60);
        return I18n.sdf(h24 ? "HH:mm" : "h:mm a").format(cal.getTime());
    }

    // ───────────────────────── offline Hijri calendar (Kuwaiti algorithm) ─────────────────────────

    public static HijriDate hijriDate(Calendar gCal, int adjustDays) {
        Calendar c = (Calendar) gCal.clone();
        if (adjustDays != 0) c.add(Calendar.DAY_OF_MONTH, adjustDays);
        int day = c.get(Calendar.DAY_OF_MONTH);
        int month = c.get(Calendar.MONTH); // 0-based
        int year = c.get(Calendar.YEAR);

        int m = month + 1;
        int y = year;
        if (m < 3) { y -= 1; m += 12; }
        int a = (int) Math.floor(y / 100.0);
        int b = 2 - a + (int) Math.floor(a / 4.0);
        if (y < 1583) b = 0;
        if (y == 1582) {
            if (m > 10) b = -10;
            if (m == 10) { b = 0; if (day > 4) b = -10; }
        }
        double jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524;
        b = 0;
        if (jd > 2299160) {
            a = (int) Math.floor((jd - 1867216.25) / 36524.25);
            b = 1 + a - (int) Math.floor(a / 4.0);
        }
        double bb = jd + b + 1524;
        double cc = Math.floor((bb - 122.1) / 365.25);
        double dd = Math.floor(365.25 * cc);
        double ee = Math.floor((bb - dd) / 30.6001);
        day = (int) (bb - dd - Math.floor(30.6001 * ee));
        month = (int) (ee - 1);
        if (ee > 13) { cc += 1; month = (int) (ee - 13); }
        year = (int) (cc - 4716);

        double iyear = 10631.0 / 30.0;
        double epochastro = 1948084.0;
        double shift1 = 8.01 / 60.0;
        double z = jd - epochastro;
        double cyc = Math.floor(z / 10631.0);
        z = z - 10631.0 * cyc;
        double j = Math.floor((z - shift1) / iyear);
        int iy = (int) (30 * cyc + j);
        z = z - Math.floor(j * iyear + shift1);
        int im = (int) Math.floor((z + 28.5001) / 29.5);
        if (im == 13) im = 12;
        int id = (int) (z - Math.floor(29.5001 * im - 29));

        HijriDate hd = new HijriDate();
        hd.day = Math.max(1, Math.min(30, id));
        hd.month = Math.max(1, Math.min(12, im));
        hd.year = iy;
        return hd;
    }

    public static String occasionLabel(Calendar cal, HijriDate hd) {
        if (hd == null) return null;
        if (hd.isEidFitr()) return I18n.t("Eid al-Fitr");
        if (hd.isEidAdha()) return I18n.t("Eid al-Adha");
        if (hd.isArafah()) return I18n.t("Día de Arafah");
        if (hd.isAshura()) return I18n.t("Ashura");
        if (hd.isRamadan()) return I18n.t("Ramadán");
        if (cal != null && cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return I18n.t("Yumu'ah");
        if (hd.isWhiteDay()) return I18n.t("Días Blancos");
        return null;
    }

    // ───────────────────────── Qibla / Kaaba direction & distance ─────────────────────────

    public static double qiblaBearingDeg(double lat, double lon) {
        return bearingDeg(lat, lon, KAABA_LAT, KAABA_LON);
    }

    static double bearingDeg(double lat, double lon, double tLat, double tLon) {
        double phi1 = Math.toRadians(lat);
        double phi2 = Math.toRadians(tLat);
        double dLon = Math.toRadians(tLon - lon);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        return ((Math.toDegrees(Math.atan2(y, x)) % 360.0) + 360.0) % 360.0;
    }

    public static double qiblaDistKm(double lat, double lon) {
        return distKm(lat, lon, KAABA_LAT, KAABA_LON);
    }

    static double distKm(double lat, double lon, double tLat, double tLon) {
        float[] res = new float[1];
        Location.distanceBetween(lat, lon, tLat, tLon, res);
        return res[0] / 1000.0;
    }

    public static float carRelativeAngle(double qiblaDeg, float carHeadingDeg) {
        if (Float.isNaN(carHeadingDeg) || carHeadingDeg < 0) return (float) qiblaDeg;
        return (float) (((qiblaDeg - carHeadingDeg) % 360.0 + 360.0) % 360.0);
    }

    public static String compassText(double deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        int idx = (int) Math.round(((deg % 360.0) + 360.0) % 360.0 / 45.0) % 8;
        return I18n.t(dirs[idx]);
    }

    // ───────────────────────── Safar (travel prayer) & alerts ─────────────────────────

    public static boolean isSafar(Context c, SharedPreferences p, double curLat, double curLon) {
        if (p == null) return false;
        String mode = p.getString("safar_mode", "auto");
        if ("on".equals(mode)) return true;
        if ("off".equals(mode)) return false;
        if (Nav.active != null && Nav.active.totalDistM >= SAFAR_KM * 1000.0) return true;
        if (!Double.isNaN(curLat) && !Double.isNaN(curLon) && c != null) {
            Nav.Place home = Nav.savedPlace(c, "nav_home");
            if (home != null) {
                float[] res = new float[1];
                Location.distanceBetween(curLat, curLon, home.lat, home.lon, res);
                if (res[0] >= SAFAR_KM * 1000.0) return true;
            }
        }
        return false;
    }

    public static String prayerNote(int idx, boolean safar, boolean isFriday) {
        if (idx == 1) return "";
        if (safar) {
            if (idx == 2 || idx == 3 || idx == 5) return I18n.t("2 rak'as (Qasr)");
            if (idx == 4) return I18n.t("3 rak'as");
            return I18n.t("2 rak'as");
        }
        if (idx == 2 && isFriday) return I18n.t("Yumu'ah");
        return "";
    }

    public static void playPrayerAlert(final Context c, int alertMode, String spokenMsg) {
        if (c == null || alertMode <= 0) return;
        if (alertMode == 2 && spokenMsg != null && spokenMsg.length() > 0) {
            Nav.speakRaw(c, spokenMsg);
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                AudioManager am = null;
                AudioManager.OnAudioFocusChangeListener ls = new AudioManager.OnAudioFocusChangeListener() {
                    public void onAudioFocusChange(int focusChange) { }
                };
                try {
                    am = (AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.requestAudioFocus(ls, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                    }
                    final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 320);
                    final AudioManager fam = am;
                    final AudioManager.OnAudioFocusChangeListener fls = ls;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        public void run() {
                            try { tg.release(); } catch (Throwable ignored) { }
                            if (fam != null) try { fam.abandonAudioFocus(fls); } catch (Throwable ignored) { }
                        }
                    }, 550);
                } catch (Throwable ignored) { }
            }
        }).start();
    }

    // ───────────────────────── custom Canvas views (Mihrab & Qibla) ─────────────────────────

    /**
     * Draws an Afnon Mihrab arch frame behind the prayer countdown card with zero bitmap allocations.
     */
    public static class MihrabView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arch = new Path();
        private final Path archIn = new Path();
        private int accentColor = 0xFFD4AF37;

        public MihrabView(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            inner.setStyle(Paint.Style.STROKE);
        }

        public void setColors(int accent, int bgFill) {
            accentColor = accent;
            fill.setColor(bgFill);
            stroke.setColor((accent & 0x00FFFFFF) | 0x88000000);
            inner.setColor((accent & 0x00FFFFFF) | 0x33000000);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float pad = Math.max(2f, w * 0.015f);
            float sw = Math.max(1.5f, w * 0.008f);
            stroke.setStrokeWidth(sw);
            inner.setStrokeWidth(Math.max(1f, sw * 0.65f));
            buildArch(arch, pad, pad, w - pad, h - pad);
            c.drawPath(arch, fill);
            c.drawPath(arch, stroke);
            float ins = pad + sw * 2.5f;
            if (w > ins * 4 && h > ins * 4) {
                buildArch(archIn, ins, ins, w - ins, h - ins);
                c.drawPath(archIn, inner);
            }
        }

        private static void buildArch(Path p, float l, float t, float r, float b) {
            p.reset();
            float w = r - l;
            float cx = (l + r) * 0.5f;
            float shoulderY = t + Math.min(b - t, w * 0.44f);
            float rBot = Math.min(14f, w * 0.06f);
            p.moveTo(l, b - rBot);
            p.lineTo(l, shoulderY);
            // Pointed Islamic Mihrab arch curve
            p.cubicTo(l, shoulderY - w * 0.22f, cx - w * 0.16f, t + w * 0.08f, cx, t);
            p.cubicTo(cx + w * 0.16f, t + w * 0.08f, r, shoulderY - w * 0.22f, r, shoulderY);
            p.lineTo(r, b - rBot);
            p.quadTo(r, b, r - rBot, b);
            p.lineTo(l + rBot, b);
            p.quadTo(l, b, l, b - rBot);
            p.close();
        }
    }

    /**
     * Lightweight Qibla compass view: shows direction relative to the front of the car (or North).
     */
    public static class QiblaCompassView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint kaaba = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint north = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path p = new Path();
        private float angleDeg = 0f;
        private int accent = 0xFFD4AF37;
        private int muted = 0xFF8B9099;
        private int bgColor = 0xFF23262E;

        public QiblaCompassView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            bg.setStyle(Paint.Style.FILL);
            arrow.setStyle(Paint.Style.FILL);
            kaaba.setStyle(Paint.Style.FILL);
            north.setTextAlign(Paint.Align.CENTER);
        }

        public void setState(float deg, int accentCol, int mutedCol, int bgCol) {
            angleDeg = deg;
            accent = accentCol;
            muted = mutedCol;
            bgColor = bgCol;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.5f, cy = h * 0.5f;
            float rad = Math.min(w, h) * 0.44f;

            bg.setColor(bgColor);
            c.drawCircle(cx, cy, rad, bg);

            ring.setColor((muted & 0x00FFFFFF) | 0x66000000);
            ring.setStrokeWidth(Math.max(1.5f, rad * 0.07f));
            c.drawCircle(cx, cy, rad, ring);

            // Front-of-car tick at 12 o'clock
            north.setColor(muted);
            north.setTextSize(Math.max(8f, rad * 0.34f));
            if (rad >= 24f) c.drawText("▲", cx, cy - rad * 0.62f, north);

            c.save();
            c.rotate(angleDeg, cx, cy);
            arrow.setColor(accent);
            p.reset();
            p.moveTo(cx, cy - rad * 0.72f);
            p.lineTo(cx + rad * 0.26f, cy + rad * 0.24f);
            p.lineTo(cx, cy + rad * 0.08f);
            p.lineTo(cx - rad * 0.26f, cy + rad * 0.24f);
            p.close();
            c.drawPath(p, arrow);

            // Small Kaaba square at arrow tip
            float ks = Math.max(3f, rad * 0.16f);
            kaaba.setColor(Color.WHITE);
            c.drawRect(cx - ks * 0.5f, cy - rad * 0.92f, cx + ks * 0.5f, cy - rad * 0.92f + ks, kaaba);
            c.restore();
        }
    }
}
