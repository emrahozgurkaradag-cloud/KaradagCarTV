# Karadag Car TV — Android Auto ve araç ekranında video

Son güncelleme: 26 Kasım 2026

Bu belge, KaradagCarTV'nin fabrikadan Android Auto bulunan araçların
ekranında nasıl çalıştığını anlatır: Google'ın resmî yolu (`play`
sürümü) ve araç ekranında görüntü veren resmî olmayan yol (`projection`
sürümü, park kilidiyle). Hangisinin neyi garanti ettiği açıkça ayrılmıştır.

## 1. Google'ın bugünkü kuralları (resmî kaynaklar)

| Konu | Durum |
|---|---|
| Android Auto **medya** kategorisi (ses) | Genel kullanımda. Video içermez: "Media apps play audio content … excluding video". |
| **Video** kategorisi, Android Automotive OS (Google built-in araçlar) | Genel kullanımda. Yalnızca park hâlinde. |
| **Video** kategorisi, Android Auto (telefon yansıtma) | **Beta / erken erişim.** Google: "The video category is coming to Android Auto in beta". Üçüncü taraf uygulamalar için Google'ın **Early Access Partner** programına kabul gerekir. |
| Parked kategoriler, Android Auto | Oyunlar ve tarayıcılar (Android 15+ telefon, `CAR_LAUNCHER`). Video henüz bu listede değil. |
| Sürüşte ses (video uygulamaları) | Beta. Yayınlama erken erişim ortaklarıyla sınırlı. |

Video uygulaması için zorunlu kurallar (developer.android.com/training/cars/parked/video,
Android for Cars parked-apps yönergesi, car-app-quality):

* `<application android:appCategory="video">`.
* `automotive_app_desc.xml` içinde `<uses name="video"/>` **olmamalı**.
* Aktivitelerde `distractionOptimized` meta-data'sı **olmamalı**.
* Araç hareket edince sistem aktiviteyi örter ve `onPause` (uyumluluk
  modunda ayrıca `onStop`) çağırır; uygulama **oynatmayı durdurmalı** ve
  sürüşte yeniden başlatmamalıdır (DD-2 / DD-3). Hız veya vites bilgisinden
  tahmin yapılmaz.
* Sabit ekran yönü zorlanmamalı; `configChanges` geniş tutulmalı (DO-1, LS-C1).
* Araca yönelik olmayan özellikler (ör. genel web tarayıcı) bulunmamalı (PC-1).

Google'ın resmî olarak **sunmadığı** yollar (yalnızca `projection`
sürümünde, park kilidiyle kullanılır; `play` sürümünde yoktur):

* `aauto.aar` / `com.google.android.apps.auto.sdk.CarActivity` (Fermata
  tarzı, resmî olmayan Google Auto SDK). Üçüncü taraf uygulamalara
  yayınlanmış bir API değildir; Play incelemesinden geçmesi garanti değildir.
* MediaProjection ile telefon ekranını araç yüzeyine yansıtmak.
* `androidx.car.app.category.NAVIGATION` kategorisini navigasyon dışı
  (video) amaçla kullanmak.
* Medya kategorisinde video veya WebView göstermek.

Kaynaklar:

* https://developer.android.com/training/cars/parked/video
* https://developer.android.com/training/cars/parked
* https://developer.android.com/agents/skills/cars/android-for-cars-parked-apps/skill
* https://developer.android.com/docs/quality-guidelines/car-app-quality
* https://developer.android.com/training/cars/whats-new
* https://9to5google.com/2026/10/30/google-android-auto-video-apps-deadline/
  (Ekim 2026: Android Auto video uygulamaları hâlâ "yakında")

### Araç ekranında video gösteren mevcut uygulamalar (araştırma)

| Uygulama | Yöntem (kanıt) |
|---|---|
| Yasin Hırlak (@car_playandroid) / `yasinsay1/Yasin-Android-Auto` | Depo ve sürümler şu an **erişilemiyor (HTTP 404)**. Arama sonuçlarında görülen sürüm notları: imzalı APK, ayrı test paketi `com.yasincarplay.independent.test`, "Android Auto media integration", "henüz gerçek araçta test edilmedi". Görüntü aktarım yöntemi kaynak koddan **doğrulanamadı**. |
| Fermata Auto (açık kaynak) | `CarService`/`MirrorService`: `CATEGORY_PROJECTION` + `CATEGORY_PROJECTION_OEM`, `aauto.aar`; `MirrorServiceFS`: Car App Library NAVIGATION; `ProjectionService` (mediaProjection). Descriptor: media, service, projection. |
| AA Car Play Video | Kapalı kaynak; "Play kapalı test kanalından kurulur, root/sideload yok" der. Teknik yöntem açıklanmıyor. "Sürüşte de oynar" iddiası var — KaradagCarTV bunu **yapmaz**. |

