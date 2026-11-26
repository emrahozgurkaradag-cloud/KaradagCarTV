#!/usr/bin/env bash
# Gradle'ı SINIRLI sayıda yeniden dener — yalnızca geçici ağ/depo
# hatalarında (eklenti/bağımlılık indirilemedi, HTTP 429/5xx, bağlantı
# kopması, zaman aşımı). Test, lint veya derleme hatası YENİDEN DENENMEZ:
# ilk denemede çıkış kodu aynen döndürülür.
#
# Kullanım: scripts/gradle-retry.sh <gradle argümanları...>
# Ortam: GRADLE_RETRY_ATTEMPTS (vars. 3), GRADLE_RETRY_DELAY_S (vars. 20)
set -uo pipefail

attempts="${GRADLE_RETRY_ATTEMPTS:-3}"
delay="${GRADLE_RETRY_DELAY_S:-20}"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

# Geçici altyapı hatası kalıpları (Gradle çıktısı).
transient='Could not resolve plugin artifact|was not found in any of the following sources|Could not GET |Could not HEAD |Could not resolve all (files|artifacts|dependencies)|Received status code (408|429|500|502|503|504)|Connection reset|Connect timed out|Read timed out|connect timed out|Remote host terminated the handshake|Unable to tunnel through proxy|Could not download|Downloading https://services.gradle.org/.*(failed|Exception)|java.net.SocketException|java.net.UnknownHostException|javax.net.ssl.SSLException'

# Gerçek hata kalıpları: görülürse geçici kalıp olsa bile yeniden deneme yok.
real='There were failing tests|Tests on .* failed|Lint found errors|Compilation error|e: file://|Execution failed for task .*(compile|lint|test|connected)'

for i in $(seq 1 "$attempts"); do
  echo "::group::gradle (deneme $i/$attempts): $*"
  ./gradlew "$@" 2>&1 | tee "$log"
  rc=${PIPESTATUS[0]}
  echo "::endgroup::"

  if [ "$rc" -eq 0 ]; then
    [ "$i" -gt 1 ] && echo "::notice::Gradle $i. denemede başarılı (önceki deneme(ler) geçici altyapı hatası)."
    exit 0
  fi

  if grep -Eq "$real" "$log"; then
    echo "::error::Gradle gerçek bir derleme/test/lint hatasıyla bitti (yeniden denenmez)."
    exit "$rc"
  fi

  if ! grep -Eq "$transient" "$log"; then
    echo "::error::Gradle hatası geçici ağ/depo hatası olarak tanınmadı (yeniden denenmez)."
    exit "$rc"
  fi

  if [ "$i" -lt "$attempts" ]; then
    wait_s=$(( delay * i ))
    echo "::warning::Geçici ağ/depo hatası (deneme $i/$attempts). ${wait_s} sn sonra yeniden denenecek."
    grep -Em3 "$transient" "$log" | sed 's/^/  > /'
    sleep "$wait_s"
  fi
done

echo "::error::Geçici ağ/depo hatası $attempts denemeden sonra sürüyor. Altyapı hatası olarak raporlanıyor."
exit "$rc"
