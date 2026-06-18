# COMMISSION FISCALE — République Islamique de Mauritanie
## Manuel d'utilisation — Système de Gestion des Crédits d'Impôt (SGCI)
**Version 4.0 — Guide utilisateur simplifié — Avril 2026**

*Document destiné à tous les utilisateurs de la plateforme*

---

## Table des matières

1. [Présentation de la plateforme](#1-présentation-de-la-plateforme)
   - 1.1 [Les différents profils](#11-les-différents-profils)
2. [Se connecter et créer un compte](#2-se-connecter-et-créer-un-compte)
   - 2.1 [Créer un compte (première fois)](#21-créer-un-compte-première-fois)
   - 2.2 [Se connecter](#22-se-connecter)
   - 2.3 [Se déconnecter](#23-se-déconnecter)
3. [Votre tableau de bord](#3-votre-tableau-de-bord)
4. [Guide : Autorité Contractante (AC)](#4-guide--autorité-contractante-ac)
   - 4.1 [Créer une convention](#41-créer-une-convention)
   - 4.2 [Soumettre une demande de correction douanière](#42-soumettre-une-demande-de-correction-douanière)
   - 4.3 [Demande de mise en place du certificat](#43-demande-de-mise-en-place-du-certificat)
   - 4.4 [Gérer vos marchés](#44-gérer-vos-marchés)
   - 4.5 [Gérer vos délégués](#45-gérer-vos-délégués)
   - 4.6 [Consulter les certificats et utilisations](#46-consulter-les-certificats-et-utilisations)
5. [Guide : Entreprise](#5-guide--entreprise)
   - 5.1 [Consulter vos demandes](#51-consulter-vos-demandes)
   - 5.2 [Consulter vos certificats](#52-consulter-vos-certificats)
   - 5.3 [Déclarer une utilisation du crédit](#53-déclarer-une-utilisation-du-crédit)
   - 5.4 [Demander un transfert de crédit](#54-demander-un-transfert-de-crédit)
   - 5.5 [Sous-traitance](#55-sous-traitance)
   - 5.6 [Simulation](#56-simulation)
6. [Guide : Délégué UPM / UEP](#6-guide--délégué-upm--uep)
   - 6.1 [Ce que vous voyez](#61-ce-que-vous-voyez)
   - 6.2 [Ce que vous pouvez faire](#62-ce-que-vous-pouvez-faire)
7. [Guide : Sous-traitant](#7-guide--sous-traitant)
8. [Guide : Direction Générale des Douanes (DGD)](#8-guide--direction-générale-des-douanes-dgd)
   - 8.1 [Traiter les demandes de correction](#81-traiter-les-demandes-de-correction)
   - 8.2 [Contrôler les utilisations Douane](#82-contrôler-les-utilisations-douane)
   - 8.3 [Vérifier les certificats (mise en place)](#83-vérifier-les-certificats-mise-en-place)
9. [Guide : Direction Générale des Impôts (DGI)](#9-guide--direction-générale-des-impôts-dgi)
   - 9.1 [Traiter les demandes de correction](#91-traiter-les-demandes-de-correction)
   - 9.2 [Vérifier les certificats](#92-vérifier-les-certificats)
   - 9.3 [Consulter les utilisations](#93-consulter-les-utilisations)
10. [Guide : Direction Générale du Budget (DGB)](#10-guide--direction-générale-du-budget-dgb)
    - 10.1 [Valider les conventions](#101-valider-les-conventions)
    - 10.2 [Consulter les demandes](#102-consulter-les-demandes)
    - 10.3 [Clôture et reporting](#103-clôture-et-reporting)
11. [Guide : Direction Générale du Trésor (DGTCP)](#11-guide--direction-générale-du-trésor-dgtcp)
    - 11.1 [Traiter les demandes de correction](#111-traiter-les-demandes-de-correction)
    - 11.2 [Gérer les certificats (mise en place)](#112-gérer-les-certificats-mise-en-place)
    - 11.3 [Traiter les utilisations](#113-traiter-les-utilisations)
    - 11.4 [Traiter les transferts](#114-traiter-les-transferts)
    - 11.5 [Valider les sous-traitances](#115-valider-les-sous-traitances)
12. [Guide : Président de la Commission](#12-guide--président-de-la-commission)
    - 12.1 [Adopter les demandes de correction](#121-adopter-les-demandes-de-correction)
    - 12.2 [Valider les certificats](#122-valider-les-certificats)
    - 12.3 [Décider sur les transferts et sous-traitances](#123-décider-sur-les-transferts-et-sous-traitances)
    - 12.4 [Reporting national](#124-reporting-national)
13. [Guide : Administrateur (ADMIN_SI)](#13-guide--administrateur-admin_si)
    - 13.1 [Gérer les utilisateurs](#131-gérer-les-utilisateurs)
    - 13.2 [Gérer les rôles et permissions](#132-gérer-les-rôles-et-permissions)
    - 13.3 [Consulter le journal d'audit](#133-consulter-le-journal-daudit)
    - 13.4 [GED (Gestion Électronique des Documents)](#134-ged-gestion-électronique-des-documents)
14. [Le module Reporting](#14-le-module-reporting)
    - 14.1 [Ce que vous voyez selon votre profil](#141-ce-que-vous-voyez-selon-votre-profil)
    - 14.2 [Informations affichées](#142-informations-affichées)
15. [Récapitulatif des flux principaux](#15-récapitulatif-des-flux-principaux)
    - 15.1 [Flux convention → certificat](#151-flux-convention--certificat)
    - 15.2 [Flux utilisation Douane](#152-flux-utilisation-douane)
    - 15.3 [Flux utilisation TVA intérieure](#153-flux-utilisation-tva-intérieure)
    - 15.4 [Flux transfert](#154-flux-transfert)
16. [Glossaire](#16-glossaire)

---

## 1. Présentation de la plateforme

Le **SGCI (Système de Gestion des Crédits d'Impôt)** est la plateforme numérique de la Commission Fiscale qui dématérialise l'ensemble du cycle de vie des crédits d'impôt accordés dans le cadre des conventions de financement : de la création de la convention jusqu'à la clôture du crédit, en passant par la mise en place du certificat, les utilisations (douane et TVA intérieure), les transferts, la sous-traitance et les corrections de marché.

La plateforme assure :
- la **traçabilité complète** de chaque dossier (journal d'audit, historique des décisions) ;
- la **collaboration entre administrations** (DGD, DGI, DGB, DGTCP, Présidence) selon un circuit de validation (workflow) ;
- la **gestion électronique des documents (GED)** avec versionnage automatique des pièces ;
- des **notifications** in-app (temps réel) et par **e-mail** à chaque étape clé ;
- un **module de reporting** filtré selon le profil de l'utilisateur.

### 1.1 Les différents profils

| Profil | Rôle dans le système |
|---|---|
| **Autorité Contractante (AC)** | Porte les conventions/marchés, dépose les demandes de correction, gère ses délégués. |
| **Délégué UPM / UEP** | Agit pour le compte de l'AC sur les marchés qui lui sont affectés. |
| **Entreprise** | Titulaire du marché : consulte ses certificats, déclare ses utilisations, demande des transferts, gère la sous-traitance. |
| **Sous-traitant** | Entreprise liée à un titulaire par une autorisation de sous-traitance ; peut utiliser une partie du crédit. |
| **DGD** | Direction Générale des Douanes : contrôle les corrections, les utilisations Douane et les certificats. |
| **DGI** | Direction Générale des Impôts : contrôle les corrections, valide certains certificats et instruit les utilisations TVA intérieure. |
| **DGB** | Direction Générale du Budget : valide les conventions, vise les corrections, consulte clôtures et reporting. |
| **DGTCP** | Direction Générale du Trésor et de la Comptabilité Publique : pivot opérationnel (certificats, utilisations, transferts, sous-traitances, clôtures). |
| **Président de la Commission** | Adopte les corrections, valide les certificats, décide des transferts/clôtures, reporting national. |
| **Administrateur (ADMIN_SI)** | Gère les utilisateurs, les rôles/permissions, le journal d'audit et la GED. |
| **Commission Relais** | Compte support pouvant agir « au nom de » une entreprise ou une AC (impersonation). |

> Le système fonctionne par **permissions** attribuées à chaque rôle. Un utilisateur ne voit et ne peut faire que ce que ses permissions autorisent.

---

## 2. Se connecter et créer un compte

### 2.1 Créer un compte (première fois)

L'inscription se fait depuis l'écran d'accueil (« Créer un compte »). Le formulaire demande :
- un **identifiant** (username) et un **mot de passe** ;
- le **type de compte** (Entreprise ou Autorité Contractante) ;
- les informations associées :
  - pour une **entreprise** : raison sociale, NIF, adresse, situation fiscale, activité… ;
  - pour une **AC** : nom, sigle, adresse, téléphone, e-mail.

Les comptes des administrations (DGD, DGI, DGB, DGTCP, Président, Admin) sont créés directement par l'administrateur.

### 2.2 Se connecter

Saisissez votre **identifiant** et votre **mot de passe** sur l'écran de connexion. Après authentification, vous arrivez sur votre tableau de bord adapté à votre profil.

**Mot de passe oublié :** depuis l'écran de connexion, utilisez « Mot de passe oublié ». Après saisie de votre e-mail, une demande de réinitialisation est créée et traitée par l'administrateur. Vous êtes notifié par e-mail de l'acceptation ou du refus.

**Changer son mot de passe :** une fois connecté, vous pouvez modifier votre mot de passe depuis votre profil (saisie de l'ancien mot de passe + nouveau mot de passe, minimum 8 caractères).

### 2.3 Se déconnecter

Cliquez sur votre nom/profil en haut de l'écran puis sur « Se déconnecter ». La session est immédiatement fermée.

---

## 3. Votre tableau de bord

Au démarrage, chaque utilisateur arrive sur un tableau de bord personnalisé :
- des **files de travail (queues)** correspondant aux dossiers en attente de votre action selon votre rôle ;
- des **indicateurs synthétiques** issus du module Reporting (nombre de dossiers par statut, montants, taux d'adoption/rejet…) sur la période sélectionnée ;
- la **cloche de notifications** (en haut à droite) affichant le nombre de messages non lus et l'historique de vos notifications ;
- l'accès aux modules autorisés (conventions, certificats, utilisations, transferts, GED, etc.).

Les notifications sont **en temps réel** (in-app) et **doublées par e-mail** pour les événements importants.

---

## 4. Guide : Autorité Contractante (AC)

### 4.1 Créer une convention

Dans le module **Conventions**, cliquez sur « Nouvelle convention ». Renseignez les informations (bailleur, projet, montants, devise/taux de change, etc.) puis joignez les **documents** requis. À la création, la convention est au statut **EN_ATTENTE** (en attente de validation par la DGB/Président). Vous pouvez ajouter, remplacer ou supprimer des documents tant que la convention n'est pas validée.

### 4.2 Soumettre une demande de correction douanière

Dans **Demandes de correction**, créez une demande (brouillon) rattachée à un marché, joignez l'offre financière et les pièces justificatives, puis **soumettez-la**. Elle entre alors dans le circuit de visa :

> **DGD → DGTCP → DGI → DGB → Président**

Vous serez notifié à chaque étape (visa, demande de compléments, rejet, adoption).

### 4.3 Demande de mise en place du certificat

Selon vos droits (rôles UPM/UEP), vous pouvez initier la **mise en place du certificat de crédit**. Le certificat est créé en **BROUILLON**, complété, puis **soumis**. Il suit ensuite le circuit DGI/DGD/DGTCP → Président → ouverture par la DGTCP.

### 4.4 Gérer vos marchés

Dans le module **Marchés** : créez et modifiez vos marchés, joignez leurs documents, et **affectez un délégué principal** ou des délégués supplémentaires à chaque marché.

### 4.5 Gérer vos délégués

Dans **Délégués** : créez les comptes de vos délégués, modifiez leurs informations, **activez/désactivez** un délégué, et synchronisez la **liste des marchés** que chacun peut traiter.

### 4.6 Consulter les certificats et utilisations

Vous pouvez consulter les **certificats** liés à vos marchés et le suivi des **utilisations** des crédits, pour suivre la consommation et l'état d'avancement des dossiers.

---

## 5. Guide : Entreprise

### 5.1 Consulter vos demandes

Depuis votre espace, retrouvez l'ensemble de vos dossiers en cours (corrections vous concernant, certificats, utilisations, transferts, sous-traitances) avec leur statut en temps réel.

### 5.2 Consulter vos certificats

Le module **Certificats** affiche vos certificats de crédit, leur statut (de **OUVERT** à **CLOTURE**), les montants alloués et les **soldes disponibles** (solde Douane / solde TVA), ainsi que le stock de TVA déductible associé.

### 5.3 Déclarer une utilisation du crédit

Dans **Utilisations de crédit**, créez une demande (brouillon), choisissez le type **Douane** ou **TVA intérieure**, complétez les informations puis **soumettez**. Le dossier suit alors le circuit propre à chaque type (voir §15).

Vous êtes notifié à chaque étape : visa DGD, saisie du chèque certifié, envoi au Trésor, quittances, liquidation, accusé de réception et clôture.

> Le système contrôle les **soldes** : une utilisation ne peut pas rendre un solde négatif, et les montants sont vérifiés à chaque étape.

### 5.4 Demander un transfert de crédit

Dans **Transferts**, créez une demande de transfert (par ex. crédit Douane → intérieur) et joignez les pièces requises. La demande passe au statut **DEMANDE** puis **EN_COURS** dès qu'une pièce obligatoire est déposée. Elle est instruite par la DGTCP puis le Président.

En cas de **rejet temporaire**, vous pouvez répondre et déposer des compléments. Vous pouvez aussi **annuler** une demande avant son exécution.

### 5.5 Sous-traitance

Dans **Sous-traitance**, soumettez une demande pour associer un sous-traitant à votre certificat (ou utilisez l'**onboarding** qui crée l'entreprise sous-traitante et le dossier en une seule opération). Une fois la sous-traitance **AUTORISÉE** par la DGTCP, vous pouvez la **suspendre**, la **réactiver** ou la **révoquer** depuis votre espace titulaire.

### 5.6 Simulation

La plateforme met à disposition des outils de **consultation des taux de change** (conversion de montants et taux entre devises), utiles lors de la saisie d'une convention ou d'un montant en devise étrangère.

> Il n'existe pas à ce jour de module de simulation métier dédié exposé en self-service ; la conversion s'effectue via les utilitaires de taux de change intégrés aux formulaires.

---

## 6. Guide : Délégué UPM / UEP

### 6.1 Ce que vous voyez

Le délégué accède aux **marchés qui lui sont affectés** par son autorité contractante, ainsi qu'aux conventions, projets et dossiers de correction associés.

### 6.2 Ce que vous pouvez faire

Selon les droits du rôle (`AUTORITE_UPM` / `AUTORITE_UEP`) : créer et gérer des projets et conventions, déposer les documents, initier et soumettre des **demandes de correction** et, le cas échéant, la **mise en place de certificats**, et consulter le **reporting** de son périmètre.

---

## 7. Guide : Sous-traitant

Le sous-traitant dispose d'un accès restreint, centré sur le **certificat partagé** par le titulaire. Il peut consulter le dossier de sous-traitance qui le concerne et, selon le solde qui lui est ouvert, participer aux utilisations de crédit dans les limites de l'autorisation.

L'autorisation de sous-traitance peut être **suspendue**, **réactivée** ou **révoquée** par le titulaire, ou **refusée** par la DGTCP. Un nouveau lien doit être établi après une révocation.

---

## 8. Guide : Direction Générale des Douanes (DGD)

### 8.1 Traiter les demandes de correction

La DGD prend en charge les demandes de correction qui lui sont transmises. Elle **enregistre/transmet** son instruction et appose son **visa** ou un **rejet**. Elle peut demander des compléments via le mécanisme de **rejet temporaire** (l'AC répond avec des pièces supplémentaires, puis la DGD résout le rejet et reprend l'instruction).

### 8.2 Contrôler les utilisations Douane

La DGD est l'acteur central du circuit Douane :
- **prise en charge** et contrôle de l'utilisation ;
- **visa DGD** : renseignement/validation des annotations sur les **lignes du bulletin de liquidation** (montants AU_CI / À PAYER) ;
- demande de compléments (rejet temporaire) ou **rejet définitif** ;
- consultation des lignes de bulletin et des quittances.

### 8.3 Vérifier les certificats (mise en place)

La DGD **prend en charge** les certificats qui lui sont adressés (statut **ENVOYEE** → **EN_CONTROLE**), appose son **visa ou rejet**, dépose ses documents et traite les rejets temporaires.

---

## 9. Guide : Direction Générale des Impôts (DGI)

### 9.1 Traiter les demandes de correction

La DGI intervient dans le circuit de visa des corrections : **visa** ou **rejet**, avec gestion des compléments par rejet temporaire.

### 9.2 Vérifier les certificats

La DGI **valide ou rejette** les certificats relevant de sa compétence et clôt les rejets temporaires. Elle peut également **valider ou rejeter les conventions**.

### 9.3 Consulter les utilisations

La DGI instruit le volet **TVA intérieure** des utilisations : décision, consultation et résolution des rejets temporaires.

---

## 10. Guide : Direction Générale du Budget (DGB)

### 10.1 Valider les conventions

La DGB **valide ou rejette** les conventions de financement. Depuis le module Conventions, ouvrez la convention au statut **EN_ATTENTE**, contrôlez les pièces puis validez (→ **VALIDE**) ou rejetez (→ **REJETE**, avec motif obligatoire).

### 10.2 Consulter les demandes

La DGB dispose d'une file de corrections et appose son **visa ou rejet**. Elle consulte conventions, marchés et offres financières.

### 10.3 Clôture et reporting

La DGB consulte les **états statistiques** et le **reporting**, utiles au pilotage budgétaire et au suivi des clôtures.

---

## 11. Guide : Direction Générale du Trésor (DGTCP)

La DGTCP est le **pivot opérationnel** de la plateforme.

### 11.1 Traiter les demandes de correction

Visa ou rejet, demande de compléments, et traitement des **réclamations** sur les demandes adoptées.

### 11.2 Gérer les certificats (mise en place)

**Ouverture du crédit**, **allocation** des montants, **génération** et **envoi** du certificat, ajustement des montants. Le certificat passe par **EN_OUVERTURE_DGTCP** puis **OUVERT**.

### 11.3 Traiter les utilisations

**Douane :**
- imputation et débit du solde après liquidation ;
- envoi au Trésor ;
- saisie des **quittances** ;
- **liquidation douanière** : génération du certificat d'utilisation et débit du solde.

**TVA intérieure :**
- vérification, validation, mise à jour du solde ;
- **apurement TVA**.

### 11.4 Traiter les transferts

**Validation** ou **rejet** des demandes de transfert, instruction/préparation, résolution des rejets temporaires. À la validation, le transfert est exécuté sur le certificat (statut **TRANSFERE**).

### 11.5 Valider les sous-traitances

**Autoriser** ou **refuser** une demande de sous-traitance depuis la file dédiée.

> La DGTCP gère aussi les **clôtures** : elle prépare la proposition de clôture et la **finalise** après validation du Président.

---

## 12. Guide : Président de la Commission

### 12.1 Adopter les demandes de correction

Après les visas des administrations, le Président **valide (adopte) ou rejette** la demande, **génère la lettre** de notification et **dépose la signature**. La demande passe alors à **ADOPTEE** puis **NOTIFIEE**.

### 12.2 Valider les certificats

Le Président **valide ou rejette** les certificats au statut **EN_VALIDATION_PRESIDENT** et **génère le document signé**. Le certificat passe à **VALIDE_PRESIDENT** avant ouverture par la DGTCP.

### 12.3 Décider sur les transferts et sous-traitances

Le Président **valide ou rejette** les transferts et **valide ou rejette les propositions de clôture**.

### 12.4 Reporting national

Le Président dispose d'une vue **nationale** du reporting : synthèse multi-domaines (conventions, certificats, utilisations, corrections) sur une période, avec possibilité de filtrer par autorité contractante et/ou entreprise.

---

## 13. Guide : Administrateur (ADMIN_SI)

### 13.1 Gérer les utilisateurs

Création, modification et désactivation des comptes (toutes administrations et entités), réinitialisation des mots de passe : traitement des **demandes de reset** avec notification e-mail à l'utilisateur (acceptation ou refus).

### 13.2 Gérer les rôles et permissions

L'administrateur gère le **référentiel des permissions** et leur **attribution aux rôles**. C'est ici que se règle ce que chaque profil peut voir et faire sur la plateforme.

### 13.3 Consulter le journal d'audit

Accès au **journal d'audit** : chaque action sensible est tracée (acteur, action, horodatage, instantané de l'objet), permettant un suivi complet a posteriori.

### 13.4 GED (Gestion Électronique des Documents)

L'administrateur configure les **types de documents** (référentiel) et les **exigences documentaires** par processus. La GED conserve **toutes les versions** d'un document :
- lors d'un remplacement, l'ancienne version est conservée (marquée inactive) ;
- un **numéro de version incrémenté** (V1, V2, …) est assigné à chaque révision ;
- les fichiers physiques restent stockés durablement.

---

## 14. Le module Reporting

### 14.1 Ce que vous voyez selon votre profil

Le périmètre des données est **filtré automatiquement selon le rôle** :
- **Rôles nationaux** (Président, ADMIN_SI, DGD, DGTCP, DGI, DGB) : vue globale avec filtres optionnels par **autorité contractante** et/ou **entreprise** ;
- **AC / Délégués / Entreprises** : vue restreinte à leur propre périmètre.

### 14.2 Informations affichées

- **Synthèse** : agrégats multi-domaines (conventions, certificats, utilisations, corrections) sur une fenêtre temporelle, avec compteurs par statut, montants et taux (adoption/rejet) ;
- **Série temporelle** : évolution mensuelle du nombre de demandes de correction.

Les filtres communs sont la **période** (du / au) et, pour les profils habilités, l'AC et l'entreprise cible.

---

## 15. Récapitulatif des flux principaux

### 15.1 Flux convention → certificat

```
AC crée la convention (EN_ATTENTE)
    └── DGB / DGI / Président valident (VALIDE)
        └── AC/UPM/UEP créent le certificat (BROUILLON → ENVOYEE)
            └── DGI / DGD / DGTCP contrôlent (EN_CONTROLE)
                └── Président valide (EN_VALIDATION_PRESIDENT → VALIDE_PRESIDENT)
                    └── DGTCP ouvre le crédit (EN_OUVERTURE_DGTCP → OUVERT)
```

### 15.2 Flux utilisation Douane

```
Entreprise soumet (BROUILLON → DEMANDEE)
    └── DGD contrôle et vise le bulletin (EN_CONTROLE_DGD → VISE)
        └── Entreprise saisit le chèque certifié (CHEQUE_SAISI)
            └── DGTCP envoie au Trésor (ENVOYEE_AU_TRESOR)
                └── DGTCP saisit les quittances (QUITTANCES_ENREGISTREES)
                    └── DGTCP liquide (débit solde) → LIQUIDEE
                        └── Entreprise accuse réception → CLOTUREE
```

### 15.3 Flux utilisation TVA intérieure

```
Entreprise soumet (DEMANDEE)
    └── DGI instruit (décision / compléments)
        └── DGTCP vérifie et valide (solde mis à jour)
            └── DGTCP apure la TVA → APUREE
```

> En cas de pièces manquantes : **INCOMPLETE** / **A_RECONTROLER** jusqu'à résolution du rejet temporaire.

### 15.4 Flux transfert

```
Entreprise crée la demande (DEMANDE)
    └── Dépôt pièces obligatoires → EN_COURS
        └── DGTCP instruite (validation ou rejet temporaire → INCOMPLETE / A_RECONTROLER)
            └── DGTCP / Président valident → TRANSFERE
            └── OU refus définitif → REJETE
            └── OU annulation entreprise → ANNULEE
```

---

## 16. Glossaire

| Terme | Définition |
|---|---|
| **SGCI** | Système de Gestion des Crédits d'Impôt — la plateforme objet de ce manuel. |
| **AC** | Autorité Contractante : entité publique porteuse de la convention/marché. |
| **UPM / UEP** | Délégués de l'AC habilités à agir sur les marchés affectés. |
| **DGD** | Direction Générale des Douanes. |
| **DGI** | Direction Générale des Impôts. |
| **DGB** | Direction Générale du Budget. |
| **DGTCP** | Direction Générale du Trésor et de la Comptabilité Publique. |
| **Président** | Président de la Commission Fiscale (adoption/validation finale). |
| **ADMIN_SI** | Administrateur du système d'information. |
| **Commission Relais** | Compte support agissant « au nom de » une entreprise ou une AC (impersonation). |
| **Convention** | Accord de financement à l'origine des crédits d'impôt. |
| **Marché** | Contrat rattaché à une convention, support des demandes de correction. |
| **Avenant** | Modification contractuelle d'un marché existant. |
| **Certificat de crédit** | Acte ouvrant un crédit d'impôt utilisable (soldes Douane et TVA). |
| **Solde** | Montant restant disponible sur un certificat (Douane / TVA) ; ne peut jamais devenir négatif. |
| **Utilisation** | Consommation d'un crédit, en mode **Douane** ou **TVA intérieure**. |
| **Transfert** | Réaffectation d'un crédit (ex. Douane → intérieur) sur un certificat. |
| **Sous-traitance** | Autorisation donnée à un sous-traitant d'utiliser une part du crédit. |
| **Demande de correction** | Demande d'ajustement douanier liée à un marché, soumise au circuit de visa. |
| **Rejet temporaire** | Demande de compléments suspendant le dossier ; le déposant répond/joint des pièces, puis l'instruction reprend. |
| **Réclamation** | Contestation déposée sur une demande de correction adoptée/notifiée. |
| **Demande d'explication** | Fil de messages permettant de demander des précisions sur un dossier en cours. |
| **Bulletin de liquidation** | Détail des lignes (montants AU_CI / À PAYER) visé par la DGD pour une utilisation Douane. |
| **Quittance** | Justificatif de paiement Trésor saisi par la DGTCP. |
| **Apurement** | Régularisation finale d'une utilisation TVA intérieure. |
| **Clôture** | Fermeture d'un certificat (proposée par DGTCP, validée par le Président). |
| **GED** | Gestion Électronique des Documents (avec versionnage V1/V2…). |
| **Notification** | Alerte in-app (temps réel) et/ou e-mail envoyée à chaque étape clé. |
| **Journal d'audit** | Historique horodaté des actions sensibles réalisées sur la plateforme. |
| **BROUILLON** | Statut initial d'un dossier : non soumis, modifiable librement par le déposant. |
| **Rejet définitif** | Refus irrévocable d'un dossier par une administration ou le Président. |
| **File de travail (queue)** | Liste des dossiers en attente d'action d'un acteur donné, visible sur son tableau de bord. |
