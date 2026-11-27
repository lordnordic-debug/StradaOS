package com.stradaos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ícono propio de una app: en el cajón se deja presionada la app y se elige «Editar ícono». Puede ser el mismo para
 * los dos temas o uno para el tema claro y otro para el oscuro. Se guarda como PNG cuadrado en files/iconos/ y vale
 * en todo StradaOS (el cajón y los botones que abren esa app). Sin ícono propio, el del pack o el de siempre.
 *
 * Archivos de una app (paquete@Actividad): «.png» = los dos temas; «.claro.png» y «.oscuro.png» = uno para cada tema
 * (puede haber solo uno: el otro tema queda con el ícono de siempre).
 */
public class AppIcons {
    static final String DIR = "iconos";
    /** Lado del PNG guardado: alcanza para el ícono más grande de la barra y no ocupa memoria de más. */
    static final int SIZE = 192;
    /** Cuál: 0 = los dos temas, 1 = claro, 2 = oscuro. */
    static final int BOTH = 0, LIGHT = 1, DARK = 2;

    private static Set<String> names;   // archivos que hay en la carpeta (null = sin leer)
    private static final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private static boolean cacheLight;

    static File dir(Context c) { return new File(c.getFilesDir(), DIR); }

    /** «paquete/Actividad» con el nombre completo: la misma clave venga del cajón o de un botón guardado. */
    static String key(String pkg, String cls) {
        if (pkg == null || cls == null) return null;
        return pkg + "/" + (cls.startsWith(".") ? pkg + cls : cls);
    }

    static String key(String component) {
        ComponentName cn = component != null ? ComponentName.unflattenFromString(component) : null;
        return cn != null ? key(cn.getPackageName(), cn.getClassName()) : null;
    }

    static String file(String key, int which) {
        return key.replace('/', '@') + (which == LIGHT ? ".claro" : which == DARK ? ".oscuro" : "") + ".png";
    }

    private static Set<String> names(Context c) {
        if (names == null) {
            names = new HashSet<String>();
            String[] l = dir(c).list();
            if (l != null) for (String n : l) names.add(n);
        }
        return names;
    }

    /** Después de guardar o de restaurar una copia: se vuelve a leer la carpeta. */
    static synchronized void reload() { names = null; cache.clear(); }

    /** ¿La app tiene algún ícono propio? */
    static synchronized boolean has(Context c, String key) {
        if (key == null) return false;
        Set<String> n = names(c);
        return n.contains(file(key, BOTH)) || n.contains(file(key, LIGHT)) || n.contains(file(key, DARK));
    }

    /** ¿Tiene un ícono para cada tema (y no uno para los dos)? */
    static synchronized boolean separate(Context c, String key) {
        if (key == null) return false;
        Set<String> n = names(c);
        return !n.contains(file(key, BOTH)) && (n.contains(file(key, LIGHT)) || n.contains(file(key, DARK)));
    }

    /** El ícono propio para ese tema (el suyo o, si no hay, el de los dos temas); null = se usa el de siempre. */
    static synchronized Bitmap get(Context c, String key, boolean light) {
        if (key == null) return null;
        Set<String> n = names(c);
        if (n.isEmpty()) return null;
        String f = file(key, light ? LIGHT : DARK);
        if (!n.contains(f)) {
            f = file(key, BOTH);
            if (!n.contains(f)) return null;
        }
        if (cacheLight != light) { cache.clear(); cacheLight = light; }   // al cambiar de tema no se guardan los del otro
        if (cache.containsKey(f)) return cache.get(f);
        Bitmap b = null;
        try { b = BitmapFactory.decodeFile(new File(dir(c), f).getAbsolutePath()); }
        catch (Throwable e) { Guard.log("Ícono propio " + f + ": " + e); }
        cache.put(f, b);
        return b;
    }

