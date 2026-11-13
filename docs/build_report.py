#!/usr/bin/env python3
"""Builds docs/Aurviz_Project_Report.pdf from the Markdown documents.

Requirements: python3, `pip install markdown pymupdf`, and Google Chrome (used headless to print the PDF
and to render the Mermaid diagrams, which needs internet access for the Mermaid script).

    python3 docs/build_report.py
"""
import datetime
import html
import pathlib
import re
import shutil
import subprocess
import sys

import markdown
import pymupdf

ROOT = pathlib.Path(__file__).resolve().parent
OUT_HTML = ROOT / "Aurviz_Project_Report.html"
OUT_PDF = ROOT / "Aurviz_Project_Report.pdf"

# (file, short title shown in the contents)
DOCUMENTS = [
    ("SRS.md", "Software Requirements Specification"),
    ("ARCHITECTURE.md", "Architecture"),
    ("TEST_REPORT.md", "Test Report"),
    ("PERFORMANCE.md", "Performance"),
    ("EVALUATOR_GUIDE.md", "Evaluator Guide"),
    ("USER_GUIDE.md", "User Guide"),
    ("API.md", "Backend API"),
    ("DEPLOYMENT.md", "Build and Deployment"),
]

CSS = """
@page { size: A4; margin: 18mm 16mm 20mm 16mm;
        @bottom-center { content: counter(page); font: 9pt 'Helvetica Neue', Arial, sans-serif; color: #64748b; } }
* { box-sizing: border-box; }
body { font: 10.5pt/1.55 'Helvetica Neue', Arial, sans-serif; color: #0f172a; }
h1, h2, h3, h4 { color: #1e1b4b; line-height: 1.25; break-after: avoid; }
h1 { font-size: 24pt; border-bottom: 3px solid #6366f1; padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 15pt; margin-top: 22px; color: #3730a3; }
h3 { font-size: 12pt; margin-top: 16px; }
a { color: #4338ca; text-decoration: none; }
p, li { orphans: 3; widows: 3; }
table { border-collapse: collapse; width: 100%; margin: 10px 0 14px; font-size: 9pt; break-inside: auto; }
tr { break-inside: avoid; }
th { background: #eef2ff; color: #312e81; text-align: left; }
th, td { border: 1px solid #cbd5e1; padding: 5px 7px; vertical-align: top; }
td:first-child { white-space: nowrap; }
tr:nth-child(even) td { background: #f8fafc; }
code { font: 9pt Menlo, Consolas, monospace; background: #f1f5f9; padding: 1px 4px; border-radius: 4px; }
pre { background: #0f172a; color: #e2e8f0; padding: 10px 12px; border-radius: 8px; overflow: hidden;
      white-space: pre-wrap; break-inside: avoid; font-size: 8.5pt; }
pre code { background: none; color: inherit; padding: 0; }
pre.mermaid { background: #fff; color: #0f172a; border: 1px solid #e2e8f0; text-align: center; white-space: normal; }
blockquote { margin: 10px 0; padding: 8px 14px; border-left: 4px solid #6366f1; background: #eef2ff; border-radius: 0 8px 8px 0; }
hr { border: 0; border-top: 1px solid #e2e8f0; margin: 18px 0; }
section.doc { break-before: page; }
.cover { position: fixed; top: 0; left: 0; right: 0; bottom: 0; padding: 0 22mm; display: flex; flex-direction: column; justify-content: center;
         align-items: center; text-align: center; color: #fff;
         background: linear-gradient(135deg, #312e81 0%, #5b21b6 55%, #0369a1 100%); break-after: page; }
.cover .logo { width: 150px; height: 150px; margin-bottom: 30px; }
.cover h1 { color: #fff; border: 0; font-size: 44pt; margin: 0; letter-spacing: 1px; }
.cover .tag { font-size: 15pt; opacity: .9; margin-top: 8px; }
.cover .meta { margin-top: 60px; font-size: 10.5pt; opacity: .85; line-height: 1.8; }
.toc { padding: 10mm 0 0; }
.toc h1 { margin-bottom: 18px; }
.toc ol { font-size: 12pt; line-height: 2.1; }
"""