Android Auto'nun görünürlük denetimi (Fermata #846/#847, AA 17.7 tersine
mühendislik, bir kullanıcının analizi): PROJECTION türü uygulamalar
yalnızca Play yükleyicisi, Play kitaplık sahipliği, sunucu izin listesi,
geliştirici head unit veya root ile listelenir; "Bilinmeyen kaynaklar"
PROJECTION/TEMPLATE türlerini kapsamaz. 14 Kasım 2026 yorumu: Fermata
Auto 2.0.2, AA 18.0'da Play yükleyici kaydıyla çalışıyor.

Kaynaklar: https://github.com/AndreyPavlenko/Fermata/issues/846 ,
https://github.com/AndreyPavlenko/Fermata/discussions/847 ,
https://github.com/AndreyPavlenko/Fermata/discussions/432 ,
https://github.com/aacarplayvideo/aacarplayvideo ,
https://github.com/yasinsay1/Yasin-Android-Auto (404).

## 2. Yapı türleri (product flavors)

### `play` — varsayılan, Google Play'e uygun

```
./gradlew assemblePlayRelease
```

Paket: `com.karadaglabs.cartv`

| Ekran | Ne görünür | Durum |
|---|---|---|
| Android Auto, sürüş + park | Medya menüsü: **📻 Canlı Radyo**, **📺 Canlı TV** (kanalın **sesi**) | Resmî medya kategorisi. |
| Android Auto, park, video | `PlayerActivity` (IPTV / video) | **Google Android Auto video erken erişimine kabul edilmeden görünmez.** Kod ve manifest hazır. |
| Android Automotive OS (Google built-in), park | Uygulamanın tamamı; Canlı TV / IPTV / video tam ekran | Resmî video kategorisi; Play'in araç kanalından dağıtım ve Google kalite incelemesi gerekir. |
| Android Automotive OS, sürüş | Sistem uygulamayı örter; video ve ses durur | DD-3 uyumlu. |

Yapılanlar:

* `CarMediaService` (Media3 `MediaLibraryService`): Canlı TV kanalları
  lisans/IPTV API'sinden (`/api/iptv`, `device_uuid` + `license_key`)
  alınır, önbelleğe yedeklenir ve Android Auto'da **ses** olarak oynatılır.
  Video izi kapalıdır. YouTube / Google Arama girişleri kaldırıldı.
* `automotive_app_desc.xml`: yalnızca `<uses name="media"/>`.
* `AndroidManifest.xml`: `android:appCategory="video"`,
  `android.hardware.type.automotive` (required=false) ve diğer isteğe bağlı
  donanımlar, `android.software.car.display_compatibility`, geniş
  `configChanges`; MediaProjection izni, NAVIGATION kategorisi, aauto
  servisleri kaldırıldı.
* `ParkedPlaybackPolicy` + `PlayerActivity`: `onPause`'da video durur,
  örtülüyken (sürüşte) tamponlama bitse bile otomatik başlamaz, park
  hâline dönünce (görünür olunca) kaldığı yerden sürer; `onStop` sonrası
  dönüşte yayın yeniden hazırlanır (önceden boş ekran kalıyordu).
* Telefon dışında (AAOS / araç UI modu) yön zorlanmaz.
* `CarScreen`: araç ekranında YouTube WebView ve Google Arama menüleri
  gizlenir (PC-1). Telefonda değişiklik yok.

### `projection` — Android Auto araç ekranında GÖRÜNTÜ (resmî olmayan yol)

```
./gradlew assembleProjectionRelease
```

Paket: `com.karadaglabs.cartv.projection` (play sürümüyle yan yana kurulur).

**Yöntem (kanıta dayalı):** Fermata Auto ve "AA Car Play Video" ile aynı
aile. Android Auto, `CATEGORY_PROJECTION_OEM` kategorili bir servisi
araç başlatıcısında "PROJECTION" türü uygulama olarak listeler; uygulama
`aauto.aar` (resmî olmayan Google Auto SDK) `CarActivityService` /
`CarActivity` ile Android Auto'nun araca kodlayıp gönderdiği bir
pencereye çizer. Görüntü telefonda üretilir, Android Auto bağlantısı
(USB/kablosuz) üzerinden H.264 akış olarak fabrikasyon ekrana gider;
araç tarafında hiçbir değişiklik gerekmez.

