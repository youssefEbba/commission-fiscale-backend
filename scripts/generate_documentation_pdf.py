#!/usr/bin/env python3
"""
Convertit docs/SGCI-Documentation-Projet.md en PDF (UTF-8, titres et paragraphes).
Dépendance : pip install fpdf2

Polices : DejaVu (OFL) téléchargée une fois dans docs/.cache/fonts/ si absente.
"""
from __future__ import annotations

import os
import sys
import urllib.request
from pathlib import Path

# Miroirs possibles pour DejaVu (OFL) si pas de polices système
DEJAVU_URLS = (
    "https://cdn.jsdelivr.net/gh/dejavu-fonts/dejavu-fonts@37c7fe6ffe3473f3b1bd7df629f0044080da3a6d/ttf/DejaVuSans.ttf",
    "https://github.com/dejavu-fonts/dejavu-fonts/raw/37c7fe6ffe3473f3b1bd7df629f0044080da3a6d/ttf/DejaVuSans.ttf",
)
DEJAVU_BOLD_URLS = (
    "https://cdn.jsdelivr.net/gh/dejavu-fonts/dejavu-fonts@37c7fe6ffe3473f3b1bd7df629f0044080da3a6d/ttf/DejaVuSans-Bold.ttf",
    "https://github.com/dejavu-fonts/dejavu-fonts/raw/37c7fe6ffe3473f3b1bd7df629f0044080da3a6d/ttf/DejaVuSans-Bold.ttf",
)


def try_download_first(urls: tuple[str, ...], dest: Path) -> bool:
    for url in urls:
        try:
            urllib.request.urlretrieve(url, dest)  # noqa: S310
            return dest.is_file() and dest.stat().st_size > 10000
        except OSError:
            continue
    return False


def resolve_font_files(cache_dir: Path) -> tuple[Path, Path]:
    """Windows : Arial ; sinon cache DejaVu téléchargée."""
    windir = os.environ.get("WINDIR", r"C:\Windows")
    arial = Path(windir) / "Fonts" / "arial.ttf"
    arial_b = Path(windir) / "Fonts" / "arialbd.ttf"
    if arial.is_file():
        bold = arial_b if arial_b.is_file() else arial
        return arial, bold

    cache_dir.mkdir(parents=True, exist_ok=True)
    regular = cache_dir / "DejaVuSans.ttf"
    bold = cache_dir / "DejaVuSans-Bold.ttf"
    if not regular.is_file():
        print("Telechargement police DejaVu (cache local)...")
        if not try_download_first(DEJAVU_URLS, regular):
            raise RuntimeError(
                "Impossible de telecharger DejaVu. Installez les polices ou verifiez le reseau."
            )
    if not bold.is_file():
        if not try_download_first(DEJAVU_BOLD_URLS, bold):
            bold = regular
    return regular, bold


def main() -> int:
    try:
        from fpdf import FPDF
        from fpdf.enums import WrapMode
    except ImportError:
        print("Installez fpdf2 : pip install fpdf2", file=sys.stderr)
        return 1

    root = Path(__file__).resolve().parent.parent
    md_path = root / "docs" / "SGCI-Documentation-Projet.md"
    out_path = root / "docs" / "SGCI-Documentation-Projet.pdf"

    if not md_path.is_file():
        print(f"Fichier introuvable : {md_path}", file=sys.stderr)
        return 1

    text = md_path.read_text(encoding="utf-8")
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            text = text[end + 4 :].lstrip("\n")

    cache_fonts = root / "docs" / ".cache" / "fonts"
    font_reg, font_bold = resolve_font_files(cache_fonts)

    pdf = FPDF(format="A4", unit="mm")
    pdf.set_margins(left=15, top=15, right=15)
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()
    epw = pdf.w - pdf.l_margin - pdf.r_margin

    pdf.add_font("DocFont", "", str(font_reg))
    pdf.add_font("DocFont", "B", str(font_bold))
    body_font = "DocFont"

    def cell_txt(h: float, txt: str, *, bold: bool = False, fsize: int = 11) -> None:
        pdf.set_x(pdf.l_margin)
        pdf.set_font(body_font, "B" if bold else "", fsize)
        pdf.multi_cell(epw, h, txt, wrapmode=WrapMode.CHAR)
        pdf.set_font(body_font, "", 11)

    in_code = False
    for raw in text.split("\n"):
        line = raw.rstrip("\r")
        if line.strip().startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            pdf.set_font(body_font, "", 9)
            cell_txt(4, line if line else " ", fsize=9)
            continue
        if not line.strip():
            pdf.ln(3)
            continue
        if line.startswith("# "):
            cell_txt(9, line[2:].strip(), bold=True, fsize=18)
            continue
        if line.startswith("## "):
            cell_txt(8, line[3:].strip(), bold=True, fsize=14)
            continue
        if line.startswith("### "):
            cell_txt(7, line[4:].strip(), bold=True, fsize=12)
            continue
        if line.startswith("|"):
            cell_txt(5, line.replace("|", "  "), fsize=9)
            continue
        if line.startswith(("- ", "* ")):
            line = "    • " + line[2:]
        cell_txt(6, line, fsize=11)

    pdf.output(str(out_path))
    print(f"PDF genere : {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
