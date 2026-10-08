# Fenerbahçe and Atatürk personal theme

The user requested a navy/yellow design and a respectful Atatürk tribute. The
dashboard uses a 1907 badge, native text, a historical portrait and a personal
dedication. It does not present the app as an official club product. The portrait
is bundled into the APK and available offline; the app makes no photo-download
request and adds no permission or image-loading dependency.

## Photograph

- Source: [Ataturk1930s.jpg, Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Ataturk1930s.jpg).
- Description/date on Commons: Mustafa Kemal Atatürk, 29 March 1932.
- Photographer: unknown, as recorded on the source page.
- Commons license: public domain in Turkey (PD-TR).
- File: 732 × 987 JPEG, 403,582 bytes.
- Source SHA-1 recorded by Commons: `441a28f5ebad06bd18c228ad28b297a77b0842e0`.

`python3 scripts/prepare_theme_assets.py` verifies these exact image bytes and
places them in drawable-nodpi before copying sync-src or building Android. Run
that step once before the existing local build instructions. A source change
fails verification instead of silently replacing the photograph. Cached verified
bytes are reused. The original photo is not retouched; ImageView scales/crops it
inside its rounded portrait frame. Text/identity/years stay native and accessible.

The original horizontal business/period/summary/navigation arrangement remains.
The UI uses content-driven heights and honors Android font scaling. Existing
native layout checks cover phone, tablet, landscape and large text, and export
fixture screenshots under the regression-test-report artifact. These previews use
test data, never uploaded user records.
