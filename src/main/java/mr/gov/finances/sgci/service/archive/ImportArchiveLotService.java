package mr.gov.finances.sgci.service.archive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LotImportArchive;
import mr.gov.finances.sgci.domain.entity.LotImportArchiveCredit;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.enums.StatutCreditLotArchive;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.LotImportArchiveCreditRepository;
import mr.gov.finances.sgci.repository.LotImportArchiveRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.archive.ImportArchiveCreditService.ContexteRapprochement;
import mr.gov.finances.sgci.web.dto.archive.ImportArchiveResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.AffectationRequest;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.CreditDetailDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.CreditDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.ImportResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.LotDto;
import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.VerificationNifDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reprise d'archive par fichier de période : l'export regroupe tous les crédits émis sur une période,
 * avec leurs utilisations. Le fichier est lu une fois et gardé en lot ; l'opérateur complète ce que
 * le fichier ne dit pas (entreprise quand le NIF est inconnu, autorité, convention, marché), puis
 * importe les crédits prêts.
 *
 * <p>Chaque crédit est importé par {@link ImportArchiveCreditService#importerReleve}, le même cœur que
 * l'import d'un relevé à un crédit, et dans sa propre transaction : un crédit en erreur n'empêche
 * pas les autres.
 */
@Service
@Slf4j
public class ImportArchiveLotService {

    /** NIF interrogés auprès de la DGI par appel : l'écran enchaîne les appels et montre l'avancement. */
    private static final int NIF_DGI_PAR_APPEL = 20;

    private final ReleveArchiveParser parser;
    private final ImportArchiveCreditService importService;
    private final DgiNifClient dgi;
    private final LotImportArchiveRepository lotRepository;
    private final LotImportArchiveCreditRepository creditRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final ConventionRepository conventionRepository;
    private final MarcheRepository marcheRepository;
    private final CertificatCreditRepository certificatRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionParCredit;

    public ImportArchiveLotService(ReleveArchiveParser parser, ImportArchiveCreditService importService,
                                   DgiNifClient dgi, LotImportArchiveRepository lotRepository,
                                   LotImportArchiveCreditRepository creditRepository,
                                   EntrepriseRepository entrepriseRepository,
                                   AutoriteContractanteRepository autoriteRepository,
                                   ConventionRepository conventionRepository, MarcheRepository marcheRepository,
                                   CertificatCreditRepository certificatRepository,
                                   ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.certificatRepository = certificatRepository;
        this.parser = parser;
        this.importService = importService;
        this.dgi = dgi;
        this.lotRepository = lotRepository;
        this.creditRepository = creditRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.autoriteRepository = autoriteRepository;
        this.conventionRepository = conventionRepository;
        this.marcheRepository = marcheRepository;
        this.objectMapper = objectMapper;
        this.transactionParCredit = new TransactionTemplate(transactionManager);
        this.transactionParCredit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------
    // Analyse du fichier
    // ------------------------------------------------------------------

    @Transactional
    public LotDto analyser(MultipartFile fichier, AuthenticatedUser user) {
        String empreinte = empreinte(fichier);
        LotImportArchive existant = lotRepository.findByEmpreinteSha256(empreinte).orElse(null);
        if (existant != null) {
            relireReleves(existant, parser.parseLot(fichier));
            LotDto dto = toDto(existant, true);
            dto.setDejaAnalyse(true);
            return dto;
        }

        List<BlocReleve> blocs = parser.parseLot(fichier);
        ContexteRapprochement contexte = importService.contexteRapprochement();

        LotImportArchive lot = LotImportArchive.builder()
                .nomFichier(fichier.getOriginalFilename() != null ? fichier.getOriginalFilename() : "export.xls")
                .empreinteSha256(empreinte)
                .dateAnalyse(Instant.now())
                .analyseParId(user != null ? user.getUserId() : null)
                .analyseParNom(user != null ? user.getUsername() : null)
                .build();

        // L'export répète parfois un crédit (15/2023 en 2023 : une seconde version, montants révisés).
        Map<String, List<Integer>> lignesParNumero = blocs.stream()
                .filter(b -> b.releve().getNumeroCredit() != null)
                .collect(Collectors.groupingBy(b -> b.releve().getNumeroCredit(), LinkedHashMap::new,
                        Collectors.mapping(BlocReleve::ligneDebut, Collectors.toList())));

        int ordre = 0;
        for (BlocReleve bloc : blocs) {
            ReleveArchiveDto releve = bloc.releve();
            // Un bloc sans numéro ni date (l'export en contient) est à compléter par l'opérateur.
            List<Integer> memesNumeros = releve.getNumeroCredit() != null
                    ? lignesParNumero.get(releve.getNumeroCredit()) : List.of();
            if (memesNumeros.size() > 1) {
                releve.getAnomalies().add("Le N° de crédit " + releve.getNumeroCredit()
                        + " figure plusieurs fois dans le fichier (blocs des lignes "
                        + memesNumeros.stream().map(String::valueOf).collect(Collectors.joining(", "))
                        + ") : un seul certificat est créé ; chaque bloc importé ensuite le complète et "
                        + "impose ses montants et ses soldes. Importez en dernier la version qui fait foi, "
                        + "et vérifiez qu'aucune utilisation ne figure deux fois sous des dates différentes.");
            }
            importService.rapprocher(releve, contexte);

            LotImportArchiveCredit credit = LotImportArchiveCredit.builder()
                    .lot(lot)
                    .ordre(++ordre)
                    .ligneDebut(bloc.ligneDebut())
                    .ligneFin(bloc.ligneFin())
                    .statut(StatutCreditLotArchive.A_COMPLETER)
                    .build();
            appliquerReleve(credit, releve);
            proposer(credit, releve, contexte);
            credit.setReleveJson(ecrire(releve));
            credit.setStatut(statutCalcule(credit, releve));
            lot.getCredits().add(credit);
        }
        lot = lotRepository.save(lot);
        log.info("Reprise d'archive : lot {} « {} » analysé — {} crédit(s), par {}", lot.getId(),
                lot.getNomFichier(), lot.getCredits().size(), user != null ? user.getUsername() : "système");
        return toDto(lot, true);
    }

    /**
     * Le même fichier, versé à nouveau, est relu : le lecteur a pu progresser depuis la première
     * analyse (ventilation du crédit douanier par taxe, 03/11/2026). Les choix de l'opérateur restent,
     * ainsi que le N° et la date qu'il a saisis pour un bloc qui n'en portait pas. Le fichier étant le
     * même, les blocs se retrouvent par leur rang.
     */
    private void relireReleves(LotImportArchive lot, List<BlocReleve> blocs) {
        for (LotImportArchiveCredit c : lot.getCredits()) {
            int i = c.getOrdre() - 1;
            if (i < 0 || i >= blocs.size()) {
                continue;
            }
            ReleveArchiveDto ancien = lire(c);
            ReleveArchiveDto nouveau = blocs.get(i).releve();
            if (nouveau.getNumeroCredit() == null) {
                nouveau.setNumeroCredit(ancien.getNumeroCredit());
            }
            if (nouveau.getDateCredit() == null) {
                nouveau.setDateCredit(ancien.getDateCredit());
            }
            for (String a : ancien.getAnomalies()) {
                if (a.startsWith("Le N° de crédit ") && a.contains("plusieurs fois") && !nouveau.getAnomalies().contains(a)) {
                    nouveau.getAnomalies().add(a);
                }
            }
            appliquerReleve(c, nouveau);
            c.setReleveJson(ecrire(nouveau));
            if (c.getStatut() != StatutCreditLotArchive.IMPORTE && c.getStatut() != StatutCreditLotArchive.IGNORE) {
                c.setStatut(statutCalcule(c, nouveau));
            }
        }
    }

    /**
     * Réapplique aux crédits déjà importés les montants et soldes recalculés depuis leur relevé :
     * composantes du crédit douanier (b, c, d, d′), solde des droits, TVA intérieure, et mise au format
     * des utilisations reprises. Aucune utilisation n'est ajoutée deux fois. Chaque crédit a sa
     * transaction ; un crédit utilisé depuis dans l'application garde ses soldes (anomalie signalée).
     */
    public ImportResultatDto recalculer(Long lotId, AuthenticatedUser user) {
        List<Long> ids = transactionParCredit.execute(tx -> lot(lotId).getCredits().stream()
                .filter(c -> c.getStatut() == StatutCreditLotArchive.IMPORTE)
                .map(LotImportArchiveCredit::getId)
                .toList());
        int faits = 0;
        int erreurs = 0;
        for (Long id : ids) {
            try {
                transactionParCredit.executeWithoutResult(tx -> {
                    LotImportArchiveCredit c = creditRepository.findById(id).orElseThrow();
                    ReleveArchiveDto releve = lire(c);
                    importService.importerReleve(releve, c.getEntreprise(), c.getAutoriteContractante(),
                            c.getConvention(), null, true, user);
                    c.setMessageErreur(releve.getAnomalies().stream()
                            .filter(a -> a.startsWith("Le crédit a déjà été utilisé"))
                            .findFirst().orElse(null));
                });
                faits++;
            } catch (RuntimeException e) {
                erreurs++;
                log.warn("Reprise d'archive : recalcul du crédit de lot {} en échec : {}", id, e.getMessage());
            }
        }
        int fFaits = faits;
        int fErreurs = erreurs;
        return transactionParCredit.execute(tx -> ImportResultatDto.builder()
                .importes(fFaits)
                .erreurs(fErreurs)
                .nonPrets(0)
                .credits(creditRepository.findAllById(ids).stream().map(c -> toDto(c, lire(c))).toList())
                .build());
    }

    /** Pré-sélections : celles du rapprochement, ou celles du certificat déjà repris. */
    private void proposer(LotImportArchiveCredit credit, ReleveArchiveDto releve, ContexteRapprochement contexte) {
        CertificatCredit dejaRepris = importService.certificatExistant(releve).orElse(null);
        credit.setDejaRepris(dejaRepris != null);
        if (dejaRepris != null) {
            credit.setCertificat(dejaRepris);
            DemandeCorrection demande = dejaRepris.getDemandeCorrection();
            credit.setEntreprise(dejaRepris.getEntreprise());
            credit.setSourceEntreprise("CREDIT_EXISTANT");
            if (demande != null) {
                credit.setAutoriteContractante(demande.getAutoriteContractante());
                credit.setSourceAutorite("CREDIT_EXISTANT");
                credit.setConvention(demande.getConvention());
                credit.setSourceConvention("CREDIT_EXISTANT");
            }
            return;
        }
        if (releve.getEntrepriseRapprocheeId() != null) {
            credit.setEntreprise(entrepriseRepository.getReferenceById(releve.getEntrepriseRapprocheeId()));
            credit.setSourceEntreprise(releve.getEntrepriseRapprocheeSource());
        }
        Marche marche = releve.getMarcheRapprocheId() != null
                ? marcheRepository.findById(releve.getMarcheRapprocheId()).orElse(null) : null;
        if (marche != null) {
            credit.setMarche(marche);
            credit.setSourceMarche("REFERENCE");
            credit.setCreerMarche(false);
        } else {
            credit.setCreerMarche(true);
        }
        if (releve.getAutoriteRapprocheeId() != null) {
            credit.setAutoriteContractante(autoriteRepository.getReferenceById(releve.getAutoriteRapprocheeId()));
            credit.setSourceAutorite(releve.getAutoriteRapprocheeSource());
        }
        if (marche != null && marche.getConvention() != null) {
            credit.setConvention(marche.getConvention());
            credit.setSourceConvention("MARCHE");
        } else {
            proposerConvention(credit, contexte);
        }
    }

    private void proposerConvention(LotImportArchiveCredit credit, ContexteRapprochement contexte) {
        Long autoriteId = credit.getAutoriteContractante() != null ? credit.getAutoriteContractante().getId() : null;
        Long conventionId = importService.conventionUnique(autoriteId, contexte);
        if (conventionId != null) {
            credit.setConvention(conventionRepository.getReferenceById(conventionId));
            credit.setSourceConvention("CONVENTION_UNIQUE");
        }
    }

    private void appliquerReleve(LotImportArchiveCredit credit, ReleveArchiveDto releve) {
        credit.setNumeroCredit(releve.getNumeroCredit());
        credit.setNif(releve.getNif());
        credit.setReferenceMarche(tronquer(releve.getReferenceMarche(), 500));
        credit.setDateCredit(releve.getDateCredit());
        credit.setMontantCreditImpot(releve.getMontantCreditImpot());
        credit.setCreditDouanier(releve.getCreditDouanier());
        credit.setCreditInterieur(releve.getCreditInterieur());
        credit.setSoldeDouanierCalcule(releve.getSoldeDouanierCalcule());
        credit.setSoldeInterieurCalcule(releve.getSoldeInterieurCalcule());
        credit.setSoldeDouanierDeclare(releve.getSoldeDouanierDeclare());
        credit.setSoldeInterieurDeclare(releve.getSoldeInterieurDeclare());
        credit.setNbUtilisations(releve.getUtilisations().size());
        credit.setNbAnomalies(releve.getAnomalies().size());
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LotDto> lister() {
        return lotRepository.findAllByOrderByDateAnalyseDesc().stream()
                .map(lot -> toDto(lot, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public LotDto detail(Long lotId) {
        return toDto(lot(lotId), true);
    }

    @Transactional(readOnly = true)
    public CreditDetailDto credit(Long lotId, Long creditId) {
        LotImportArchiveCredit credit = creditDuLot(lotId, creditId);
        ReleveArchiveDto releve = lire(credit);
        return CreditDetailDto.builder().credit(toDto(credit, releve)).releve(releve).build();
    }

    // ------------------------------------------------------------------
    // Compléments de l'opérateur
    // ------------------------------------------------------------------

    @Transactional
    public LotDto affecter(Long lotId, AffectationRequest req) {
        LotImportArchive lot = lot(lotId);
        List<LotImportArchiveCredit> cibles = cibles(lot, req);

        Entreprise entreprise = req.getEntrepriseId() == null ? null
                : entrepriseRepository.findById(req.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Entreprise non trouvée : " + req.getEntrepriseId()));
        AutoriteContractante autorite = req.getAutoriteContractanteId() == null ? null
                : autoriteRepository.findById(req.getAutoriteContractanteId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Autorité contractante non trouvée : " + req.getAutoriteContractanteId()));
        Convention convention = req.getConventionId() == null ? null
                : conventionRepository.findById(req.getConventionId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Convention non trouvée : " + req.getConventionId()));
        Marche marche = req.getMarcheId() == null ? null
                : marcheRepository.findById(req.getMarcheId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Marché non trouvé : " + req.getMarcheId()));

        ContexteRapprochement contexte = autorite != null && convention == null
                ? importService.contexteRapprochement() : null;

        if (req.getNumeroCredit() != null || req.getDateCredit() != null) {
            if (cibles.size() != 1) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le N° et la date du crédit se saisissent crédit par crédit.");
            }
            corrigerIdentite(cibles.get(0), req);
        }

        for (LotImportArchiveCredit c : cibles) {
            if (entreprise != null) {
                c.setEntreprise(entreprise);
                c.setSourceEntreprise("MANUEL");
            }
            if (autorite != null) {
                boolean changement = c.getAutoriteContractante() == null
                        || !autorite.getId().equals(c.getAutoriteContractante().getId());
                c.setAutoriteContractante(autorite);
                c.setSourceAutorite("MANUEL");
                // Une convention qui n'est pas celle de la nouvelle autorité n'a plus de sens.
                if (changement && convention == null && !conventionLiee(c.getConvention(), autorite)) {
                    c.setConvention(null);
                    c.setSourceConvention(null);
                    proposerConvention(c, contexte);
                }
            }
            if (convention != null) {
                c.setConvention(convention);
                c.setSourceConvention("MANUEL");
            }
            if (marche != null) {
                c.setMarche(marche);
                c.setSourceMarche("MANUEL");
                c.setCreerMarche(false);
            }
            if (Boolean.TRUE.equals(req.getCreerMarche())) {
                c.setMarche(null);
                c.setSourceMarche(null);
                c.setCreerMarche(true);
            }
            if (req.getConfirmerMalgreAnomalies() != null) {
                c.setConfirmerMalgreAnomalies(req.getConfirmerMalgreAnomalies());
            }
            if (Boolean.TRUE.equals(req.getIgnorer())) {
                c.setStatut(StatutCreditLotArchive.IGNORE);
            } else if (Boolean.FALSE.equals(req.getIgnorer()) || c.getStatut() != StatutCreditLotArchive.IGNORE) {
                c.setStatut(statutCalcule(c, lire(c)));
            }
        }
        return toDto(lot, true);
    }

    /**
     * N° et date du crédit, quand le bloc ne les donne pas : ils sont reportés dans le relevé gardé,
     * qui est celui importé. Le numéro décide aussi si le crédit a déjà été repris.
     */
    private void corrigerIdentite(LotImportArchiveCredit c, AffectationRequest req) {
        ReleveArchiveDto releve = lire(c);
        if (req.getNumeroCredit() != null && !req.getNumeroCredit().isBlank()) {
            releve.setNumeroCredit(req.getNumeroCredit().trim());
            c.setNumeroCredit(releve.getNumeroCredit());
            CertificatCredit existant = importService.certificatExistant(releve).orElse(null);
            c.setDejaRepris(existant != null);
            c.setCertificat(existant);
        }
        if (req.getDateCredit() != null) {
            releve.setDateCredit(req.getDateCredit().atStartOfDay(ZoneOffset.UTC).toInstant());
            c.setDateCredit(releve.getDateCredit());
        }
        c.setReleveJson(ecrire(releve));
    }

    private boolean conventionLiee(Convention convention, AutoriteContractante autorite) {
        return convention != null && convention.autoritesLiees().stream()
                .anyMatch(ac -> autorite.getId().equals(ac.getId()));
    }

    /** Crédits visés : la liste donnée, ou ceux d'un NIF ; jamais un crédit déjà importé. */
    private List<LotImportArchiveCredit> cibles(LotImportArchive lot, AffectationRequest req) {
        List<LotImportArchiveCredit> cibles;
        if (req.getCreditIds() != null && !req.getCreditIds().isEmpty()) {
            Set<Long> ids = new HashSet<>(req.getCreditIds());
            cibles = lot.getCredits().stream().filter(c -> ids.contains(c.getId())).toList();
        } else if (req.getNif() != null && !req.getNif().isBlank()) {
            String nif = ImportArchiveCreditService.normaliserNif(req.getNif());
            cibles = lot.getCredits().stream()
                    .filter(c -> nif != null && nif.equals(ImportArchiveCreditService.normaliserNif(c.getNif())))
                    .toList();
        } else {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Indiquez les crédits (creditIds) ou le NIF visés.");
        }
        return cibles.stream().filter(c -> c.getStatut() != StatutCreditLotArchive.IMPORTE).toList();
    }

    /**
     * Relance les rapprochements, par exemple après la création d'entreprises ou de marchés : seuls
     * les choix encore vides sont complétés, ceux de l'opérateur restent.
     */
    @Transactional
    public LotDto rapprocher(Long lotId) {
        LotImportArchive lot = lot(lotId);
        ContexteRapprochement contexte = importService.contexteRapprochement();
        for (LotImportArchiveCredit c : lot.getCredits()) {
            if (c.getStatut() == StatutCreditLotArchive.IMPORTE) {
                continue;
            }
            ReleveArchiveDto releve = lire(c);
            releve.setEntrepriseRapprocheeId(null);
            releve.setMarcheRapprocheId(null);
            releve.setAutoriteRapprocheeId(null);
            importService.rapprocher(releve, contexte);
            if (c.getEntreprise() == null && releve.getEntrepriseRapprocheeId() != null) {
                c.setEntreprise(entrepriseRepository.getReferenceById(releve.getEntrepriseRapprocheeId()));
                c.setSourceEntreprise(releve.getEntrepriseRapprocheeSource());
            }
            if (c.getMarche() == null && releve.getMarcheRapprocheId() != null) {
                c.setMarche(marcheRepository.getReferenceById(releve.getMarcheRapprocheId()));
                c.setSourceMarche("REFERENCE");
                c.setCreerMarche(false);
            }
            if (c.getAutoriteContractante() == null && releve.getAutoriteRapprocheeId() != null) {
                c.setAutoriteContractante(autoriteRepository.getReferenceById(releve.getAutoriteRapprocheeId()));
                c.setSourceAutorite(releve.getAutoriteRapprocheeSource());
            }
            if (c.getConvention() == null) {
                proposerConvention(c, contexte);
            }
            if (c.getStatut() != StatutCreditLotArchive.IGNORE) {
                c.setStatut(statutCalcule(c, releve));
            }
        }
        return toDto(lot, true);
    }

    /**
     * Interroge la DGI pour les NIF encore sans entreprise, {@value #NIF_DGI_PAR_APPEL} par appel ;
     * le lot rendu dit combien il en reste. Si la DGI ne répond pas dès le premier NIF, l'erreur est
     * remontée ; une panne en cours de route garde ce qui a déjà été obtenu.
     */
    @Transactional
    public LotDto interrogerDgi(Long lotId) {
        LotImportArchive lot = lot(lotId);
        Map<String, List<LotImportArchiveCredit>> parNif = nifsAInterroger(lot);
        int faits = 0;
        for (Map.Entry<String, List<LotImportArchiveCredit>> e : parNif.entrySet()) {
            if (faits >= NIF_DGI_PAR_APPEL) {
                break;
            }
            VerificationNifDto reponse;
            try {
                reponse = dgi.verifier(e.getValue().get(0).getNif());
            } catch (ApiException ex) {
                if (faits == 0) {
                    throw ex;
                }
                log.warn("Reprise d'archive : interrogation DGI interrompue après {} NIF : {}", faits, ex.getMessage());
                break;
            }
            for (LotImportArchiveCredit c : e.getValue()) {
                c.setInconnuDgi(!reponse.isTrouve());
                c.setRaisonSocialeDgi(reponse.getRaisonSociale());
                c.setNomCommercialDgi(reponse.getNomCommercial());
                c.setEtatDgi(reponse.getEtat());
            }
            faits++;
        }
        return toDto(lot, true);
    }

    /** NIF sans entreprise, jamais interrogés, parmi les crédits à traiter. */
    private Map<String, List<LotImportArchiveCredit>> nifsAInterroger(LotImportArchive lot) {
        Map<String, List<LotImportArchiveCredit>> parNif = new LinkedHashMap<>();
        for (LotImportArchiveCredit c : lot.getCredits()) {
            String nif = ImportArchiveCreditService.normaliserNif(c.getNif());
            if (nif == null || c.getEntreprise() != null || c.getInconnuDgi() != null
                    || c.getStatut() == StatutCreditLotArchive.IMPORTE
                    || c.getStatut() == StatutCreditLotArchive.IGNORE) {
                continue;
            }
            parNif.computeIfAbsent(nif, k -> new ArrayList<>()).add(c);
        }
        return parNif;
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    /**
     * Importe les crédits prêts demandés (tous ceux du lot si la liste est vide), chacun dans sa propre
     * transaction. Volontairement non transactionnelle : un échec ne doit pas annuler les crédits
     * déjà importés.
     */
    public ImportResultatDto importer(Long lotId, List<Long> creditIds, AuthenticatedUser user) {
        List<Long> ids = transactionParCredit.execute(tx -> {
            LotImportArchive lot = lot(lotId);
            Set<Long> demandes = creditIds == null || creditIds.isEmpty() ? null : new HashSet<>(creditIds);
            return lot.getCredits().stream()
                    .filter(c -> demandes == null || demandes.contains(c.getId()))
                    .map(LotImportArchiveCredit::getId)
                    .toList();
        });

        int importes = 0;
        int erreurs = 0;
        int nonPrets = 0;
        List<Long> traites = new ArrayList<>();
        for (Long id : ids) {
            Boolean pret = transactionParCredit.execute(tx -> {
                LotImportArchiveCredit c = creditRepository.findById(id).orElseThrow();
                return c.getStatut() == StatutCreditLotArchive.PRET || c.getStatut() == StatutCreditLotArchive.ERREUR
                        && statutCalcule(c, lire(c)) == StatutCreditLotArchive.PRET;
            });
            if (!Boolean.TRUE.equals(pret)) {
                nonPrets++;
                continue;
            }
            try {
                transactionParCredit.executeWithoutResult(tx -> importerCredit(id, user));
                importes++;
            } catch (RuntimeException e) {
                erreurs++;
                String message = e instanceof ApiException api ? api.getMessage()
                        : e.getClass().getSimpleName() + (e.getMessage() != null ? " : " + e.getMessage() : "");
                log.warn("Reprise d'archive : crédit de lot {} en erreur : {}", id, message);
                transactionParCredit.executeWithoutResult(tx -> {
                    LotImportArchiveCredit c = creditRepository.findById(id).orElseThrow();
                    c.setStatut(StatutCreditLotArchive.ERREUR);
                    c.setMessageErreur(tronquer(message, 2000));
                });
            }
            traites.add(id);
        }

        int fImportes = importes;
        int fErreurs = erreurs;
        int fNonPrets = nonPrets;
        return transactionParCredit.execute(tx -> ImportResultatDto.builder()
                .importes(fImportes)
                .erreurs(fErreurs)
                .nonPrets(fNonPrets)
                .credits(creditRepository.findAllById(traites).stream()
                        .map(c -> toDto(c, lire(c)))
                        .toList())
                .build());
    }

    private void importerCredit(Long creditId, AuthenticatedUser user) {
        LotImportArchiveCredit c = creditRepository.findById(creditId).orElseThrow();
        ReleveArchiveDto releve = lire(c);
        Entreprise entreprise = c.getEntreprise();
        AutoriteContractante autorite = c.getAutoriteContractante();
        Convention convention = c.getConvention();

        // Un crédit dont la référence ARCHIVE-<N° Credit> existe déjà est refusé.
        importService.refuserSiDejaRepris(releve);
        Marche marche = null;
        if (c.getMarche() != null) {
            marche = c.getMarche();
            if (marche.getDemandeCorrection() != null) {
                // Un marché ne porte qu'un dossier : le crédit est repris sans ce rattachement.
                releve.getAnomalies().add("Le marché « " + marche.getNumeroMarche()
                        + " » est déjà rattaché à un autre dossier : crédit repris sans rattachement au marché.");
                marche = null;
            }
        } else if (Boolean.TRUE.equals(c.getCreerMarche())) {
            marche = importService.creerMarcheDepuisReleve(releve, autorite, convention);
            c.setMarche(marche);
            c.setSourceMarche("CREE");
        }

        ImportArchiveResultatDto resultat = importService.importerReleve(releve, entreprise, autorite, convention,
                marche, true, user);

        c.setCertificat(entityCertificat(resultat.getCertificatId()));
        c.setDejaRepris(resultat.isCertificatDejaExistant());
        c.setStatut(StatutCreditLotArchive.IMPORTE);
        c.setMessageErreur(null);
        c.setDateImport(Instant.now());
        c.setReleveJson(ecrire(releve));
        c.setNbAnomalies(releve.getAnomalies().size());
    }

    private CertificatCredit entityCertificat(Long id) {
        return id == null ? null : certificatRepository.getReferenceById(id);
    }

    @Transactional
    public void supprimer(Long lotId) {
        lotRepository.delete(lot(lotId));
    }

    // ------------------------------------------------------------------
    // Règle « prêt » et conversions
    // ------------------------------------------------------------------

    private StatutCreditLotArchive statutCalcule(LotImportArchiveCredit c, ReleveArchiveDto releve) {
        if (c.getStatut() == StatutCreditLotArchive.IMPORTE) {
            return StatutCreditLotArchive.IMPORTE;
        }
        return manquants(c, releve).isEmpty() ? StatutCreditLotArchive.PRET : StatutCreditLotArchive.A_COMPLETER;
    }

    private List<String> manquants(LotImportArchiveCredit c, ReleveArchiveDto releve) {
        List<String> manquants = new ArrayList<>();
        if (releve.getNumeroCredit() == null || releve.getNumeroCredit().isBlank()) {
            manquants.add("NUMERO");
        }
        if (releve.getDateCredit() == null) {
            manquants.add("DATE");
        }
        if (c.getEntreprise() == null) {
            manquants.add("ENTREPRISE");
        }
        if (c.getAutoriteContractante() == null) {
            manquants.add("AUTORITE");
        }
        if (c.getConvention() == null) {
            manquants.add("CONVENTION");
        }
        if (!Boolean.TRUE.equals(c.getDejaRepris()) && c.getMarche() == null && !Boolean.TRUE.equals(c.getCreerMarche())) {
            manquants.add("MARCHE");
        }
        if (!releve.getAnomalies().isEmpty() && !Boolean.TRUE.equals(c.getConfirmerMalgreAnomalies())) {
            manquants.add("ANOMALIES");
        }
        return manquants;
    }

    private LotDto toDto(LotImportArchive lot, boolean avecCredits) {
        List<LotImportArchiveCredit> credits = lot.getCredits();
        Map<String, Long> parStatut = new LinkedHashMap<>();
        for (StatutCreditLotArchive s : StatutCreditLotArchive.values()) {
            parStatut.put(s.name(), 0L);
        }
        credits.forEach(c -> parStatut.merge(c.getStatut().name(), 1L, Long::sum));
        Set<String> nifsSansEntreprise = new LinkedHashSet<>();
        for (LotImportArchiveCredit c : credits) {
            if (c.getEntreprise() == null && c.getStatut() != StatutCreditLotArchive.IMPORTE
                    && c.getStatut() != StatutCreditLotArchive.IGNORE) {
                String nif = ImportArchiveCreditService.normaliserNif(c.getNif());
                if (nif != null) {
                    nifsSansEntreprise.add(nif);
                }
            }
        }
        return LotDto.builder()
                .id(lot.getId())
                .nomFichier(lot.getNomFichier())
                .dateAnalyse(lot.getDateAnalyse())
                .analyseParNom(lot.getAnalyseParNom())
                .nbCredits(credits.size())
                .parStatut(parStatut)
                .nifsSansEntreprise(nifsSansEntreprise.size())
                .nifsAInterrogerDgi(nifsAInterroger(lot).size())
                .credits(avecCredits ? credits.stream().map(c -> toDto(c, lire(c))).toList() : null)
                .build();
    }

    private CreditDto toDto(LotImportArchiveCredit c, ReleveArchiveDto releve) {
        Entreprise e = c.getEntreprise();
        AutoriteContractante ac = c.getAutoriteContractante();
        Convention conv = c.getConvention();
        Marche m = c.getMarche();
        CertificatCredit cert = c.getCertificat();
        return CreditDto.builder()
                .id(c.getId())
                .ordre(c.getOrdre())
                .ligneDebut(c.getLigneDebut())
                .ligneFin(c.getLigneFin())
                .numeroCredit(c.getNumeroCredit())
                .nif(c.getNif())
                .referenceMarche(releve.getReferenceMarche())
                .numeroMarche(releve.getNumeroMarche())
                .dateMarche(releve.getDateMarche())
                .dureeMarche(releve.getDureeMarche())
                .dateCredit(c.getDateCredit())
                .montantMarche(releve.getMontantMarche())
                .montantCreditImpot(c.getMontantCreditImpot())
                .creditDouanier(c.getCreditDouanier())
                .creditInterieur(c.getCreditInterieur())
                .transfertCredit(releve.getTransfertCredit())
                .totalUtilisationsDouane(releve.getTotalUtilisationsDouane())
                .totalUtilisationsInterieur(releve.getTotalUtilisationsInterieur())
                .sortieTransfert(releve.getTotalTransfertSortant() != null
                        ? releve.getTotalTransfertSortant() : releve.getTransfertCredit())
                .soldeDouanierCalcule(c.getSoldeDouanierCalcule())
                .soldeInterieurCalcule(c.getSoldeInterieurCalcule())
                .soldeInterieurRegleFichier(releve.getSoldeInterieurRegleFichier())
                .stockTvaEntrees(releve.getStockTvaEntrees())
                .stockTvaDeduit(releve.getStockTvaDeduit())
                .stockTvaRestant(releve.getStockTvaRestant())
                .paiementEntrepriseTotal(releve.getPaiementEntrepriseTotal())
                .reportsTotal(releve.getReportsTotal())
                .soldeDouanierDeclare(c.getSoldeDouanierDeclare())
                .soldeInterieurDeclare(c.getSoldeInterieurDeclare())
                .nbUtilisations(c.getNbUtilisations() != null ? c.getNbUtilisations() : 0)
                .anomalies(releve.getAnomalies())
                .entrepriseId(e != null ? e.getId() : null)
                .entrepriseRaisonSociale(e != null ? e.getRaisonSociale() : null)
                .sourceEntreprise(c.getSourceEntreprise())
                .autoriteContractanteId(ac != null ? ac.getId() : null)
                .autoriteContractanteNom(ac != null ? ac.getNom() : null)
                .sourceAutorite(c.getSourceAutorite())
                .conventionId(conv != null ? conv.getId() : null)
                .conventionIntitule(conv != null ? (conv.getIntitule() != null ? conv.getIntitule() : conv.getReference()) : null)
                .sourceConvention(c.getSourceConvention())
                .marcheId(m != null ? m.getId() : null)
                .marcheNumero(m != null ? m.getNumeroMarche() : null)
                .sourceMarche(c.getSourceMarche())
                .creerMarche(Boolean.TRUE.equals(c.getCreerMarche()))
                .confirmerMalgreAnomalies(Boolean.TRUE.equals(c.getConfirmerMalgreAnomalies()))
                .raisonSocialeDgi(c.getRaisonSocialeDgi())
                .nomCommercialDgi(c.getNomCommercialDgi())
                .etatDgi(c.getEtatDgi())
                .inconnuDgi(Boolean.TRUE.equals(c.getInconnuDgi()))
                .dejaRepris(Boolean.TRUE.equals(c.getDejaRepris()))
                .statut(c.getStatut().name())
                .manquants(c.getStatut() == StatutCreditLotArchive.IMPORTE ? List.of() : manquants(c, releve))
                .certificatId(cert != null ? cert.getId() : null)
                .certificatReference(cert != null ? (cert.getReference() != null ? cert.getReference() : cert.getNumero()) : null)
                .messageErreur(c.getMessageErreur())
                .dateImport(c.getDateImport())
                .build();
    }

    private LotImportArchive lot(Long lotId) {
        return lotRepository.findById(lotId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Lot d'import non trouvé : " + lotId));
    }

    private LotImportArchiveCredit creditDuLot(Long lotId, Long creditId) {
        LotImportArchiveCredit credit = creditRepository.findById(creditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Crédit de lot non trouvé : " + creditId));
        if (!credit.getLot().getId().equals(lotId)) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Crédit de lot non trouvé : " + creditId);
        }
        return credit;
    }

    private ReleveArchiveDto lire(LotImportArchiveCredit c) {
        try {
            return objectMapper.readValue(c.getReleveJson(), ReleveArchiveDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Relevé du crédit de lot " + c.getId() + " illisible", e);
        }
    }

    private String ecrire(ReleveArchiveDto releve) {
        try {
            return objectMapper.writeValueAsString(releve);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Relevé impossible à enregistrer", e);
        }
    }

    private static String empreinte(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est obligatoire");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fichier.getBytes()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Fichier illisible : " + e.getMessage());
        }
    }

    private static String tronquer(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
