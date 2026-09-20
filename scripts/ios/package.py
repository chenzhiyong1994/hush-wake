"""Package the unsigned device app and simulator app from verified Xcode builds."""
from pathlib import Path
import argparse
import hashlib
import plistlib
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def archive(source: Path, destination: Path, prefix: str):
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as bundle:
        for file in sorted(source.rglob("*")):
            if file.is_file():
                bundle.write(file, str(Path(prefix) / file.relative_to(source)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", type=Path, required=True)
    parser.add_argument("--simulator", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    device = args.device.resolve()
    with (device / "Info.plist").open("rb") as file:
        info = plistlib.load(file)
    assert info["CFBundleIdentifier"] == "com.hushwake.app.ios"
    assert (device / info["CFBundleExecutable"]).is_file()
    assert not (device / "embedded.mobileprovision").exists(), "Public unsigned build must contain no provisioning profile"
    assert not (device / "_CodeSignature").exists(), "Unexpected signing state"
    assert len(list(device.glob("*.m4a"))) == 14, "Incomplete offline audio bundle"
    version = info["CFBundleShortVersionString"]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    archive(device, output / f"HushWake-iOS-{version}-beta-unsigned.ipa", "Payload/HushWake.app")
    archive(args.simulator.resolve(), output / f"HushWake-iOS-{version}-beta-simulator.zip", "HushWake.app")
    shutil.copy(ROOT / "docs/ios-install.md", output / "INSTALL.md")
    shutil.copy(ROOT / "docs/audio-credits.md", output / "AUDIO-CREDITS.md")
    shutil.copy(ROOT / "LICENSE", output / "LICENSE")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    (output / "BUILD.txt").write_text(f"Source commit: {commit}\niOS {version} beta\nSigning: UNSIGNED (device)\n", encoding="utf-8")
    files = sorted(path for path in output.iterdir() if path.is_file() and path.name != "SHA256SUMS.txt")
    checksums = "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in files)
    (output / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8")
    print(f"Packaged {version} from {commit}; device IPA requires user signing before installation.")


if __name__ == "__main__":
    main()
