# Karadag Car TV — Android Auto ve araç ekranında video

Son güncelleme: 26 Kasım 2026

Bu belge, KaradagCarTV'nin fabrikadan Android Auto bulunan araçların
ekranında **Google'ın izin verdiği şekilde** nasıl çalıştığını, neyin
çalışmadığını ve neyin Google'ın kararına bağlı olduğunu anlatır.

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

Google'ın **izin vermediği** ve bu projede "destekleniyor" olarak sunulmayan yollar:

* `aauto.aar` / `com.google.android.apps.auto.sdk.CarActivity` (Fermata
  tarzı, resmî olmayan Google Auto SDK). Üçüncü taraf uygulamalara
  yayınlanmış bir API değildir; Play'e gönderilemez.
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

### `sideloadLegacy` — DESTEKLENMEYEN, deneysel

```
./gradlew assembleSideloadLegacyDebug
```

Paket: `com.karadaglabs.cartv.legacy` (play sürümüyle yan yana kurulabilir).

Önceki aauto.aar `CarActivity`, MediaProjection yansıtma ve NAVIGATION
şablonu denemeleri silinmedi, `app/src/sideloadLegacy` altına taşındı.
Bu sürüm Google Play'e gönderilemez; gerçek araçta görünmesi Android Auto
geliştirici ayarlarında "Bilinmeyen kaynaklar"ın açık olmasına ve Android
Auto host sürümüne bağlıdır ve **garanti edilmez**. Bu oturumda gerçek
araçta doğrulanmamıştır.

## 3. Android Auto'da park hâlinde video için yapılması gerekenler

1. Google'ın formuyla **Video Apps Early Access Partner** başvurusu
   (bağlantı: developer.android.com/training/cars/parked/video →
   "Express interest in bringing your video app to Android Auto").
2. Kabul sonrası Google'ın verdiği ek manifest/SDK şartları (şu an
   herkese açık belgelenmiş değil) `play` yapısına eklenir.
3. IPTV yayın hakları: Google Play incelemesi, yayınlanan kanallar için
   dağıtım hakkı sorabilir. `KaradagCarTV-API` içindeki `contents`
   doğrulama alanları (verification) bu kayıt için kullanılabilir.

Google kabul etmeden, `play` yapısı Android Auto ekranında yalnızca
medya (ses) arayüzüyle görünür. Bu, Google'ın politikasıdır; kodla
aşılamaz ve aşılmaya çalışılmamıştır.

## 4. Test

Otomatik (bu depoda, CI'da da çalışır):

```
./gradlew testPlayDebugUnitTest testSideloadLegacyDebugUnitTest lintPlayDebug assemblePlayRelease assembleSideloadLegacyDebug
```

* `PlayComplianceTest`: manifest/descriptor/kaynak kuralları.
* `ParkedPlaybackPolicyTest`, `CarScreenTest`: park/sürüş davranışı.
* `CustomerStatusTest`, `RegistrationUiStateTest`: lisans zinciri.
* `testSideloadLegacy/*`: eski projection kodunun mevcut davranışı.

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
