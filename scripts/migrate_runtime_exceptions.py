"""
Remplace throw new RuntimeException("...") par ApiException avec code HTTP + code stable.
Usage: python scripts/migrate_runtime_exceptions.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "src/main/java/mr/gov/finances/sgci/service"
CONTROLLERS = ROOT / "src/main/java/mr/gov/finances/sgci/web/controller"

IMPORT_BLOCK = (
    "\nimport mr.gov.finances.sgci.web.exception.ApiErrorCode;\n"
    "import mr.gov.finances.sgci.web.exception.ApiException;\n"
)

STRING_RE = r'"((?:[^"\\]|\\.)*)"'


def classify(msg: str) -> tuple[str, str]:
    ml = msg.lower()
    if "non trouvé" in msg or "non trouvee" in ml or "introuvable" in ml:
        return "notFound", "ApiErrorCode.RESOURCE_NOT_FOUND"
    if "accès refusé" in msg or "acces refuse" in ml or "hors périmètre" in msg or "hors perimetre" in ml:
        return "forbidden", "ApiErrorCode.ACCESS_DENIED"
    if (
        msg.startswith("Seul ")
        or msg.startswith("Seule ")
        or "réservé" in ml
        or "reserve " in ml
        or "rôle non autorisé" in msg
        or "role non autorise" in ml
    ):
        return "forbidden", "ApiErrorCode.ROLE_FORBIDDEN"
    if "déjà" in msg or "deja" in ml:
        return "conflict", "ApiErrorCode.CONFLICT"
    if "utilisateur non authentifié" in ml or msg == "Non authentifié":
        return "unauthorized", "ApiErrorCode.AUTH_REQUIRED"
    if "documents obligatoires" in ml or "type de fichier non autorisé" in ml:
        return "badRequest", "ApiErrorCode.VALIDATION_FAILED"
    return "badRequest", "ApiErrorCode.BUSINESS_RULE_VIOLATION"


def replace_throw_runtime(content: str) -> tuple[str, int]:
    def repl_throw(m: re.Match) -> str:
        raw_inner = m.group(1)
        msg = bytes(raw_inner, "utf-8").decode("unicode_escape") if "\\" in raw_inner else raw_inner
        factory, code = classify(msg)
        return f'throw ApiException.{factory}({code}, "{raw_inner}")'

    pattern1 = re.compile(r"throw new RuntimeException\(" + STRING_RE + r"\)")
    content, n1 = pattern1.subn(repl_throw, content)

    def repl_or_else(m: re.Match) -> str:
        raw_inner = m.group(1)
        msg = bytes(raw_inner, "utf-8").decode("unicode_escape") if "\\" in raw_inner else raw_inner
        factory, code = classify(msg)
        return f'.orElseThrow(() -> ApiException.{factory}({code}, "{raw_inner}"))'

    pattern2 = re.compile(
        r"\.orElseThrow\(\(\) -> new RuntimeException\(" + STRING_RE + r"\)\)"
    )
    content, n2 = pattern2.subn(repl_or_else, content)

    return content, n1 + n2


def ensure_imports(content: str) -> str:
    if "import mr.gov.finances.sgci.web.exception.ApiException;" in content:
        return content
    if not content.startswith("package "):
        return IMPORT_BLOCK + content
    nl = content.find("\n")
    return content[: nl + 1] + IMPORT_BLOCK + content[nl + 1 :]


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "new RuntimeException" not in text:
        return False
    new_text, count = replace_throw_runtime(text)
    if count == 0:
        return False
    new_text = ensure_imports(new_text)
    path.write_text(new_text, encoding="utf-8")
    print(f"{path.relative_to(ROOT)}: {count} replacement(s)")
    return True


def main() -> int:
    n = 0
    for base in (SERVICE, CONTROLLERS):
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.java")):
            if process_file(path):
                n += 1
    print(f"Done. Modified {n} file(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
