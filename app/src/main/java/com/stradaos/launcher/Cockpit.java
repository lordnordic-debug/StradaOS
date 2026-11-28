package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Tema premium «Sport Cockpit» (Bloque 26, 2.40). Un tablero en píldora con el velocímetro y el mapa redondos y el logo
 * al medio; abajo, el reproductor con la carátula redonda y los próximos mantenimientos (se pueden cambiar de lado).
 * Arriba, Android Auto y CarPlay a la izquierda y Wi-Fi, Bluetooth y Ajustes a la derecha; la hora, el clima y la fecha
 * van en la pestaña de abajo de la píldora. La barra de apps va a la izquierda, a la derecha o abajo.
 *
 * La píldora es un carrusel: Cabina (velocímetro, logo y mapa redondo) → Mapa (el mapa llena la píldora y la velocidad
 * queda chica en una esquina) → Video. Entre Cabina y Mapa no se cambia de página: el mapa es el mismo y su ventana
 * (un hueco en una máscara) se agranda del círculo a la píldora cuadro a cuadro, sin rearmar nada.
 *
 * Android 4.1: clipPath no funciona con la GPU antes de 4.3, así que las formas curvas se tapan con una máscara que
 * pinta el fondo de la píldora ({@link Mask}); los brillos son degradados (sin desenfoque ni capas por software).
 * Faro y Main le preguntan a {@link Prem#cockpit()} para usar estos colores.
 */
final class Cockpit {
    private Cockpit() { }

    static final String ID = "cockpit";

    // ── colores (oscuro / claro) ──
    static int base(boolean l) { return l ? 0xFFE7E9ED : 0xFF101116; }
    static int glass(boolean l) { return l ? 0xFFF5F6F8 : 0xFF1B1C22; }
    static int glassEdge(boolean l) { return l ? 0xFFFFFFFF : 0x16FFFFFF; }
    static int pill(boolean l) { return l ? 0xFFF5F6F8 : 0xFF1B1C22; }
    static int pillEdge(boolean l) { return l ? 0xFFFFFFFF : 0x16FFFFFF; }
    static int pillIcon(boolean l) { return l ? 0xFF3B3E45 : 0xFFC3C6CD; }
    static int soft(boolean l) { return l ? 0x0F000000 : 0x14FFFFFF; }
    static int tile(boolean l) { return l ? 0xFFE8EAED : 0xFF26272E; }
    static int track(boolean l) { return l ? 0xFFD2D5DA : 0xFF34353C; }
    /** Botón del medio (reproducir) y la pestaña de la píldora. */
    static int chip(boolean l) { return l ? 0xFFE6E8EB : 0xFF24252C; }

    // ── fondo de la pantalla ──

    /** Casi plano: una luz suave arriba al centro, una pizca del acento abajo y las esquinas más oscuras. */
    static void paint(Canvas c, float w, float h, boolean light, int accent) {
        c.drawColor(base(light));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float u = Math.max(w, h);
        int glow = light ? 0xFFFFFFFF : 0xFF262833;
        p.setShader(new RadialGradient(w * 0.5f, h * 0.18f, u * 0.7f,
            new int[]{Aurora.a(glow, light ? 0.6f : 0.7f), Aurora.a(glow, light ? 0.2f : 0.22f), Aurora.a(glow, 0f)},
            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.5f, h * 1.05f, u * 0.6f,
            new int[]{Aurora.a(accent, light ? 0.05f : 0.08f), Aurora.a(accent, 0f)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int dark = light ? 0xFF8C93A0 : 0xFF000000;
        p.setShader(new RadialGradient(w * 0.5f, h * 0.45f, u * 0.72f,
            new int[]{Aurora.a(dark, 0f), Aurora.a(dark, 0f), Aurora.a(dark, light ? 0.10f : 0.38f)},
            new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    static int grain(boolean light) { return light ? 3 : 5; }

    // ── la píldora ──

    /**
     * Fondo de la píldora (px): «m» de margen alrededor para el brillo o la sombra; la píldora de afuera y, adentro (a
     * «ins»), la de adentro, más oscura. Se pinta una vez; las máscaras lo usan para tapar las curvas.
     */
    static Bitmap pillBitmap(int w, int h, float m, float ins, float k, boolean light, int accent) {
        Bitmap b;
        try { b = Bitmap.createBitmap(Math.max(8, w), Math.max(8, h), Bitmap.Config.ARGB_8888); }
        catch (Throwable e) { return null; }
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        RectF o = new RectF(m, m, w - m, h - m);
        float ro = o.height() / 2;
        if (light) {
            // sombra suave debajo (capas que crecen, sin desenfoque)
            for (int i = 5; i >= 0; i--) {
                float g = (i + 1) * 1.3f * k;
                p.setColor(Aurora.a(0xFF1C2230, 0.02f + (5 - i) * 0.005f));
                RectF r = new RectF(o.left - g, o.top - g * 0.4f + 3 * k, o.right + g, o.bottom + g + 3 * k);
                c.drawRoundRect(r, ro + g, ro + g, p);
            }
        } else {
            // brillo del acento alrededor del borde
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 4; i++) {
                p.setStrokeWidth((2 + i * 3.2f) * k);
                p.setColor(Aurora.a(accent, 0.11f - i * 0.025f));
                c.drawRoundRect(o, ro, ro, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        p.setShader(new LinearGradient(0, o.top, 0, o.bottom,
            light ? new int[]{0xFFF4F5F7, 0xFFE3E5E9} : new int[]{0xFF1E1F26, 0xFF121318}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(o, ro, ro, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.3f * k));
        p.setColor(light ? 0xFFC9CCD2 : Aurora.a(accent, 0.55f));
        c.drawRoundRect(o, ro, ro, p);
        // la de adentro
        RectF in = new RectF(o.left + ins, o.top + ins, o.right - ins, o.bottom - ins);
        float ri = in.height() / 2;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, in.top, 0, in.bottom,
            light ? new int[]{0xFFDCDFE3, 0xFFE6E8EB} : new int[]{0xFF050507, 0xFF0B0B0E}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(in, ri, ri, p);
        p.setShader(null);
        if (light) {
            // sombra interior arriba
            p.setShader(new LinearGradient(0, in.top, 0, in.top + in.height() * 0.25f,
                new int[]{0x1A1C2230, 0x001C2230}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(in, ri, ri, p);
            p.setShader(null);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, k));
        p.setColor(light ? 0xFFCDD0D5 : 0xFF2A2023);
        c.drawRoundRect(in, ri, ri, p);
        return b;
    }

    /**
     * Tapa todo menos un hueco redondeado con el fondo de la píldora (la vista «holder» lo tiene estirado). Así se ven
     * redondos el mapa y el video también en Android 4.1/4.2 (sin clipPath).
     */
    static final class Mask extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();
        private final RectF hole = new RectF();
        private final Matrix m = new Matrix(), mb = new Matrix();
        private final int[] la = new int[2], lb = new int[2];
        private final Bitmap bmp, bg;
        private final View holder, bgHolder;
        private float rad;
        private boolean dirty = true;

        /**
         * bmp/holder: el fondo de la píldora y la vista que lo tiene estirado. bg/bgHolder: el fondo de la pantalla
         * (en las esquinas, fuera de la píldora, se ve la pantalla; sin él ahí se asomaba el mapa).
         */
        Mask(Context c, Bitmap bmp, View holder, Bitmap bg, View bgHolder) {
            super(c);
            this.bmp = bmp;
            this.holder = holder;
            this.bg = bg;
            this.bgHolder = bgHolder;
            if (bmp != null) p.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            if (bg != null) pb.setShader(new BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            else pb.setColor(Main.BG);
        }

        /** El hueco, en px de esta vista. */
        void setHole(float l, float t, float r, float b, float radius) {
            hole.set(l, t, r, b);
            rad = radius;
            dirty = true;
            invalidate();
        }

        /**
         * Quieta, la máscara se pinta una vez en una imagen (por software) y después solo se copia: es lo más barato
         * para la GPU de los radios y lo más estable en el emulador de Android 14. Mientras el hueco se anima (live),
         * se dibuja como forma con el fondo de imagen, cuadro a cuadro.
         */
        private boolean live;
        private Bitmap cache;
        private String cacheKey;
        private final Paint cp = new Paint(Paint.FILTER_BITMAP_FLAG), clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        void setLive(boolean on) { if (live != on) { live = on; invalidate(); } }

        private boolean drawCached(Canvas c, int w, int h, int dxb, int dyb, int dxp, int dyp) {
            String key = w + "x" + h + ":" + dxb + "," + dyb + ":" + dxp + "," + dyp + ":" + hole + ":" + rad;
            if (cache == null || !key.equals(cacheKey)) {
                try {
                    if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
                        if (cache != null) cache.recycle();
                        cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    } else cache.eraseColor(0);
                } catch (Throwable e) { cache = null; return false; }
                Canvas cv = new Canvas(cache);
                if (bg != null && bgHolder != null && bgHolder.getWidth() > 0) {
                    cv.save();
                    cv.concat(mb);
                    cv.drawBitmap(bg, 0, 0, cp);
                    cv.restore();
                } else cv.drawColor(Main.BG);
                if (bmp != null && holder != null && holder.getWidth() > 0) {
                    cv.save();
                    cv.concat(m);
                    cv.drawBitmap(bmp, 0, 0, cp);
                    cv.restore();
                }
                if (hole.width() > 0 && hole.height() > 0) {
                    clear.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                    cv.drawRoundRect(hole, rad, rad, clear);
                }
                cacheKey = key;
            }
            c.drawBitmap(cache, 0, 0, null);
            return true;
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (cache != null) { cache.recycle(); cache = null; cacheKey = null; }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { dirty = true; }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (dirty) {
                path.reset();
                path.setFillType(Path.FillType.EVEN_ODD);
                path.addRect(-2, -2, w + 2, h + 2, Path.Direction.CW);
                if (hole.width() > 0 && hole.height() > 0) path.addRoundRect(hole, rad, rad, Path.Direction.CW);
                dirty = false;
            }
            getLocationInWindow(la);
            int dxb = 0, dyb = 0, dxp = 0, dyp = 0;
            if (bg != null && bgHolder != null && bgHolder.getWidth() > 0) {
                bgHolder.getLocationInWindow(lb);
                dxb = lb[0] - la[0]; dyb = lb[1] - la[1];
                mb.setScale(bgHolder.getWidth() / (float) bg.getWidth(), bgHolder.getHeight() / (float) bg.getHeight());
                mb.postTranslate(dxb, dyb);
            }
            if (bmp != null && holder != null && holder.getWidth() > 0) {
                holder.getLocationInWindow(lb);
                dxp = lb[0] - la[0]; dyp = lb[1] - la[1];
                m.setScale(holder.getWidth() / (float) bmp.getWidth(), holder.getHeight() / (float) bmp.getHeight());
                m.postTranslate(dxp, dyp);
            }
            if (!live && drawCached(c, (int) w, (int) h, dxb, dyb, dxp, dyp)) return;
            // animándose: la forma con la pantalla detrás y encima la píldora con su brillo o su sombra
            if (bg != null) pb.getShader().setLocalMatrix(mb);
            c.drawPath(path, pb);
            if (bmp != null && holder != null && holder.getWidth() > 0) {
                p.getShader().setLocalMatrix(m);
                c.drawPath(path, p);
            }
        }
    }

    /** Aro de luz alrededor del hueco del mapa (círculo o píldora) y, en el círculo, una sombra suave hacia el borde. */
    static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint v = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float k;
        private final boolean light;
        private final int accent;
        private float rad, round = 1f;

        Ring(Context c, float scale, boolean light, int accent) {
            super(c);
            k = scale;
            this.light = light;
            this.accent = accent;
            p.setStyle(Paint.Style.STROKE);
        }

        /** roundness: 1 = círculo (con sombra hacia el borde), 0 = píldora. */
        void set(float l, float t, float rt, float b, float radius, float roundness) {
            r.set(l, t, rt, b);
            rad = radius;
            round = roundness;
            if (roundness > 0.01f && r.width() > 0) {
                float rr = Math.min(r.width(), r.height()) / 2;
                v.setShader(new RadialGradient(r.centerX(), r.centerY(), rr,
                    new int[]{0x00000000, 0x00000000, light ? 0x22000000 : 0x8C000000},
                    new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (r.width() <= 0) return;
            if (round > 0.01f) {
                v.setAlpha(Math.round(255 * round));
                c.drawRoundRect(r, rad, rad, v);
            }
            // brillo (dos trazos anchos y tenues) y el aro fino
            for (int i = 0; i < 2; i++) {
                p.setStrokeWidth((i == 0 ? 7f : 3.5f) * k);
                p.setColor(Aurora.a(accent, light ? (i == 0 ? 0.08f : 0.16f) : (i == 0 ? 0.14f : 0.26f)));
                c.drawRoundRect(r, rad, rad, p);
            }
            p.setStrokeWidth(Math.max(1.2f, 1.6f * k));
            p.setColor(Aurora.a(accent, light ? 0.75f : 0.85f));
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    /** El logo oficial recortado en círculo (el disco negro con su aro rojo) con un resplandor del acento detrás. */
    static final class Logo extends View {
        private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint a = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix m = new Matrix();
        private final Bitmap bmp;
        private final boolean light;
        private final int accent;

        Logo(Context c, Bitmap logo, boolean light, int accent) {
            super(c);
            bmp = logo;
            this.light = light;
            this.accent = accent;
            if (logo != null) a.setShader(new BitmapShader(logo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cx = w / 2f, cy = h / 2f, d = Math.min(w, h) / 2f;
            g.setShader(new RadialGradient(cx, cy, d,
                new int[]{Aurora.a(accent, light ? 0.45f : 0.65f), Aurora.a(accent, light ? 0.18f : 0.28f), Aurora.a(accent, 0f)},
                new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP));   // desde el centro (el disco tapa lo de adentro)
            if (bmp != null) {
                // el disco del logo ocupa ~80 % del dibujo; con 0,92 queda un aro rojo fino alrededor del negro
                float rr = d * 0.70f, crop = bmp.getWidth() / 2f * 0.92f, sc = rr / crop;
                m.setScale(sc, sc);
                m.postTranslate(cx - bmp.getWidth() / 2f * sc, cy - bmp.getHeight() / 2f * sc);
                a.getShader().setLocalMatrix(m);
            }
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, d = Math.min(getWidth(), getHeight()) / 2f;
            if (d <= 0) return;
            c.drawCircle(cx, cy, d, g);
            if (bmp != null) c.drawCircle(cx, cy, d * 0.70f, a);
        }
    }

    /**
     * Velocímetro de la referencia: cara redonda con aro de luz, escala arriba (abierta abajo) con la zona roja al
     * final y una aguja corta; el número grande en cursiva con la unidad al lado. Con el escáner OBD y «Revoluciones
     * junto al velocímetro», debajo del número aparecen la barra de las revoluciones y su valor. El límite de la vía
     * (navegación integrada) va abajo, en el hueco de la escala. Se acerca a la velocidad nueva cuadro a cuadro.
     */
    static final class Gauge extends View {
        private static final float START = 150f, SWEEP = 240f, TAU = 0.42f;
        private final float k;
        private boolean light, mph, ok;
        private int accent = Main.RED, text = 0xFFF2F3F5, muted = 0xFF8C919A;
        private float max = 200, target, val;
        private long last;
        private int limit;
        private final Tach tach = new Tach();
        private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            tick = new Paint(Paint.ANTI_ALIAS_FLAG), num = new Paint(Paint.ANTI_ALIAS_FLAG),
            big = new Paint(Paint.ANTI_ALIAS_FLAG), unit = new Paint(Paint.ANTI_ALIAS_FLAG),
            zone = new Paint(Paint.ANTI_ALIAS_FLAG), needle = new Paint(Paint.ANTI_ALIAS_FLAG),
            bar = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rc = new RectF();

        Gauge(Context c, float scale) {
            super(c);
            k = scale;
            ring.setStyle(Paint.Style.STROKE);
            tick.setStyle(Paint.Style.STROKE);
            tick.setStrokeCap(Paint.Cap.ROUND);
            zone.setStyle(Paint.Style.STROKE);
            needle.setStyle(Paint.Style.STROKE);
            needle.setStrokeCap(Paint.Cap.ROUND);
            bar.setStrokeCap(Paint.Cap.ROUND);
            bar.setStyle(Paint.Style.STROKE);
            num.setTextAlign(Paint.Align.CENTER);
            big.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
            unit.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC));
        }

        void setTheme(boolean isLight, int accentColor, int textColor, int mutedColor) {
            light = isLight;
            accent = accentColor;
            text = textColor;
            muted = mutedColor;
            requestFaces();
        }

        void setUnit(boolean isMph) { mph = isMph; max = isMph ? 120 : 200; invalidate(); }

        void setLimit(int l) { if (l != limit) { limit = l; invalidate(); } }

        void setRpm(float rpm) { if (tach.set(rpm)) postInvalidateOnAnimation(); }

        void setSpeed(float v, boolean hasFix) {
            ok = hasFix;
            float t = hasFix ? Math.max(0, Math.min(max * 1.05f, v)) : 0;
            if (t == target) { invalidate(); return; }
            target = t;
            if (last == 0) last = SystemClock.uptimeMillis() - 16;
            postInvalidateOnAnimation();
        }

        private void requestFaces() {
            float w = getWidth(), h = getHeight();
            if (w > 0 && h > 0) faces(w, h);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { faces(w, h); }

        private void faces(float w, float h) {
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
            face.setShader(new RadialGradient(cx, cy - r * 0.15f, r,
                light ? new int[]{0xFFF6F7F9, 0xFFE6E8EC, 0xFFD6D9DE} : new int[]{0xFF1E2234, 0xFF141724, 0xFF0B0D15},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            if (last != 0) {
                float dt = Math.min(0.1f, Math.max(0f, (now - last) / 1000f));
                val += (target - val) * (1f - (float) Math.exp(-dt / TAU));
                if (Math.abs(target - val) < 0.04f) { val = target; last = 0; }
                else { last = now; postInvalidateOnAnimation(); }
            }
            if (tach.step()) postInvalidateOnAnimation();
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, R = Math.min(w, h) / 2f;
            if (R <= 0) return;
            float rf = R * 0.93f;   // la cara; afuera queda el aro con su brillo
            // brillo y aro
            ring.setStrokeWidth(R * 0.08f);
            ring.setColor(Aurora.a(accent, light ? 0.07f : 0.12f));
            c.drawCircle(cx, cy, rf + R * 0.02f, ring);
            ring.setStrokeWidth(R * 0.035f);
            ring.setColor(Aurora.a(accent, light ? 0.14f : 0.24f));
            c.drawCircle(cx, cy, rf, ring);
            c.drawCircle(cx, cy, rf, face);
            ring.setStrokeWidth(Math.max(1.2f, R * 0.012f));
            ring.setColor(Aurora.a(accent, light ? 0.7f : 0.8f));
            c.drawCircle(cx, cy, rf, ring);

            float frac = Math.max(0, Math.min(1, val / max));
            // zona roja: el último 20 % de la escala, cada vez más intensa
            float rz = rf * 0.86f;
            rc.set(cx - rz, cy - rz, cx + rz, cy + rz);
            zone.setStrokeWidth(rf * 0.075f);
            int seg = 8;
            for (int i = 0; i < seg; i++) {
                float a0 = START + SWEEP * (0.8f + 0.2f * i / seg), sw = SWEEP * 0.2f / seg + 0.6f;
                zone.setColor(Aurora.a(accent, 0.35f + 0.6f * (i + 1f) / seg));
                c.drawArc(rc, a0, sw, false, zone);
            }
            // rayitas: cada 10 (5 en mph), las grandes cada 20 (10) y números cada 40 (20)
            int minor = mph ? 5 : 10, major = mph ? 10 : 20, label = mph ? 20 : 40;
            num.setTextSize(rf * 0.105f);
            num.setColor(Aurora.a(text, 0.72f));
            for (int v = 0; v <= max; v += minor) {
                double an = Math.toRadians(START + SWEEP * v / max);
                float cs = (float) Math.cos(an), sn = (float) Math.sin(an);
                boolean mj = v % major == 0;
                float r0 = rf * (mj ? 0.74f : 0.78f), r1 = rf * 0.82f;
                tick.setStrokeWidth(Math.max(1f, rf * (mj ? 0.014f : 0.008f)));
                tick.setColor(Aurora.a(text, mj ? 0.6f : 0.28f));
                c.drawLine(cx + cs * r0, cy + sn * r0, cx + cs * r1, cy + sn * r1, tick);
                if (v % label == 0) {
                    float rl = rf * 0.62f;
                    c.drawText(String.valueOf(v), cx + cs * rl, cy + sn * rl - (num.ascent() + num.descent()) / 2f, num);
                }
            }
            // aguja corta junto a la escala, con brillo
            double na = Math.toRadians(START + SWEEP * frac);
            float ncs = (float) Math.cos(na), nsn = (float) Math.sin(na);
            float n0 = rf * 0.60f, n1 = rf * 0.92f;
            needle.setStrokeWidth(rf * 0.075f);
            needle.setColor(Aurora.a(accent, 0.22f));
            c.drawLine(cx + ncs * n0, cy + nsn * n0, cx + ncs * n1, cy + nsn * n1, needle);
            needle.setStrokeWidth(Math.max(2f, rf * 0.028f));
            needle.setColor(accent);
            c.drawLine(cx + ncs * n0, cy + nsn * n0, cx + ncs * n1, cy + nsn * n1, needle);

            // número y unidad, centrados juntos
            boolean rpm = tach.visible();
            String n = ok ? String.valueOf(Math.round(val < 1.5f && target == 0 ? 0 : val)) : "--";
            String u = mph ? "mph" : "km/h";
            big.setTextSize(rf * 0.40f);
            big.setColor(text);
            unit.setTextSize(rf * 0.13f);
            unit.setColor(Aurora.a(text, 0.85f));
            float nw = big.measureText(n), uw = unit.measureText(u), gap = rf * 0.05f;
            float x0 = cx - (nw + gap + uw) / 2f, base = cy + rf * (rpm ? 0.08f : 0.14f);
            c.drawText(n, x0, base, big);
            c.drawText(u, x0 + nw + gap, base, unit);

            // más corta que el hueco de la escala: los números 0 y 200 quedan libres a los lados
            float bw = rf * 0.62f, bx0 = cx - bw / 2, bx1 = cx + bw / 2;
            if (rpm) {
                // barra de las revoluciones y su valor (como «Mode · Self» de la referencia)
                float by = cy + rf * 0.25f, rv = tach.value();
                bar.setStrokeWidth(rf * 0.045f);
                bar.setColor(Aurora.a(text, light ? 0.15f : 0.14f));
                c.drawLine(bx0, by, bx1, by, bar);
                float f = Math.max(0f, Math.min(1f, rv / Tach.MAX));
                if (f > 0.01f) {
                    bar.setColor(rv >= Tach.RED ? accent : text);
                    c.drawLine(bx0, by, bx0 + bw * f, by, bar);
                }
                unit.setTextSize(rf * 0.10f);
                unit.setColor(muted);
                unit.setTextAlign(Paint.Align.LEFT);
                c.drawText("RPM", bx0, by + rf * 0.17f, unit);
                unit.setTextAlign(Paint.Align.RIGHT);
                unit.setColor(rv >= Tach.RED ? accent : text);
                unit.setTextSize(rf * 0.115f);
                c.drawText(I18n.integer(Math.round(rv / 50f) * 50), bx1, by + rf * 0.17f, unit);
                unit.setTextAlign(Paint.Align.LEFT);
            }
            // límite de la vía, abajo en el hueco de la escala
            if (limit > 0) {
                float lr = rf * (rpm ? 0.11f : 0.14f), ly = cy + rf * (rpm ? 0.66f : 0.58f);
                boolean over = ok && val > limit + 2;
                dot.setStyle(Paint.Style.FILL);
                dot.setColor(light ? 0xFFFFFFFF : 0xFF17191D);
                c.drawCircle(cx, ly, lr, dot);
                dot.setStyle(Paint.Style.STROKE);
                dot.setStrokeWidth(Math.max(1.5f, lr * 0.16f));
                dot.setColor(over ? 0xFFFF453A : light ? 0xFFC9CDD3 : 0xFF3A3E45);
                c.drawCircle(cx, ly, lr, dot);
                dot.setStyle(Paint.Style.FILL);
                num.setColor(text);
                num.setTextSize(lr * (limit >= 100 ? 0.78f : 0.92f));
                num.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText(String.valueOf(limit), cx, ly - (num.ascent() + num.descent()) / 2f, num);
                num.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    /** Pestaña de la píldora (abajo al centro): forma de trapecio con las esquinas redondas. */
    static final class Tab extends FrameLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float k;
        private final boolean light;
        private final int accent;

        Tab(Context c, float scale, boolean light, int accent) {
            super(c);
            k = scale;
            this.light = light;
            this.accent = accent;
            setWillNotDraw(false);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float sl = h * 0.55f, r = h * 0.32f;
            path.reset();
            path.moveTo(0, h);
            path.cubicTo(sl * 0.45f, h, sl * 0.5f, 0, sl + r * 0.2f, 0);
            path.lineTo(w - sl - r * 0.2f, 0);
            path.cubicTo(w - sl * 0.5f, 0, w - sl * 0.45f, h, w, h);
            path.close();
            p.setShader(new LinearGradient(0, 0, 0, h,
                light ? new int[]{0xFFF6F7F9, 0xFFE9EBEE} : new int[]{0xFF22232A, 0xFF17181D}, null, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
            Shader sh = p.getShader();
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, 1.2f * k));
            p.setColor(light ? 0xFFC9CCD2 : Aurora.a(accent, 0.5f));
            c.drawPath(path, p);
            p.setShader(sh);
        }
    }

    /** Botón de volumen: parlante de trazo con «−» o «+». */
    static final class Vol extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final boolean up;

        Vol(Context c, int color, boolean up) {
            super(c);
            this.up = up;
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), kk = Math.min(w, h) / 24f;
            c.save();
            c.translate((w - 24 * kk) / 2, (h - 24 * kk) / 2);
            path.reset();
            path.moveTo(3 * kk, 9.5f * kk);
            path.lineTo(6.5f * kk, 9.5f * kk);
            path.lineTo(11 * kk, 5.5f * kk);
            path.lineTo(11 * kk, 18.5f * kk);
            path.lineTo(6.5f * kk, 14.5f * kk);
            path.lineTo(3 * kk, 14.5f * kk);
            path.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f * kk);
            c.drawLine(14.5f * kk, 12 * kk, 21 * kk, 12 * kk, p);
            if (up) c.drawLine(17.75f * kk, 8.75f * kk, 17.75f * kk, 15.25f * kk, p);
            c.restore();
        }
    }

    /** Fondo del botón de reproducir: círculo del color de acento con un resplandor suave alrededor. */
    static final class PlayBg extends android.graphics.drawable.Drawable {
        private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG), f = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final boolean light;
        private float lastR = -1;

        PlayBg(int accent, boolean light) {
            this.accent = accent;
            this.light = light;
            f.setColor(accent);
        }

        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY(), R = Math.min(b.width(), b.height()) / 2f;
            if (R != lastR) {
                g.setShader(new RadialGradient(cx, cy, R, new int[]{Aurora.a(accent, light ? 0.34f : 0.5f), Aurora.a(accent, light ? 0.26f : 0.4f), Aurora.a(accent, 0f)},
                    new float[]{0f, 0.76f, 1f}, Shader.TileMode.CLAMP));
                lastR = R;
            }
            c.drawCircle(cx, cy, R, g);
            c.drawCircle(cx, cy, R * 0.76f, f);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // ── miniatura para la galería ──

    /** Miniatura con la misma distribución que buildCockpit en Main. */
    static final class Thumb extends View {
        final boolean portrait, light;
        boolean selected;
        private final float s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Thumb(Context c, float s, boolean portrait, boolean light) {
            super(c);
            this.s = s;
            this.portrait = portrait;
            this.light = light;
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, (int) (w * (portrait ? 1.42f : 0.586f)));
        }

        private void rr(Canvas c, float l, float t, float rt, float b, float rad, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(color);
            r.set(l, t, rt, b);
            c.drawRoundRect(r, rad, rad, p);
        }

        private void stroke(Canvas c, float l, float t, float rt, float b, float rad, int color, float sw) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(color);
            r.set(l, t, rt, b);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = portrait ? w : h, rad = u * 0.06f, crad = u * 0.035f;
            rr(c, 0, 0, w, h, rad, base(light));
            int text = light ? 0xFF15171B : 0xFFF2F3F5, muted = light ? 0xFF7A7F88 : 0xFF7E838C, acc = Main.RED;
            float pad = u * 0.035f, gap = u * 0.03f, topH = u * 0.075f, sw = Math.max(1f, s * 0.8f);
            // barra de arriba: Android Auto y CarPlay a la izquierda, íconos a la derecha
            rr(c, pad + (portrait ? 0 : u * 0.17f), pad + topH * 0.15f, pad + (portrait ? 0 : u * 0.17f) + u * 0.13f, pad + topH * 0.75f, topH * 0.3f, glass(light));
            for (int i = 0; i < 3; i++) {
                p.setColor(Aurora.a(muted, 0.9f));
                c.drawCircle(w - pad - u * 0.02f - i * u * 0.05f, pad + topH * 0.45f, u * 0.012f, p);
            }
            float top = pad + topH + gap * 0.5f;
            float x0 = pad, x1 = w - pad, bottom = h - pad;
            if (!portrait) {
                // barra de apps a la izquierda
                float rw = u * 0.13f;
                rr(c, pad, top, pad + rw, bottom, crad, glass(light));
                for (int i = 0; i < 6; i++) {
                    p.setColor(Aurora.a(text, 0.6f));
                    c.drawCircle(pad + rw / 2, top + (bottom - top) * (i + 0.5f) / 6f, rw * 0.15f, p);
                }
                x0 = pad + rw + gap;
            }
            float cardsH = (bottom - top) * (portrait ? 0.42f : 0.33f);
            float pillB = bottom - cardsH - gap;
            float pillH = portrait ? Math.min(pillB - top, (x1 - x0) * 0.42f) : pillB - top;
            pill(c, x0, top, x1, top + pillH, acc, text, muted, sw);
            float cy = top + pillH + gap;
            if (!portrait) {
                float cw = (x1 - x0 - gap) / 2;
                player(c, x0, cy, cw, bottom - cy, crad, text, muted, acc);
                maint(c, x0 + cw + gap, cy, cw, bottom - cy, crad, text, muted, acc);
            } else {
                float ch = (bottom - cy - gap) / 2;
                player(c, x0, cy, x1 - x0, ch, crad, text, muted, acc);
                maint(c, x0, cy + ch + gap, x1 - x0, ch, crad, text, muted, acc);
            }
            if (selected) stroke(c, 1.25f * s, 1.25f * s, w - 1.25f * s, h - 1.25f * s, rad, Main.RED, 2.5f * s);
        }

        private void pill(Canvas c, float l, float t, float rt, float b, int acc, int text, int muted, float sw) {
            float hh = b - t, ro = hh / 2;
            rr(c, l, t, rt, b, ro, light ? 0xFFEDEEF1 : 0xFF1C1D23);
            stroke(c, l, t, rt, b, ro, light ? 0xFFC9CCD2 : Aurora.a(acc, 0.6f), sw);
            float ins = hh * 0.06f, il = l + ins, it = t + ins, ir = rt - ins, ib = b - ins, ih = ib - it;
            rr(c, il, it, ir, ib, ih / 2, light ? 0xFFDDE0E4 : 0xFF060608);
            float d = ih * 0.86f, cyy = it + ih / 2;
            float gx = il + ih / 2, mx = ir - ih / 2;
            // velocímetro
            p.setColor(light ? 0xFFE9EBEE : 0xFF161A28);
            c.drawCircle(gx, cyy, d / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(acc);
            c.drawCircle(gx, cyy, d / 2, p);
            r.set(gx - d * 0.4f, cyy - d * 0.4f, gx + d * 0.4f, cyy + d * 0.4f);
            p.setStrokeWidth(d * 0.06f);
            c.drawArc(r, -60, 50, false, p);
            p.setStyle(Paint.Style.FILL);
            rr(c, gx - d * 0.2f, cyy - d * 0.08f, gx + d * 0.16f, cyy + d * 0.06f, d * 0.03f, Aurora.a(text, 0.9f));
            // mapa
            p.setColor(light ? 0xFFE4E0D6 : 0xFF20232A);
            c.drawCircle(mx, cyy, d / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(Aurora.a(muted, 0.4f));
            for (int i = -2; i <= 2; i++) c.drawLine(mx - d * 0.4f, cyy + i * d * 0.16f, mx + d * 0.4f, cyy + i * d * 0.12f, p);
            p.setColor(acc);
            p.setStrokeWidth(sw * 1.6f);
            c.drawLine(mx - d * 0.12f, cyy + d * 0.25f, mx + d * 0.05f, cyy - d * 0.05f, p);
            c.drawLine(mx + d * 0.05f, cyy - d * 0.05f, mx + d * 0.18f, cyy - d * 0.2f, p);
            p.setStrokeWidth(sw);
            c.drawCircle(mx, cyy, d / 2, p);
            p.setStyle(Paint.Style.FILL);
            // logo chico al medio
            float lc = (gx + mx) / 2, lr = ih * 0.17f;
            p.setColor(Aurora.a(acc, 0.3f));
            c.drawCircle(lc, cyy, lr * 1.35f, p);
            p.setColor(acc);
            c.drawCircle(lc, cyy, lr, p);
            p.setColor(0xFF0A0A0C);
            c.drawCircle(lc, cyy, lr * 0.8f, p);
            // pestaña
            float tw = (mx - gx) * 0.5f;
            rr(c, lc - tw / 2, ib - ih * 0.12f, lc + tw / 2, ib + ins * 0.4f, ih * 0.05f, light ? 0xFFF3F4F6 : 0xFF22232A);
        }

        private void card(Canvas c, float x, float y, float w, float hh, float rad) {
            if (light) rr(c, x, y + rad * 0.2f, x + w, y + hh + rad * 0.3f, rad, 0x121C2230);
            rr(c, x, y, x + w, y + hh, rad, glass(light));
            if (!light) stroke(c, x, y, x + w, y + hh, rad, glassEdge(false), Math.max(1f, s * 0.6f));
        }

        private void player(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted, int acc) {
            card(c, x, y, w, hh, rad);
            float d = hh * 0.62f, cx = x + hh * 0.1f + d / 2, cyy = y + hh / 2;
            p.setColor(Aurora.a(acc, 0.3f));
            c.drawCircle(cx, cyy, d * 0.6f, p);
            p.setColor(acc);
            c.drawCircle(cx, cyy, d / 2, p);
            float tx = cx + d / 2 + hh * 0.12f, tr = x + w - hh * 0.1f;
            rr(c, tx, y + hh * 0.18f, tx + (tr - tx) * 0.5f, y + hh * 0.28f, hh * 0.04f, Aurora.a(text, 0.9f));
            rr(c, tx, y + hh * 0.34f, tx + (tr - tx) * 0.32f, y + hh * 0.41f, hh * 0.03f, Aurora.a(muted, 0.8f));
            rr(c, tx, y + hh * 0.52f, tr, y + hh * 0.55f, hh * 0.015f, Aurora.a(muted, 0.4f));
            rr(c, tx, y + hh * 0.52f, tx + (tr - tx) * 0.6f, y + hh * 0.55f, hh * 0.015f, text);
            float by = y + hh * 0.75f;
            for (int i = 0; i < 5; i++) {
                float bx = tx + (tr - tx) * (i + 0.5f) / 5f;
                p.setColor(i == 2 ? acc : Aurora.a(text, 0.7f));
                c.drawCircle(bx, by, hh * (i == 2 ? 0.09f : 0.04f), p);
            }
        }

        private void maint(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted, int acc) {
            card(c, x, y, w, hh, rad);
            float px = x + w * 0.07f;
            rr(c, px, y + hh * 0.12f, px + w * 0.5f, y + hh * 0.26f, hh * 0.07f, tile(light));
            p.setColor(acc);
            c.drawCircle(px + hh * 0.07f, y + hh * 0.19f, hh * 0.025f, p);
            for (int i = 0; i < 3; i++) {
                float ly = y + hh * (0.44f + i * 0.17f);
                p.setColor(Aurora.a(text, 0.8f));
                c.drawCircle(px + hh * 0.02f, ly, hh * 0.018f, p);
                rr(c, px + hh * 0.07f, ly - hh * 0.035f, px + w * (0.5f - i * 0.06f), ly + hh * 0.035f, hh * 0.03f, Aurora.a(text, 0.8f));
                rr(c, x + w * 0.74f, ly - hh * 0.025f, x + w * 0.9f, ly + hh * 0.025f, hh * 0.02f, Aurora.a(muted, 0.7f));
            }
        }
    }
}
