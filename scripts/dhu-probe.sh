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
  adb shell cat /data/data/$GH/shared_prefs/$f 2>/dev/null | tr -d '\r' > "$OUT/prefs-$(echo "$f" | tr ':' '_')"
done
grep -ihE "developer|unknown|head|hu_|server|frx|setup|accepted|terms" "$OUT"/prefs-* 2>/dev/null | head -30

log "== Head unit server arama: gearhead ve GMS dex dizeleri / bileşenleri"
SDKD="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
DEXDUMP="$(ls -d "$SDKD"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1)"
export LC_ALL=C
for pkg in $GH com.google.android.gms; do
  W="$OUT/scan-$pkg"; mkdir -p "$W"
  i=0; for p in $(adb shell pm path $pkg | tr -d '\r' | sed 's/^package://'); do adb pull "$p" "$W/a$i.apk" >/dev/null 2>&1; i=$((i+1)); done
  for a in "$W"/*.apk; do unzip -o -q "$a" 'classes*.dex' -d "$W/$(basename "$a" .apk)" 2>/dev/null; done
  for d in $(find "$W" -name 'classes*.dex'); do
    for s in "head unit server" "Head unit server" "HeadUnitServer" "headunit_server" "HEAD_UNIT_SERVER" "start_head_unit_server" "5277" "DeveloperSettings" "developer_settings" "car_developer"; do
      grep -aoE "[A-Za-z0-9_./\$ -]{0,40}$s[A-Za-z0-9_./\$ -]{0,40}" "$d" 2>/dev/null | head -3 | sed "s#^#  [$pkg $(basename "$d")] #"
    done
  done | sort -u | head -60
  rm -f "$W"/*.apk
done
adb shell dumpsys package com.google.android.gms | tr -d '\r' | grep -oE "com.google.android.gms/[A-Za-z0-9_.\$]*(car|Car)[A-Za-z0-9_.\$]*" | sort -u | head -60 > "$OUT/gms-car-components.txt"
log "GMS car bileşenleri:"; cat "$OUT/gms-car-components.txt"

log "== Arayüz otomasyonu: Android Auto ayarları -> geliştirici modu -> Start head unit server"
UI="$(dirname "$0")/aa-ui.py"
shot() { adb exec-out screencap -p > "$OUT/ui-$1.png" 2>/dev/null; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui-$1.xml" >/dev/null 2>&1; }
tap() { # tap <step> <regex>
  dump "$1"; local r; r="$(python3 "$UI" "$OUT/ui-$1.xml" "$2")" || return 1
  local x y lbl; read -r x y lbl <<<"$r"; adb shell input tap "$x" "$y"; log "tap '$2' -> ${lbl} ($x,$y)"; sleep 2; return 0
}
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null
read SW SH < <(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr 'x' ' ')
log "ekran ${SW}x${SH}"
adb shell am start -n $GH/.companion.settings.DefaultSettingsActivity >/dev/null 2>&1; sleep 6; shot 01-settings
# İlk kurulum / izin / kabul ekranları (birkaç tur)
for i in 1 2 3 4 5; do
  tap "02-setup-$i" "^(Accept|Continue|Got it|OK|Allow|Agree|Kabul|Devam|İzin ver|Next|Skip|Not now|Later)$" || break
  shot "02-setup-$i"
done
dump 03-settings; grep -oE 'text="[^"]{2,60}"' "$OUT/ui-03-settings.xml" | head -30
# Sürüm satırını bul (kaydırarak) ve 10 kez dokun
found=""
for i in 1 2 3 4 5 6 7 8 9 10; do
  dump "04-scan-$i"
  if r="$(python3 "$UI" "$OUT/ui-04-scan-$i.xml" "^Version|^Sürüm|^Version [0-9]")"; then found="$r"; break; fi
  adb shell input swipe $((SW/2)) $((SH*85/100)) $((SW/2)) $((SH*15/100)) 400; sleep 1
done
if [ -n "$found" ]; then
  set -- $found; log "Sürüm satırı: $3 $4 ($1,$2)"
  for k in $(seq 1 10); do adb shell input tap "$1" "$2"; sleep 0.3; done
  sleep 2; shot 05-after-version-taps
  tap 06-devdialog "^(OK|Allow|Tamam|İzin ver)$" && shot 06-devdialog
  # Geliştirici menüsü ayarlar yeniden açılınca görünür (AA 4.3'te diyalog çıkmıyor).
  adb shell am force-stop $GH; sleep 1
  adb shell am start -n $GH/.companion.settings.DefaultSettingsActivity >/dev/null 2>&1; sleep 5; shot 06b-reopened
  dump 06b-reopened; log "araç çubuğu: $(grep -oE 'content-desc="[^"]+"' "$OUT/ui-06b-reopened.xml" | tr '\n' ' ')"
  # Geliştirici ayarları -> "Unknown sources" (Play dışı kurulum; AA 4.x/5.x)
  if tap 07a-overflow "More options|Diğer seçenekler" && tap 07b-devsettings "^Developer settings$|Geliştirici ayarları"; then
    shot 07b-devsettings
    us=""
    for i in 1 2 3 4 5 6 7 8; do
      dump "07c-scan-$i"
      if us="$(python3 "$UI" "$OUT/ui-07c-scan-$i.xml" "^Unknown sources$|Bilinmeyen kaynaklar")"; then break; fi
      us=""; adb shell input swipe $((SW/2)) $((SH*85/100)) $((SW/2)) $((SH*15/100)) 400; sleep 1
    done
    if [ -n "$us" ]; then set -- $us; adb shell input tap "$1" "$2"; log "Unknown sources dokunuldu ($1,$2)"; sleep 2; shot 07d-unknown-sources
      tap 07e-confirm "^(OK|Allow|Tamam)$" && shot 07e-confirm
    else log "Unknown sources bulunamadı"; dump 07c-last; grep -oE 'text="[^"]{2,60}"' "$OUT/ui-07c-last.xml" | head -40; fi
    adb shell input keyevent KEYCODE_BACK; sleep 2
  fi
  tap 07-overflow "More options|Diğer seçenekler" && shot 07-overflow
  if tap 08-hus "Start head unit server|Ana birim sunucusunu başlat|head unit server"; then shot 08-hus
  else dump 08-menu; grep -oE 'text="[^"]{2,60}"' "$OUT/ui-08-menu.xml" | head -20; fi
else
  log "Sürüm satırı bulunamadı (Android Auto ilk kurulumu tamamlanmamış olabilir)"
fi
shot 09-final
log "== 5277 dinleyen soket (hex 149D)"
adb shell "grep -i ':149D' /proc/net/tcp /proc/net/tcp6" 2>/dev/null | head -5

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
CFG="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/extras/google/auto/config/default_sensors.ini"

# DHU'yu arka planda FIFO'dan komut okuyacak şekilde çalıştırır; oturum
# sırasında telefon tarafında onay ekranlarını geçer ve ekran görüntüleri alır.
run_dhu() { # run_dhu <tag> <seconds> [faketime-date]
  local tag="$1" secs="$2" when="${3:-}"
  local pre=(); [ -n "$when" ] && command -v faketime >/dev/null && pre=(faketime "$when")
  local fifo="$OUT/dhu-$tag.fifo"; rm -f "$fifo"; mkfifo "$fifo"
  SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy timeout $((secs+15)) "${pre[@]}" "$DHU" --headless -a 127.0.0.1:5277 -c "$CFG" \
    < "$fifo" > "$OUT/dhu-$tag.txt" 2>&1 &
  local pid=$!
  exec 3> "$fifo"
  send() { echo "$1" >&3 2>/dev/null; log "DHU <- $1"; }
  sleep 8; send "help"
  local t=8 n=0
  while [ $t -lt "$secs" ] && kill -0 $pid 2>/dev/null; do
    n=$((n+1))
    if grep -qE "auth failure|Failed to read from transport" "$OUT/dhu-$tag.txt"; then break; fi
    [ $n -eq 1 ] && send "focus video"
    # Telefon: Android Auto onay/izin/güncelleme ekranları
    shot "phone-$tag-$n"
    tap "phone-$tag-$n" "^(Accept|Continue|Got it|OK|Allow|Agree|Kabul|Devam|İzin ver|Next|Not now|Later|Skip|Done|Turn on|While using the app|Only this time)$" || true
    send "screenshot $PWD/$OUT/dhu-$tag-$n.png"
    sleep 12; t=$((t+15))
  done
  send "quit"; sleep 3; exec 3>&-
  kill $pid 2>/dev/null; wait $pid 2>/dev/null
  rm -f "$fifo"
  grep -v "^ALSA" "$OUT/dhu-$tag.txt" | grep -vE "^\s+\"|^\t" | head -60
}
if [ -x "$DHU" ]; then
  log "== DHU gerçek saatle (sertifika kontrolü)"
  run_dhu now 45
  if grep -qE "certificate has expired|certificate is not yet valid|auth failure" "$OUT/dhu-now.txt"; then
    # Android Auto 5.3'ün gömülü sertifikası bugünün tarihinde süresi dolmuş
    # (CI 45849636694: 2021/2022'de "expired", 2020-10-01'de "Verify ok").
    adb shell settings put global auto_time 0
    for when in "2020-10-01 12:00:00" "2020-12-01 12:00:00"; do
      tag="t$(echo "$when" | cut -c1-10 | tr -d '-')"
      adb shell date "$(date -d "$when" +%m%d%H%M%Y.%S)" >/dev/null 2>&1
      log "emülatör saati: $(adb shell date | tr -d '\r') | DHU faketime: $when"
      adb shell am force-stop $GH; sleep 2
      adb shell am start -n $GH/.companion.settings.DefaultSettingsActivity >/dev/null 2>&1; sleep 4
      tap "hus-$tag" "More options|Diğer seçenekler" >/dev/null && tap "hus2-$tag" "Start head unit server|head unit server" >/dev/null
      sleep 3; adb forward tcp:5277 tcp:5277 >/dev/null
      adb logcat -c
      log "== DHU faketime $when (oturum 180 sn)"
      run_dhu "$tag" 180 "$when"
      adb logcat -d -v time > "$OUT/logcat-$tag.txt"
      log "== Oturum logcat (CAR./Karadag/gearhead)"
      grep -E "CAR\.|KaradagCar|KaradagProjection|karadaglabs|DynamicApiFactory|ApiFactory" "$OUT/logcat-$tag.txt" | cut -c1-230 | tail -120
      if grep -q "Verify returned: ok" "$OUT/dhu-$tag.txt"; then log "Sertifika bu tarihte geçerli: $when"; break; fi
    done
  fi
else
  log "DHU yok"
fi

log "== logcat (gearhead / Karadag / projection)"
adb logcat -d -v time > "$OUT/logcat.txt"
grep -iE "KaradagCar|KaradagProjection|CarVideoService" "$OUT/logcat.txt" | head -60
exit 0
