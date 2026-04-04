# Proposition — Reporting SGCI (statistiques, graphiques, rapports)

Document de cadrage pour l’écran **Reporting** du portail SGCI, aligné sur les entités et statuts du backend (`DemandeCorrection`, `CertificatCredit`, `UtilisationCredit`, `Convention`, `ReferentielProjet`, `Marche`, `TransfertCredit`, `SousTraitance`, `ClotureCredit`, `AuditLog`, etc.).

**État actuel du backend** : consultation paginée des journaux d’audit (`GET /api/audit-logs`) ; agrégats reporting : `GET /api/reporting/summary` et `GET /api/reporting/timeseries/demandes` (voir [REPORTING_API.md](REPORTING_API.md)). Les exports fichiers / jobs planifiés restent à cadrer.

---

## 1. Tableaux de bord recommandés (par profil)

| Tableau de bord | Public cible | Focus |
|-----------------|--------------|--------|
| **Vue direction / Présidence** | Président, pilotage | Volumes globaux, délais moyens, montants crédits ouverts / consommés, taux de rejet |
| **Vue instruction correction** | DGD, DGTCP, DGI, DGB, AC | Files par statut, délais par étape, demandes en retard |
| **Vue mise en place (certificat)** | DGTCP, Président | Certificats par statut, délais visas → ouverture |
| **Vue utilisation crédit** | DGD, DGTCP, Entreprise | Utilisations douane vs TVA intérieure, liquidations / apurements |
| **Vue entreprise** | Entreprise | Ses dossiers : demandes, certificat, soldes, utilisations |
| **Vue conformité / SI** | Admin SI, audit | Activité utilisateurs, actions sensibles (`AuditLog`) |

---

## 2. Indicateurs clés (KPI) à afficher en cartes ou bandeau

### 2.1 Demandes de correction (`StatutDemande`)

- Nombre total de demandes (période sélectionnable : jour / mois / année / personnalisé).
- Répartition par statut : `RECUE`, `INCOMPLETE`, `RECEVABLE`, `EN_EVALUATION`, `EN_VALIDATION`, `ADOPTEE`, `REJETEE`, `NOTIFIEE`, `ANNULEE`.
- **Taux d’adoption** : `ADOPTEE` + `NOTIFIEE` / demandes closes (hors `ANNULEE` si défini ainsi).
- **Taux de rejet** : `REJETEE` / total traité.
- **Délai moyen** (et médian) entre `RECUE` → `ADOPTEE` ou `NOTIFIEE` (nécessite dates métier ou reconstruction via audit).
- Nombre de **rejets temporaires** ouverts / résolus (si exposé en stats).

### 2.2 Certificats de crédit (`StatutCertificat`)

- Volume par statut : notamment `EN_CONTROLE`, `OUVERT`, `CLOTURE`, `ANNULE`.
- Montants agrégés (si politique de confidentialité le permet) : somme `montantCordon` / `montantTVAInterieure` à l’ouverture, **soldes** `soldeCordon` / `soldeTVA` restants.
- **Délai moyen** entre création certificat et passage `OUVERT`.
- Certificats **en attente validation Président** (`EN_VALIDATION_PRESIDENT`).

### 2.3 Utilisations de crédit (`StatutUtilisation`, `TypeUtilisation`)

- Nombre d’utilisations par type : **DOUANIER** vs **TVA_INTERIEURE**.
- Répartition par statut : `DEMANDEE` → … → `LIQUIDEE` / `APUREE` / `REJETEE`.
- **Montants** : somme des montants déclarés / imputés (selon champs métier).
- Taux de **liquidation** (douane) et **apurement** (TVA intérieure).

### 2.4 Référentiel amont : conventions, référentiels projet, marchés

- Conventions par `StatutConvention` (dont `EN_ATTENTE`, `VALIDE`, `REJETE`, `ANNULEE` si utilisé).
- Référentiels projet par `StatutReferentielProjet`.
- Marchés par `StatutMarche` (dont `ANNULE` si utilisé).

### 2.5 Opérations annexes

- **Transferts de crédit** : volume, montants, statuts.
- **Sous-traitances** : volume, statuts.
- **Clôtures** : nombre, motifs, montants résiduels si disponibles.

### 2.6 Activité système (`AuditLog`)

