"""Attach verified iOS downloads to an EXISTING release; never create or move a tag."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
REPO = "chenzhiyong1994/hush-wake"
TAG = "v0.4.8-beta"
VERSION = "0.1.0-beta"


def run(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--downloads", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("GITHUB_REPOSITORY") != REPO or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise SystemExit("Release upload requires the first-party main-branch workflow")
    commit = run("git", "rev-parse", "HEAD")
    assert commit == os.environ.get("EXPECTED_SOURCE_COMMIT"), "Source drifted from the audited commit"
    files = args.downloads.resolve()
    build = (files / "BUILD.txt").read_text()
    assert f"Source commit: {commit}\n" in build, "Artifact source differs from checked-out source"
    assert "Signing: UNSIGNED (device)" in build
    expected = {
        f"HushWake-iOS-{VERSION}-unsigned.ipa", f"HushWake-iOS-{VERSION}-simulator.zip",
        "INSTALL.md", "AUDIO-CREDITS.md", "LICENSE", "BUILD.txt",
    }
    manifest = (files / "SHA256SUMS.txt").read_text()
    checksums = {}
    for line in manifest.splitlines():
        sha, name = line.split("  ", 1)
        assert re.fullmatch(r"[a-f0-9]{64}", sha) and name in expected
        assert name not in checksums and digest(files / name) == sha, "Artifact checksum mismatch"
        checksums[name] = sha
    assert set(checksums) == expected
    assert {p.name for p in files.iterdir()} == expected | {"SHA256SUMS.txt"}

    release = json.loads(run("gh", "api", f"repos/{REPO}/releases/tags/{TAG}"))
    original_assets = {item["name"]: item.get("digest") for item in release["assets"]}
    assert "HushWake-0.4.8-beta.apk" in original_assets, "Expected existing Android release not found"
    tag_before = run("gh", "api", f"repos/{REPO}/git/ref/tags/{TAG}", "--jq", ".object.sha")
    source = files / f"HushWake-iOS-{VERSION}-source.zip"
    subprocess.run(["git", "archive", "--format=zip", f"--prefix=HushWake-iOS-{VERSION}/",
                    "--output", str(source), commit], cwd=ROOT, check=True)
    checksums[source.name] = digest(source)
    (files / "SHA256SUMS.txt").write_text("".join(f"{sha}  {name}\n" for name, sha in sorted(checksums.items())))
    checksums["SHA256SUMS.txt"] = digest(files / "SHA256SUMS.txt")
    for name, sha in checksums.items():
        if name in original_assets:
            assert original_assets[name] == f"sha256:{sha}", f"Refusing to replace an existing asset: {name}"
        else:
            subprocess.run(["gh", "release", "upload", TAG, str(files / name), "--repo", REPO], check=True)

    marker = f"## iOS {VERSION}"
    notes = release["body"].split(marker)[0].rstrip()
    notes += f"""\n\n{marker}（独立开源测试版）

- 原生 SwiftUI，iOS / iPadOS 17+；单次/周重复闹钟、一次稍后提醒、6 种闹铃、8 种助眠声、定时渐隐和本地数据。
- IPA **未签名**，需要自己的签名身份才能安装；另附模拟器包、独立源码包、安装说明及 SHA-256。
- 有声闹钟需要保持应用前台；后台和锁屏仅无声通知。助眠声支持正常后台播放。
- 仅内置扬声器或明确识别的唯一有线耳机可播放；蓝牙/USB/未知输出保持阻断。实体机零串音和个人签名安装仍待验证，非稳定版。
- 构建已通过核心单元测试、iOS 集成/UI 测试、模拟器启动和 Release arm64 构建。

iOS 源码提交：[{commit}](https://github.com/{REPO}/tree/{commit})。
验证记录：https://github.com/{REPO}/actions/runs/{os.environ['GITHUB_RUN_ID']} 。
安装方法：[iOS 下载与安装](https://github.com/{REPO}/blob/{commit}/docs/ios-install.md)。

原 `v0.4.8-beta` 标签和 Android APK 保持不变。GitHub 页面自带的旧标签 Source code 归档不包含 iOS；请下载 `HushWake-iOS-{VERSION}-source.zip` 或上述提交的源码。
"""
    with tempfile.TemporaryDirectory(prefix="hushwake-release-") as temporary:
        body = Path(temporary, "notes.md")
        body.write_text(notes, encoding="utf-8")
        subprocess.run(["gh", "release", "edit", TAG, "--repo", REPO,
                        "--title", "HushWake 0.4.8-beta + iOS 0.1.0-beta", "--notes-file", str(body)], check=True)
    final = json.loads(run("gh", "api", f"repos/{REPO}/releases/tags/{TAG}"))
    published = {item["name"]: item.get("digest") for item in final["assets"]}
    assert all(published.get(name) == f"sha256:{sha}" for name, sha in checksums.items())
    assert all(published.get(name) == sha for name, sha in original_assets.items())
    assert tag_before == run("gh", "api", f"repos/{REPO}/git/ref/tags/{TAG}", "--jq", ".object.sha")
    print(f"Published and verified {len(checksums)} iOS assets; Android tag and assets unchanged: {final['html_url']}")


if __name__ == "__main__":
    main()
