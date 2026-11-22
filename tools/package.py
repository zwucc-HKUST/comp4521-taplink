"""Export the committed Android Studio project, demo QR cards, and an installable APK."""
from hashlib import sha256
from pathlib import Path
import argparse
import json
import subprocess
import os
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("output", nargs="?", type=Path, default=root.parent / "deliverables" / "Astra")
parser.add_argument("--offline", action="store_true", help="Build with the local Gradle dependency cache.")
options = parser.parse_args()
out = options.output.resolve()
if out.is_relative_to(root):
    raise SystemExit("Choose an output directory outside the checkout.")

def git(*args):
    return subprocess.check_output(["git", *args], cwd=root)
if git("status", "--porcelain").strip():
    raise SystemExit("Commit changes before packaging to identify the source revision.")
revision = git("rev-parse", "HEAD").decode().strip()
wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
command = [str(wrapper), "assembleDebug"]
if options.offline:
    command.append("--offline")
subprocess.run(command, cwd=root, check=True)
if git("status", "--porcelain").strip() or git("rev-parse", "HEAD").decode().strip() != revision:
    raise SystemExit("Checkout changed during the build. Run packaging again on the intended revision.")
apk = (root / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()
out.mkdir(parents=True, exist_ok=True)
(out / "TapLink-Astra.apk").write_bytes(apk)
meta = {"revision": revision, "apkSha256": sha256(apk).hexdigest()}
bundle = out / "TapLink-Astra-Handoff.zip"
# Git supplies the committed bytes and Unix executable mode even on Windows.
subprocess.run(["git", "archive", "--format=zip", "--prefix=TapLinkAstra/",
                "--output", str(bundle), revision], cwd=root, check=True)
with zipfile.ZipFile(bundle, "a", zipfile.ZIP_DEFLATED) as z:
    z.writestr("TapLink-Astra.apk", apk)
    z.writestr("BUILD_INFO.json", json.dumps(meta, indent=2))
with zipfile.ZipFile(bundle) as z:
    assert z.testzip() is None
    assert sha256(z.read("TapLink-Astra.apk")).hexdigest() == meta["apkSha256"]
print(bundle, meta["revision"], meta["apkSha256"])