- Nombre d’actions **CREATE / UPDATE / DELETE** sur la période.
- Top entités modifiées (`entityType` : `DemandeCorrection`, `CertificatCredit`, etc.).
- Top utilisateurs par nombre d’actions (avec garde-fou confidentialité).

### 2.7 Notifications (optionnel)

- Volume de notifications émises par `NotificationType` (si table ou agrégation disponible).

---

## 3. Graphiques recommandés (types et usages)

| Graphique | Données | Usage |
|-----------|---------|--------|
| **Histogramme / barres** | Demandes ou utilisations par statut ou par mois | Comparer les volumes |
| **Courbe temporelle (line / area)** | Créations ou clôtures par jour ou mois | Tendance dans le temps |
| **Camembert / donut** | Part des statuts ou des types d’utilisation | Vue proportionnelle rapide |
| **Entonnoir (funnel)** | Étapes `StatutDemande` ou `StatutCertificat` | Taux de conversion entre étapes |
| **Barres empilées** | Par autorité contractante ou par entreprise (top N) | Pilotage territorial / client |
| **Heatmap calendrier** (optionnel) | Nombre de dépôts par jour | Pic d’activité |
| **Tableau croisé** | Dimensions × statuts | Export Excel naturel |

**Filtres globaux** à prévoir sur tous les graphiques : période, autorité contractante, entreprise, rôle (selon périmètre utilisateur).

---

## 4. Rapports à générer (exports)

Chaque rapport = **période** + **filtres** + **format** (PDF pour synthèse, **Excel/CSV** pour analyse).

### 4.1 Rapports opérationnels

| Rapport | Contenu principal | Format suggéré |
|---------|-------------------|----------------|
| **Synthèse demandes de correction** | Liste des demandes avec statut, dates clés, AC, entreprise, délais | Excel + PDF synthèse |
| **Synthèse certificats** | Numéro, statut, montants, soldes, dates | Excel |
| **Synthèse utilisations** | Type, statut, montants, certificat lié, liquidation / apurement | Excel |
| **Dossiers GED** | Référence dossier, étapes, nombre de pièces par étape (agrégat) | PDF / Excel |

### 4.2 Rapports pilotage / performance

| Rapport | Contenu | Format |
|---------|---------|--------|
| **Tableau de bord mensuel institutionnel** | KPI §2 sur une page + graphiques clés | PDF |
| **Délais moyens par étape** | Workflow correction / certificat / utilisation | Excel |
| **Taux de rejet et motifs** | Agrégats par motif (demande, certificat, utilisation) | Excel + graphiques dans PDF |

### 4.3 Rapports conformité

| Rapport | Contenu | Format |
|---------|---------|--------|
| **Journal d’audit filtré** | Aligné sur `GET /api/audit-logs` (qui, quand, quoi) | CSV / Excel |
| **Traçabilité d’une entité** | Historique des actions pour un ID (demande, certificat…) | PDF |

### 4.4 Rapports financiers (à valider avec la métrologie)

| Rapport | Contenu | Remarque |
|---------|---------|----------|
| **Crédits ouverts vs utilisés** | Agrégats par période / par entreprise | Données sensibles — restreindre les rôles |
| **Stock TVA / cordon** (si exposé) | Soldes agrégés | Même remarque |

---

## 5. Implémentation technique (rappel)

- **Source de vérité** : tables JPA existantes + `audit_log`.
- **API** : endpoints dédiés type `GET /api/reporting/kpi`, `GET /api/reporting/timeseries`, ou requêtes `@Query` dans les repositories avec agrégation (`COUNT`, `SUM`, `GROUP BY`).
- **Performance** : index sur colonnes `statut`, `dateCreation`, clés étrangères ; pour gros volumes, vues matérialisées ou jobs nocturnes.
- **Sécurité** : même filtrage que les listes métier (`AuthenticatedUser`, périmètre AC / entreprise).

---

## 6. Priorisation suggérée (MVP reporting)

1. Cartes KPI + **histogramme** statuts (demandes, certificats, utilisations) sur période glissante.
2. **Courbe** des créations de demandes / mois.
3. Export **Excel** synthèse demandes + utilisations.
4. Export **audit** CSV depuis filtres existants.
5. Ensuite : funnel, délais, rapports PDF institutionnels.

---

*Document de proposition produit — à valider avec le métier et la conformité des données personnelles / financières.*