    /**
     * Guarda los íconos de una app y borra los que tenía. same = el mismo para los dos temas (se usa light).
     * Un null deja ese tema con el ícono de siempre.
     */
    static synchronized void save(Context c, String key, boolean same, Bitmap light, Bitmap dark) throws Exception {
        File d = dir(c);
        for (int w = BOTH; w <= DARK; w++) {
            File f = new File(d, file(key, w));
            if (f.exists() && !f.delete()) throw new Exception("No se pudo borrar " + f.getName());
        }
        try {
            if (same) write(d, file(key, BOTH), light);
            else {
                write(d, file(key, LIGHT), light);
                write(d, file(key, DARK), dark);
            }
        } finally { reload(); }
    }

    private static void write(File d, String name, Bitmap b) throws Exception {
        if (b == null) return;
        if (!d.isDirectory() && !d.mkdirs()) throw new Exception("No se pudo crear " + d);
        File tmp = new File(d, name + ".tmp"), f = new File(d, name);
        FileOutputStream os = new FileOutputStream(tmp);
        try { b.compress(Bitmap.CompressFormat.PNG, 100, os); } finally { os.close(); }
        if (!tmp.renameTo(f)) { tmp.delete(); throw new Exception("No se pudo guardar " + name); }
    }

    /** La imagen elegida, centrada en un cuadrado de SIZE (sin recortar: lo que sobra queda transparente). */
    static Bitmap fit(Bitmap src) {
        // de a mitades hasta acercarse: reducir de una vez (900 → 192) deja los bordes serruchados
        Bitmap cur = src;
        while (Math.max(cur.getWidth(), cur.getHeight()) >= SIZE * 2) {
            Bitmap half = Bitmap.createScaledBitmap(cur, Math.max(1, cur.getWidth() / 2), Math.max(1, cur.getHeight() / 2), true);
            if (cur != src) cur.recycle();
            cur = half;
        }
        int w = cur.getWidth(), h = cur.getHeight();
        float k = SIZE / (float) Math.max(w, h);
        int dw = Math.max(1, Math.round(w * k)), dh = Math.max(1, Math.round(h * k));
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        int x = (SIZE - dw) / 2, y = (SIZE - dh) / 2;
        cv.drawBitmap(cur, new Rect(0, 0, w, h), new Rect(x, y, x + dw, y + dh), p);
        if (cur != src) cur.recycle();
        return out;
    }

    /** Miniatura para la lista de imágenes (lado menor de al menos px); null si no se puede leer. */
    static Bitmap thumb(File f, int px) {
        try {
            String path = f.getAbsolutePath();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, d);
        } catch (Throwable e) { return null; }
    }

    // ── copia de seguridad: los PNG van dentro del mismo archivo ──

    /** Archivos de la carpeta (nombre → bytes), para la copia de seguridad. */
    static synchronized Map<String, byte[]> all(Context c) throws Exception {
        Map<String, byte[]> out = new HashMap<String, byte[]>();
        File[] fs = dir(c).listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName();
            if (!f.isFile() || !n.endsWith(".png") || f.length() <= 0 || f.length() > 2L * 1024 * 1024) continue;
            out.put(n, Backup.readAll(new java.io.FileInputStream(f)));
        }
        return out;
    }

    /** Restaurar una copia: se reemplazan todos los íconos propios por los de la copia. */
    static synchronized void replaceAll(Context c, Map<String, byte[]> files) throws Exception {
        File d = dir(c);
        File[] old = d.listFiles();
        if (old != null) for (File f : old) f.delete();
        try {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                String n = e.getKey();
                // solo nombres de ícono (nada de rutas): la copia es un archivo que el usuario puede tocar
                if (!n.endsWith(".png") || n.indexOf('/') >= 0 || n.indexOf('\\') >= 0 || n.startsWith(".")) continue;
                if (!d.isDirectory() && !d.mkdirs()) throw new Exception("No se pudo crear " + d);
                FileOutputStream os = new FileOutputStream(new File(d, n));
                try { os.write(e.getValue()); } finally { os.close(); }
            }
        } finally { reload(); }
    }
}
