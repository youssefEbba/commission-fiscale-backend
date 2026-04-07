"""Remplace orElseThrow / throw avec RuntimeException + concaténation ou cause."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/mr/gov/finances/sgci"

IMPORT_BLOCK = (
    "\nimport mr.gov.finances.sgci.web.exception.ApiErrorCode;\n"
    "import mr.gov.finances.sgci.web.exception.ApiException;\n"
)


def classify_prefix(prefix: str) -> tuple[str, str]:
    ml = prefix.lower()
    if "non trouvé" in prefix or "non trouvee" in ml or "introuvable" in ml:
        return "notFound", "ApiErrorCode.RESOURCE_NOT_FOUND"
    if "accès refusé" in prefix or "hors périmètre" in prefix:
        return "forbidden", "ApiErrorCode.ACCESS_DENIED"
    if prefix.startswith("Seul ") or prefix.startswith("Seule "):
        return "forbidden", "ApiErrorCode.ROLE_FORBIDDEN"
    if "déjà" in prefix or "deja" in ml:
        return "conflict", "ApiErrorCode.CONFLICT"
    return "badRequest", "ApiErrorCode.BUSINESS_RULE_VIOLATION"


def ensure_imports(content: str) -> str:
    if "import mr.gov.finances.sgci.web.exception.ApiException;" in content:
        return content
    if not content.startswith("package "):
        return IMPORT_BLOCK + content
    nl = content.find("\n")
    return content[: nl + 1] + IMPORT_BLOCK + content[nl + 1 :]


def process_text(text: str) -> tuple[str, int]:
    n = 0

    def repl_or_else(m: re.Match) -> str:
        nonlocal n
        prefix = m.group(1)
        rest = m.group(2).strip()
        factory, code = classify_prefix(prefix)
        n += 1
        return f".orElseThrow(() -> ApiException.{factory}({code}, \"{prefix}\" + {rest}))"

    p1 = re.compile(
        r"\.orElseThrow\(\s*\(\)\s*->\s*new RuntimeException\(\s*\"((?:[^\"\\]|\\.)*)\"\s*\+\s*([^)]+?)\s*\)\s*\)",
        re.DOTALL,
    )
    text = p1.sub(repl_or_else, text)

    def repl_throw_minio(m: re.Match) -> str:
        nonlocal n
        n += 1
        return (
            "throw ApiException.badRequest(ApiErrorCode.STORAGE_UPLOAD_FAILED, "
            "\"Erreur upload MinIO: \" + e.getMessage(), e)"
        )

    text = re.sub(
        r"throw new RuntimeException\(\s*\"Erreur upload MinIO: \"\s*\+\s*e\.getMessage\(\)\s*,\s*e\s*\)",
        repl_throw_minio,
        text,
        flags=re.DOTALL,
    )

    def repl_throw_minio2(m: re.Match) -> str:
        nonlocal n
        n += 1
        return (
            "throw ApiException.badRequest(ApiErrorCode.STORAGE_UPLOAD_FAILED, "
            "\"Erreur upload MinIO\", e)"
        )

    text = re.sub(
        r"throw new RuntimeException\(\s*\"Erreur upload MinIO\"\s*,\s*e\s*\)",
        repl_throw_minio2,
        text,
        flags=re.DOTALL,
    )

    def repl_notif_ser(m: re.Match) -> str:
        nonlocal n
        n += 1
        return (
            "throw ApiException.internal(ApiErrorCode.INTERNAL_ERROR, "
            "\"Erreur sérialisation notification\", e)"
        )

    text = re.sub(
        r"throw new RuntimeException\(\s*\"Erreur sérialisation notification\"\s*,\s*e\s*\)",
        repl_notif_ser,
        text,
        flags=re.DOTALL,
    )

    return text, n


def main() -> int:
    total = 0
    for path in sorted(JAVA_ROOT.rglob("*.java")):
        t = path.read_text(encoding="utf-8")
        if "new RuntimeException" not in t:
            continue
        new_t, c = process_text(t)
        if c == 0 and new_t == t:
            continue
        if c > 0:
            new_t = ensure_imports(new_t)
            path.write_text(new_t, encoding="utf-8")
            print(f"{path.relative_to(ROOT)}: {c}")
            total += c
        elif new_t != t:
            path.write_text(new_t, encoding="utf-8")
    print(f"Total orElseThrow/MinIO replacements: {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
