#!/usr/bin/env bash
# Emülatör/cihaz üzerinde kurulum denetimleri (CI'da GitHub Actions emülatörü).
# Gerçek araç veya Android Auto head unit DEĞİLDİR.
set -uo pipefail
OUT="app/build/outputs/apk"
fail=0
err() { echo "::error::$*"; fail=1; }

PLAY="$(ls $OUT/play/debug/*.apk | head -1)"
PROJ="$(ls $OUT/projection/debug/*.apk | head -1)"

echo "== Cihaz: $(adb shell getprop ro.product.model) API $(adb shell getprop ro.build.version.sdk)"

adb install -r -g "$PLAY" || err "play kurulamadı"
adb install -r -g "$PROJ" || err "projection kurulamadı"

pkgs="$(adb shell pm list packages | tr -d '\r')"
echo "$pkgs" | grep -qx "package:com.karadaglabs.cartv" || err "play paketi yok"
echo "$pkgs" | grep -qx "package:com.karadaglabs.cartv.projection" || err "projection paketi yok"
echo "Yan yana kurulu: $(echo "$pkgs" | grep karadaglabs | tr '\n' ' ')"

for p in com.karadaglabs.cartv com.karadaglabs.cartv.projection; do
  adb logcat -c
  adb shell am start -W -n "$p/com.karadaglabs.cartv.RegistrationActivity" | tr -d '\r'
  sleep 6
  pid="$(adb shell pidof "$p" | tr -d '\r')"
  [ -n "$pid" ] || err "$p açılışta çalışmıyor (çöktü?)"
  if adb logcat -d -b crash | grep -q "$p"; then
    adb logcat -d -b crash | grep -A20 "$p" | head -40
    err "$p çökme kaydı var"
  else
    echo "$p açıldı, pid=$pid, çökme yok"
  fi
  adb shell am force-stop "$p"
done

echo "== projection servisleri (PackageManager)"
adb shell dumpsys package com.karadaglabs.cartv.projection | tr -d '\r' | \
  grep -E "CATEGORY_PROJECTION|CarVideoService|CarMediaService|CarMirrorService|CAR_SPEED" | head -20

echo "== CAR_SPEED izni bu cihazda tanımlı mı / koruma seviyesi"
adb shell dumpsys package permission com.google.android.gms.permission.CAR_SPEED | tr -d '\r' | head -15 || true

echo "== Android Auto uygulaması (gearhead) cihazda mı"
if adb shell pm path com.google.android.projection.gearhead | grep -q package:; then
  echo "gearhead KURULU"
else
  echo "gearhead KURULU DEĞİL: aauto.aar CarActivity sınıflarını çalışma anında gearhead'den yükler; bu cihazda Android Auto oturumu ve DHU testi yapılamaz."
fi

exit $fail