| Bileşen | Görev |
|---|---|
| `CarVideoService` (CATEGORY_PROJECTION / _OEM) | Android Auto başlatıcısındaki "Karadag Car TV" simgesi |
| `CarVideoActivity` (aauto `CarActivity`) | Kanal ızgarası, Canlı TV / IPTV görüntüsü (ExoPlayer → `SurfaceView`), radyo |
| `CarVideoPlayerHolder` | `CarMediaService`'in tek ExoPlayer'ını araç yüzeyine bağlar; park kilidinde görüntüyü keser, ses sürer |
| `CarDrivingSensors` + `DrivingSafetyGate` | **Park kilidi**: araç sensörleri (sürüş durumu, hız, vites, el freni) |
| `CarYouTubeActivity`, `CarGoogleActivity` + `HostedWebDrivingLock` | Araçta WebView; sürüşte duraklatılır ve gizlenir |
| `CarMirrorService` + `MirrorProjectionService` + `MirrorDrivingGate` | Car App Library NAVIGATION yüzeyine telefon ekranı yansıtma; yalnızca araç hızı ≈ 0 iken |

Araç ekranındaki kontroller (Canlı TV): dokununca açılan alt çubuk —
⏮ önceki kanal, ⏸/▶ duraklat/oynat, ⏭ sonraki kanal, ☰ kanal listesi,
⛶ tam ekran (üst menüyü gizler). Direksiyon/döner düğme medya tuşları:
PLAY_PAUSE, PLAY, PAUSE/STOP, NEXT/CHANNEL_UP, PREVIOUS/CHANNEL_DOWN.
Kanal seçimi ve son kanal `IptvRepository` ile kalıcıdır.

**Park kilidi (kullanıcı kapatamaz):**

* Kaynak: Android Auto bağlantısındaki araç sensörleri
  (`CarActivityHost.getCarManager("sensor")`). Head unit'in sürüş durumunu
  (DRIVING_STATUS) bildirmesi Android Auto protokolünde zorunludur.
* Görüntü yalnızca en az bir sinyal parkı **olumlu** gösterirse açılır:
  sürüş durumu kısıtsız (0), hız ≤ 0,5 m/s, vites P veya el freni çekili.
* Herhangi bir sinyal sürüşü gösterirse kapanır: sürüş durumu
  `NO_VIDEO` biti, hız > 0,5 m/s, vites D/R.
* Hiç sinyal yoksa görüntü **kapalıdır** (fail-safe). Ekranda neden yazar;
  Canlı TV sesi sürer.
* Ekran yansıtma: Car App Library `CarHardwareManager` hız verisi
  (`CAR_SPEED` izni); hız gelmezse yansıtma kapalı.
* Bağlantı kesildiğinde (`onStop`): sensörler bırakılır, görüntü
  kilitlenir, oynatma durur (telefonda kendi kendine çalmaz). Yeniden
  bağlanınca son kanal seçili gelir.

