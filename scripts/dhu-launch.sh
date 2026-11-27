#!/usr/bin/env bash
# Android Auto'nun KaradagCarTV'yi KENDİSİNİN açıp açmadığını ve araç
# akışında görüntü olup olmadığını Desktop Head Unit ile sınar (CI emülatörü).
# Gerçek araç DEĞİLDİR.
#
# Girdi (ortam):
#   AA_DIR      : güncel Android Auto APK/XAPK dosyalarının bulunduğu dizin
#   STREAM_URL  : emülatörden erişilen TEST yayını (CI'da ffmpeg ile üretilir,
#                 10.0.2.2 üzerinden sunulur; gerçek/yetkisiz URL değildir)
set -uo pipefail
trap '' PIPE
OUT="${DHU_OUT:-dhu-launch-out}"; mkdir -p "$OUT"
GH=com.google.android.projection.gearhead
PKG=com.karadaglabs.cartv.projection
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
export DHU_BIN="$SDK/extras/google/auto/desktop-head-unit"
export DHU_CFG="$SDK/extras/google/auto/config/default_sensors.ini"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)"
UI="$(dirname "$0")/aa-ui.py"
log() { echo "[dhu-launch] $*"; }
shot() { adb exec-out screencap -p > "$OUT/phone-$1.png" 2>/dev/null; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/phone-$1.xml" >/dev/null 2>&1; }
tap() { dump "$1"; local r; r="$(python3 "$UI" "$OUT/phone-$1.xml" "$2")" || return 1
  local x y lbl; read -r x y lbl <<<"$r"; adb shell input tap "$x" "$y"; log "tap '$2' -> $lbl"; sleep 2; }

log "cihaz API $(adb shell getprop ro.build.version.sdk | tr -d '\r'), ABI $(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device

# --- 1. Güncel Android Auto: imza önceden kurulu paketle AYNI olmalı -------
before="$(adb shell dumpsys package $GH | tr -d '\r' | grep -m1 versionName | xargs)"
log "önceden kurulu gearhead: $before"
adb pull "$(adb shell pm path $GH | head -1 | tr -d '\r' | sed 's/^package://')" "$OUT/gearhead-preinstalled.apk" >/dev/null 2>&1
ref="$("$APKSIGNER" verify --print-certs "$OUT/gearhead-preinstalled.apk" 2>/dev/null | grep -m1 'SHA-256 digest' | awk '{print $NF}')"
log "önceden kurulu imza SHA-256: $ref"
W="$OUT/aa"; rm -rf "$W"; mkdir -p "$W"
for f in "$AA_DIR"/*; do
  case "$f" in *.xapk|*.apks|*.zip) unzip -o -q "$f" '*.apk' -d "$W" ;; *.apk) cp "$f" "$W/" ;; esac
done
ls -la "$W" | sed 's/^/  /'
abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r' | tr '-' '_')"
sel=()
for a in "$W"/*.apk; do
  b="$(basename "$a")"
  if echo "$b" | grep -qE 'config\.(arm64_v8a|armeabi_v7a|armeabi|x86_64|x86)\.apk$'; then
    echo "$b" | grep -q "config.$abi.apk" && sel+=("$a")
  else sel+=("$a"); fi
done
# Bu ABI'ye ait bölünmüş paket yoksa arm64 (emülatör ARM çevirisiyle) dene.
if ! printf '%s\n' "${sel[@]}" | grep -qE 'config\.(x86_64|x86|arm64_v8a|armeabi_v7a)\.apk'; then
  for a in "$W"/*config.arm64_v8a.apk; do [ -f "$a" ] && sel+=("$a"); done
fi
base="$(printf '%s\n' "${sel[@]}" | grep -vE 'config\.' | head -1)"
new="$("$APKSIGNER" verify --print-certs "$base" 2>/dev/null | grep -m1 'SHA-256 digest' | awk '{print $NF}')"
ver="$(aapt2 dump badging "$base" 2>/dev/null | grep -oE "versionName='[^']+'" | head -1)"
[ -z "$ver" ] && ver="$("$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)" dump badging "$base" 2>/dev/null | grep -oE "versionName='[^']+'" | head -1)"
log "indirilen Android Auto: $ver imza SHA-256: $new"
AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)"
log "indirilen paket SDK: $("$AAPT2" dump badging "$base" 2>/dev/null | grep -oE "(sdkVersion|targetSdkVersion):'[0-9]+'" | tr '\n' ' ')"
"$APKSIGNER" verify --print-certs --verbose "$base" > "$OUT/aa-signers.txt" 2>&1
"$APKSIGNER" lineage --in "$base" --print-certs > "$OUT/aa-lineage.txt" 2>&1
log "imza şemaları: $(grep -E 'Verified using v[0-9.]+ scheme' "$OUT/aa-signers.txt" | tr '\n' ' ')"
log "rotasyon geçmişi (lineage): $(grep -E 'SHA-256' "$OUT/aa-lineage.txt" | awk '{print $NF}' | tr '\n' ' ')"
# SDK'nın (aauto.aar gms.car.i) güvendiği sertifikalar: Google gearhead
# release fdb00c43… ve AOSP test anahtarı 1975b2f1… (emülatör taslakları).
# Kabul: indirilen imza ya da rotasyon geçmişi, cihazdaki paketin imzasını içermeli.
GOOGLE_GEARHEAD=fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf
if [ -n "$new" ] && { [ "$new" = "$GOOGLE_GEARHEAD" ] || grep -qi "$GOOGLE_GEARHEAD" "$OUT/aa-lineage.txt"; }; then
  log "imza doğrulandı: Google gearhead release sertifikası (fdb00c43…) indirilen paketin imzası veya rotasyon geçmişinde"
  if [ "$ref" != "$new" ] && ! grep -qi "$ref" "$OUT/aa-lineage.txt"; then
    # Emülatör imajındaki taslak AOSP test anahtarıyla imzalı: Google imzalı
    # sürüm güncelleme olarak kurulamaz. Yazılabilir sistem imajından kaldırılır.
    sysdir="$(adb shell pm path $GH | tr -d '\r' | sed 's/^package://' | head -1 | xargs dirname)"
    log "test anahtarlı sistem paketi kaldırılıyor: $sysdir"
    adb shell pm uninstall $GH >/dev/null 2>&1
    for k in 1 2; do
      r="$(adb remount 2>&1)"; log "remount: $(echo "$r" | tail -1)"
      if echo "$r" | grep -qiE "reboot|verity"; then
        adb disable-verity >/dev/null 2>&1; adb reboot; adb wait-for-device
        until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
        adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
      else break; fi
    done
    for attempt in 1 2; do
      log "mount: $(adb shell "mount | grep -E ' /(product|system) '" | tr -d '\r' | tr '\n' ';' | cut -c1-300)"
      log "rm: $(adb shell "rm -rf '$sysdir' && echo silindi || echo HATA" 2>&1 | tr -d '\r'); kalan: $(adb shell "ls '$sysdir' 2>&1" | tr -d '\r' | head -1)"
      adb shell rm -rf /data/system/package_cache/* 2>/dev/null
      adb reboot; adb wait-for-device
      until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
      adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
      adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null
      left="$(adb shell pm path $GH | tr -d '\r')"
      log "sistem paketi sonrası ($attempt): '$left'"
      [ -z "$left" ] && break
      log "remount (yeniden): $(adb remount 2>&1 | tail -1)"
    done
  fi
  printf '%s\n' "${sel[@]}" | sed 's/^/  kurulacak: /'
  adb install-multiple -r -g -i com.android.vending "${sel[@]}" 2>&1 | tail -3
else
  log "UYARI: imza eşleşmedi veya okunamadı ($new != $ref); kurulum YAPILMADI"
fi
after="$(adb shell dumpsys package $GH | tr -d '\r' | grep -m1 versionName | xargs)"
log "kurulu gearhead: $after"
echo "gearhead_before=$before" > "$OUT/versions.txt"; echo "gearhead_after=$after" >> "$OUT/versions.txt"
adb shell dumpsys package com.google.android.gms | tr -d '\r' | grep -m1 versionName | sed 's/^ */gms_/' >> "$OUT/versions.txt"

# --- 2. KaradagCarTV projection (Play yükleyicisi olarak işaretli: test) --
APK="$(ls app/build/outputs/apk/projection/debug/*.apk | head -1)"
adb install -r -g -i com.android.vending "$APK" | tail -1
adb shell am force-stop $PKG
uid="$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')"
STREAM="${STREAM_URL:-http://10.0.2.2:8088/test.ts}"
CH="[{&quot;id&quot;:1,&quot;name&quot;:&quot;DHU Test Yayini&quot;,&quot;stream_url&quot;:&quot;$STREAM&quot;,&quot;logo_url&quot;:&quot;&quot;,&quot;category&quot;:&quot;News&quot;}]"
cat > "$OUT/prefs.xml" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="customer_email">dhu@test.invalid</string>
    <string name="license_key">DHU-CI-TEST</string>
    <string name="iptv_channels">$CH</string>
    <string name="iptv_last_update">CI</string>
    <int name="projection_last_channel_id" value="1" />
</map>
XML
adb shell mkdir -p /data/data/$PKG/shared_prefs
adb push "$OUT/prefs.xml" /data/data/$PKG/shared_prefs/karadag_car_tv_cache.xml >/dev/null
adb shell chown -R "$uid:$uid" /data/data/$PKG/shared_prefs; adb shell chmod 660 /data/data/$PKG/shared_prefs/karadag_car_tv_cache.xml
adb shell restorecon -R /data/data/$PKG/shared_prefs
log "test kanalı yazıldı: $STREAM (emülatörden HTTP: $(adb shell "echo -e 'HEAD /test.ts HTTP/1.0\\r\\n\\r\\n' | toybox nc -w 5 10.0.2.2 8088 2>&1 | head -1" | tr -d '\r'))"

# --- 3. Android Auto: geliştirici modu, bilinmeyen kaynaklar, HU sunucusu --
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null
read SW SH < <(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr 'x' ' ')
open_settings() {
  adb shell am force-stop $GH; sleep 1
  for a in .companion.settings.DefaultSettingsActivity com.google.android.apps.auto.carservice.settings.SettingsActivity; do
    adb shell am start -n "$GH/$a" 2>&1 | grep -q Error || { sleep 5; return 0; }
  done
  adb shell am start -a android.intent.action.APPLICATION_PREFERENCES -p $GH >/dev/null 2>&1; sleep 5
}
open_settings; shot 01-settings
for i in 1 2 3 4 5 6; do
  tap "02-setup-$i" "^(Accept|Continue|Got it|OK|Allow|Agree|Next|Skip|Not now|Later|Get started)$" || break
done
found=""
for i in $(seq 1 10); do
  dump "03-scan-$i"
  if r="$(python3 "$UI" "$OUT/phone-03-scan-$i.xml" "^Version|^Sürüm")"; then found="$r"; break; fi
  adb shell input swipe $((SW/2)) $((SH*85/100)) $((SW/2)) $((SH*15/100)) 400; sleep 1
done
if [ -n "$found" ]; then
  set -- $found; log "sürüm satırı: $3 $4"
  for k in $(seq 1 10); do adb shell input tap "$1" "$2"; sleep 0.3; done; sleep 2
  tap 04-devdialog "^(OK|Allow)$"; shot 04-dev
  open_settings
  if tap 05-overflow "More options" && tap 05-dev "^Developer settings$"; then
    us=""
    for i in $(seq 1 8); do dump "06-scan-$i"
      if us="$(python3 "$UI" "$OUT/phone-06-scan-$i.xml" "^Unknown sources$")"; then break; fi
      us=""; adb shell input swipe $((SW/2)) $((SH*85/100)) $((SW/2)) $((SH*15/100)) 400; sleep 1; done
    if [ -n "$us" ]; then set -- $us; adb shell input tap "$1" "$2"; sleep 2; tap 06-confirm "^(OK|Allow)$"; log "Unknown sources açıldı"; fi
    shot 06-devsettings; adb shell input keyevent KEYCODE_BACK; sleep 2
  fi
  tap 07-overflow "More options" && tap 07-hus "Start head unit server|head unit server" && log "head unit server başlatıldı"
else
  log "sürüm satırı bulunamadı"; dump 03-last; grep -oE 'text="[^"]{2,60}"' "$OUT/phone-03-last.xml" | head -30
fi
shot 08-final
sleep 3
log "5277: $(adb shell "grep -ci ':149D' /proc/net/tcp /proc/net/tcp6" | tr -d '\r' | tr '\n' ' ')"
adb forward tcp:5277 tcp:5277

# --- 4. DHU oturumu (keşif -> CarVideoActivity -> video -> park kilidi) --
export DHU_OUT="$OUT" APP_PKG="$PKG"
python3 "$(dirname "$0")/dhu_session.py"
if grep -qE "certificate has expired|not yet valid" "$OUT"/dhu-s1.txt 2>/dev/null; then
  log "DHU sertifika hatası: faketime ile yeniden deneniyor"
  adb shell settings put global auto_time 0
  adb shell date "$(date -d '2020-10-01 12:00:00' +%m%d%H%M%Y.%S)" >/dev/null
  DHU_FAKETIME="2020-10-01 12:00:00" python3 "$(dirname "$0")/dhu_session.py"
fi
adb logcat -d -v time > "$OUT/logcat-all.txt"
grep -E "KaradagCar|KaradagProjection|EVT=" "$OUT/logcat-all.txt" | cut -c1-260 > "$OUT/karadag-events.txt"
log "== Karadag olayları (son 80)"; tail -80 "$OUT/karadag-events.txt"
log "== Android Auto (CAR./GH.) önemli satırlar"
grep -E "CAR\.(SETUP|STARTUP|FIRST|PROJECTION|VALIDATOR|APP)|GH\.(App|Launcher|CarActivity)|karadaglabs|DynamicApiFactory|Update needed|gearheadUpToDate" "$OUT/logcat-all.txt" | cut -c1-230 | tail -60
log "== ÖZET"; cat "$OUT/summary.txt" 2>/dev/null
exit 0
