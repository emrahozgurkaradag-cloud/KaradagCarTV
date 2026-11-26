# Karadag Car TV — Android Auto araç ekranında video

Son güncelleme: 26 Kasım 2026 · Branch: `android-auto-official-video` · Sürüm 1.2.0 (versionCode 8)

Bu belgedeki her bilgi şu etiketlerden biriyle işaretlidir:

* **[Doğrulandı: kod/bytecode]** — bu depoda veya `app/libs/aauto.aar` içinde `javap` ile incelendi.
* **[Doğrulandı: otomatik test]** — birim testi, cihaz içi (emülatör) testi veya APK doğrulaması geçiyor. Emülatör testleri **araç ve Android Auto host'u içermez**.
* **[Kaynak beyanı]** — bir web sayfası böyle diyor; bu projede bağımsız olarak doğrulanmadı.
* **[Doğrulanmadı]** — gerçek araçta, Android Auto DHU'da veya Android Auto kurulu bir cihazda hiç denenmedi.

---

## 1. Resmî Android Auto desteği

Google'ın Android Auto (telefon yansıtma) için üçüncü taraf uygulamalara sunduğu kategoriler medya, mesajlaşma, navigasyon/POI/IoT (şablon) ve park hâlinde oyun/tarayıcıdır.

* [Kaynak beyanı] Medya kategorisi yalnızca ses içerir: "Media apps play audio content … excluding video". https://developer.android.com/agents/skills/cars/android-for-cars-parked-apps/skill
* [Kaynak beyanı] Google'ın video sayfasında "The video category is coming to Android Auto in beta" ifadesi ve bir erken erişim başvuru bağlantısı var. https://developer.android.com/training/cars/parked/video
* [Kaynak beyanı] Basın (Ekim 2026): Android Auto video uygulamaları hâlâ "yakında". https://9to5google.com/2026/10/30/google-android-auto-video-apps-deadline/
* [Doğrulanmadı] Android Auto video betasının kimlere açık olduğu, kabul şartları ve takvim.

KaradagCarTV'de bu yol **`play`** sürümüdür (`com.karadaglabs.cartv`):

* [Doğrulandı: kod + test] `CarMediaService` (Media3 `MediaLibraryService`) Android Auto'da Canlı Radyo ve Canlı TV **sesini** sunar. Kaynak lisans/IPTV API'si (`/api/iptv`), önbellek yedeklidir; video izi kapalıdır.
* [Doğrulandı: test] `automotive_app_desc.xml` yalnızca `media` içerir.

**Bu yol Android Auto ekranında video göstermez.**

## 2. Resmî Android Automotive OS video desteği

Android Automotive OS (Google built-in) araçlarda uygulama doğrudan araç bilgisayarında çalışır; Android Auto ile karıştırılmamalıdır.

* [Kaynak beyanı] Video kategorisi AAOS'ta park hâlinde kullanılabilir; sürüşe geçince sistem uygulamayı örter ve `onPause` çağırır.
* [Doğrulandı: kod + test] `play` sürümü: `android:appCategory="video"`, `android.hardware.type.automotive` `required=false`, `display_compatibility`, geniş `configChanges`, manifestte sabit ekran yönü yok. `PlayerActivity` `onPause`'da videoyu durdurur, örtülüyken kendiliğinden başlamaz.
* [Doğrulandı: test] Yön kilidi yalnızca telefonda uygulanır (iki yön destekli ve araç ekranı değil). AAOS'ta ve sabit yönlü ekranlarda yön zorlanmaz.
* [Doğrulanmadı] AAOS emülatöründe veya gerçek AAOS araçta çalıştırılmadı. Play'in AAOS kanalına gönderilmedi.

`projection` sürümü (`aauto.aar`) AAOS'ta **kullanılmaz**.

## 3. KaradagCarTV projection yöntemi (Android Auto ekranında görüntü — resmî DEĞİL)

### 3.1 Mimari

