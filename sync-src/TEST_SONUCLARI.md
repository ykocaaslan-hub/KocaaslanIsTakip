# Test sonuçları — 1.3.1

## Çalıştırılan kontroller

- Beş Java kaynak dosyası Java 17 sözdizimiyle ayrıştırıldı: başarılı.
- PIN, yedek kayıt doğrulama, tarih hesapları, CSV kaçışları ve düzenleme tutarı için 28 Java kontrolü: başarılı. Bu kontroller MainActivity kaynak kodundan çıkarılan gerçek ifadeleri/metotları çalıştırır; Android ekranını çalıştırmaz.
- Gerçek DbHelper.java kodu ve Python sqlite3 üzerinden gerçek SQLite motoruyla 27 kontrol: başarılı. Android SQLite API yerine test köprüsü kullanılır; Android davranışının tam doğrulaması değildir.
- Veritabanı kontrolleri: işletme ayrımı, gelir/gider/net toplamı, başlangıç dahil/bitiş hariç tarih filtresi, açıklama/kategori/tür araması, SQL parametreleri, tutar/tarih/kategori güncelleme, olmayan kayıtta hata, başarısız ekleme, geçerli/boş yedek, hata sırasında rollback, 250 kayda erişim, limit ve altı aylık grafik verileri.
- XML dosyaları ayrıştırıldı ve uygulama/sürüm metinleri karşılaştırıldı: başarılı.
- ZIP bütünlüğü kontrol edildi: başarılı.

## Testte bulunan ve giderilen hata

Düzenleme ekranı mevcut tutarı iki ondalığa yuvarlıyordu: 123.456 → 123.46. Bu senaryo testte başarısız oldu. Form artık Double.toString ile mevcut tutarı koruyor; test yeniden çalıştırıldığında geçti.

## Tamamlanamayan doğrulama

Gradle ve Android SDK bu ortamda kurulu değil. Ağ erişimi isteği tamamlanmadığı için araçlar indirilemedi. APK derlemesi, Android lint, emülatör/gerçek cihaz testleri yapılmadı. Yerel testlerin geçmesi, APK'nın derlendiği veya Android üzerinde tüm ekranların çalıştığı anlamına gelmez. Android 15/16 sistem çubukları, ekran döndürme, dosya seçici ve dokunma/kaydırma davranışları cihazda doğrulanmalıdır.

Test düzeneği bu çalışma alanında /workspace/android-tests altında tutuluyor.
