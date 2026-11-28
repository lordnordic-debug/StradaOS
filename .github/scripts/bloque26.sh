#!/bin/bash
# Bloque 26 (2.40): tema premium «Sport Cockpit» (en StradaOS Prueba se aplica sin código).
# Uso: bloque26.sh API APK CARPETA
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
# Medellín, a unos 48 km/h (26 nudos): mapa, clima y velocidad
( while true; do adb emu geo fix -75.5680 6.2442 >/dev/null 2>&1; adb emu geo fix -75.5680 6.2442 1495 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go demo-aceite 4; go demo-mant 4
demo() { go inicio 3; go pista-demo:Night_Drive 3; }
go tema-premium:cockpit 8
sleep 20   # que lleguen el clima y las teselas
demo; shot c01-cabina-oscuro
go tema:claro 7; demo; shot c02-cabina-claro
go tema:oscuro 6; demo
# deslizar sobre la píldora: Cabina → Mapa (animación a medias y al final) → Video no (sin «Mapa y video») → Cabina
adb shell input swipe 700 230 330 230 200; sleep 0.25; shot c03-a-mapa-medio
sleep 2; shot c04-mapa
go tema:claro 7; demo; shot c05-mapa-claro
go tema:oscuro 6; demo
adb shell input swipe 330 230 700 230 200; sleep 3; shot c06-vuelve-cabina
# tocar el mapa redondo lo agranda; tocar el velocímetro chico vuelve
adb shell input tap 838 230; sleep 2; shot c07-toque-mapa
adb shell input tap 190 320; sleep 2; shot c08-toque-velocimetro
# con «Mapa y video»: Cabina → Mapa → Video → Cabina
go pref:center_card=both 5; demo; go cabina:0 3
adb shell input swipe 700 230 330 230 200; sleep 2; shot c10-carrusel-mapa
adb shell input swipe 700 230 330 230 200; sleep 3; shot c11-carrusel-video
adb shell input swipe 700 230 330 230 200; sleep 3; shot c12-carrusel-vuelve-cabina
adb shell input swipe 330 230 700 230 200; sleep 3; shot c13-carrusel-atras-video
adb shell input swipe 330 230 700 230 200; sleep 3; shot c14-carrusel-atras-mapa
go pref:center_card= 5; go cabina:0 3
# barra de apps y reproductor de lado
go pref:ck_dock=right 5; demo; shot c20-barra-derecha
go pref:ck_dock=bottom 5; demo; shot c21-barra-abajo
go tema:claro 7; demo; shot c22-barra-abajo-claro
go tema:oscuro 6
go pref:ck_dock= 5
go pref:ck_player=right 5; demo; shot c23-reproductor-derecha
go pref:ck_player= 5
go pref:dock_count=10 5; demo; shot c24-diez-botones
go pref:dock_count=7 4
go pref:dock_labels=false 5; demo; shot c25-sin-nombres
go pref:dock_labels= 4
go pref:dock_mode=none 5; demo; shot c26-sin-barra
go pref:dock_mode= 5
# acentos
go pref:accent=-13665034 5; demo; shot c30-acento-azul
go pref:accent=-13779348 5; demo; shot c31-acento-verde
go pref:accent= 5
# casos
go pref:maint= 4; demo; shot c40-solo-aceite
go pref:oil_on=false 5; demo; shot c41-sin-mantenimientos
go demo-aceite 4; go demo-mant 4
go pref:weather_on=false 5; demo; shot c42-sin-clima
go pref:weather_on=true 5
go pref:show_player=false 5; shot c43-sin-reproductor
go pref:show_player=true 5
go pref:conn_buttons=aa 5; demo; shot c44-solo-aa
go pref:conn_buttons= 5
go pref:clock=s:12 5; demo; shot c45-reloj-12h
go pref:clock= 5
go pref:font_size=3 5; demo; shot c46-letra-enorme
go pref:font_size= 5
go pref:speed_unit=mph 5; demo; shot c47-mph
go pref:speed_unit= 5
# revoluciones con el auto simulado del escáner
go pref:speedo_rpm=true 3; go obd-demo 12; sleep 30; go obd-vel-demo:1 4; demo; sleep 4; shot c50-revoluciones
go obd-off 4; go obd-vel-demo:0 2; go pref:speedo_rpm= 3
# navegación: pasa sola a la vista Mapa
go cabina:0 3
go pref:nav_app=internal 5; go inicio 4; go 'nav-ir:6.2702,-75.5660,Parque\ Explora' 25; go pista-demo:Night_Drive 3; sleep 4; shot c60-navegando
go tema:claro 7; go pista-demo:Night_Drive 3; sleep 4; shot c61-navegando-claro
go tema:oscuro 6
go nav-salir 4; go pref:nav_app= 5; go cabina:0 3
# galería y ajustes
go ajustes:11 5; for i in 1 2 3 4 5; do adb shell input swipe 600 520 600 80 400; sleep 2; done; shot c70-galeria
adb shell input swipe 600 520 600 80 400; sleep 2; shot c70b-galeria-2
go ajustes:3 5; shot c71-pantalla-inicio
go inicio 4
go idioma:en 6; demo; shot c80-ingles
go idioma:ar 6; demo; shot c81-arabe
go idioma:de 6; demo; shot c82-aleman
go idioma:es 6
if [ "$API" -ge 18 ]; then
  size 1280x480; demo; shot c90-1280x480
  size 1920x720; demo; shot c91-1920x720
  go cabina:1 3; shot c91b-1920x720-mapa; go cabina:0 3
  size 800x480; demo; shot c92-800x480
  size 1280x720; demo; shot c93-1280x720
  size 1080x1920; demo; shot c94-vertical
  go tema:claro 7; demo; shot c95-vertical-claro
  go tema:oscuro 6
  go cabina:1 3; shot c96-vertical-mapa; go cabina:0 3
  go pref:ck_dock=bottom 5; demo; shot c97-vertical-abajo; go pref:ck_dock= 5
  size 768x1024; demo; shot c98-vertical-768x1024
  size 1024x600
else
  go sim:5 6; demo; shot c90-1280x480
  go sim:7 6; demo; shot c94-vertical
  go sim:0 6
fi
go tema-premium:cards 6; demo; shot c99a-cards-sigue-igual
go tema-premium:carbono 6; demo; shot c99b-carbono-sigue-igual
go tema-premium:faro 6; demo; shot c99c-faro-sigue-igual
go inicio 5
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