[Doğrulandı: kod/bytecode]

| Katman | Bileşen | Kanıt |
|---|---|---|
| Keşif | `CarVideoService` (`android.intent.action.MAIN` + `CATEGORY_PROJECTION` + `CATEGORY_PROJECTION_OEM`, exported) | Fermata Auto'nun açık kaynak manifestiyle aynı. Emülatörde `PackageManager.queryIntentServices` ile bulunuyor [otomatik test]. |
| Host bağlantısı | `aauto.aar` `CarActivityService` → `gms.car.d.onBind` → `CarActivityServiceProxy.onBind` | Bytecode: `gms.car.a` proxy'yi **Android Auto uygulamasından** (`com.google.android.projection.gearhead`, alternatif `…bumblebee`) çalışma anında `DynamicApiFactory.newCarActivityServiceProxy` ile yükler. Araç penceresinin davranışı bu yüzden telefondaki Android Auto sürümüne bağlıdır. |
| Araç penceresi | `CarVideoActivity` (aauto `CarActivity`) | Kanal ızgarası, kontroller, `SurfaceView` (`setZOrderMediaOverlay`). |
| Oynatıcı | `CarMediaService`'in tek ExoPlayer'ı (`PlayerFactory`), `CarVideoPlayerHolder` ile `SurfaceView`'e bağlanır | Görüntü telefonda kodlanır, Android Auto bağlantısıyla araca gider. |
| Servis yaşamı | `CarVideoActivity` → Media3 `MediaController` (SessionToken) ile `CarMediaService`'e bağlanır | `startService` yerine geçti (Android 12+ arka plan başlatma kısıtı). |
| Park kilidi | `CarDrivingSensors` + `DrivingSafetyGate` + `GateStabilizer` | Bölüm 5. |
| Yansıtma | `CarMirrorService` (Car App Library NAVIGATION) + `MirrorProjectionService` (MediaProjection) + `MirrorDrivingGate` | Yalnızca hız ≈ 0 iken; Android 14+ ön plan kuralına uygun. |
| Web | `CarYouTubeActivity`, `CarGoogleActivity` + `HostedWebDrivingLock` | Sürüşte medya duraklatılır, gizlenir. |

### 3.2 Yayın biçimleri

[Doğrulandı: kod + birim testi; emülatörde TS ve HLS gerçek çözümlemeyle]

* HLS (`.m3u8`, `output=hls|m3u8`): `application/x-mpegURL` (media3-exoplayer-hls).
* MPEG-TS (`.ts`, `output=ts|mpegts`): `video/mp2t` (TsExtractor).
* MP4/MKV/uzantısız adres: ExoPlayer içerik koklamasıyla açılır.
* DASH (`.mpd`): desteklenmez (modül yok).
* HTTP: http↔https yönlendirmeleri açık (`allowCrossProtocolRedirects`), 10/15 sn zaman aşımı, kendi User-Agent.
* Canlı hata kurtarma (`LiveRetryPolicy`): `BEHIND_LIVE_WINDOW` → canlı kenara atlama; ağ hatası ve bozuk kapsayıcı → 2/4/8 sn ile en çok 3 deneme; çözücü/biçim/DRM hatası → deneme yok.

### 3.3 Ses ve görüntü izleri, yüzey yaşam döngüsü

[Doğrulandı: emülatör testleri — gerçek ExoPlayer + SurfaceView, araç yok]