**Android Auto'nun dağıtım engeli (koddan bağımsız):** Android Auto
17.7 analizine göre (Fermata #846/#847) PROJECTION ve TEMPLATE türleri
"Bilinmeyen kaynaklar" ayarıyla **açılmaz**; uygulama ancak şu
durumlarda araç başlatıcısında görünür:

1. Kayıtlı yükleyici Google Play'dir — Play Console **iç test / kapalı
   test** kanalından kurulum (AA Car Play Video bu yolu kullandığını
   söylüyor). Play incelemesinin bu kategoriyi kabul edip etmeyeceği
   garanti değildir; üretim (production) kanalında reddedilme riski
   yüksektir.
2. Geliştirici head unit (Google DHU, AAWireless geliştirici modu,
   aa-proxy-rs).
3. Root + Xposed/LSPosed modülü (önerilmez).

Release imzası şu an debug anahtarıyla yapılıyor (`app/build.gradle.kts`);
Play'e yüklemek için gerçek bir imza anahtarı tanımlanmalıdır.

### Android Automotive OS ile farkı

Android Automotive OS (Google built-in) araçlarda uygulama doğrudan araç
bilgisayarında çalışır; `play` sürümü resmî video kategorisiyle
(`android:appCategory="video"`) tam ekran video oynatır, sürüşte sistem
uygulamayı örter. `aauto.aar`/projection yolu orada **kullanılmaz**.
Android Auto (telefon yansıtma) için yukarıdaki `projection` sürümü,
resmî yol için Google'ın video erken erişim programı gerekir.

## 3. Resmî yol: Android Auto video erken erişimi

1. Google'ın formuyla **Video Apps Early Access Partner** başvurusu
   (developer.android.com/training/cars/parked/video).
2. Kabul sonrası Google'ın ek şartları `play` yapısına eklenir.
3. IPTV yayın hakları belgelenmelidir.

## 4. Test

Otomatik (bu depoda, CI'da da çalışır):

```
./gradlew testPlayDebugUnitTest testProjectionDebugUnitTest lintPlayDebug lintProjectionDebug assemblePlayRelease assembleProjectionRelease
```

* `PlayComplianceTest`: manifest/descriptor/kaynak kuralları.
* `ParkedPlaybackPolicyTest`, `CarScreenTest`: park/sürüş davranışı.
* `CustomerStatusTest`, `RegistrationUiStateTest`: lisans zinciri.
* `testProjection/*`: projection yolu, park kilidi (`DrivingSafetyGate`), kanal değiştirme ve kontroller.

Elle (gerçek cihaz gerekir; bu bulut oturumunda yapılamadı):

1. **Android Auto medya (ses)** — Desktop Head Unit (DHU):
   * Telefonda Android Auto → Ayarlar → sürüm numarasına 10 kez dokun →
     Geliştirici ayarları → "Start head unit server".
   * Bilgisayarda: `adb forward tcp:5277 tcp:5277` ve
     `$ANDROID_HOME/extras/google/auto/desktop-head-unit`.
   * Play Store'dan kurulmamış (adb ile kurulmuş) uygulamalar için
     Geliştirici ayarları → "Unknown sources".
   * Beklenen: Karadag Car TV medya uygulaması; Canlı Radyo ve Canlı TV
     listeleri; kanal seçilince ses çalar, görüntü yoktur.
2. **Android Automotive OS (park video)** — Android Studio'da
   "Automotive (with Google Play)" emülatörü, API 35+:
   * `adb install app-play-debug.apk`, uygulamayı aç, lisansı etkinleştir,
     Canlı TV'den kanal oynat.
   * Sürüş benzetimi: Emulator → Extended controls → Car data (vitesi
     sürüşe al, hızı sıfırın üstüne çıkar). Beklenen: sistem katmanı
     uygulamayı örter, ses ve video durur; park'a dönünce oynatma sürer.
3. **Gerçek araç**: yukarıdaki 1. madde fabrikadan Android Auto olan
   araçta Play'den (iç test kanalı) kurulmuş uygulamayla tekrarlanır.

4. **projection sürümü, gerçek araç** (bu oturumda YAPILMADI):
   * Kurulum: Play iç test kanalından (`com.karadaglabs.cartv.projection`)
     veya geliştirici head unit ile.
   * Lisans: telefonda uygulamayı aç, e-posta ile etkinleştir (ayrı paket
     olduğu için lisans durumu bu pakette yeniden sorgulanır).
   * Araç park + el freni: Android Auto başlatıcısında "Karadag Car TV" →
     Canlı TV → kanal → görüntü araç ekranında olmalı.
   * Dokun → kontrol çubuğu; ⏭/⏮ kanal, ⏸/▶, ⛶ tam ekran; direksiyon
     medya tuşları.
   * Hareket et (güvenli alanda, yolcu gözlemciyle): görüntü kesilmeli,
     "Araç hareket hâlinde" yazısı çıkmalı, ses sürmeli. Durup park edince
     görüntü geri gelmeli.
   * `adb logcat -s KaradagCarDriving KaradagCarVideo CarVideoPlayerHolder`
     ile `CAR SENSORS supported=[...]` ve `VIDEO GATE` satırlarını kaydet:
     sensör numaraları farklıysa kilit "no_parked_signal" der ve görüntü
     hiç açılmaz (güvenli taraf) — bu durumda log ile düzeltilir.
   * Kabloyu çek / kablosuz bağlantıyı kes: telefonda ses durmalı;
     yeniden bağlanınca son kanal seçili gelmeli.
