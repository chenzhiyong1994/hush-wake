"""Check the static Pages artifact without third-party dependencies or network access."""

from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"
HOME = "https://chenzhiyong1994.github.io/hush-wake/"
REPO = "https://github.com/chenzhiyong1994/hush-wake"


class Page(HTMLParser):
    def __init__(self, source):
        super().__init__(convert_charrefs=True)
        self.ids = set()
        self.urls = []
        self.canonical = None
        self.headings = 0
        self.feed(source)

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if "id" in attrs:
            assert attrs["id"] not in self.ids, f"Duplicate id: {attrs['id']}"
            self.ids.add(attrs["id"])
        if tag == "h1":
            self.headings += 1
        if tag == "img":
            assert "alt" in attrs, "Image missing alt attribute"
        if tag == "link" and attrs.get("rel") == "canonical":
            self.canonical = attrs.get("href")
        for key in ("href", "src"):
            if key in attrs:
                self.urls.append(attrs[key])


def verify():
    source = (SITE / "index.html").read_text(encoding="utf-8")
    page = Page(source)
    assert page.headings == 1, "Homepage must have one primary heading"
    assert page.canonical == HOME, "Unexpected Pages canonical URL"
    for reference in page.urls:
        url = urlsplit(reference)
        if url.scheme or url.netloc:
            assert url.scheme == "https", f"Non-HTTPS URL: {reference}"
            if reference.startswith(f"{REPO}/blob/main/"):
                target = unquote(url.path.split("/blob/main/", 1)[1])
                assert (ROOT / target).is_file(), f"Missing linked repository file: {target}"
            continue
        assert not url.path.startswith("/"), f"Root-relative path breaks project Pages: {reference}"
        target = (SITE / unquote(url.path)).resolve() if url.path else SITE / "index.html"
        assert target.is_relative_to(SITE.resolve()), f"Asset escapes Pages artifact: {reference}"
        assert target.is_file(), f"Missing local target: {reference}"
        if url.fragment:
            if target.suffix == ".html":
                ids = page.ids if target.name == "index.html" else Page(target.read_text(encoding="utf-8")).ids
            else:
                ids = {element.get("id") for element in ET.parse(target).iter()}
            assert unquote(url.fragment) in ids, f"Missing fragment: {reference}"

    version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")).group(1)
    ios_version = re.search(r'MARKETING_VERSION:\s*"([^"]+)"', (ROOT / "ios/project.yml").read_text(encoding="utf-8")).group(1) + "-beta"
    release_tag = re.search(r'^TAG = "([^"]+)"', (ROOT / "scripts/ios/publish.py").read_text(encoding="utf-8"), re.MULTILINE).group(1)
    apk = f"{REPO}/releases/download/v{version}/HushWake-{version}.apk"
    ios_downloads = [f"{REPO}/releases/download/{release_tag}/HushWake-iOS-{ios_version}-{suffix}"
                     for suffix in ("unsigned.ipa", "simulator.zip", "source.zip")]
    for relative in ("site/index.html", "README.md", "README.zh-CN.md"):
        text = (ROOT / relative).read_text(encoding="utf-8")
        assert apk in text, f"Download version out of sync: {relative}"
        for download in ios_downloads:
            assert download in text, f"Missing or stale iOS download in {relative}: {download}"
        assert HOME in text, f"Missing homepage link: {relative}"
        versions = set(re.findall(r"\b\d+\.\d+\.\d+-beta\b", text))
        assert versions == {version, ios_version}, f"Stale beta metadata in {relative}: {versions}"

    allowed = {".html", ".css", ".js", ".svg"}
    files = [path for path in SITE.rglob("*") if path.is_file()]
    for path in SITE.rglob("*"):
        assert not path.is_symlink(), f"Symlink cannot be published: {path}"
        if path.is_file():
            assert path.suffix in allowed or path.name == ".nojekyll", f"Unexpected artifact: {path}"
    for svg in SITE.rglob("*.svg"):
        ET.parse(svg)
    print(f"PASS: {len(files)} static files, {len(page.urls)} URLs, project subpath, SVG, Android {version} and iOS {ios_version} downloads")


if __name__ == "__main__":
    verify()