* Başlangıç fail-safe: görüntü kilitli (`videoBlocked=true`). Oynatıcı bırakılınca (`clearPlayer`) yeniden kilitlenir; yeni oynatıcıya kilit uygulanır.
* Kilitli: `TRACK_TYPE_VIDEO` devre dışı, yüzey bağlı değil, ses izi seçili, oynatma sürer. Kilit oynatıcı seviyesindedir; testte video izinin seçilmediği ve yüzeye kare düşmediği doğrulanır.
* Kilit açılınca video izi seçilir, yüzey bağlanır, ilk kare çizilir.
* Yüzey yok olup yeniden oluşunca ses sürer, yeni yüzeye kare çizilir; yüzey oluşunca oynatma zorla başlatılmaz.
* Kanal değişimi (TS → HLS) yeni `MediaItem` ile yapılır. Aynı kanal yeni adresle gelirse yeniden hazırlanır; hata sonrası `STATE_IDLE`'da aynı kanal da yeniden hazırlanır.
* Bağlantı kopması: `onStop` sensörleri bırakır, görüntüyü kilitler, oynatmayı durdurur. Yoklamada `CarNotConnectedException` gelirse karar `car_disconnected` olur ve oynatma duraklatılır. Yeniden bağlanınca son kanal seçili gelir.

### 3.4 Araç ekranındaki kontroller

[Doğrulandı: kod + kaynak testi] [Doğrulanmadı: araçta]

Dokununca alt çubuk açılır: ⏮ önceki kanal · ⏸/▶ · ⏭ sonraki kanal · ☰ kanal listesi · ⛶ tam ekran. Medya tuşları: PLAY_PAUSE, PLAY, PAUSE/STOP, NEXT/CHANNEL_UP, PREVIOUS/CHANNEL_DOWN. Kanal geçişi dairesel, son kanal kalıcıdır. Kontrol çubuğu 5 sn sonra gizlenir; kilitliyken ve duraklatılmışken açık kalır.

## 4. Kurulum ve APK varyantları

| Varyant | Paket | versionName | Not |
|---|---|---|---|
| `playDebug` / `playRelease` | `com.karadaglabs.cartv` | 1.2.0 | Resmî yol |
| `projectionDebug` / `projectionRelease` | `com.karadaglabs.cartv.projection` | 1.2.0-projection | Araç ekranında görüntü |

* [Doğrulandı: otomatik test] Paket adları farklıdır. Uygulama içi provider yetkileri (`…androidx-startup`) ve özel izin tanımları çakışmaz. `play` APK'sında projection bileşeni yoktur. Emülatörde iki paket yan yana kurulup açılır (`scripts/device-checks.sh`).
* Lisans ayrı pakette ayrıca etkinleştirilir; aynı API ve `device_uuid` mekanizması kullanılır.

**Android Auto'da görünürlük** — kurulum yönteminin etkisi:

| Yöntem | Durum |
|---|---|
| Play Store (iç/kapalı test kanalı) yükleyicisi | [Kaynak beyanı] Fermata #846/#847: AA 17.7 PROJECTION türünü Play yükleyicisiyle listeliyor; bir yorum (14.11.2026) AA 18.0'da çalıştığını söylüyor. [Doğrulanmadı] |
| Sideload + Android Auto "Bilinmeyen kaynaklar" | [Kaynak beyanı] Aynı analiz: bu ayar MEDIA'yı açar, PROJECTION/TEMPLATE türlerini **açmaz**. Yani sideload'da `play` medya kısmı görünebilir, projection görünmeyebilir. [Doğrulanmadı] |
| Geliştirici head unit (Google DHU, AAWireless geliştirici modu, aa-proxy-rs) | [Kaynak beyanı] Denetimleri atlar. [Doğrulanmadı] |
| Root + Xposed/LSPosed | [Kaynak beyanı] Önerilmez. [Doğrulanmadı] |

Uygulamanın Android Auto başlatıcısında **görünmesi** ile araç penceresinde **video yüzeyinin çalışması** ayrı konulardır. Bu projede ikisi de gerçek bir Android Auto oturumunda doğrulanmadı. Video yüzeyi kod yolu ise emülatörde, sıradan bir `SurfaceView` üzerinde doğrulandı.

## 5. Gereken izinler

