package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Modular multi-religion companion engine for StradaOS (zero overhead when set to "off").
 * Supports: Off, Islam (Afnon), Christianity, Judaism, Hinduism, and Sikhism.
 * Reuses Prayer.java's offline astronomical solar & bearing math with no extra dependencies.
 */
public class Faith {

    public static final String OFF = "off";
    public static final String ISLAM = "islam";
    public static final String CHRISTIAN = "christianity";
    public static final String JUDAISM = "judaism";
    public static final String HINDU = "hinduism";
    public static final String SIKH = "sikhism";

    public static final String[] MODE_KEYS = {OFF, ISLAM, CHRISTIAN, JUDAISM, HINDU, SIKH};
    public static final String[] MODE_NAMES = {"Apagado", "Islam", "Cristianismo", "Judaísmo", "Hinduismo", "Sijismo"};

    public static final double JERUSALEM_LAT = 31.7767;
    public static final double JERUSALEM_LON = 35.2345;

    public static class Row {
        public final String label, note, time;
        public final boolean active;
        public Row(String label, String note, String time, boolean active) {
            this.label = label;
            this.note = note;
            this.time = time;
            this.active = active;
        }
    }

    public static class Snapshot {
        public String mode;
        public String badge;
        public String nextTitle;
        public String topShortTitle;
        public String countdown;
        public String calendarLine;
        public boolean hasCompass;
        public String compassTitle;
        public String compassTargetName;
        public double compassDeg;
        public double compassDistKm;
        public String worshipCat;
        public String worshipLabel;
        public String travelBtnLabel;
        public String radioBtnLabel;
        public String prayerTitle;
        public String prayerScript;
        public String prayerTranslit;
        public String prayerTranslation;
        public String footerNote;
        public final List<Row> rows = new ArrayList<Row>();
    }

    // ───────────────────────── mode & preferences ─────────────────────────

    public static String mode(SharedPreferences p) {
        if (p == null) return OFF;
        String m = p.getString("companion_mode", null);
        if (m == null) {
            m = p.getBoolean("islamic_on", false) ? ISLAM : OFF;
        }
        for (String k : MODE_KEYS) if (k.equals(m)) return m;
        return OFF;
    }

    public static boolean isEnabled(SharedPreferences p) {
        return !OFF.equals(mode(p));
    }

    public static String countdownMode(SharedPreferences p) {
        if (!isEnabled(p)) return "off";
        String m = p.getString("companion_countdown", null);
        if (m == null) m = p.getString("islamic_countdown", "both");
        return m;
    }

    public static boolean showInTop(SharedPreferences p) {
        String m = countdownMode(p);
        return "both".equals(m) || "top".equals(m);
    }

    public static boolean showInCard(SharedPreferences p) {
        String m = countdownMode(p);
        return "both".equals(m) || "card".equals(m);
    }

    public static String modeDisplayName(String m) {
        for (int i = 0; i < MODE_KEYS.length; i++) if (MODE_KEYS[i].equals(m)) return I18n.t(MODE_NAMES[i]);
        return I18n.t("Apagado");
    }

    public static String worshipCategory(SharedPreferences p) {
        String m = mode(p);
        if (ISLAM.equals(m)) return "masjid";
        if (CHRISTIAN.equals(m)) return "church";
        if (JUDAISM.equals(m)) return "synagogue";
        if (HINDU.equals(m)) return "temple";
        if (SIKH.equals(m)) return "gurdwara";
        return null;
    }

    public static String worshipShortLabel(SharedPreferences p) {
        String m = mode(p);
        if (ISLAM.equals(m)) return "Mezquita";
        if (CHRISTIAN.equals(m)) return "Iglesia";
        if (JUDAISM.equals(m)) return "Sinagoga";
        if (HINDU.equals(m)) return "Templo";
        if (SIKH.equals(m)) return "Gurdwara";
        return "Culto";
    }

    // ───────────────────────── unified snapshot builder ─────────────────────────

