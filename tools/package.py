"""Export the committed Android Studio project, demo QR cards, and an installable APK."""
from hashlib import sha256
from pathlib import Path
import json
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root.parent / "deliverables" / "Astra"
out.mkdir(parents=True, exist_ok=True)
def git(*args):
    return subprocess.check_output(["git", *args], cwd=root)
if git("status", "--porcelain").strip():
    raise SystemExit("Commit changes before packaging to identify the source revision.")
apk = (root / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()
(out / "TapLink-Astra.apk").write_bytes(apk)
files = git("ls-files", "-z").decode("utf-8").split("\0")
meta = {"revision": git("rev-parse", "HEAD").decode().strip(), "apkSha256": sha256(apk).hexdigest()}
bundle = out / "TapLink-Astra-Handoff.zip"
with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as z:
    for name in filter(None, files):
        z.write(root / name, "TapLinkAstra/" + name)
    z.writestr("TapLink-Astra.apk", apk)
    z.writestr("BUILD_INFO.json", json.dumps(meta, indent=2))
with zipfile.ZipFile(bundle) as z:
    assert z.testzip() is None
    assert sha256(z.read("TapLink-Astra.apk")).hexdigest() == meta["apkSha256"]
print(bundle, meta["revision"], meta["apkSha256"])