def logo_svg() -> str:
    svg = (ROOT / "assets" / "logo.svg").read_text()
    return re.sub(r'width="512" height="512"', 'class="logo"', svg)


def convert(md_text: str, stem: str) -> str:
    body = markdown.markdown(
        md_text,
        extensions=["tables", "fenced_code", "sane_lists"],
        output_format="html5",
    )
    # Mermaid code fences become diagram containers.
    body = re.sub(
        r'<pre><code class="language-mermaid">(.*?)</code></pre>',
        lambda m: '<pre class="mermaid">' + m.group(1) + "</pre>",
        body,
        flags=re.S,
    )
    # Links between documents point to sections of the combined report.
    def link(m):
        target = pathlib.PurePosixPath(m.group(1)).stem.lower().replace("_", "-")
        return f'href="#doc-{target}"'

    body = re.sub(r'href="(?:\.\./)?(?:docs/)?([A-Za-z_]+\.md)(?:#[^"]*)?"', link, body)
    return f'<section class="doc" id="doc-{stem.lower().replace("_", "-")}">{body}</section>'


def main() -> int:
    chrome = shutil.which("google-chrome") or shutil.which("chromium") or \
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    if not pathlib.Path(chrome).exists() and not shutil.which(chrome):
        print("Google Chrome not found; HTML written only.", file=sys.stderr)

    sections = []
    toc = []
    for filename, title in DOCUMENTS:
        text = (ROOT / filename).read_text()
        stem = pathlib.Path(filename).stem
        sections.append(convert(text, stem))
        toc.append(f'<li><a href="#doc-{stem.lower().replace("_", "-")}">{html.escape(title)}</a></li>')

    today = datetime.date.today().strftime("%d %B %Y")
    head = f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Aurviz Project Report</title>
<style>{CSS}</style>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>mermaid.initialize({{startOnLoad: true, theme: 'neutral', securityLevel: 'loose'}});</script>
</head><body>"""
    cover = f"""{head}<style>@page {{ size: A4; margin: 0; @bottom-center {{ content: none; }} }}</style>
<div class="cover">{logo_svg()}<h1>Aurviz</h1>
<div class="tag">A personal AI agent for Android</div>
<div class="meta">Project Report<br>Requirements &middot; Architecture &middot; Testing &middot; Performance &middot; Guides<br>{today}</div></div>
</body></html>"""
    body = f"""{head}<section class="toc"><h1>Contents</h1><ol>{''.join(toc)}</ol></section>
{''.join(sections)}
</body></html>"""

    cover_html = ROOT / "_cover.html"
    cover_pdf = ROOT / "_cover.pdf"
    body_pdf = ROOT / "_body.pdf"
    cover_html.write_text(cover)
    OUT_HTML.write_text(body)

    def print_pdf(source: pathlib.Path, target: pathlib.Path) -> None:
        subprocess.run(
            [chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
             "--virtual-time-budget=30000", f"--print-to-pdf={target}", source.as_uri()],
            check=True, stderr=subprocess.DEVNULL,
        )

    print_pdf(cover_html, cover_pdf)
    print_pdf(OUT_HTML, body_pdf)

    merged = pymupdf.open(cover_pdf)
    cover_pages = len(merged)
    if cover_pages > 1:  # keep the cover to a single page
        merged.delete_pages(1, cover_pages - 1)
    merged.insert_pdf(pymupdf.open(body_pdf))
    merged.save(OUT_PDF, deflate=True)
    for temp in (cover_html, cover_pdf, body_pdf, OUT_HTML):
        temp.unlink(missing_ok=True)
    print(f"Wrote {OUT_PDF}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
