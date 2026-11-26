# Karadag Car TV — Android Auto araç ekranında video

Son güncelleme: 26 Kasım 2026 · Branch: `android-auto-official-video`

Bu belgede üç tür bilgi vardır ve her biri işaretlidir:

* **[Kod/bytecode ile doğrulandı]** — bu depoda veya `aauto.aar` içinde
  doğrudan incelendi.
* **[Kaynak beyanı]** — bir web sayfası böyle söylüyor; bu projede
  bağımsız olarak doğrulanmadı, değişebilir.
* **[Gerçek araçta doğrulanmadı]** — çalışması beklenir ama hiçbir
  gerçek araçta, DHU'da veya emülatörde denenmedi.

---

## 1. İki ayrı yol

| | **A. Resmî Android Auto desteği** | **B. `projection` yöntemi** |
|---|---|---|
| Sürüm | `play` (`com.karadaglabs.cartv`) | `projection` (`com.karadaglabs.cartv.projection`) |
| Araç ekranında ne olur | Android Auto **medya** arayüzü: Canlı Radyo + Canlı TV **sesi** | Araç ekranında **görüntülü** Canlı TV / IPTV, kanal ızgarası, kontroller |
| Google API'si | Media3 `MediaLibraryService` (resmî) | `aauto.aar` `CarActivity` + `CATEGORY_PROJECTION_OEM` (**resmî değil**, Google yayınlamıyor) |
| Video | Android Auto'da yok | Var — yalnızca park kilidi açıkken |
| Android Automotive OS | `android:appCategory="video"` ile park hâlinde tam ekran video | Kullanılmaz |
| Görünürlük şartı | Normal kurulum | Android Auto'nun yükleyici denetimi (bölüm 4) |

İki yol birbirine bağlı değildir. `play` sürümü Google kurallarına
uyacak şekilde tasarlandı; `projection` sürümü araç ekranında görüntüyü
hedefler ve Google'ın üçüncü taraf uygulamalar için yayınladığı bir
yöntem değildir.

---

## 2. A — Resmî Android Auto desteği (`play`)

Google'ın kendi belgelerinde **yazanlar** (26 Kasım 2026'da okundu):

* [Kaynak beyanı] Medya kategorisi sesle sınırlı: "Media apps play audio
  content … excluding video".
  https://developer.android.com/agents/skills/cars/android-for-cars-parked-apps/skill
* [Kaynak beyanı] Video kategorisi Android Automotive OS'ta park
  hâlinde kullanılabilir; aynı sayfada Android Auto için "The video
  category is coming to Android Auto in beta" ifadesi ve bir "early
  access partner" başvuru formu bağlantısı vardır.
  https://developer.android.com/training/cars/parked/video
* [Kaynak beyanı] "What's new" sayfasında 19 Mayıs 2026 tarihli
  "Video app experiences are coming to Android Auto" notu.
  https://developer.android.com/training/cars/whats-new
* [Kaynak beyanı] Basın: Ekim 2026 itibarıyla Android Auto video
  uygulamaları hâlâ "yakında".
  https://9to5google.com/2026/10/30/google-android-auto-video-apps-deadline/

Bu projede **doğrulanmayanlar**: Android Auto video betasının kimlere
açık olduğu, erken erişim programının kabul şartları, gerekli ek manifest
alanları ve takvim. Bunlar Google'ın sayfalarındaki ifadelerdir; Google
değiştirebilir.

`play` sürümünde yapılanlar [Kod ile doğrulandı]:

* `CarMediaService`: Radyo ve Canlı TV kanalları (lisans/IPTV API'si
  `/api/iptv`, önbellek yedekli) Android Auto'da ses olarak; video izi kapalı.
* `automotive_app_desc.xml`: yalnızca `media`.
* `<application android:appCategory="video">`, `android.hardware.type.automotive`
  `required=false`, `display_compatibility`, geniş `configChanges`.
* `PlayerActivity` + `ParkedPlaybackPolicy`: sistem aktiviteyi örtünce
  (`onPause`) video durur, örtülüyken kendiliğinden başlamaz.
* Ekran yönü: manifestte sabit `screenOrientation` YOK. Yatay kilit
  yalnızca telefonda (`CarScreen.applyPhoneLandscape`: cihaz iki yönü de
  destekliyor ve araç ekranı/AAOS değil); AAOS ve sabit yönlü araç
  ekranlarında yön zorlanmaz.
