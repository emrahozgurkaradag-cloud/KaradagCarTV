#!/usr/bin/env bash
# Android Auto + Desktop Head Unit (DHU) keşif/test betiği (CI emülatörü).
# Gerçek araç DEĞİLDİR. Amaç: gerçek Android Auto uygulamasının (gearhead)
# bir head unit'e bağlanıp KaradagCarTV projection servisini KENDİSİNİN
# başlatıp başlatmadığını ve araç akışında görüntü olup olmadığını görmek.
set -uo pipefail
OUT="${DHU_OUT:-dhu-out}"
mkdir -p "$OUT"
GH=com.google.android.projection.gearhead
DHU="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/extras/google/auto/desktop-head-unit"
log() { echo "[dhu-probe] $*"; }

log "Cihaz: $(adb shell getprop ro.product.model | tr -d '\r') API $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
log "root: $(adb shell id -u | tr -d '\r')"

APK="$(ls app/build/outputs/apk/projection/debug/*.apk | head -1)"
adb install -r -g "$APK" >/dev/null && log "projection APK kuruldu"

adb shell dumpsys package $GH | tr -d '\r' > "$OUT/gearhead-package.txt"
log "gearhead $(grep -m1 versionName "$OUT/gearhead-package.txt" | xargs)"
log "== gearhead bileşenleri (head unit / developer / server / projection)"
grep -oE "$GH/[A-Za-z0-9_.\$]+" "$OUT/gearhead-package.txt" | sort -u > "$OUT/gearhead-components.txt"
grep -iE "headunit|head_unit|server|developer|hu\b|wifi|usb|projection|carservice|setup|firstrun|frx" "$OUT/gearhead-components.txt" | head -60

log "== gearhead shared_prefs"
adb shell ls /data/data/$GH/shared_prefs 2>&1 | tr -d '\r' | head -40
for f in $(adb shell ls /data/data/$GH/shared_prefs 2>/dev/null | tr -d '\r'); do
  adb shell cat /data/data/$GH/shared_prefs/$f 2>/dev/null | tr -d '\r' > "$OUT/prefs-$f"
done
grep -ihE "developer|unknown|head|hu_|server|frx|setup|accepted|terms" "$OUT"/prefs-* 2>/dev/null | head -30

log "== Head unit server başlatma denemeleri"
adb logcat -c
for comp in $(grep -iE "HeadUnitServer|HeadunitServer|HeadUnitService|DeveloperHeadUnit|HuServer|headunit" "$OUT/gearhead-components.txt"); do
  log "startservice $comp"
  adb shell am startservice -n "$comp" 2>&1 | tr -d '\r' | head -3
  adb shell am start-foreground-service -n "$comp" 2>&1 | tr -d '\r' | head -3
done
sleep 5
log "== 5277 dinleniyor mu"
adb shell "cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | grep -i ':149D'" | head -5   # 5277 = 0x149D

adb forward tcp:5277 tcp:5277
if [ -x "$DHU" ]; then
  log "== DHU (headless, SDL dummy) 90 sn"
  ( sleep 20; echo "help"; sleep 20; echo "screenshot $PWD/$OUT/dhu-screen-1.png"; sleep 30; echo "screenshot $PWD/$OUT/dhu-screen-2.png"; sleep 15; echo "quit" ) | \
    SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy timeout 100 "$DHU" --headless -a 127.0.0.1:5277 \
      -c "${ANDROID_HOME:-$ANDROID_SDK_ROOT}/extras/google/auto/config/default_sensors.ini" > "$OUT/dhu.txt" 2>&1
  grep -v "^ALSA" "$OUT/dhu.txt" | head -120
else
  log "DHU yok"
fi

log "== logcat (gearhead / Karadag / projection)"
adb logcat -d -v time > "$OUT/logcat.txt"
grep -iE "CAR\.|gearhead|KaradagCar|KaradagProjection|Projection|HeadUnit|CarActivity|DynamicApiFactory" "$OUT/logcat.txt" | head -150
exit 0
