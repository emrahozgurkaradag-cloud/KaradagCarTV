#!/usr/bin/env bash
# Emülatördeki Android Auto (gearhead) APK'sını yerinde analiz eder.
# APK dışarı yüklenmez; yalnızca özet loglanır. Amaç: aauto.aar'ın
# çalışma anında yüklediği host kodunun araç penceresini hangi Android
# API'leriyle oluşturduğunu kanıtlamak (bu sürüm için).
set -uo pipefail
PKG=com.google.android.projection.gearhead
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
DEXDUMP="$(ls -d "$SDK"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

paths="$(adb shell pm path "$PKG" 2>/dev/null | tr -d '\r' | sed 's/^package://')"
if [ -z "$paths" ]; then echo "gearhead yok: analiz atlandı"; exit 0; fi
echo "gearhead $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 versionName | xargs)"
i=0
for p in $paths; do adb pull "$p" "$WORK/g$i.apk" >/dev/null && i=$((i+1)); done
mkdir -p "$WORK/dex"
for a in "$WORK"/g*.apk; do unzip -o -q "$a" 'classes*.dex' -d "$WORK/dex/$(basename "$a" .apk)" 2>/dev/null || true; done
dexes="$(find "$WORK/dex" -name 'classes*.dex')"
if [ -z "$dexes" ]; then echo "gearhead APK'sında dex yok (taslak/stub paket): host kodu YOK"; exit 0; fi
echo "dex dosyaları: $(echo "$dexes" | wc -l)"

echo "== Dize kanıtları (dex string tablosu)"
for s in com.google.android.gms.car.DynamicApiFactory newCarActivityServiceProxy \
         com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM \
         com.google.android.gms.car.category.CATEGORY_PROJECTION \
         com.google.android.gms.car.application CAR.VALIDATOR allow_unknown_sources; do
  n=0; for d in $dexes; do grep -aqF "$s" "$d" && n=$((n+1)); done
  echo "  '$s' -> $n dex"
done

[ -x "$DEXDUMP" ] || { echo "dexdump yok"; exit 0; }
DUMP="$WORK/dump.txt"
for d in $dexes; do "$DEXDUMP" -d "$d" 2>/dev/null; done > "$DUMP"

echo "== DynamicApiFactory sınıfı ve newCarActivityServiceProxy dönüş tipi"
grep -nE "Class descriptor *: 'Lcom/google/android/gms/car/DynamicApiFactory;'" "$DUMP" | head -3
grep -oE "Lcom/google/android/gms/car/DynamicApiFactory;\.newCarActivityServiceProxy:\([^)]*\)[^ ]*" "$DUMP" | sort -u | head -3

echo "== Araç penceresi için kullanılan Android API çağrıları (sınıf bazında)"
for api in "Landroid/hardware/display/DisplayManager;.createVirtualDisplay" \
           "Landroid/app/Presentation;.<init>" \
           "Landroid/view/Surface;.<init>:(Landroid/graphics/SurfaceTexture;)V" \
           "Landroid/media/MediaCodec;.createInputSurface" \
           "Landroid/media/MediaCodec;.createEncoderByType" \
           "Landroid/view/WindowManager;.addView"; do
  classes="$(awk -v api="$api" '
    /Class descriptor/ { cls=$4 }
    index($0, api) { print cls }' "$DUMP" | sort -u)"
  echo "  $api -> $(echo "$classes" | grep -c . ) sınıf: $(echo "$classes" | head -5 | tr '\n' ' ')"
done

echo "== H.264 kodlayıcı / video projection dizeleri"
for s in video/avc "OMX." "CAR.VIDEO" "VideoEncoder" "projection" "Presentation"; do
  n=0; for d in $dexes; do grep -aqF "$s" "$d" && n=$((n+1)); done
  echo "  '$s' -> $n dex"
done