    public static Snapshot snapshot(Context c, SharedPreferences p, double lat, double lon) {
        String m = mode(p);
        Calendar now = Calendar.getInstance();
        boolean hasCoord = !Double.isNaN(lat) && !Double.isNaN(lon);
        if (ISLAM.equals(m)) return buildIslam(c, p, now, lat, lon, hasCoord);
        if (CHRISTIAN.equals(m)) return buildChristian(c, p, now, lat, lon, hasCoord);
        if (JUDAISM.equals(m)) return buildJudaism(c, p, now, lat, lon, hasCoord);
        if (HINDU.equals(m)) return buildHindu(c, p, now, lat, lon, hasCoord);
        if (SIKH.equals(m)) return buildSikh(c, p, now, lat, lon, hasCoord);
        return null;
    }

    // ── 1. Islam ──

    private static Snapshot buildIslam(Context c, SharedPreferences p, Calendar now, double lat, double lon, boolean hasCoord) {
        Snapshot s = new Snapshot();
        s.mode = ISLAM;
        s.worshipCat = "masjid";
        s.worshipLabel = "Mezquita cercana";
        s.travelBtnLabel = "Safar";
        s.radioBtnLabel = "Corán";
        s.prayerTitle = "Oración de viaje (Du'a al-Safar)";
        s.prayerScript = Prayer.DUA_SAFAR_AR;
        s.prayerTranslit = Prayer.DUA_SAFAR_TRANSLIT;
        s.prayerTranslation = Prayer.DUA_SAFAR_EN;
        s.hasCompass = hasCoord;
        s.compassTitle = "Dirección de la Qibla";
        s.compassTargetName = "Kaaba";

        Prayer.HijriDate hd = Prayer.hijriDate(now, p != null ? p.getInt("hijri_adj", 0) : 0);
        s.calendarLine = hd.formatShort();
        boolean showOcc = p == null || p.getBoolean("islamic_occasions", true);
        String occ = showOcc ? Prayer.occasionLabel(now, hd) : null;

        if (!hasCoord) {
            s.badge = occ != null ? occ : "Islam";
            s.nextTitle = I18n.t("Esperando señal GPS");
            s.topShortTitle = I18n.t("Rezo · GPS…");
            s.countdown = "--:--:--";
            return s;
        }

        Prayer.DayTimes dt = Prayer.today(p, lat, lon);
        Prayer.NextInfo ni = Prayer.nextPrayer(dt, now, hd);
        boolean safar = Prayer.isSafar(c, p, lat, lon);
        boolean isFriday = now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;

        String base = ni != null ? I18n.t(Prayer.PRAYER_KEYS[ni.idx]) : "";
        String full = base;
        if (ni != null && showOcc) {
            if (ni.isIftar) full = I18n.t("Iftar") + " · " + base;
            else if (ni.isSuhoor) full = I18n.t("Suhoor") + " · " + base;
        }
        s.nextTitle = full;
        s.topShortTitle = (ni != null && ni.isIftar && showOcc) ? I18n.t("Iftar") : base;
        s.countdown = ni != null ? Prayer.formatCountdown(ni.remSec) : "--:--:--";
        s.badge = safar ? (occ != null ? occ + " · " + I18n.t("Qasr") : I18n.t("Viaje (Qasr)"))
                : (occ != null ? occ : I18n.t("Islam"));
        s.compassDeg = Prayer.qiblaBearingDeg(lat, lon);
        s.compassDistKm = Prayer.qiblaDistKm(lat, lon);

        for (int i = 0; i < Prayer.PRAYER_KEYS.length; i++) {
            boolean isNext = ni != null && !ni.tomorrow && ni.idx == i;
            String pName = I18n.t(Prayer.PRAYER_KEYS[i]);
            if (hd.isRamadan() && i == 4) pName += " (" + I18n.t("Iftar") + ")";
            if (hd.isRamadan() && i == 0) pName += " (" + I18n.t("Suhoor") + ")";
            s.rows.add(new Row(pName, Prayer.prayerNote(i, safar, isFriday), Prayer.formatTime(c, p, dt.mins[i]), isNext));
        }
        if (safar) {
            s.footerNote = I18n.t("Safar activo (>80 km): Dhuhr + Asr y Maghrib + Isha se pueden combinar (Jam').");
        }
        return s;
    }

    // ── 2. Christianity ──