* Araç ekranında (AAOS / araç UI modu) YouTube WebView ve Google Arama gizli.

---

## 3. B — `projection` yöntemi (araç ekranında görüntü)

### 3.1 Yöntem ve kanıt

[Kod ile doğrulandı — Fermata Auto açık kaynak manifesti] Fermata Auto
araç ekranına `CATEGORY_PROJECTION` + `CATEGORY_PROJECTION_OEM`
kategorili bir servis ve `aauto.aar` ile pencere açar; ayrıca Car App
Library NAVIGATION yüzeyine MediaProjection ile yansıtma yapar.
Descriptor: `media`, `service`, `projection`.
https://raw.githubusercontent.com/AndreyPavlenko/Fermata/master/fermata/src/auto/AndroidManifest.xml

KaradagCarTV `projection` aynı aileyi kullanır. Görüntü telefonda
üretilir ve Android Auto bağlantısının kendi video akışıyla araç
ekranına gider; araçta değişiklik gerekmez.

| Bileşen | Görev |
|---|---|
| `CarVideoService` | Android Auto başlatıcısındaki "Karadag Car TV" (PROJECTION) |
| `CarVideoActivity` | Kanal ızgarası; ExoPlayer → `SurfaceView`; kontroller |
| `CarVideoPlayerHolder` | `CarMediaService`'in tek ExoPlayer'ını araç yüzeyine bağlar |
| `CarDrivingSensors` + `DrivingSafetyGate` | Park kilidi (araç sensörleri) |
| `CarYouTubeActivity` / `CarGoogleActivity` + `HostedWebDrivingLock` | Araçta WebView; sürüşte medya duraklatılır, gizlenir |
| `CarMirrorService` + `MirrorProjectionService` + `MirrorDrivingGate` | Telefon ekranı yansıtma; yalnızca hız ≈ 0 iken |

Araştırılan diğer çalışmalar:

* Yasin Hırlak (@car_playandroid) / `github.com/yasinsay1/Yasin-Android-Auto`:
  depo ve sürüm sayfaları **HTTP 404**. Kaynak kodu incelenemedi; yöntemi
  **doğrulanamadı**.
* AA Car Play Video: [Kaynak beyanı] "Play kapalı test kanalından
  kurulur"; teknik yöntem açıklanmıyor. https://github.com/aacarplayvideo/aacarplayvideo

### 3.2 Kontroller

Dokununca alt çubuk: ⏮ önceki kanal · ⏸/▶ · ⏭ sonraki kanal ·
☰ kanal listesi · ⛶ tam ekran (üst menüyü gizler). Medya tuşları:
PLAY_PAUSE, PLAY, PAUSE/STOP, NEXT/CHANNEL_UP, PREVIOUS/CHANNEL_DOWN.
Son kanal kalıcıdır. [Gerçek araçta doğrulanmadı]

### 3.3 Ses / görüntü ayrımı ve bağlantı kesilmesi

* `CarVideoPlayerHolder` başlangıçta **görüntü kilitli** (`videoBlocked=true`).
* Kilitliyken: yüzey bağlantısı kesilir, video izi seçilmez
  (`TRACK_TYPE_VIDEO` devre dışı), ses izi açık kalır.
* Kilit açılınca: video izi seçilir, `SurfaceView` hazır olunca bağlanır.
* Yüzey yeniden oluştuğunda oynatma zorla başlatılmaz (kullanıcı
  duraklatması korunur).
* Kanal değiştirme: farklı kanal → yeni `MediaItem` + `prepare`; aynı kanal
  hata sonrası (`STATE_IDLE`) → yeniden `prepare`.
* Bağlantı kesilmesi: `onStop` → sensörler bırakılır, görüntü kilitlenir,
  oynatma durur. Sensör yoklaması `CarNotConnectedException` verirse
  karar `car_disconnected` olur ve oynatma duraklatılır. Yeniden
  bağlanınca son kanal seçili gelir.

---

## 4. Android Auto'nun yükleyici denetimi (projection için)