| İzin | Varyant | Neden | Kanıt |
|---|---|---|---|
| `INTERNET` | ikisi | IPTV/API | — |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | ikisi | `CarMediaService` arka plan oynatma (Media3) | — |
| `POST_NOTIFICATIONS` | ikisi | Medya bildirimi (API 33+) | — |
| `com.google.android.gms.permission.CAR_SPEED` | projection | Hız sensörü; ayrıca Car App Library `addSpeedListener` | [Doğrulandı: bytecode] gms `CarSensorManager.registerListener` `@RequiresPermission(anyOf={ACCESS_FINE_LOCATION, gms CAR_SPEED, android.car CAR_SPEED, …MILEAGE, …FUEL}, conditional=true)`. [Doğrulandı: emülatör, Google Play hizmetleri] API 29 ve 34'te `sourcePackage=com.google.android.gms`, `prot=dangerous` → çalışma anında izin gerekir; `CarPlayerBridge.requestCarPermissions` (MainActivity) ister. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | projection | Yansıtma | Android 14+: ön plana yalnızca onaydan sonra (`ACTION_START`) geçilir. |
| `androidx.car.app.ACCESS_SURFACE`, `NAVIGATION_TEMPLATES` | projection | `CarMirrorService` yüzeyi | — |

Sürüş durumu, vites ve el freni için SDK'da izin listelenmemiştir [Doğrulandı: bytecode].

## 6. Park kilidi (araç sensörleri)

### 6.1 `aauto.aar` kanıtı [Doğrulandı: bytecode]

* Projection adaptörü `apps.auto.sdk.service.a.f`: `addListener(type, rate)` → gms `registerListener` (tip/hız değişmeden); `getSensorConfig` → "not supported in projection".
* `CarSensorEvent`: `intValues[i] = byteValues[i]`. Sürüş durumu (11) ve vites (7) → `byteValues[0]`; el freni (6) → `byteValues[0] == 1`; hız (2) → `floatValues[0]` (m/s).
* Sabitler: `DRIVE_STATUS_UNRESTRICTED=0`, kısıt bitleri 1/2/4/8/16, `FULLY_RESTRICTED=31`; vites `NEUTRAL=0`, `FIRST..TENTH=1..10`, `DRIVE=100`, `PARK=101`, `REVERSE=102`; `SENSOR_RATE_UI=2`; servis adı `"sensor"`.
* Yönetici: `gms.car.c.a(String)` = `CarActivityHost.getCarManager`.

### 6.2 Kurallar [Doğrulandı: birim testleri] — kullanıcı kapatamaz

1. Yalnızca taze değerler sayılır: son 3 sn içinde olay **veya** 1 sn'lik `getLatestSensorEvent` yoklaması. Gelecek zamanlı damga güvenilmez.
2. Sürüş işareti görüntüyü kapatır: sürüş durumu ≠ 0 (her bit ve bilinmeyen değer), hız > 0,5 m/s veya NaN, vites D/R/1–10.
3. Park işareti ile sürüş işareti aynı anda gelirse sonuç `conflicting_signals`, görüntü kapalı.
4. Açılma şartı: sürüş durumu taze ve 0. Head unit sürüş durumunu desteklemiyorsa: hız taze ve duruyor **ve** (vites P veya el freni çekili).
5. Kaydı başarılı ve desteklenen **her** sensör taze olmalıdır (`missing_required_*`). Tek bir taze "park" değeri yetmez.
6. Park kararı 2 sn kesintisiz sürmeden görüntü açılmaz (`confirming_park`); kapanma anında olur.
7. Veri yok, bayat veya bağlantı kopuk olduğunda kapalı (`no_signal`, `stale_signal`, `car_disconnected`).
8. Yansıtma yalnızca hıza bakar: hız taze, ≤ 0,5 m/s ve 2 sn kararlı olmalı; bir bekçi her saniye yeniden değerlendirir.
9. Kilit oynatıcı seviyesindedir (video izi devre dışı + yüzey bağlantısı yok); arayüzde nedeni yazar. Ses ayrı yönetilir ve sürer.

## 7. Android Auto sürüm uyumluluğu