    private static Snapshot buildChristian(Context c, SharedPreferences p, Calendar now, double lat, double lon, boolean hasCoord) {
        Snapshot s = new Snapshot();
        s.mode = CHRISTIAN;
        s.worshipCat = "church";
        s.worshipLabel = "Iglesia cercana";
        s.travelBtnLabel = "Oración";
        s.radioBtnLabel = "Radio";
        s.prayerTitle = "Oración del conductor";
        s.prayerScript = "Psalm 121:8";
        s.prayerTranslit = "The Lord will watch over your coming and going both now and forevermore.";
        s.prayerTranslation = "Heavenly Father, grant me wisdom, alertness, and patience on the road. "
                + "Watch over every passenger and fellow traveler, and bring us safely to our destination. Amen.";

        String season = christianSeason(now);
        s.calendarLine = season;
        boolean sunday = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
        s.badge = sunday ? I18n.t("Culto dominical") : season;

        if (!hasCoord) {
            s.nextTitle = I18n.t("Esperando señal GPS");
            s.topShortTitle = I18n.t("Devoción · GPS…");
            s.countdown = "--:--:--";
            return s;
        }

        int[] sun = solarMinutes(now, lat, lon); // [sunrise, noon, sunset]
        int[] mins = {sun[0], sun[1], sun[2], clampM(sun[2] + 150)};
        String[] labels = {"Oración de la mañana", "Oración del mediodía", "Vísperas", "Completas"};
        int next = pickNext(mins, now);
        s.nextTitle = I18n.t(labels[next]);
        s.topShortTitle = s.nextTitle;
        s.countdown = Prayer.formatCountdown(remSeconds(mins[next], now));
        for (int i = 0; i < labels.length; i++) {
            s.rows.add(new Row(I18n.t(labels[i]), "", Prayer.formatTime(c, p, mins[i]), i == next));
        }
        return s;
    }

    private static String christianSeason(Calendar cal) {
        int y = cal.get(Calendar.YEAR), m = cal.get(Calendar.MONTH) + 1, d = cal.get(Calendar.DAY_OF_MONTH);
        if (m == 12 && d == 25) return I18n.t("Navidad");
        if ((m == 12 && d >= 25) || (m == 1 && d <= 5)) return I18n.t("Tiempo de Navidad");
        if (m == 1 && d == 6) return I18n.t("Epifanía");
        int[] easter = easterGregorian(y);
        double jdNow = Prayer.julianDate(y, m, d);
        double jdEaster = Prayer.julianDate(y, easter[0], easter[1]);
        int diff = (int) Math.round(jdNow - jdEaster);
        if (diff == 0) return I18n.t("Domingo de Pascua");
        if (diff == -2) return I18n.t("Viernes Santo");
        if (diff == -7) return I18n.t("Domingo de Ramos");
        if (diff >= -46 && diff < 0) return I18n.t("Cuaresma");
        if (diff > 0 && diff < 49) return I18n.t("Tiempo Pascual");
        if (diff == 49) return I18n.t("Pentecostés");
        if (m == 12 || (m == 11 && d >= 27)) return I18n.t("Adviento");
        return I18n.t("Tiempo Ordinario");
    }

    private static int[] easterGregorian(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return new int[]{month, day};
    }

    // ── 3. Judaism ──

