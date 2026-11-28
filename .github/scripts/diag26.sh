#!/bin/bash
# Diagnóstico temporal del Bloque 26: cuelgue del emulador de Android 14 con Sport Cockpit y el video.
# Uso: diag26.sh API APK CARPETA
API=$1; APK=$2; OUT=$3; mkdir -p $OUT
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
size() {  # tamaño de la pantalla, densidad 160
  if [ "$API" -ge 18 ]; then adb shell wm size $1; adb shell wm density 160
  else adb shell am display-size $1; adb shell am display-density 160; fi
  sleep 6
}
size 1024x600
if [ "$API" -ge 21 ]; then
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell sysctl -w net.ipv6.conf.all.disable_ipv6=1 >/dev/null 2>&1
  adb shell sysctl -w net.ipv6.conf.default.disable_ipv6=1 >/dev/null 2>&1
  sleep 3
fi
if [ "$API" -ge 23 ]; then adb install -r -g "$APK" | tail -1; else adb install -r "$APK" | tail -1; fi
shot() {
  rm -f /tmp/shot.png; rm -rf /tmp/emushot; mkdir -p /tmp/emushot
  adb shell screencap -p /data/local/tmp/s.png >/dev/null 2>&1 && adb pull /data/local/tmp/s.png /tmp/shot.png >/dev/null 2>&1
  if [ ! -s /tmp/shot.png ] || [ "$(stat -c %s /tmp/shot.png)" -lt 15000 ]; then
    adb emu screenrecord screenshot /tmp/emushot >/dev/null 2>&1; sleep 2
    f=$(ls -t /tmp/emushot/*.png 2>/dev/null | head -1)
    [ -n "$f" ] && cp "$f" /tmp/shot.png
  fi
  [ -s /tmp/shot.png ] && cp /tmp/shot.png "$OUT/$1.png" && echo "captura $1"
}
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-5}"; }
adb logcat -v time > $OUT/logcat-todo.txt 2>&1 &
LC=$!
info() {
  echo "=== $1 $(date +%T)" >> $OUT/info.txt
  timeout 20 adb shell top -b -n 1 2>/dev/null | head -14 >> $OUT/info.txt
  timeout 10 adb shell ps -A 2>/dev/null | grep -i strada >> $OUT/info.txt
  timeout 10 adb shell ls -l /data/anr 2>/dev/null >> $OUT/info.txt
}
gi() { timeout 30 adb shell am start -n $ACT --es prueba "$1" >/dev/null 2>&1; sleep "${2:-5}"; info "$1"; shot "$3"; }
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
gi idioma:es 6 d00
gi tema-premium:cockpit 10 d01
gi pista-demo:Night_Drive 4 d02
gi pref:center_card=both 6 d03
gi centro:video 8 d04
gi video-modo:1 6 d05
gi video-modo:0 6 d06
gi centro:mapa 8 d07
gi explorar:1 5 d08
gi explorar:0 5 d09
gi pagina:1 6 d10
gi pagina:1 6 d11
gi pref:center_card= 6 d12
gi pref:ck_dock= 6 d13
gi pref:ck_player= 6 d14
gi tema:claro 6 d15
gi cabina:1a 4 d16
gi cabina:0a 4 d17
adb shell input swipe 700 230 330 230 300; sleep 3; info swipe1; shot d18-desliza
adb shell input swipe 330 230 700 230 300; sleep 3; info swipe2; shot d19-desliza-atras
sleep 5
timeout 60 adb pull /data/anr $OUT/anr >/dev/null 2>&1
timeout 30 adb shell dumpsys gfxinfo com.stradaos.launcher.debug > $OUT/gfxinfo.txt 2>&1
kill $LC 2>/dev/null
grep -E "stradaos|AndroidRuntime|FATAL|ANR|libc|DEBUG|hwui|RenderThread|BLAST|swiftshader|vulkan|Vulkan|EGL|gralloc" $OUT/logcat-todo.txt | tail -400 > $OUT/logcat-filtro.txt
ls -l $OUT
