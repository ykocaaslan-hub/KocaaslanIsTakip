"""Legacy build entry point; UI fixes now live in sync-src and are tested directly."""
from pathlib import Path

main = Path("app/app/src/main/java/com/kocaaslan/istakip/MainActivity.java")
source = main.read_text(encoding="utf-8")
if "ScrollView mainScroll" not in source:
    raise SystemExit("Missing source scrolling layout; build sync-src instead of an old archive")
