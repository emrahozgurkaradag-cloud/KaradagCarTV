#!/usr/bin/env bash
# KaradagCarTV APK doğrulaması (yerel ve GitHub Actions).
#  - play ve projection paket adları farklı mı (yan yana kurulum)
#  - provider authority'leri ve özel <permission> tanımları çakışıyor mu
#  - play APK'sında projection bileşenleri YOK mu
#  - projection APK'sında araç ekranı bileşenleri VAR mı
#  - release imza durumu (imzasızsa açıkça bildirir)
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
AAPT2="$BT/aapt2"
APKSIGNER="$BT/apksigner"
OUT="app/build/outputs/apk"
fail=0
err() { echo "::error::$*"; fail=1; }
note() { echo "$*"; }

find_apk() { ls "$OUT/$1/$2/"*.apk 2>/dev/null | head -1; }

pkg() { "$AAPT2" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p"; }
manifest() { "$AAPT2" dump xmltree --file AndroidManifest.xml "$1"; }
# Yalnızca <application> altındaki provider'lar (<queries> hariç).
authorities() {
  manifest "$1" | awk '
    /^      E: /{ top=$2 }
    top=="application" && /android:authorities/ { print }
  ' | sed -n 's/.*="\([^"]*\)".*/\1/p' | tr ';' '\n' | sort -u
}
permissions_defined() { manifest "$1" | awk '/E: permission /{f=1;next} f&&/android:name/{print; f=0}' | sed -n 's/.*="\([^"]*\)".*/\1/p' | sort -u; }

declare -A PKG
for flavor in play projection; do
  for type in debug release; do
    apk="$(find_apk "$flavor" "$type")"
    if [ -z "$apk" ]; then err "APK yok: $flavor/$type"; continue; fi
    p="$(pkg "$apk")"
    PKG["$flavor-$type"]="$p"
    note "APK $flavor/$type: $(basename "$apk") paket=$p boyut=$(stat -c %s "$apk")"
  done
done

[ "${PKG[play-release]:-}" = "com.karadaglabs.cartv" ] || err "play paketi beklenmeyen: ${PKG[play-release]:-yok}"
[ "${PKG[projection-release]:-}" = "com.karadaglabs.cartv.projection" ] || err "projection paketi beklenmeyen: ${PKG[projection-release]:-yok}"
[ "${PKG[play-release]:-x}" != "${PKG[projection-release]:-x}" ] || err "play ve projection aynı paket adını kullanıyor"

P="$(find_apk play debug)"; J="$(find_apk projection debug)"
if [ -n "$P" ] && [ -n "$J" ]; then
  common_auth="$(comm -12 <(authorities "$P") <(authorities "$J") || true)"
  [ -z "$common_auth" ] || err "Ortak provider authority: $common_auth"
  common_perm="$(comm -12 <(permissions_defined "$P") <(permissions_defined "$J") || true)"
  [ -z "$common_perm" ] || err "Ortak özel izin tanımı: $common_perm"
  note "play authorities: $(authorities "$P" | tr '\n' ' ')"
  note "projection authorities: $(authorities "$J" | tr '\n' ' ')"

  pm="$(manifest "$P")"
  for forbidden in CarVideoService CarMirrorService CATEGORY_PROJECTION MEDIA_PROJECTION androidx.car.app.category.NAVIGATION; do
    if echo "$pm" | grep -q "$forbidden"; then err "play APK'sında $forbidden var"; fi
  done
  jm="$(manifest "$J")"
  for required in CarVideoService CATEGORY_PROJECTION_OEM CarMediaService com.google.android.gms.car.application; do
    echo "$jm" | grep -q "$required" || err "projection APK'sında $required yok"
  done
fi

for flavor in play projection; do
  apk="$(find_apk "$flavor" release)"
  [ -n "$apk" ] || continue
  if "$APKSIGNER" verify --print-certs "$apk" >/tmp/sig.txt 2>&1; then
    if grep -q "CN=Android Debug" /tmp/sig.txt; then
      err "$flavor release DEBUG anahtarıyla imzalı"
    else
      note "$flavor release imzalı: $(grep 'certificate SHA-256' /tmp/sig.txt | head -1)"
    fi
  else
    echo "::warning::$flavor release APK İMZASIZ ($(basename "$apk")). GitHub Secrets ile release imzası yapılandırılmadı; Play'e yüklenemez."
  fi
done

exit $fail
