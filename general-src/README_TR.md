# Kocaaslan İş Takip

Yavuz Kocaaslan ve Kocaaslan Kantin için hazırlanmış, internetsiz çalışan native Android ciro/gider ve kâr-zarar takip uygulaması.

## Özellikler

- İki işletme için birbirinden ayrı kayıtlar
- Gelir ve gider girişi
- Günlük, haftalık ve aylık gelir / gider / net kâr-zarar
- Gelir ve gider kategorileri
- Son 6 aylık grafik
- Arama ve dönem filtreleme
- CSV dışa aktarma (Excel ile açılabilir)
- JSON yedekleme ve geri yükleme
- İsteğe bağlı 4 haneli PIN kilidi
- SQLite ile cihazda yerel veri saklama
- Tablet kullanımına uygun büyük, renkli arayüz

## APK

Bu proje, `../.github/workflows/apk.yml` dosyası sayesinde GitHub Actions üzerinden otomatik olarak kurulabilir APK üretir. Ayrıntılı adımlar için `APK_OLUSTURMA_REHBERI.md` dosyasına bakın.


## Sürüm 1.1.0
- Genel Toplam kartı eklendi: ilk kayıttan bugüne toplam gelir, gider ve net kâr/zarar.

## Sürüm 1.3.1
- PIN oluşturma kontrolü düzeltildi.
- Yedekler silmeden önce doğrulanır; geri yükleme tek veritabanı işlemiyle yapılır, hata durumunda geri alınır.
- Kayıt ekleme başarısızlığı bildirilir.
- Kayıt satırına dokunarak tutar, kategori, açıklama ve tarih düzenlenir.
- 200 kayıt sınırı kaldırıldı.
- Ayarlar üzerinden özel tarih aralığı ve kategori raporu açılır. Kategori raporu seçili dönem ve aramayı kullanır.
- Ekran yeniden oluşturulduğunda işletme, arama ve filtre korunur.
- Sistem çubukları için ekran kenarı boşlukları eklendi.

## Doğrulama durumu
55 yerel Java/SQLite kontrolü, kaynak sözdizimi ve XML doğrulaması başarılı. Ayrıntılar TEST_SONUCLARI.md dosyasında. Bu ortamda Gradle/Android SDK ile APK derlemesi ve cihaz testi yapılmadı. Yayından önce debug derlemesiyle PIN, kayıt düzenleme, ekran döndürme, Android 15/16 görünümü ve yedek geri yükleme senaryolarını test edin. Orijinal verilerinizi uygulama içinden yedekleyin.
