# Kocaaslan İş Takip — APK oluşturma

Bu proje native Android uygulamasıdır. İnternet olmadan çalışır ve verileri cihazdaki SQLite veritabanında saklar.

## En kolay yol: GitHub üzerinden APK oluşturma

1. GitHub üzerinde `ykocaaslan-hub/KocaaslanIsTakip` deposunu açın.
2. Ana proje depodaki **KocaaslanIsTakip/** klasörüdür; v2–v6 ZIP dosyaları yalnızca yedektir.
3. Depo Settings > Secrets and variables > Actions bölümüne mevcut imza anahtarının şifresini `KOCAASLAN_SIGNING_PASSWORD` adıyla ekleyin. Ardından GitHub'da üst menüden **Actions** bölümüne girin.
4. Soldan **Kocaaslan Is Takip APK** iş akışını seçin.
5. **Run workflow** düğmesine basın.
6. İşlem başarıyla tamamlandığında çalışmanın sayfasındaki **Artifacts** bölümünde **KocaaslanIsTakip-APK** dosyasını indirin.
7. İndirilen ZIP'i açın. İçindeki `app-release.apk` dosyasını Android tabletinize gönderip açın.
8. Android, ilk kurulumda “Bu kaynaktan uygulama yüklemeye izin ver” izni isteyebilir. Yalnızca kendi oluşturduğunuz APK için izin verin.

GitHub Actions release APK üretir. Depo Settings > Secrets and variables > Actions bölümüne, şifreli imza anahtarının mevcut şifresini `KOCAASLAN_SIGNING_PASSWORD` adıyla ekleyin. Yeni bir şifre mevcut şifreli anahtarı açamaz.

## Android Studio ile

Projeyi Android Studio'da açın. Gradle eşitlemesi bittikten sonra **Build > Build APK(s)** seçin. APK genellikle `app/build/outputs/apk/debug/app-debug.apk` yolunda oluşur.

## Uygulama bilgileri

- Uygulama adı: **Kocaaslan İş Takip**
- Paket: `com.kocaaslan.istakip`
- Android minimum sürüm: Android 6.0 (API 23)
- Hedef Android: API 36
- İşletmeler: **Yavuz Kocaaslan** ve **Kocaaslan Kantin**
- Veriler cihazda saklanır; uygulamayı kaldırmadan önce uygulama içindeki yedekleme özelliğini kullanmanız önerilir.