    private static Snapshot buildJudaism(Context c, SharedPreferences p, Calendar now, double lat, double lon, boolean hasCoord) {
        Snapshot s = new Snapshot();
        s.mode = JUDAISM;
        s.worshipCat = "synagogue";
        s.worshipLabel = "Sinagoga cercana";
        s.travelBtnLabel = "Tefilá";
        s.radioBtnLabel = "Radio";
        s.prayerTitle = "Oración del viajero (Tefilat HaDerej)";
        s.prayerScript = "יְהִי רָצוֹן מִלְּפָנֶיךָ ה׳ אֱלֹהֵינוּ וֵאלֹהֵי אֲבוֹתֵינוּ שֶׁתּוֹלִיכֵנוּ לְשָׁלוֹם וְתַצְעִידֵנוּ לְשָׁלוֹם";
        s.prayerTranslit = "Yehi ratzon milfanecha Adonai Eloheinu v'Elohei avoteinu shetolicheinu l'shalom v'tatz'ideinu l'shalom, "
                + "v'tadricheinu l'shalom, v'tagi'einu limchoz cheftzeinu l'chayim ul'simcha ul'shalom.";
        s.prayerTranslation = "May it be Your will, Lord our God, to lead us in peace, guide our steps in peace, "
                + "and bring us to our destination in life, joy, and peace. Blessed are You, who hears prayer.";
        s.hasCompass = hasCoord;
        s.compassTitle = "Mizraj (Jerusalén)";
        s.compassTargetName = "Jerusalén";

        int[] heb = gregorianToHebrew(now); // [year, month(1=Nisan..12=Adar/13=AdarII), day, leapFlag]
        String hMonth = hebrewMonthName(heb[1], heb[3] == 1);
        s.calendarLine = heb[2] + " " + hMonth + " " + heb[0];
        String occ = hebrewOccasion(now, heb[1], heb[2]);
        s.badge = occ != null ? occ : I18n.t("Judaísmo");

        if (!hasCoord) {
            s.nextTitle = I18n.t("Esperando señal GPS");
            s.topShortTitle = I18n.t("Zmanim · GPS…");
            s.countdown = "--:--:--";
            return s;
        }

        s.compassDeg = Prayer.bearingDeg(lat, lon, JERUSALEM_LAT, JERUSALEM_LON);
        s.compassDistKm = Prayer.distKm(lat, lon, JERUSALEM_LAT, JERUSALEM_LON);

        int[] sun = solarMinutes(now, lat, lon); // [sunrise, noon, sunset]
        int shaah = Math.max(20, (sun[2] - sun[0]) / 12);
        int alot = clampM(sun[0] - 72);
        int netz = sun[0];
        int shema = clampM(netz + shaah * 3);
        int chatzot = sun[1];
        int mincha = clampM(chatzot + Math.max(30, shaah / 2));
        int plag = clampM(netz + (int) Math.round(shaah * 10.75));
        int shkiah = sun[2];
        int tzeit = clampM(shkiah + 42);

        int dow = now.get(Calendar.DAY_OF_WEEK);
        int candle = clampM(shkiah - 18);
        int[] mins = {alot, netz, shema, chatzot, mincha, plag, dow == Calendar.FRIDAY ? candle : shkiah, tzeit};
        String[] labels = {
            "Alot HaShachar", "Netz HaHamah (Sunrise)", "Sof Zman Shema", "Chatzot",
            "Mincha Gedolah", "Plag HaMincha",
            dow == Calendar.FRIDAY ? "Candle Lighting" : "Shkiah (Sunset)",
            dow == Calendar.SATURDAY ? "Havdalah (Tzeit)" : "Tzeit HaKochavim"
        };
        int next = pickNext(mins, now);
        s.nextTitle = I18n.t(labels[next]);
        s.topShortTitle = s.nextTitle;
        s.countdown = Prayer.formatCountdown(remSeconds(mins[next], now));
        for (int i = 0; i < labels.length; i++) {
            s.rows.add(new Row(I18n.t(labels[i]), "", Prayer.formatTime(c, p, mins[i]), i == next));
        }
        return s;
    }

