package mr.gov.finances.sgci.service.archive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ReferentielTaxe;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.ReferentielTaxeRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.domain.entity.TvaDeductibleStock;
import mr.gov.finances.sgci.domain.enums.TvaDeductibleStockSource;
import mr.gov.finances.sgci.web.dto.archive.StockTvaArchiveDto;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.archive.ImportArchiveResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LigneTaxeArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.UtilisationArchiveDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reprise d'un relevé de crédit d'impôt issu de l'ancienne application.
 *
 * <p>Le relevé apporte le crédit et ses utilisations ; l'interface fournit le contexte qui en est
 * absent (entreprise, autorité contractante, convention, marché). Le relevé ne comportant aucune
 * demande de correction, l'import en crée une d'archive qui porte le rattachement du dossier et à
 * laquelle le certificat et le marché sont reliés.
 *
 * <p>L'opération se fait en deux temps — {@link #previsualiser} ne lit rien d'autre que le fichier
 * et n'écrit rien, {@link #importer} enregistre. Ce découpage est délibéré : les relevés comportent
 * des écarts de solde que l'opérateur doit voir avant de valider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportArchiveCreditService {

    private static final String PREFIXE_NUMERO = "ARCHIVE-";

    private final ReleveArchiveParser parser;
    private final CertificatCreditRepository certificatRepository;
    private final UtilisationCreditRepository utilisationRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final MarcheRepository marcheRepository;
    private final ConventionRepository conventionRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final ReferentielTaxeRepository referentielTaxeRepository;
    private final TransfertCreditRepository transfertCreditRepository;
    private final TvaDeductibleStockRepository tvaStockRepository;

    // ------------------------------------------------------------------
    // Aperçu — aucune écriture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReleveArchiveDto previsualiser(MultipartFile fichier) {
        ReleveArchiveDto releve = parser.parse(fichier);

        rapprocher(releve, contexteRapprochement());

        certificatExistant(releve).ifPresent(existant -> {
            releve.setCertificatDejaImporteId(existant.getId());
            releve.setCertificatDejaImporteReference(
                    existant.getReference() != null ? existant.getReference() : existant.getNumero());
            releve.getAnomalies().add("Ce crédit a déjà été repris (certificat "
                    + releve.getCertificatDejaImporteReference() + "). Le dossier sera complété : "
                    + "seules les utilisations absentes seront ajoutées, les montants et soldes "
                    + "seront réalignés sur ce relevé.");
        });

        return releve;
    }

    /**
     * Tables de référence chargées une fois pour rapprocher un ou plusieurs relevés : un export de
     * période en contient jusqu'à une centaine, qu'il serait coûteux de rapprocher table par table.
     */
    public record ContexteRapprochement(List<Entreprise> entreprises, List<Marche> marches,
                                        List<AutoriteContractante> autorites, List<Convention> conventions) {
    }

    @Transactional(readOnly = true)
    public ContexteRapprochement contexteRapprochement() {
        return new ContexteRapprochement(entrepriseRepository.findAll(), marcheRepository.findAll(),
                autoriteRepository.findAll(), conventionRepository.findAll());
    }

    /**
     * Pré-sélections tirées du relevé : l'entreprise par le NIF, le marché par sa référence, l'autorité
     * par le marché retrouvé ou, à défaut, par le code qui figure dans la référence du marché.
     * Rien n'est écrit : l'opérateur valide ou corrige chaque choix.
     */
    public void rapprocher(ReleveArchiveDto releve, ContexteRapprochement contexte) {
        rapprocherEntreprise(releve, contexte);
        rapprocherMarcheEtAutorite(releve, contexte);
        if (releve.getAutoriteRapprocheeId() == null) {
            proposerAutoriteParCode(releve, contexte);
        }
    }

    /** Certificat déjà repris pour ce relevé (même numéro « ARCHIVE-… »), s'il existe. */
    public Optional<CertificatCredit> certificatExistant(ReleveArchiveDto releve) {
        return certificatRepository.findByNumero(numeroCertificat(releve));
    }

    /**
     * Rapproche l'entreprise par le NIF. Le relevé et la base ne cadrent pas les zéros de tête de la
     * même façon ({@code 90600034} contre {@code 0090600034}) : la comparaison les ignore.
     */
    private void rapprocherEntreprise(ReleveArchiveDto releve, ContexteRapprochement contexte) {
        String nifReleve = normaliserNif(releve.getNif());
        if (nifReleve == null) {
            return;
        }
        contexte.entreprises().stream()
                .filter(e -> nifReleve.equals(normaliserNif(e.getNif())))
                .findFirst()
                .ifPresent(e -> {
                    releve.setEntrepriseRapprocheeId(e.getId());
                    releve.setEntrepriseRapprocheeRaisonSociale(e.getRaisonSociale());
                    releve.setEntrepriseRapprocheeSource("NIF");
                });
    }

    public static String normaliserNif(String nif) {
        if (nif == null || nif.isBlank()) {
            return null;
        }
        String chiffres = nif.trim().replaceAll("[^0-9]", "").replaceFirst("^0+", "");
        return chiffres.isBlank() ? null : chiffres;
    }

    /**
     * Rapproche le marché sur sa référence, puis en déduit l'autorité contractante en remontant
     * la convention rattachée. Le relevé ne nomme pas l'autorité : c'est le marché qui la désigne.
     */
    private void rapprocherMarcheEtAutorite(ReleveArchiveDto releve, ContexteRapprochement contexte) {
        String reference = normaliserReference(releve.getNumeroMarche() != null
                ? releve.getNumeroMarche() : releve.getReferenceMarche());
        if (reference == null) {
            return;
        }
        contexte.marches().stream()
                .filter(m -> correspond(reference, m.getNumeroMarche()) || correspond(reference, m.getReference()))
                .findFirst()
                .ifPresent(m -> {
                    releve.setMarcheRapprocheId(m.getId());
                    releve.setMarcheRapprocheNumero(m.getNumeroMarche());
                    releve.setMarcheRapprocheIntitule(m.getIntitule());
                    if (m.autoriteEffective() != null) {
                        AutoriteContractante ac = m.autoriteEffective();
                        releve.setAutoriteRapprocheeId(ac.getId());
                        releve.setAutoriteRapprocheeNom(ac.getNom());
                        releve.setAutoriteRapprocheeSource("MARCHE");
                    }
                });
    }

    /**
     * Les références de marché portent le sigle de l'acheteur : « 2220/T/046/CPMP/MHA/UGPRD/2025 »
     * désigne l'UGPRD, rattachée au MHA. Le segment le plus à droite qui égale le code d'une
     * autorité l'emporte ; à défaut, le code de son ministère de tutelle.
     */
    private void proposerAutoriteParCode(ReleveArchiveDto releve, ContexteRapprochement contexte) {
        String reference = releve.getNumeroMarche() != null ? releve.getNumeroMarche() : releve.getReferenceMarche();
        if (reference == null) {
            return;
        }
        List<String> segments = Arrays.stream(reference.toUpperCase().split("[/\\s]+"))
                .map(String::trim)
                .filter(seg -> seg.length() >= 2 && !seg.chars().allMatch(Character::isDigit))
                .toList();
        for (Function<AutoriteContractante, String> code : List.<Function<AutoriteContractante, String>>of(
                AutoriteContractante::getCode, AutoriteContractante::getMinistereTutelleCode)) {
            for (int i = segments.size() - 1; i >= 0; i--) {
                String segment = segments.get(i);
                List<AutoriteContractante> trouvees = contexte.autorites().stream()
                        .filter(ac -> code.apply(ac) != null && segment.equalsIgnoreCase(code.apply(ac).trim()))
                        .toList();
                if (trouvees.size() == 1) {
                    releve.setAutoriteRapprocheeId(trouvees.get(0).getId());
                    releve.setAutoriteRapprocheeNom(trouvees.get(0).getNom());
                    releve.setAutoriteRapprocheeSource("CODE_AC");
                    return;
                }
            }
        }
    }

    /** La convention s'impose quand l'autorité n'en a qu'une ; sinon l'opérateur choisit. */
    public Long conventionUnique(Long autoriteId, ContexteRapprochement contexte) {
        if (autoriteId == null) {
            return null;
        }
        List<Convention> conventions = contexte.conventions().stream()
                .filter(c -> c.autoritesLiees().stream().anyMatch(ac -> autoriteId.equals(ac.getId())))
                .toList();
        return conventions.size() == 1 ? conventions.get(0).getId() : null;
    }

    /** Rapprochement souple : la référence du relevé est souvent un fragment de celle du marché. */
    private boolean correspond(String referenceReleve, String valeurBase) {
        String base = normaliserReference(valeurBase);
        return base != null && (base.contains(referenceReleve) || referenceReleve.contains(base));
    }

    private String normaliserReference(String valeur) {
        if (valeur == null) {
            return null;
        }
        String compact = valeur.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return compact.length() < 4 ? null : compact;   // trop court = rapprochement non fiable
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    @Transactional
    public ImportArchiveResultatDto importer(MultipartFile fichier, Long entrepriseId,
                                             Long autoriteContractanteId, Long conventionId, Long marcheId,
                                             boolean confirmerMalgreAnomalies, AuthenticatedUser user) {
        ReleveArchiveDto releve = parser.parse(fichier);

        Entreprise entreprise = entrepriseRepository.findById(entrepriseId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Entreprise non trouvée : " + entrepriseId));
        AutoriteContractante autorite = autoriteRepository.findById(autoriteContractanteId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Autorité contractante non trouvée : " + autoriteContractanteId));
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Convention non trouvée : " + conventionId));
        Marche marche = marcheId == null ? null
                : marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Marché non trouvé : " + marcheId));

        return importerReleve(releve, entreprise, autorite, convention, marche, confirmerMalgreAnomalies, user);
    }

    /**
     * Cœur de la reprise, partagé par l'import d'un relevé à un crédit et par celui d'un export de
     * période : le relevé est déjà lu, l'entreprise, l'autorité et la convention sont choisies.
     */
    @Transactional
    public ImportArchiveResultatDto importerReleve(ReleveArchiveDto releve, Entreprise entreprise,
                                                   AutoriteContractante autorite, Convention convention,
                                                   Marche marche, boolean confirmerMalgreAnomalies,
                                                   AuthenticatedUser user) {
        String numero = numeroCertificat(releve);
        // Un crédit déjà repris n'est pas un doublon : le relevé peut être reversé complété de
        // nouvelles utilisations. On complète alors l'existant au lieu de refuser.
        CertificatCredit existant = certificatRepository.findByNumero(numero).orElse(null);

        // Un crédit sans utilisation est légitime : le crédit est ouvert, rien n'a encore été
        // consommé. On le reprend tel quel, ses utilisations seront versées plus tard.
        if (!releve.getAnomalies().isEmpty() && !confirmerMalgreAnomalies) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le relevé comporte " + releve.getAnomalies().size()
                            + " anomalie(s). Confirmez explicitement pour importer malgré tout.");
        }

        // Le relevé ne porte aucune demande de correction : on en crée une d'archive, qui rattache
        // le dossier à son autorité contractante et à sa convention et rend le certificat visible
        // dans les écrans qui passent par la demande.
        CertificatCredit certificat;
        TransfertCredit transfert;
        if (existant == null) {
            DemandeCorrection demande = demandeCorrectionRepository.save(
                    creerDemandeArchive(releve, entreprise, autorite, convention, numero));

            if (marche != null) {
                rattacherMarche(marche, demande, convention);
            }

            certificat = creerCertificat(releve, entreprise, numero);
            certificat.setDemandeCorrection(demande);
            certificat = certificatRepository.save(certificat);

            transfert = creerTransfertArchive(releve, certificat);
        } else {
            // Relevé reversé complété : on garde le dossier en place et on l'actualise.
            certificat = existant;
            transfert = transfertExistantOuCree(releve, certificat);
            rafraichirMontantsDepuisReleve(releve, certificat);
        }

        // Lignes déjà reprises pour ce crédit. Le libellé seul ne suffit pas : un relevé peut
        // répéter le même libellé sur des opérations distinctes (54 lignes pour 51 libellés sur le
        // relevé de référence). La clé associe donc libellé, date et montant.
        List<UtilisationCredit> enBase = utilisationRepository.findByCertificatCreditId(certificat.getId());
        Map<String, UtilisationCredit> dejaImportees = new HashMap<>();
        for (UtilisationCredit u : enBase) {
            if (u.getOrigineArchiveLibelle() != null) {
                dejaImportees.putIfAbsent(cleLigneArchive(u.getOrigineArchiveLibelle(), u.getDateDemande(), u.getMontant()), u);
            }
        }
        // Un crédit utilisé dans l'application garde son historique : le rejeu de la TVA intérieure
        // et du stock ne s'applique qu'à un crédit dont toutes les opérations viennent de l'archive.
        boolean rejouable = enBase.stream().allMatch(u -> u.getOrigineArchiveLibelle() != null);
        Map<Integer, UtilisationCredit> parRang = new HashMap<>();

        Map<String, ReferentielTaxe> referentiel = referentielTaxeRepository.findAll().stream()
                .collect(Collectors.toMap(t -> t.getCodeTaxe().toUpperCase(),
                        Function.identity(), (a, b) -> a));

        int douanieres = 0;
        int interieures = 0;
        int lignesTaxe = 0;
        int ignorees = 0;
        for (int rang = 0; rang < releve.getUtilisations().size(); rang++) {
            UtilisationArchiveDto u = releve.getUtilisations().get(rang);
            String cle = cleLigneArchive(u.getLibelle(), u.getDate(), u.getMontant());
            UtilisationCredit deja = u.getLibelle() != null ? dejaImportees.get(cle) : null;
            if (deja != null) {
                ignorees++;   // ligne déjà reprise lors d'un versement précédent
                if (rejouable && deja instanceof UtilisationTVAInterieure i && u.getTvaDeductibleUtilisee() != null) {
                    appliquerRejeu(i, u);
                    utilisationRepository.save(i);
                }
                parRang.put(rang, deja);
                continue;
            }
            UtilisationCredit cree;
            if (u.isDouaniere()) {
                UtilisationDouaniere douaniere = creerUtilisationDouaniere(u, certificat, entreprise, referentiel);
                cree = utilisationRepository.save(douaniere);   // cascade sur les lignes de bulletin
                lignesTaxe += douaniere.getLignes().size();
                douanieres++;
            } else {
                cree = utilisationRepository.save(creerUtilisationInterieure(u, certificat, entreprise));
                interieures++;
            }
            if (u.getLibelle() != null) {
                dejaImportees.put(cle, cree);
            }
            parRang.put(rang, cree);
        }

        if (releve.getStockTva() != null) {
            if (rejouable && reconstituerStockTva(certificat, releve, parRang)) {
                log.info("Import archive : stock de TVA déductible du certificat {} reconstitué ({} restant)",
                        numero, releve.getStockTvaRestant());
            } else {
                releve.getAnomalies().add("Le crédit a déjà été utilisé dans l'application : le stock de TVA "
                        + "déductible n'a pas été reconstitué depuis le relevé.");
            }
        }

        log.info("Import archive : certificat {} {} pour l'entreprise {} — {} utilisation(s) douanière(s), "
                        + "{} intérieure(s), {} ligne(s) de taxe, {} ligne(s) ignorée(s), par {}",
                numero, existant == null ? "créé" : "complété", entreprise.getId(),
                douanieres, interieures, lignesTaxe, ignorees,
                user != null ? user.getUsername() : "système");

        ImportArchiveResultatDto resultat = construireResultat(releve, certificat, entreprise,
                douanieres, interieures, lignesTaxe, autorite, marche, transfert);
        resultat.setCertificatDejaExistant(existant != null);
        resultat.setUtilisationsIgnorees(ignorees);
        return resultat;
    }

    /**
     * Demande de correction d'archive, support du rattachement du dossier repris.
     *
     * <p>Elle est créée directement dans son état final : le dossier a déjà été instruit dans
     * l'ancienne application, la faire repartir à zéro la ferait réapparaître dans les files
     * d'attente DGD / DGTCP / DGI / DGB comme s'il restait à traiter.
     */
    private DemandeCorrection creerDemandeArchive(ReleveArchiveDto releve, Entreprise entreprise,
                                                  AutoriteContractante autorite, Convention convention,
                                                  String numeroCertificat) {
        DemandeCorrection demande = new DemandeCorrection();
        demande.setNumero(numeroCertificat.replace(PREFIXE_NUMERO, "ARCHIVE-DC-"));
        demande.setReference(null);   // attribuée par ReferenceBackfillMigration
        demande.setStatut(StatutDemande.NOTIFIEE);
        demande.setAutoriteContractante(autorite);
        demande.setEntreprise(entreprise);
        demande.setConvention(convention);
        demande.setCreditInterieur(valeurOuZero(releve.getCreditInterieur()));
        demande.setCreditExterieur(valeurOuZero(releve.getCreditDouanier()));
        demande.setIntituleMarche(releve.getReferenceMarche());
        demande.setDateDepot(releve.getDateCredit());
        demande.setValidationDgd(true);
        demande.setValidationDgtcp(true);
        demande.setValidationDgi(true);
        demande.setValidationDgb(true);
        return demande;
    }

    /**
     * Matérialise le transfert du relevé en {@link TransfertCredit} déjà exécuté.
     *
     * <p>Le montant retenu est la <b>seule part réellement transférée</b> vers le crédit intérieur —
     * la composante TVA. Le reliquat de droits, mis à zéro à la clôture, n'est pas un transfert et
     * n'a donc pas à figurer ici.
     *
     * <p>L'enjeu dépasse la traçabilité : le contrôle d'éligibilité interdit les utilisations
     * douanières sur un certificat dont le transfert a été exécuté. Sans cet enregistrement, ce
     * garde-fou resterait inactif sur les dossiers repris.
     *
     * <p>Aucune écriture de stock de TVA déductible n'est créée : les soldes du relevé intègrent
     * déjà le transfert, en ajouter une reviendrait à compter deux fois le même montant.
     */
    private TransfertCredit creerTransfertArchive(ReleveArchiveDto releve, CertificatCredit certificat) {
        BigDecimal montant = releve.getTransfertCredit();
        if (montant == null || montant.signum() <= 0) {
            return null;   // relevé sans transfert
        }
        TransfertCredit transfert = TransfertCredit.builder()
                .certificatCredit(certificat)
                .montant(montant)
                .statut(StatutTransfert.TRANSFERE)   // opération déjà réalisée dans l'ancien système
                .dateDemande(releve.getDateCredit())
                .operationsDouaneCloturees(Boolean.TRUE)
                .build();
        return transfertCreditRepository.save(transfert);
    }

    /**
     * Clé d'unicité d'une ligne de relevé : libellé, date et montant réunis.
     *
     * <p>Le libellé seul provoquerait la perte d'opérations distinctes portant le même intitulé.
     * Deux lignes identiques sur ces trois critères sont, elles, indiscernables : les traiter comme
     * une seule est le comportement voulu.
     */
    private static String cleLigneArchive(String libelle, java.time.Instant date, BigDecimal montant) {
        return (libelle == null ? "" : libelle.trim())
                + "|" + (date == null ? "" : date.toString())
                + "|" + (montant == null ? "" : montant.stripTrailingZeros().toPlainString());
    }

    /**
     * Transfert du relevé : réutilise celui déjà repris plutôt que d'en créer un second.
     *
     * <p>Un relevé reversé porte le même transfert que la fois précédente ; le recréer doublerait
     * le montant transféré dans les écrans de suivi.
     */
    private TransfertCredit transfertExistantOuCree(ReleveArchiveDto releve, CertificatCredit certificat) {
        List<TransfertCredit> existants = transfertCreditRepository.findByCertificatCreditId(certificat.getId());
        if (!existants.isEmpty()) {
            return existants.get(0);
        }
        return creerTransfertArchive(releve, certificat);
    }

    /**
     * Réaligne les montants et les soldes du crédit sur le relevé le plus récent.
     *
     * <p>Le relevé fait foi : ses soldes en sont recalculés, nets des utilisations qu'il contient. Mais si le
     * crédit a depuis été consommé dans l'application, réécrire ces soldes effacerait ces
     * consommations. Dans ce cas les soldes sont laissés intacts et une anomalie est signalée :
     * mieux vaut un écart visible qu'un solde faussement rétabli.
     */
    private void rafraichirMontantsDepuisReleve(ReleveArchiveDto releve, CertificatCredit certificat) {
        boolean consommeHorsArchive = utilisationRepository.findByCertificatCreditId(certificat.getId())
                .stream()
                .anyMatch(u -> u.getOrigineArchiveLibelle() == null);

        if (releve.getCreditDouanier() != null) {
            certificat.setMontantCordon(releve.getCreditDouanier());
        }
        if (releve.getCreditInterieur() != null) {
            certificat.setMontantTVAInterieure(releve.getCreditInterieur());
        }
        if (releve.getMontantMarche() != null) {
            certificat.setMontantMarcheHt(releve.getMontantMarche());
        }

        if (consommeHorsArchive) {
            releve.getAnomalies().add("Le crédit a déjà été utilisé dans l'application : les soldes "
                    + "recalculés depuis le relevé n'ont pas été réappliqués, pour ne pas effacer ces "
                    + "utilisations. Vérifiez la concordance des soldes.");
            appliquerComposantes(releve, certificat, false);
        } else {
            appliquerComposantes(releve, certificat, true);
        }
        certificat.setStatut(statutSelonSoldes(certificat));
        certificatRepository.save(certificat);
        normaliserUtilisationsArchive(certificat.getId());
    }

    /**
     * Composantes du crédit telles que les lit l'application (récapitulatif fiscal, solde cordon,
     * renonciation) : b, c, d accordée et g = h + d, puis — si {@code avecSoldes} — le solde des
     * droits (cordon) et la TVA d'importation restante (d′), calculés séparément. Sans ventilation
     * dans le relevé (ligne « Totaux » absente), tout le solde douanier reste en droits.
     */
    private void appliquerComposantes(ReleveArchiveDto releve, CertificatCredit certificat, boolean avecSoldes) {
        if (releve.getTvaImportation() != null) {
            certificat.setDroitsEtTaxesDouaneHorsTva(releve.getDroitsEtTaxesHorsTva());
            certificat.setTaxesConsommation(releve.getTaxesConsommation());
            certificat.setTvaImportationDouaneAccordee(releve.getTvaImportation());
            // h = g − d : le relevé donne h (crédit intérieur), g s'en déduit.
            if (releve.getCreditInterieur() != null) {
                certificat.setTvaCollecteeTravaux(releve.getCreditInterieur().add(releve.getTvaImportation()));
            }
        }
        if (!avecSoldes) {
            return;
        }
        if (releve.getSoldeTvaImportationCalcule() != null) {
            certificat.setSoldeCordon(soldeRetenu(releve.getSoldeDroitsCalcule()));
            certificat.setTvaImportationDouane(soldeRetenu(releve.getSoldeTvaImportationCalcule()));
        } else {
            certificat.setSoldeCordon(soldeRetenu(releve.getSoldeDouanierCalcule()));
            certificat.setTvaImportationDouane(BigDecimal.ZERO);
        }
        certificat.setSoldeTVA(soldeRetenu(releve.getSoldeInterieurCalcule()));
    }

    /** Un crédit encore pourvu — droits, TVA d'importation ou TVA intérieure — reste exploitable. */
    private StatutCertificat statutSelonSoldes(CertificatCredit c) {
        return valeurOuZero(c.getSoldeCordon()).signum() > 0
                || valeurOuZero(c.getSoldeTVA()).signum() > 0
                || valeurOuZero(c.getTvaImportationDouane()).signum() > 0
                ? StatutCertificat.OUVERT
                : StatutCertificat.CLOTURE;
    }

    /**
     * Utilisations reprises avant le 03/11/2026 : la douane était marquée « apurée » (l'application
     * dit « liquidée ») et la TVA intérieure n'avait pas son montant de TVA, d'où des compteurs et une
     * colonne TVA à zéro. Sans effet sur une utilisation déjà conforme.
     */
    @Transactional
    public int normaliserUtilisationsArchive(Long certificatId) {
        int corrigees = 0;
        for (UtilisationCredit u : utilisationRepository.findByCertificatCreditId(certificatId)) {
            if (u.getOrigineArchiveLibelle() != null && completerUtilisationArchive(u)) {
                utilisationRepository.save(u);
                corrigees++;
            }
        }
        return corrigees;
    }

    /** Vrai si l'utilisation d'archive a été modifiée. */
    public static boolean completerUtilisationArchive(UtilisationCredit u) {
        boolean modifiee = false;
        if (u instanceof UtilisationDouaniere d && d.getStatut() == StatutUtilisation.APUREE) {
            d.setStatut(StatutUtilisation.LIQUIDEE);
            modifiee = true;
        }
        if (u instanceof UtilisationTVAInterieure i && i.getMontantTVA() == null && i.getMontant() != null) {
            remplirTvaInterieure(i);
            modifiee = true;
        }
        return modifiee;
    }

    /** Ancienne application : le montant imputé sur le crédit intérieur est la TVA elle-même, sans déduction. */
    private static void remplirTvaInterieure(UtilisationTVAInterieure i) {
        i.setMontantTVA(i.getMontant());
        i.setTvaDeductibleUtilisee(BigDecimal.ZERO);
        i.setTvaNette(i.getMontant());
        i.setPaiementEntreprise(BigDecimal.ZERO);
        i.setReportANouveau(BigDecimal.ZERO);
    }

    /**
     * Marché tiré du relevé, quand l'application n'en connaît aucun qui corresponde : numéro, date,
     * montant et durée viennent du relevé, l'autorité et la convention du choix de l'opérateur.
     * Le statut suit le crédit : encore pourvu, le marché est en cours ; épuisé, il est clos.
     */
    @Transactional
    public Marche creerMarcheDepuisReleve(ReleveArchiveDto releve, AutoriteContractante autorite,
                                          Convention convention) {
        boolean creditPourvu = soldeRetenu(releve.getSoldeDouanierCalcule()).signum() > 0
                || soldeRetenu(releve.getSoldeInterieurCalcule()).signum() > 0;
        String numero = releve.getNumeroMarche() != null ? releve.getNumeroMarche() : releve.getReferenceMarche();
        Marche marche = Marche.builder()
                .numeroMarche(numero != null ? numero : "Marché du crédit " + releve.getNumeroCredit())
                .intitule(releve.getReferenceMarche())
                .dateSignature(releve.getDateMarche() != null
                        ? LocalDate.ofInstant(releve.getDateMarche(), ZoneOffset.UTC) : null)
                .montantContratHt(valeurOuZero(releve.getMontantMarche()))
                .dureeExecution(releve.getDureeMarche())
                .statut(creditPourvu ? StatutMarche.EN_COURS : StatutMarche.CLOTURE)
                .autoriteContractante(autorite)
                .convention(convention)
                .build();
        if (convention != null) {
            marche.getConventions().add(convention);
        }
        return marcheRepository.save(marche);
    }

    /** Relie le marché choisi à la demande d'archive, en refusant d'en détourner un déjà rattaché. */
    private void rattacherMarche(Marche marche, DemandeCorrection demande, Convention convention) {
        if (marche.getDemandeCorrection() != null
                && !marche.getDemandeCorrection().getId().equals(demande.getId())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Le marché « " + marche.getNumeroMarche() + " » est déjà rattaché à un autre dossier.");
        }
        marche.setDemandeCorrection(demande);
        if (marche.getConvention() == null) {
            marche.setConvention(convention);
        }
        marcheRepository.save(marche);
    }

    /**
     * Crée le certificat avec les soldes <b>recalculés</b> depuis le relevé (crédit − utilisations ±
     * transfert) ; les soldes affichés par l'ancienne application ne servent que de contrôle.
     *
     * <p>Un solde douanier nul est normal après un transfert —
     * seule la composante TVA du crédit douanier est transférable vers le crédit intérieur, le
     * reliquat de droits (DD, RS, PC, PSC, TCO) étant mis à zéro à la clôture du crédit.
     */
    private CertificatCredit creerCertificat(ReleveArchiveDto releve, Entreprise entreprise, String numero) {
        CertificatCredit certificat = new CertificatCredit();
        certificat.setNumero(numero);
        certificat.setReference(null);   // attribuée par ReferenceBackfillMigration au redémarrage
        certificat.setEntreprise(entreprise);
        certificat.setMontantCordon(releve.getCreditDouanier());
        certificat.setMontantTVAInterieure(releve.getCreditInterieur());
        certificat.setMontantMarcheHt(releve.getMontantMarche());
        appliquerComposantes(releve, certificat, true);
        certificat.setDateMiseEnPlace(releve.getDateCredit());
        // Dossier repris : la date d'émission est celle du relevé, pas celle de l'import.
        certificat.setDateEmission(releve.getDateCredit());
        // Un crédit encore pourvu reste exploitable ; épuisé, il est clos.
        certificat.setStatut(statutSelonSoldes(certificat));
        return certificat;
    }

    private UtilisationDouaniere creerUtilisationDouaniere(UtilisationArchiveDto u, CertificatCredit certificat,
                                                          Entreprise entreprise,
                                                          Map<String, ReferentielTaxe> referentiel) {
        UtilisationDouaniere d = new UtilisationDouaniere();
        d.setCertificatCredit(certificat);
        d.setEntreprise(entreprise);
        d.setStatut(StatutUtilisation.LIQUIDEE);   // utilisation douanière historique, déjà liquidée
        d.setMontant(u.getMontant());
        d.setDateDemande(u.getDate());
        d.setDateLiquidation(u.getDate());
        d.setNumeroDeclaration(u.getLibelle());
        d.setOrigineArchiveLibelle(u.getLibelle());
        // Reprise d'un dossier clos sur papier : aucune émission de certificat n'est attendue (A20).
        d.setEmissionCertificatRequise(Boolean.FALSE);
        d.setNumeroBulletin(u.getNumeroQuittance());
        d.setTotalPrisEnCharge(u.getTotalPrisEnCharge());
        d.setTotalAPayer(u.getTotalAPayer());
        d.setMontantDroits(valeurTaxe(u, "DD"));
        d.setMontantTVA(valeurTaxe(u, "TVA"));

        for (LigneTaxeArchiveDto l : u.getLignesTaxe()) {
            ReferentielTaxe taxe = referentiel.get(l.getCodeTaxe().toUpperCase());
            LigneBulletinLiquidation ligne = LigneBulletinLiquidation.builder()
                    .codeTaxe(l.getCodeTaxe())
                    .denominationTaxe(taxe != null ? taxe.getDenominationTaxe() : l.getCodeTaxe())
                    .typeLigne(TypeLigneTaxe.GLOBALE)
                    .valeurTaxe(l.getValeur())
                    .affectationEntreprise(AffectationTaxe.valueOf(l.getAffectation()))
                    .affectation(AffectationTaxe.valueOf(l.getAffectation()))   // archive : décision acquise
                    .utilisationDouaniere(d)
                    .build();
            d.getLignes().add(ligne);
        }
        return d;
    }

    private UtilisationTVAInterieure creerUtilisationInterieure(UtilisationArchiveDto u,
                                                               CertificatCredit certificat,
                                                               Entreprise entreprise) {
        UtilisationTVAInterieure i = new UtilisationTVAInterieure();
        i.setCertificatCredit(certificat);
        i.setEntreprise(entreprise);
        i.setStatut(StatutUtilisation.APUREE);
        i.setMontant(u.getMontant());
        i.setDateDemande(u.getDate());
        i.setDateLiquidation(u.getDate());
        i.setNumeroFacture(u.getLibelle());
        i.setOrigineArchiveLibelle(u.getLibelle());
        i.setEmissionCertificatRequise(Boolean.FALSE);
        i.setCreditInterieurUtilise(u.getMontant());
        if (u.getTvaDeductibleUtilisee() != null) {
            appliquerRejeu(i, u);
        } else {
            remplirTvaInterieure(i);   // relevé lu avant le rejeu (lot ancien non relu)
        }
        return i;
    }

    /** Valeurs de l'apurement rejoué selon les règles de l'application (voir le lecteur). */
    private static void appliquerRejeu(UtilisationTVAInterieure i, UtilisationArchiveDto u) {
        i.setMontantTVA(u.getMontant());
        i.setTvaDeductibleUtilisee(u.getTvaDeductibleUtilisee());
        i.setTvaNette(u.getTvaNette());
        i.setCreditInterieurUtilise(u.getCreditInterieurUtilise());
        i.setPaiementEntreprise(u.getPaiementEntreprise());
        i.setReportANouveau(u.getReportANouveau());
        i.setSoldeTVAAvant(u.getSoldeTvaAvant());
        i.setSoldeTVAApres(u.getSoldeTvaApres());
    }

    /**
     * Lignes du stock de TVA déductible d'un crédit repris, refaites depuis le rejeu du relevé : une par
     * importation (rattachée à son utilisation douanière) et une pour le transfert, avec leurs restes.
     * Les lignes posées par une reprise précédente sont remplacées, jamais doublées. Une ligne de
     * transfert alors que le relevé n'en porte pas vient d'une renonciation faite dans l'application :
     * dans ce cas rien n'est touché (faux en retour).
     */
    private boolean reconstituerStockTva(CertificatCredit certificat, ReleveArchiveDto releve,
                                         Map<Integer, UtilisationCredit> parRang) {
        List<TvaDeductibleStock> existantes = tvaStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(certificat.getId());
        boolean transfertDansLeReleve = releve.getStockTva().stream().anyMatch(s -> "TRANSFERT_CREDIT".equals(s.getSource()));
        boolean transfertApplication = existantes.stream()
                .anyMatch(s -> s.getSource() == TvaDeductibleStockSource.TRANSFERT_CREDIT && !transfertDansLeReleve);
        if (transfertApplication) {
            return false;
        }
        tvaStockRepository.deleteAll(existantes);
        tvaStockRepository.flush();
        Instant parDefaut = releve.getDateCredit() != null ? releve.getDateCredit() : Instant.now();
        for (StockTvaArchiveDto s : releve.getStockTva()) {
            UtilisationCredit u = s.getIndexUtilisation() != null ? parRang.get(s.getIndexUtilisation()) : null;
            tvaStockRepository.save(TvaDeductibleStock.builder()
                    .certificatCredit(certificat)
                    .utilisationDouane(u instanceof UtilisationDouaniere d ? d : null)
                    .source("TRANSFERT_CREDIT".equals(s.getSource())
                            ? TvaDeductibleStockSource.TRANSFERT_CREDIT : TvaDeductibleStockSource.UTILISATION_DOUANE)
                    .montantInitial(s.getMontantInitial())
                    .montantRestant(s.getMontantRestant())
                    .dateCreation(s.getDate() != null ? s.getDate() : parDefaut)
                    .build());
        }
        return true;
    }

    private ImportArchiveResultatDto construireResultat(ReleveArchiveDto releve, CertificatCredit certificat,
                                                       Entreprise entreprise, int douanieres, int interieures,
                                                       int lignesTaxe, AutoriteContractante autorite,
                                                       Marche marche, TransfertCredit transfert) {
        ImportArchiveResultatDto resultat = new ImportArchiveResultatDto();
        resultat.setCertificatId(certificat.getId());
        resultat.setCertificatNumero(certificat.getNumero());
        resultat.setCertificatReference(certificat.getReference());
        resultat.setCertificatStatut(certificat.getStatut().name());
        resultat.setEntrepriseId(entreprise.getId());
        resultat.setEntrepriseRaisonSociale(entreprise.getRaisonSociale());
        if (certificat.getDemandeCorrection() != null) {
            resultat.setDemandeCorrectionId(certificat.getDemandeCorrection().getId());
            resultat.setDemandeCorrectionNumero(certificat.getDemandeCorrection().getNumero());
            resultat.setConventionId(certificat.getDemandeCorrection().getConvention() != null
                    ? certificat.getDemandeCorrection().getConvention().getId() : null);
        }
        resultat.setAutoriteContractanteId(autorite != null ? autorite.getId() : null);
        resultat.setMarcheId(marche != null ? marche.getId() : null);
        if (transfert != null) {
            resultat.setTransfertCreditId(transfert.getId());
            resultat.setTransfertCreditMontant(transfert.getMontant());
        }
        resultat.setUtilisationsDouanieres(douanieres);
        resultat.setUtilisationsInterieures(interieures);
        resultat.setLignesTaxeCreees(lignesTaxe);
        resultat.setSoldeCordon(certificat.getSoldeCordon());
        resultat.setSoldeTVA(certificat.getSoldeTVA());
        resultat.setAnomalies(releve.getAnomalies());

        // Contexte choisi par l'opérateur, tracé dans le journal applicatif.
        if (autorite != null || marche != null) {
            log.info("Import archive : certificat {} rattaché au contexte autorité={} marché={}",
                    certificat.getNumero(),
                    autorite != null ? autorite.getNom() : "-",
                    marche != null ? marche.getNumeroMarche() : "-");
        }
        return resultat;
    }

    private BigDecimal valeurTaxe(UtilisationArchiveDto u, String code) {
        return u.getLignesTaxe().stream()
                .filter(l -> code.equalsIgnoreCase(l.getCodeTaxe()))
                .map(LigneTaxeArchiveDto::getValeur)
                .findFirst()
                .orElse(null);
    }

    /** Un solde calculé négatif (signalé en anomalie à la lecture) est ramené à zéro. */
    private BigDecimal soldeRetenu(BigDecimal calcule) {
        return calcule == null || calcule.signum() < 0 ? BigDecimal.ZERO : calcule;
    }

    private BigDecimal valeurOuZero(BigDecimal valeur) {
        return valeur != null ? valeur : BigDecimal.ZERO;
    }

    /** Numéro déduit du relevé, qui sert aussi de garde contre le double import. */
    public String numeroCertificat(ReleveArchiveDto releve) {
        String base = releve.getNumeroCredit() != null && !releve.getNumeroCredit().isBlank()
                ? releve.getNumeroCredit().trim()
                : releve.getNomFichier();
        return PREFIXE_NUMERO + base;
    }

    /** Codes de taxe connus du référentiel, pour information à l'écran. */
    @Transactional(readOnly = true)
    public List<String> codesTaxeConnus() {
        return referentielTaxeRepository.findAll().stream()
                .map(ReferentielTaxe::getCodeTaxe)
                .sorted()
                .toList();
    }
}
