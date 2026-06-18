"""
Generate a standalone clean diagram for §15.3 — Flux utilisation TVA intérieure.
Matches exactly the content of MANUEL_UTILISATION.md lines 407-416.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "scripts" / "diagrams" / "flux_tva_interieure.png"

# ── Palette
NAVY   = "#1F497D"
GREEN  = "#2E7D32"
ORANGE = "#E65100"
TEAL   = "#00695C"
AMBER  = "#F57F17"
BLUE   = "#1565C0"
WHITE  = "#FFFFFF"
LGRAY  = "#F5F5F5"
MGRAY  = "#EEEEEE"
DGRAY  = "#424242"
RED    = "#B71C1C"

fig, ax = plt.subplots(figsize=(12, 7.5), facecolor=WHITE)
ax.set_xlim(0, 12)
ax.set_ylim(0, 7.5)
ax.axis("off")

# ── Title bar
title_bg = FancyBboxPatch((0.2, 6.85), 11.6, 0.55,
                           boxstyle="round,pad=0.05",
                           facecolor=NAVY, edgecolor="none", zorder=2)
ax.add_patch(title_bg)
ax.text(6.0, 7.12, "§ 15.3 — Flux : Utilisation du crédit — TVA intérieure",
        ha="center", va="center", fontsize=12, fontweight="bold",
        color=WHITE, zorder=3)

# ── Step definitions: (color, actor, label, status_text, x, y)
steps = [
    (GREEN,  "Entreprise", "Soumet la demande\n(TVA intérieure)",       "DEMANDEE",              1.2, 5.6),
    (ORANGE, "DGI",        "Instruit le dossier\ndécision / compléments","EN_VERIFICATION",       1.2, 3.85),
    (TEAL,   "DGTCP",      "Vérifie et valide\nsolde TVA mis à jour",   "VALIDEE",               1.2, 2.1),
    (TEAL,   "DGTCP",      "Apure la TVA\nrégularisation finale",       "APUREE",                1.2, 0.45),
]

BOX_W, BOX_H = 4.6, 1.1
STEP_X = 1.8   # left edge of content box
SPINE_X = 1.1  # vertical line x

# Vertical spine
ax.plot([SPINE_X, SPINE_X], [0.9, 6.2],
        color=NAVY, lw=2.8, zorder=1, solid_capstyle="round")

for i, (color, actor, label, status, cx, cy) in enumerate(steps):
    # Circle on spine
    circ = plt.Circle((SPINE_X, cy + BOX_H/2), 0.21,
                       facecolor=color, edgecolor=WHITE, linewidth=2, zorder=4)
    ax.add_patch(circ)
    ax.text(SPINE_X, cy + BOX_H/2, str(i+1),
            ha="center", va="center", fontsize=9, fontweight="bold",
            color=WHITE, zorder=5)

    # Connector line
    ax.plot([SPINE_X + 0.21, STEP_X - 0.05], [cy + BOX_H/2, cy + BOX_H/2],
            color=color, lw=1.6, zorder=2)

    # Content box
    box = FancyBboxPatch((STEP_X, cy), BOX_W, BOX_H,
                         boxstyle="round,pad=0.08",
                         facecolor=WHITE, edgecolor=color,
                         linewidth=2.0, zorder=3)
    ax.add_patch(box)

    # Actor badge
    badge = FancyBboxPatch((STEP_X + 0.08, cy + 0.08), 1.3, BOX_H - 0.16,
                           boxstyle="round,pad=0.04",
                           facecolor=color, edgecolor="none", zorder=4)
    ax.add_patch(badge)
    ax.text(STEP_X + 0.73, cy + BOX_H/2, actor,
            ha="center", va="center", fontsize=8.5, fontweight="bold",
            color=WHITE, zorder=5)

    # Action label
    ax.text(STEP_X + 1.55, cy + BOX_H/2 + 0.04, label,
            ha="left", va="center", fontsize=8.5, color=DGRAY,
            linespacing=1.35, zorder=4)

    # Status chip (right side)
    chip = FancyBboxPatch((STEP_X + BOX_W - 1.55, cy + 0.28), 1.45, 0.52,
                          boxstyle="round,pad=0.04",
                          facecolor=color, edgecolor="none",
                          alpha=0.15, zorder=4)
    ax.add_patch(chip)
    ax.text(STEP_X + BOX_W - 0.82, cy + 0.54, status,
            ha="center", va="center", fontsize=7.5, fontweight="bold",
            color=color, zorder=5)

# ── Side branch: INCOMPLETE / A_RECONTROLER ──────────────────────────────────
BX = 8.6   # branch column x-center

# Branch vertical dashed line (between DGI and DGTCP level)
y_top = steps[1][5] + BOX_H/2   # DGI level
y_bot = steps[2][5] + BOX_H/2   # DGTCP level
ax.plot([BX, BX], [y_bot + 0.05, y_top - 0.05],
        color=AMBER, lw=1.6, linestyle="--", zorder=2, alpha=0.9)

# INCOMPLETE chip
incomp = FancyBboxPatch((BX - 1.25, y_top - 0.31), 2.5, 0.62,
                         boxstyle="round,pad=0.06",
                         facecolor=AMBER, edgecolor="none",
                         alpha=0.9, zorder=4)
ax.add_patch(incomp)
ax.text(BX, y_top, "INCOMPLETE",
        ha="center", va="center", fontsize=8.5, fontweight="bold",
        color=WHITE, zorder=5)

# A_RECONTROLER chip
recon = FancyBboxPatch((BX - 1.3, y_bot - 0.31), 2.6, 0.62,
                        boxstyle="round,pad=0.06",
                        facecolor=BLUE, edgecolor="none",
                        alpha=0.9, zorder=4)
ax.add_patch(recon)
ax.text(BX, y_bot, "A_RECONTROLER",
        ha="center", va="center", fontsize=8.5, fontweight="bold",
        color=WHITE, zorder=5)

# Arrow: DGI box → INCOMPLETE
ax.annotate("", xy=(BX - 1.25, y_top),
            xytext=(STEP_X + BOX_W + 0.06, y_top),
            arrowprops=dict(arrowstyle="-|>", color=AMBER, lw=1.5,
                            mutation_scale=13))
ax.text((STEP_X + BOX_W + BX - 1.2) / 2, y_top + 0.25,
        "pièces manquantes", ha="center", va="center",
        fontsize=7.5, color=AMBER, style="italic")

# Arrow: INCOMPLETE → A_RECONTROLER
ax.annotate("", xy=(BX, y_bot + 0.31),
            xytext=(BX, y_top - 0.31),
            arrowprops=dict(arrowstyle="-|>", color=AMBER, lw=1.4,
                            mutation_scale=12))

# Arrow: A_RECONTROLER → back to DGI (retour contrôle)
ax.annotate("", xy=(STEP_X + BOX_W + 0.06, y_bot),
            xytext=(BX - 1.3, y_bot),
            arrowprops=dict(arrowstyle="-|>", color=BLUE, lw=1.4,
                            mutation_scale=13))
ax.text((STEP_X + BOX_W + BX - 1.3) / 2, y_bot - 0.26,
        "retour au contrôle DGI", ha="center", va="center",
        fontsize=7.5, color=BLUE, style="italic")

# Side branch label
ax.text(BX, (y_top + y_bot) / 2 + 0.02,
        "Rejet\ntemporaire",
        ha="center", va="center", fontsize=7, color=AMBER,
        fontweight="bold", linespacing=1.3,
        bbox=dict(boxstyle="round,pad=0.2", facecolor=WHITE,
                  edgecolor=AMBER, alpha=0.7))

# ── Bottom note ───────────────────────────────────────────────────────────────
note_bg = FancyBboxPatch((0.2, 0.04), 11.6, 0.35,
                          boxstyle="round,pad=0.04",
                          facecolor=MGRAY, edgecolor="#BDBDBD",
                          linewidth=1, zorder=2)
ax.add_patch(note_bg)
ax.text(6.0, 0.22,
        "En cas de pièces manquantes : INCOMPLETE / A_RECONTROLER jusqu'à résolution du rejet temporaire.",
        ha="center", va="center", fontsize=7.8, color=DGRAY,
        style="italic", zorder=3)

plt.tight_layout(pad=0.2)
fig.savefig(OUT, dpi=180, bbox_inches="tight", facecolor=WHITE)
plt.close(fig)
print(f"Saved: {OUT}")