[Kaynak beyanı — bir kullanıcının Android Auto 17.7 tersine mühendisliği,
Fermata #846/#847] Android Auto, PROJECTION türü uygulamaları yalnızca
şu durumlarda başlatıcıda listeler: kayıtlı yükleyici Google Play ise,
Play kitaplığında sahiplik/imza eşleşmesi varsa, Google sunucu izin
listesindeyse, geliştirici head unit kullanılıyorsa veya root/Xposed ile.
"Bilinmeyen kaynaklar" ayarı PROJECTION ve TEMPLATE türlerini
**kapsamaz**. Bir yoruma göre (14 Kasım 2026) Fermata Auto, Android Auto
18.0'da Play yükleyici kaydıyla çalışıyor.
https://github.com/AndreyPavlenko/Fermata/issues/846 ·
https://github.com/AndreyPavlenko/Fermata/discussions/847

Bu projede bağımsız olarak doğrulanmadı. Pratik sonuç: `projection`
APK'sı araç ekranında görünmesi için Play Console iç/kapalı test
kanalından (gerçek release imzasıyla, bölüm 6) veya geliştirici head
unit ile kurulmalıdır. Google Play incelemesinin bu uygulamayı kabul
edeceği garanti değildir.

---

## 5. Park kilidi — sensör kanıtı ve kurallar

### 5.1 `aauto.aar` bytecode incelemesi [Kod/bytecode ile doğrulandı]

`javap` ile `app/libs/aauto.aar` içindeki `classes.jar`:

* `com.google.android.apps.auto.sdk.service.a.f extends
  android.support.car.hardware.CarSensorManager` — projection'da kullanılan
  adaptör. `addListener(l, type, rate)` → `gms.car.CarSensorManager
  .registerListener(proxy, type, rate)`: **tip ve hız değiştirilmeden**
  geçirilir. `getSensorConfig` → "not supported in projection".
* `getLatestSensorEvent(type)` → gms `RawEventData(sensorType, timeStamp,
  floatValues, byteValues)` aynen `CarSensorEvent`'e sarılır; dinleyici
  sarmalayıcısı (`service.a.h`) da aynı.
* `CarSensorEvent` kurucusu: `intValues[i] = byteValues[i]`.
* Çözümleyiciler:
  * `getDrivingStatusData()` → tip 11, `intValues[0]` (= `byteValues[0]`)
  * `getGearData()` → tip 7, `intValues[0]` (= `byteValues[0]`)
  * `getParkingBrakeData()` → tip 6, `intValues[0] == 1` ise çekili
  * `getCarSpeedData()` → tip 2, `floatValues[0]`
* Sabitler: `DRIVE_STATUS_UNRESTRICTED=0`, `NO_VIDEO=1`, `NO_KEYBOARD_INPUT=2`,
  `NO_VOICE_INPUT=4`, `NO_CONFIG=8`, `LIMIT_MESSAGE_LEN=16`,
  `FULLY_RESTRICTED=31`; `GEAR_NEUTRAL=0`, `GEAR_FIRST..TENTH=1..10`,
  `GEAR_DRIVE=100`, `GEAR_PARK=101`, `GEAR_REVERSE=102`;
  `SENSOR_RATE_UI=2`; `Car.SENSOR_SERVICE="sensor"`.
* Yönetici erişimi: `gms.car.c.a(String)` →
  `CarActivityHost.getCarManager(String)`.

Önceki sürümdeki (`aa31328`) hata: sürüş durumu, vites ve el freni önce
`floatValues`'tan okunuyordu; SDK'nın çözümleyicisiyle çelişiyordu.
Düzeltildi.

### 5.2 Doğrulanmayanlar [Gerçek araçta doğrulanmadı]

* Head unit'lerin hangi sensörleri desteklediği ve ne sıklıkla gönderdiği.
* Parkta sürüş durumunun gerçekten 0 olduğu. Bazı araçlar parkta da
  kısıt bildirirse görüntü hiç açılmaz (güvenli taraf).
* Hız/vites için Android Auto'nun uygulamadan izin isteyip istemediği.
  `aauto.aar` yalnızca AAOS izin adlarını (`android.car.permission.CAR_SPEED`
  vb.) içeriyor; projection için gereken izin SDK'da yok. Kural bu yüzden
  hıza değil, sürüş durumuna dayanır.
* Car App Library yansıtma için `com.google.android.gms.permission.CAR_SPEED`.

### 5.3 Kurallar (kullanıcı kapatamaz; ayar, düğme, gizli menü yok)

1. Yalnızca **taze** (son 3 sn içinde olay veya 1 sn'lik yoklamayla
   doğrulanmış) değerler sayılır. Yoklama başarısız/veri kesik → değer
   bayatlar → görüntü kapanır (`stale_signal`). Gelecek zamanlı damga
   güvenilmez.
2. Taze sürüş işareti → kapalı: sürüş durumu ≠ 0 (her kısıt biti ve
   bilinmeyen değer), hız > 0,5 m/s veya NaN, vites D/R/1–10.
3. Park işareti ile sürüş işareti aynı anda → `conflicting_signals`, kapalı.
4. Açılma şartı: sürüş durumu taze ve 0. Head unit sürüş durumunu
   **desteklemiyorsa** (desteklenenler listesinde yoksa): hız taze ve
   duruyor **ve** (vites P veya el freni çekili). Liste alınamazsa en
   sıkı kural (sürüş durumu şart) uygulanır.
5. Hiç veri yok → kapalı (`no_signal`). Araç bağlantısı yok → kapalı
   (`car_disconnected`).
6. Yansıtma: yalnızca hız (Car App Library); taze ve ≤ 0,5 m/s değilse
   kapalı; her saniye bekçi yeniden değerlendirir.

Kilitliyken ekranda nedeni yazar; Canlı TV sesi sürer.

---

## 6. Release imzası

`app/build.gradle.kts` release için debug anahtarını **kullanmaz**.
Değerler yalnızca ortam değişkenlerinden okunur:
`KARADAG_RELEASE_KEYSTORE`, `KARADAG_RELEASE_KEYSTORE_PASSWORD`,
`KARADAG_RELEASE_KEY_ALIAS`, `KARADAG_RELEASE_KEY_PASSWORD`.
Eksikse release APK'ları imzasız (`*-release-unsigned.apk`) üretilir
ve Gradle uyarı yazar.

GitHub Actions için repo → Settings → Secrets and variables → Actions:

* `KARADAG_RELEASE_KEYSTORE_BASE64` — `base64 -w0 release.jks` çıktısı
* `KARADAG_RELEASE_KEYSTORE_PASSWORD`
* `KARADAG_RELEASE_KEY_ALIAS`
* `KARADAG_RELEASE_KEY_PASSWORD`

İş akışı secret yoksa `::warning::` ile "Release imzası yok" bildirir;
`scripts/verify-apks.sh` imza durumunu raporlar ve debug sertifikasıyla
imzalı bir release'i hata sayar. Keystore iş sonunda silinir.

---

## 7. Test

Otomatik (CI: `.github/workflows/android.yml`, PR'da ve bu branch'e push'ta):

```
./gradlew testPlayDebugUnitTest testProjectionDebugUnitTest lintPlayDebug lintProjectionDebug assemblePlayDebug assemblePlayRelease assembleProjectionDebug assembleProjectionRelease
scripts/verify-apks.sh
```

`verify-apks.sh`: paket adları (`com.karadaglabs.cartv` /
`com.karadaglabs.cartv.projection`) farklı; uygulama içi provider
yetkileri ve özel izin tanımları çakışmıyor; `play` APK'sında projection
bileşeni yok; `projection` APK'sında araç ekranı bileşenleri var;
release imza durumu.

### Gerçek araçta doğrulanacaklar (hiçbiri henüz yapılmadı)

1. `projection` APK'sı Play iç test kanalından kurulunca Android Auto
   başlatıcısında "Karadag Car TV" görünüyor mu.
2. Lisans etkinleştirme (ayrı paket; telefonda bir kez).
3. Park + el freni: Canlı TV → kanal → görüntü araç ekranında.
4. IPTV kanalı görüntülü.
5. ⏮ ⏭ ⏸/▶ ☰ ⛶ ve direksiyon medya tuşları.
6. Güvenli alanda, gözlemciyle hareket: görüntü 1–3 sn içinde kesilmeli,
   uyarı çıkmalı, ses sürmeli; durup park edince görüntü dönmeli.
7. `adb logcat -s KaradagCarDriving KaradagCarVideo CarVideoPlayerHolder`:
   `CAR SENSORS supported=[...]`, `CAR SENSOR register type=… ok=…`,
   `VIDEO GATE allowed=… reason=…` satırlarını kaydet. Parkta
   `reason=parked` görülmüyorsa nedenini (ör. `no_signal`,
   `driving_status_restricted`) bildir.
8. Kablo/kablosuz bağlantı kesilince telefonda sesin durması; yeniden
   bağlanınca son kanal.
9. Farklı ekran boyutları (geniş/dikey araç ekranları): ızgara ve
   kontrol çubuğu okunuyor mu.
