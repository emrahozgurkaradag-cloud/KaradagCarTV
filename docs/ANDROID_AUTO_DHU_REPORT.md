# KaradagCarTV – Android Auto host entegrasyonu ve video görüntüleme: son rapor

Tarih: 27.11.2026 · Branch: `android-auto-official-video` · `main`'e dokunulmadı, PR/merge yok.

## 1. Sonuç

Android Auto 18.1 (Google imzalı) Desktop Head Unit'te uygulamayı tanıyor, **ÖK TV** girişinden `CarVideoActivity`'yi açıyor ve Media3 videosu araç ekranında görünüyor. Park kilidi `restrict all` / `restrict none` ile doğru çalışıyor. Bağlantı kesilip yeniden kurulunca uygulama toparlanıyor.

**Gerçek araçta test yapılmadı.** DHU, emülatördeki gerçek Android Auto'ya bağlanan resmî Google aracıdır ama gerçek aracın birebir karşılığı değildir.

## 2. Durumlar

| Alan | Durum | Kanıt |
|---|---|---|
| Build (play/projection × debug/release + AAB) | PASS | CI [46019307194](https://github.com/emrahozgurkaradag-cloud/KaradagCarTV/actions/runs/46019307194), `a095a16` |
| Birim testleri | PASS | play 86/86, projection 111/111 |
| Lint | PASS | 0 hata (play 80, projection 103 uyarı) |
| Emülatör cihaz testleri | PASS | API 29: 22/22, API 34: 22/22 |
| DHU keşif (AA `CarVideoService`'i bağladı, host kodu yüklendi) | PASS | DHU CI [45994044154](https://github.com/emrahozgurkaradag-cloud/KaradagCarTV/actions/runs/45994044154) |
| DHU activity başlatma (`CarVideoActivity`) | PASS | `CAR VIDEO ACTIVITY AÇILDI`, pencere `CarActivityHostImpl` PhoneWindow |
| DHU video görüntüleme | PASS | `car-s1-playing.png`, `car-s2-playing.png` (doymuş piksel oranı 0.71) |
| Gerçek araç | **TEST EDİLMEDİ** | — |

### DHU ayrıntısı (s1 = ilk bağlantı, s2 = kesip yeniden bağlanma)

| Kontrol | s1 | s2 |
|---|---|---|
| ÖK TV seçildi (ÖK Yansıtma açılmadı) | PASS | PASS |
| `CarVideoActivity` açıldı | PASS | PASS |
| Surface oluştu (800×400 ekran, SurfaceView 800×328) | PASS | PASS |
| Media3 `setVideoSurfaceHolder` bağlandı | PASS | PASS |
| İlk kare (1280×720 H.264) | PASS (42 sn) | PASS |
| Video DHU ekranında | PASS | PASS |
| `restrict all` → görüntü kapalı (`driving_status_restricted`) | PASS | PASS |
| `restrict none` → `parked`, video geri geldi | PASS (11 sn) | PASS (15 sn) |
| Bağlantı kesilince çökme yok | PASS | PASS |

Test yayını: CI içinde ffmpeg ile üretilen yerel test deseni (`http://10.0.2.2:8088/test.ts`). Uygulamaya yeni yayın adresi eklenmedi.

## 3. APK'lar

Artifact: [`apks`](https://github.com/emrahozgurkaradag-cloud/KaradagCarTV/actions/runs/46019307194/artifacts/15207567687) (ayrıca `release-bundles`, `reports`, `device-test-reports-api29/34`, `ui-screenshots-api29/34`).

| Dosya | Paket | Sürüm | İmza |
|---|---|---|---|
| `play/debug/app-play-debug.apk` | `com.karadaglabs.cartv` | 1.2.0 (8) | debug anahtarı |
| `play/release/app-play-release-unsigned.apk` | `com.karadaglabs.cartv` | 1.2.0 (8) | **İMZASIZ** |
| `projection/debug/app-projection-debug.apk` | `com.karadaglabs.cartv.projection` | 1.2.0-projection (8) | debug anahtarı |
| `projection/release/app-projection-release-unsigned.apk` | `com.karadaglabs.cartv.projection` | 1.2.0-projection (8) | **İMZASIZ** |

Release imzası: `KARADAG_RELEASE_*` GitHub Secrets tanımlı değil. Yeni anahtar üretilmedi, imza düzeni değiştirilmedi. Release APK/AAB'ler bu hâliyle Play'e yüklenemez. Android Auto'da test için `projection/debug` APK'sı kullanılabilir.

## 4. Bulunan ve düzeltilen gerçek sorun (uygulama)

Android Auto ızgarasında iki giriş vardı ve ikisi de "Karadag Car TV" etiketliydi; AA bunları "Karadag ..." diye kısaltıyordu. DHU testinde video yerine yansıtma (`CarMirrorService` → MediaProjection izni) açıldı. Kullanıcı da hangisinin video olduğunu ayırt edemezdi.

Düzeltme: `CarVideoService` → **ÖK TV**, `CarMirrorService` → **ÖK Yansıtma**.

Değişen uygulama dosyaları:

| Dosya | Neden |
|---|---|
| `app/src/projection/AndroidManifest.xml` | İki servisin etiketi string kaynağına bağlandı |
| `app/src/projection/res/values/strings_car_launcher.xml` (yeni) | "ÖK TV", "ÖK Yansıtma" |
| `app/src/testProjection/.../AndroidAutoDiscoveryTest.kt` | Etiketlerin farklı ve kısaltılmayacak uzunlukta kalmasını denetleyen test |

`CarVideoActivity`, `CarVideoService`, Media3 oynatıcı, Surface bağlama, park kilidi ve `DrivingSafetyGate` değiştirilmedi. DHU sonuçları mevcut pencere/Surface mekanizmasının (host PhoneWindow içinde SurfaceView, varsayılan z-order) doğru çalıştığını gösterdi; z-order veya Surface için kod değişikliği gerekmedi.

## 5. Test altyapısı (emülatöre özgü, uygulamaya dokunmaz)

| Dosya | Neden |
|---|---|
| `.github/workflows/android-auto-dhu-launch.yml` (yeni) | API 34 emülatöründe AA 18.1 + DHU uçtan uca testi, kanıtlar artifact'a |
| `scripts/dhu-launch.sh` (yeni) | AA kurulumu ve imza doğrulama, geliştirici modu, head unit server, test kanalı |
| `scripts/dhu_session.py` (yeni) | DHU oturumu: ızgarada ÖK TV, kanal seçimi, Surface/kare/ekran görüntüsü, park kilidi, yeniden bağlantı |
| `.github/workflows/android.yml`, `android-auto-dhu.yml`, `.gitignore` | Betik değişikliklerinde ana CI'yı tetiklememe; Python önbelleği hariç |
| `docs/ANDROID_AUTO.md` | §7.3 DHU sonuçları, güncel kısıtlamalar |

Emülatörde gerekip gerçek telefonda gerekmeyen adımlar (yalnızca Android Auto'yu etkiler):

1. Emülatör imajlarındaki Android Auto taslakları AOSP test anahtarıyla imzalı; Google imzalı sürüm üstüne kurulamıyor. Taslak kaldırılıp AA 18.1 ayrıcalıklı sistem uygulaması olarak yerleştirildi. Gerçek telefonlarda olduğu gibi, otomotiv projeksiyon rolü ve CDM izni olmadan AA'nın `:car` süreci çöküyordu.
2. AA 18.1'de devre dışı gelen `FirstActivityImpl` etkinleştirildi ve bildirim erişimi verildi. Gerçek telefonda bunları AA'nın kendi kurulumu ve kullanıcı yapar.

## 6. İstenen sorulara kısa yanıtlar

1. **Keşif:** AA, `CarVideoService`'i `MAIN` + `CATEGORY_PROJECTION` / `CATEGORY_PROJECTION_OEM` ile buluyor. AA 18.1 ızgarasında "ÖK TV" olarak görünüyor.
2. **Manifest:** Tam. Tek eksik ayırt edilebilir etiketti (düzeltildi).
3. **Bağlantıda çalışan kod:** `CarVideoService.onCreate` → `HOST_LOAD ok=true` (`DynamicApiFactory.newCarActivityServiceProxy`) → `onBind` → `HOST BIND OK`.
4. **CarVideoActivity gerçekten açılıyor mu:** Evet, DHU'da iki oturumda da açıldı.
5. **aauto.aar ne sağlıyor:** `CarActivityService` / `CarActivity` (host'un yüklediği proxy) ve host penceresi.
6. **Pencere/Surface:** `CarActivityHostImpl` PhoneWindow, araç ekranı 800×400 sanal ekran (`presentation=true`), içinde `SurfaceView` → `ExoPlayer.setVideoSurfaceHolder`.
7. **Z-order / yaşam döngüsü / odak:** Video host penceresinin arkasında kalmıyor (ekran görüntüsü). Sürüş kısıtında Surface yok ediliyor, park edince yeniden oluşup bağlanıyor. Bağlantı kesilince `ACTIVITY_STOP` → `SERVICE_UNBIND`, çökme yok.
8. **Fermata ile karşılaştırma:** Aynı yöntem: `MainCarActivity.getWindow()` = host penceresi, `CATEGORY_PROJECTION(_OEM)` servisi, aynı `aauto.aar` SDK'sı. Fark yok, ek düzeltme gerekmedi.

## 7. Açık gözlemler (oynatmayı engellemiyor, kod değiştirilmedi)

* `CarVideoFocus` isteği AA 18.1'de `NoClassDefFoundError` ile reddediliyor. Video yine de görünüyor; sonucu oynatmayı yönetmiyor.
* `getLatestSensorEvent` bazı sensörlerde `Wrong signature` döndürüyor. Park kilidi dinleyicilerle doğru karar verdi (fail-safe: doğrulanamazsa görüntü kapalı).
* İlk bağlantıda canlı TS kaynağı geç hazır olabiliyor (s1: ilk kare 42 sn).

## 8. Gerçek araçta doğrulanması gerekenler

Play iç test kanalından kurulumla başlatıcıda görünme, gerçek araç sensörleriyle park kilidi, gerçek IPTV yayınları, direksiyon tuşları, farklı ekran çözünürlükleri. Ayrıntı: `docs/ANDROID_AUTO.md` §9.
