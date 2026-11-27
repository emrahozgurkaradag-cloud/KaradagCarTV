# KaradagCarTV arayüz ve ÖK marka kimliği

## ÖK monogramı
- Dosya: `app/src/main/res/drawable/ic_ok_monogram.xml` (108 birim ızgara, tamamen vektör).
- Ö: turuncu (#FF7A1A) halka + iki nokta. K: açık renk gövde, halkanın sağ kenarıyla
  aynı çizgiyi paylaşır; iki çapraz kol halkanın dışına açılır.
- Kullanıldığı yerler: adaptif uygulama ikonu (ön/arka/monochrome), Android 12+ sistem açılış
  ekranı ve API 29-30 açılış arka planı, telefon üst barı (`OkTopBar`), ayarlar ekranı,
  oynatıcı üst bilgisi, araç ekranı başlığı (`HostedNavigation.menuBar`), yansıtma bildirimi
  küçük ikonu (`ic_ok_notification`).
- Renkler: turuncu #FF7A1A, antrasit #1B1E23 / #23272E, yüzey #2A2F37, yazı #F4F1EC.
- Simge seti `ic_ok_*` (24 birim, 2 birim çizgi); stok/üçüncü taraf ikon kullanılmaz.
  YouTube ve Google bölümleri genel "video" ve "web" simgeleriyle gösterilir; bu markaların
  logoları kullanılmaz.

## Tasarım sistemi
- Telefon (Compose): `ui/theme/*`, `ui/OkUi.kt`. Ölçüler `OkMetrics` ile ekran sınıfına göre:
  araç modu (UI_MODE_TYPE_CAR / Automotive) veya kısa yatay ekranlarda dokunma hedefi ≥ 64–76dp.
- Araç (Android Auto host penceresi, View): `CarUiKit`, `CarChannelBrowser`, `HostedNavigation`.
  Aynı renkler ve simgeler; yerleşim head-unit boyutlarına göre (GridView AUTO_FIT, 240dp sütun).

## Ana ekran (telefon)
- Önbellekten anında çizilir (lisans özellikleri, içerik, kanallar); lisans doğrulama, içerik
  ve IPTV arka planda yenilenir. Lisans hata kodlarında kayıt ekranına dönülür (değişmedi).
- Canlı TV, YouTube, Radyo, Google her zaman görünür. Lisansta yoksa veya araç ekranında
  web özelliklerine izin verilmiyorsa kutucuk kilit simgesi ve nedeniyle gösterilir.
- Arama (kanal/kategori; kanal yoksa Google araması önerisi), son izlenenler, öne çıkanlar
  (sunucu `is_featured`; yoksa `sort_order` ilk kanallar), kategoriler, favoriler, ayarlar.

## Canlı TV
- Kaynak yalnızca `/api/iptv` (mevcut IptvRepository + önbellek). Yeni yayın adresi eklenmedi.
- Sunucu yalnızca `is_active` ve `is_verified` kanalları döndürür. Playlist senkronizasyonuyla
  gelen kanallar `is_active=false`, `is_verified=false` ile içe aktarılır ve yönetici panelinde
  doğrulanana kadar uygulamada görünmez. Bu, bilinçli bir güvenlik kuralıdır; istemci tarafı
  bu kanalları gösteremez.
- İstemci tarafında hiçbir geçerli kanal gizlenmez: kategoriler sunucu verisinden dinamik,
  bilinmeyen kategori adları olduğu gibi gösterilir; `;` ile çoklu kategori desteklenir.
- Favoriler ve son izlenenler (en fazla 12) yalnızca cihazda (`ChannelLibraryStore`).
  Araç ekranında favori: kanal kartına uzun basış.

## Ekran görüntüleri
- `UiScreenshotDeviceTest` (CI emülatörü, API 29/34): telefon ana ekranı, Canlı TV, kategori,
  favoriler, oynatıcı; araç yerleşimi 800x480, 1280x720, 1920x720.
- Telefon ekranları ağ kapalıyken çekilir (sahte test lisansı sunucuya gitmez), bu yüzden
  "Çevrimdışı" şeridi görünür. Araç yerleşimi aynı View sınıflarının ekran dışı çizimidir;
  gerçek Android Auto host penceresi veya gerçek araç değildir.
