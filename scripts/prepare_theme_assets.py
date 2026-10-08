"""Bundle a checksum-pinned historical portrait at build time; no runtime download."""
import hashlib
from pathlib import Path
import urllib.request

SOURCE = 'https://upload.wikimedia.org/wikipedia/commons/a/a8/Ataturk1930s.jpg'
SHA1 = '441a28f5ebad06bd18c228ad28b297a77b0842e0'
TARGET = Path(__file__).resolve().parent.parent / 'sync-src/app/src/main/res/drawable-nodpi/ataturk_portrait.jpg'


def main():
    if TARGET.exists() and hashlib.sha1(TARGET.read_bytes()).hexdigest() == SHA1:
        print('Bundled portrait already verified')
        return
    request = urllib.request.Request(SOURCE, headers={'User-Agent': 'KocaaslanIsTakip/1.6.7 (https://github.com/ykocaaslan-hub/KocaaslanIsTakip)'})
    with urllib.request.urlopen(request, timeout=40) as response:
        data = response.read(1024 * 1024)
    if hashlib.sha1(data).hexdigest() != SHA1 or not data.startswith(b'\xff\xd8'):
        raise ValueError('Historical portrait checksum/type changed; refusing to bundle')
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print('Historical portrait verified and bundled (732x987, 403582 bytes)')


if __name__ == '__main__':
    main()