* [Doğrulandı: bytecode] `aauto.aar` (2019 tarihli) çalışma anında Android Auto uygulamasından sınıf yükler. Android Auto güncellemesi bu arayüzü değiştirir veya kaldırırsa araç penceresi açılmaz ("Error loading car activity host").
* [Kaynak beyanı] Fermata kullanıcıları: AA 17.7'de görünürlük denetimi; AA 18.0'da Play yükleyici kaydıyla çalışıyor; KingInstaller Android 14+ ile çalışmıyor (#432).
* [Doğrulanmadı] Belirli bir Android Auto sürümüyle uçtan uca test yapılmadı.
* Telefon: minSdk 29 (Android 10). Emülatör testleri API 29 ve 34'te çalışır (CI).

## 8. Bilinen kısıtlamalar

* Projection yolu Google'ın resmî API'si değildir. Play incelemesinden geçmesi ve Android Auto güncellemelerinde çalışmaya devam etmesi garanti değildir.
* Android Auto DHU testi CI'da yapılmadı. DHU, telefondaki Android Auto uygulamasının (gearhead) geliştirici ayarlarından elle açılan "head unit server" moduna ve `adb forward tcp:5277` ile masaüstü DHU ikili dosyasına (grafik arayüzlü) bağlanır. [Doğrulandı: CI] `google_apis` emülatör imajlarında (API 29/34) `com.google.android.projection.gearhead` paketi bulunuyor; sürümü `device-checks.sh` ile raporlanır. Ancak geliştirici modu ve head unit server arayüzden açılır, başsız CI'da otomatikleştirilmedi. Bu yüzden bir Android Auto oturumu kurulmadı ve `CarVideoActivity` bir host içinde açılmadı.
* Bazı araçlar parkta da sürüş kısıtı bildiriyorsa görüntü hiç açılmaz (bilinçli güvenli taraf).
* DASH desteklenmez. DRM'li yayınlar test edilmedi.
* Release APK'ları imza secret'ları tanımlanmadıkça imzasızdır ve Play'e yüklenemez.

## 9. Gerçek araçta henüz doğrulanmamış davranışlar

1. Projection APK'sının Android Auto başlatıcısında görünmesi (Play iç test kurulumuyla).
2. `CarVideoActivity` penceresinin açılması ve `SurfaceView` görüntüsünün araç ekranına ulaşması.
3. Canlı TV ve IPTV (gerçek sağlayıcı) görüntüsü; ağ kesintisinde yeniden deneme.
4. Kontroller ve direksiyon tuşları.
5. Araç sensörlerinin gerçekte gelmesi: `adb logcat -s KaradagCarDriving` → `CAR SENSORS supported=[…]`, `register type=… ok=…`, `VIDEO GATE allowed=… reason=…`.
6. Hareket başlayınca görüntünün 1–3 sn içinde kesilmesi ve sesin sürmesi; park edince yaklaşık 2–3 sn sonra görüntünün dönmesi.
7. Bağlantı kesilip yeniden kurulunca toparlanma.
8. Farklı araç ekranı çözünürlükleri.
9. `CAR_SPEED` izninin gerçek cihazda verilmesi veya çalışma anında istenmesi.

## 10. Test altyapısı ve release imzası

**Son CI sonucu:** çalışma 45818671152 (`ed0a44f`), üç iş başarılı. Birim testleri: play 54/54, projection 114/114. Lint: 0 hata (play 68, projection 83 uyarı). Cihaz testleri API 29 ve API 34'te: projection 14/14, play 2/2. Emülatörde iki paket yan yana kuruldu ve açıldı, çökme yok. Release APK'ları imzasız (secret tanımlı değil).

**Yerel ve CI** (`.github/workflows/android.yml`; push, pull request ve manuel tetikleme):

1. `build` işi: bağımlılıklar → imza ayarı → `testPlayDebugUnitTest testProjectionDebugUnitTest` → `lintPlayDebug lintProjectionDebug` → 4 APK → `scripts/verify-apks.sh` (paket adı, versionCode, çakışma, imza) → artifact'lar (`apks`, `reports`).
2. `device-tests` işi (API 29 ve 34 emülatörü, KVM):
   * `connectedPlayDebugAndroidTest`, `connectedProjectionDebugAndroidTest`: gerçek ExoPlayer ve SurfaceView ile TS/HLS oynatma, kilitliyken yalnızca ses, kilit açılınca ilk kare, yüzey yeniden oluşturma, kanal değişimi, bozuk yayından toparlanma, duraklat/sürdür, fail-safe sıfırlama; PackageManager keşif sorguları.
   * `scripts/device-checks.sh`: iki paketin yan yana kurulumu, açılışta çökme kontrolü, servis kaydı, `CAR_SPEED` tanımı, gearhead varlığı.

**Ağ dayanıklılığı (sınırlı retry):** Gradle adımları `scripts/gradle-retry.sh` ile çalışır. En çok 3 deneme yapılır (20 sn, ardından 40 sn bekleme) ve **yalnızca** geçici ağ/depo hatalarında yeniden denenir: eklenti/bağımlılık çözülemedi, HTTP 408/429/5xx, bağlantı kopması, zaman aşımı. Test, lint ve derleme hataları ilk denemede olduğu gibi döner. 3 denemeden sonra da süren altyapı hatası `::error::` ile raporlanır. Ayrıca `gradle.properties`, Gradle'ın kendi depo isteği yeniden denemelerini (4) ve 60 sn zaman aşımlarını ayarlar. Bu branch da Gradle önbelleğine yazar; önceden yalnızca `main` yazabiliyordu ve `main`'de iş akışı hiç çalışmadığı için her iş sıfırdan indiriyordu. Bu durum 4a72fac çalışmasındaki Foojay eklenti indirme hatasına yol açtı.

**`CAR_SPEED` izin akışı:** `projection` manifesti izni ister. `MainActivity.onCreate` → `CarPlayerBridge.requestCarPermissions`: izin verilmemişse `ActivityCompat.requestPermissions` ile sorulur (`play` sürümünde işlem yok). [Doğrulandı: CI] Google Play hizmetleri bu izni `prot=dangerous` olarak tanımlar; yani kullanıcı onayı gerekir. Kullanıcı reddederse `CarDrivingSensors` hız sensörüne hiç kayıt olmaz. Hız "zorunlu sensör" sayılmaz; kilit sürüş durumu, vites ve el frenine dayanır. Sürüş durumu her durumda zorunludur. Yansıtma (Car App Library) hıza bağlı olduğundan izin yoksa yansıtma kapalı kalır. CI'da `adb install -g` izinleri önceden verir; diyalog akışının kendisi arayüz testiyle doğrulanmadı.

**`gearhead` notu:** Emülatörde `com.google.android.projection.gearhead` paketinin bulunması gerçek araçtaki Android Auto davranışını **kanıtlamaz**. Emülatörde araç/head unit bağlantısı yoktur; Android Auto oturumu başlamaz, `CarVideoService` host tarafından bağlanmaz ve görünürlük denetimi (yükleyici, sunucu listesi) çalışmaz.

**Release imzası:** Debug anahtarı kullanılmaz. `KARADAG_RELEASE_KEYSTORE`, `…_KEYSTORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD` ortam değişkenleri gerekir. CI bunları şu secret'lardan oluşturur: `KARADAG_RELEASE_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KARADAG_RELEASE_KEYSTORE_PASSWORD`, `KARADAG_RELEASE_KEY_ALIAS`, `KARADAG_RELEASE_KEY_PASSWORD`. Secret yoksa CI uyarı verir ve çıktılar `*-release-unsigned.apk` olur. `verify-apks.sh` debug sertifikalı bir release'i hata sayar. Keystore iş sonunda silinir; parolalar loglanmaz.