    private static String hebrewOccasion(Calendar cal, int m, int d) {
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY) return I18n.t("Shabbat");
        if (dow == Calendar.FRIDAY) return I18n.t("Erev Shabbat");
        if (m == 7 && (d == 1 || d == 2)) return I18n.t("Rosh Hashanah");
        if (m == 7 && d == 10) return I18n.t("Yom Kippur");
        if (m == 7 && d >= 15 && d <= 21) return I18n.t("Sukkot");
        if ((m == 9 && d >= 25) || (m == 10 && d <= 2)) return I18n.t("Hanukkah");
        if (m == 12 && d == 14) return I18n.t("Purim");
        if (m == 1 && d >= 15 && d <= 22) return I18n.t("Passover");
        return null;
    }

    // ── 4. Hinduism ──

    private static Snapshot buildHindu(Context c, SharedPreferences p, Calendar now, double lat, double lon, boolean hasCoord) {
        Snapshot s = new Snapshot();
        s.mode = HINDU;
        s.worshipCat = "temple";
        s.worshipLabel = "Templo cercano";
        s.travelBtnLabel = "Yatra";
        s.radioBtnLabel = "Bhajans";
        s.prayerTitle = "Mantra de viaje seguro (Shubh Yatra)";
        s.prayerScript = "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात् ॥\nस्वस्ति पन्थामनुचरेम सूर्याचन्द्रमसाविव ।";
        s.prayerTranslit = "Om Bhur Bhuvah Svah, Tat Savitur Varenyam, Bhargo Devasya Dhimahi, Dhiyo Yo Nah Prachodayat. "
                + "Svasti pantham anucharema surya-chandramasaviva.";
        s.prayerTranslation = "May we meditate on the radiant glory of the Creator who illuminates our intellect. "
                + "May we travel safely along our path like the sun and moon in harmony.";

        String panchang = hinduLunarSummary(now);
        s.calendarLine = panchang;
        s.badge = panchang.contains("Ekadashi") ? I18n.t("Ekadashi")
                : panchang.contains("Purnima") ? I18n.t("Purnima")
                : panchang.contains("Amavasya") ? I18n.t("Amavasya")
                : I18n.t("Panchang");

        if (!hasCoord) {
            s.nextTitle = I18n.t("Esperando señal GPS");
            s.topShortTitle = I18n.t("Panchang · GPS…");
            s.countdown = "--:--:--";
            return s;
        }

        int[] sun = solarMinutes(now, lat, lon); // [sunrise, noon, sunset]
        int brahma = clampM(sun[0] - 96);
        int sunrise = sun[0];
        int abhijit = clampM(sun[1] - 24);
        int sunset = sun[2];

        // Rahu Kaal by weekday (1=Sun..7=Sat): 8th, 2nd, 7th, 5th, 6th, 4th, 3rd eighth of day
        int[] rahuSlot = {7, 1, 6, 4, 5, 3, 2};
        int dow = now.get(Calendar.DAY_OF_WEEK) - 1;
        int eighth = Math.max(30, (sunset - sunrise) / 8);
        int rahuStart = clampM(sunrise + eighth * rahuSlot[Math.max(0, Math.min(6, dow))]);
        int rahuEnd = clampM(rahuStart + eighth);

        int[] mins = {brahma, sunrise, abhijit, sunset};
        String[] labels = {"Brahma Muhurta", "Suryodaya (Sunrise)", "Abhijit Muhurta", "Sandhya (Sunset)"};
        int next = pickNext(mins, now);
        s.nextTitle = I18n.t(labels[next]);
        s.topShortTitle = s.nextTitle;
        s.countdown = Prayer.formatCountdown(remSeconds(mins[next], now));

        for (int i = 0; i < labels.length; i++) {
            s.rows.add(new Row(I18n.t(labels[i]), "", Prayer.formatTime(c, p, mins[i]), i == next));
        }
        s.rows.add(new Row(I18n.t("Rahu Kaal"), I18n.t("Daily window"),
                Prayer.formatTime(c, p, rahuStart) + " – " + Prayer.formatTime(c, p, rahuEnd), false));
        return s;
    }

    private static String hinduLunarSummary(Calendar cal) {
        double jd = Prayer.julianDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) + 0.5;
        double knownNewMoon = 2451550.1; // Jan 6, 2000 18:14 UT
        double synodic = 29.530588853;
        double age = ((jd - knownNewMoon) % synodic + synodic) % synodic;
        int tithi = Math.max(1, Math.min(30, (int) Math.floor(age / (synodic / 30.0)) + 1));
        boolean shukla = tithi <= 15;
        int dayInPaksha = shukla ? tithi : tithi - 15;
        String paksha = I18n.t(shukla ? "Shukla Paksha" : "Krishna Paksha");
        String special = tithi == 15 ? " · Purnima" : tithi == 30 ? " · Amavasya" : dayInPaksha == 11 ? " · Ekadashi" : "";
        return paksha + " · " + I18n.t("Tithi") + " " + dayInPaksha + special;
    }

    // ── 5. Sikhism ──

    private static Snapshot buildSikh(Context c, SharedPreferences p, Calendar now, double lat, double lon, boolean hasCoord) {
        Snapshot s = new Snapshot();
        s.mode = SIKH;
        s.worshipCat = "gurdwara";
        s.worshipLabel = "Gurdwara cercano";
        s.travelBtnLabel = "Ardas";
        s.radioBtnLabel = "Gurbani";
        s.prayerTitle = "Oración de viaje (Ardas Shabad)";
        s.prayerScript = "ਤੂ ਠਾਕੁਰੁ ਤੁਮ ਪਹਿ ਅਰਦਾਸਿ ॥ ਜੀਉ ਪਿੰਡੁ ਸਭੁ ਤੇਰੀ ਰਾਸਿ ॥";
        s.prayerTranslit = "Tu Thakur tum peh Ardas. Jio pind sabh teri raas. "
                + "Tum maat pita hum barik tere, tumri kirpa meh sookh ghanere.";
        s.prayerTranslation = "You are our Lord and Master; to You I offer this prayer. Body and soul are all Yours. "
                + "You are our Mother and Father, and in Your grace we find lasting peace and safe passage.";

        String nanak = nanakshahiDate(now);
        s.calendarLine = nanak;
        s.badge = nanak.contains("1 Vaisakh") ? I18n.t("Vaisakhi") : I18n.t("Nitnem");

        if (!hasCoord) {
            s.nextTitle = I18n.t("Esperando señal GPS");
            s.topShortTitle = I18n.t("Nitnem · GPS…");
            s.countdown = "--:--:--";
            return s;
        }

        int[] sun = solarMinutes(now, lat, lon); // [sunrise, noon, sunset]
        int amrit = clampM(sun[0] - 150);
        int sunrise = sun[0];
        int rehras = sun[2];
        int sohila = clampM(sun[2] + 180);
        int[] mins = {amrit, sunrise, rehras, sohila};
        String[] labels = {"Amrit Vela", "Sunrise (Asa Di Var)", "Rehras Sahib", "Kirtan Sohila"};
        int next = pickNext(mins, now);
        s.nextTitle = I18n.t(labels[next]);
        s.topShortTitle = s.nextTitle;
        s.countdown = Prayer.formatCountdown(remSeconds(mins[next], now));
        for (int i = 0; i < labels.length; i++) {
            s.rows.add(new Row(I18n.t(labels[i]), "", Prayer.formatTime(c, p, mins[i]), i == next));
        }
        return s;
    }

    private static String nanakshahiDate(Calendar cal) {
        String[] months = {"Chet", "Vaisakh", "Jeth", "Harh", "Sawan", "Bhadon", "Assu", "Katak", "Maghar", "Poh", "Magh", "Phagun"};
        int[] starts = {14, 14, 15, 15, 16, 16, 15, 15, 14, 14, 13, 12}; // starts in March..February
        int y = cal.get(Calendar.YEAR), m = cal.get(Calendar.MONTH) + 1, d = cal.get(Calendar.DAY_OF_MONTH);
        int idx = (m + 9) % 12; // March=0..Feb=11
        if (d < starts[idx]) idx = (idx + 11) % 12;
        int nYear = (m > 3 || (m == 3 && d >= 14)) ? y - 1468 : y - 1469;
        int startMonth = ((idx + 2) % 12) + 1;
        int startYear = (startMonth > m) ? y - 1 : y;
        double jdNow = Prayer.julianDate(y, m, d);
        double jdStart = Prayer.julianDate(startYear, startMonth, starts[idx]);
        int nDay = Math.max(1, (int) Math.round(jdNow - jdStart) + 1);
        return nDay + " " + months[idx] + " " + nYear + " NS";
    }

    // ───────────────────────── shared solar & calendar helpers ─────────────────────────

    private static int[] solarMinutes(Calendar cal, double lat, double lon) {
        int tzMin = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 60000;
        double tz = tzMin / 60.0;
        double jd = Prayer.julianDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) - lon / (15.0 * 24.0);
        double[] sun = Prayer.sunPos(jd + 0.5);
        double noon = 12.0 + tz - lon / 15.0 - sun[1];
        double rise = Prayer.sunAngleTime(jd + 0.5, lat, lon, tz, 0.833, true);
        double set = Prayer.sunAngleTime(jd + 0.5, lat, lon, tz, 0.833, false);
        if (Double.isNaN(rise) || Double.isNaN(set)) {
            rise = noon - 6.0;
            set = noon + 6.0;
        }
        return new int[]{
            clampM((int) Math.round(rise * 60.0)),
            clampM((int) Math.round(noon * 60.0)),
            clampM((int) Math.round(set * 60.0))
        };
    }

    private static int clampM(int m) {
        return ((m % 1440) + 1440) % 1440;
    }

    private static int pickNext(int[] mins, Calendar now) {
        int nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        for (int i = 0; i < mins.length; i++) {
            if (mins[i] * 60 > nowSec) return i;
        }
        return 0;
    }

    private static long remSeconds(int targetMin, Calendar now) {
        int nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        int tSec = targetMin * 60;
        return tSec > nowSec ? (tSec - nowSec) : (24 * 3600 - nowSec + tSec);
    }

    // ── Exact Hebrew calendar conversion (Dershowitz / Reingold style) ──

    private static boolean hebrewLeap(int y) {
        return (((7 * y) + 1) % 19) < 7;
    }

    private static int hebrewElapsedDays(int y) {
        int months = (235 * ((y - 1) / 19)) + (12 * ((y - 1) % 19)) + ((7 * ((y - 1) % 19) + 1) / 19);
        int parts = 204 + 793 * (months % 1080);
        int hours = 5 + 12 * months + 793 * (months / 1080) + (parts / 1080);
        int day = 1 + 29 * months + (hours / 24);
        int p = (hours % 24) * 1080 + (parts % 1080);
        int alt = day;
        if (p >= 19440 || ((day % 7 == 2) && p >= 9924 && !hebrewLeap(y)) || ((day % 7 == 1) && p >= 16789 && hebrewLeap(y - 1))) {
            alt = day + 1;
        }
        if (alt % 7 == 0 || alt % 7 == 3 || alt % 7 == 5) alt++;
        return alt;
    }

    private static int hebrewYearDays(int y) {
        return hebrewElapsedDays(y + 1) - hebrewElapsedDays(y);
    }

    private static int hebrewMonthDays(int y, int m) {
        if (m == 2 || m == 4 || m == 6 || m == 10 || m == 13) return 29;
        if (m == 12 && !hebrewLeap(y)) return 29;
        int yd = hebrewYearDays(y);
        if (m == 8 && yd % 10 != 5) return 29;
        if (m == 9 && yd % 10 == 3) return 29;
        return 30;
    }

    /** Returns {year, month (1=Nisan..7=Tishrei..12/13=Adar), day, leapFlag} */
    public static int[] gregorianToHebrew(Calendar cal) {
        long jd = (long) Math.floor(Prayer.julianDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) + 0.5);
        long hebrewEpoch = 347996L;
        int y = (int) Math.floor(((jd - hebrewEpoch) * 98496.0) / 35975351.0) + 1;
        while (jd >= hebrewToJd(y + 1, 7, 1)) y++;
        while (jd < hebrewToJd(y, 7, 1)) y--;
        int m = (jd < hebrewToJd(y, 1, 1)) ? 7 : 1;
        while (jd > hebrewToJd(y, m, hebrewMonthDays(y, m))) m++;
        int d = (int) (jd - hebrewToJd(y, m, 1)) + 1;
        return new int[]{y, m, d, hebrewLeap(y) ? 1 : 0};
    }

    private static long hebrewToJd(int y, int m, int d) {
        long jd = 347995L + hebrewElapsedDays(y) + d;
        if (m < 7) {
            for (int i = 7; i <= (hebrewLeap(y) ? 13 : 12); i++) jd += hebrewMonthDays(y, i);
            for (int i = 1; i < m; i++) jd += hebrewMonthDays(y, i);
        } else {
            for (int i = 7; i < m; i++) jd += hebrewMonthDays(y, i);
        }
        return jd;
    }

    private static String hebrewMonthName(int m, boolean leap) {
        String[] n = {"Nisan", "Iyyar", "Sivan", "Tammuz", "Av", "Elul", "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", leap ? "Adar I" : "Adar", "Adar II"};
        return (m >= 1 && m <= 13) ? n[m - 1] : "";
    }
}
