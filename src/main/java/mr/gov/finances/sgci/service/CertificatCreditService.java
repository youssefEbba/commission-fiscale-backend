package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LettreCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentCertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.LettreCorrectionRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.AdminCorrectionCertificatRequest;
import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CertificatCreditFicheDto;
import mr.gov.finances.sgci.web.dto.CertificatCreditJournalDto;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEligibilityDto;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.GroupementDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.PageResponse;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificatCreditService {

    /** Tolérance MRU sur les totaux récap. (arrondis manuels). */
    private static final BigDecimal RECAP_TOLERANCE_MRU = new BigDecimal("1");

    private final CertificatCreditRepository repository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final LettreCorrectionRepository lettreCorrectionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final UtilisationCreditService utilisationCreditService;
    private final DocumentCertificatCreditRepository documentCertificatCreditRepository;
    private final DecisionCertificatCreditRepository decisionCertificatCreditRepository;
    private final TvaDeductibleStockRepository tvaDeductibleStockRepository;
    private final CertificatCreditWorkflow workflow;
    private final AuditService auditService;
    private final WorkflowNotificationHelper workflowNotificationHelper;
    private final DocumentCertificatCreditService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final DossierGedService dossierGedService;
    private final EffectiveIdentityService effectiveIdentityService;
    private final UtilisationCreditEligibilityHelper utilisationEligibilityHelper;
    private final VisaRequirementResolver visaRequirementResolver;
    private final ReferenceSequenceGenerator referenceSequenceGenerator;
    private final GroupementService groupementService;

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findAll(AuthenticatedUser user) {
        List<CertificatCredit> list = resolveCertificatList(user, null);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificatCreditDto findById(Long id, AuthenticatedUser user) {
        CertificatCredit c = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user != null && !canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public CertificatUtilisationEligibilityDto evaluateUtilisationEligibility(
            Long certificatId, TypeUtilisation type, AuthenticatedUser user) {
        if (type == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le type d'utilisation est obligatoire");
        }
        CertificatCredit c = repository.findById(certificatId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + certificatId));
        if (user != null && !canAccessCertificat(certificatId, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
        return utilisationEligibilityHelper.evaluate(c, type, null);
    }

    /** Statuts considérés comme « mis en place » pour le journal des crédits. */
    private static final Set<StatutCertificat> STATUTS_MIS_EN_PLACE = EnumSet.of(
            StatutCertificat.OUVERT, StatutCertificat.MODIFIE, StatutCertificat.CLOTURE);

    /**
     * Recherche multi-critères des crédits d'impôt, dans le périmètre du rôle appelant, paginée.
     * Tous les critères sont optionnels et combinés en ET. Les critères texte sont insensibles à la casse (contient).
     */
    @Transactional(readOnly = true)
    public PageResponse<CertificatCreditDto> search(String nif, String numeroMarche, String conventionRef,
                                                    String projet, Long autoriteContractanteId,
                                                    StatutCertificat statut, Instant from, Instant to,
                                                    int page, int size, AuthenticatedUser user) {
        List<CertificatCredit> scoped = resolveCertificatList(user, null);
        List<CertificatCreditDto> filtered = scoped.stream()
                .filter(c -> matchesText(nif, c.getEntreprise() != null ? c.getEntreprise().getNif() : null))
                .filter(c -> matchesText(numeroMarche, marcheNumero(c)))
                .filter(c -> matchesText(conventionRef, conventionReference(c)))
                .filter(c -> matchesText(projet, conventionProjet(c)))
                .filter(c -> autoriteContractanteId == null
                        || autoriteContractanteId.equals(autoriteContractanteId(c)))
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> withinPeriod(c.getDateEmission(), from, to))
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageResponse.of(filtered, page, size);
    }

    /**
     * Journal daté des crédits mis en place (statut OUVERT / MODIFIE / CLOTURE) sur une période, paginé,
     * avec agrégats financiers. Le filtre porte sur {@code dateMiseEnPlace} (fallback {@code dateEmission}).
     */
    @Transactional(readOnly = true)
    public CertificatCreditJournalDto journal(Instant from, Instant to, int page, int size, AuthenticatedUser user) {
        List<CertificatCredit> scoped = resolveCertificatList(user, null);
        List<CertificatCredit> misEnPlace = scoped.stream()
                .filter(c -> STATUTS_MIS_EN_PLACE.contains(c.getStatut()))
                .filter(c -> withinPeriod(journalDate(c), from, to))
                .sorted(Comparator.comparing(this::journalDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        BigDecimal totalCordon = BigDecimal.ZERO;
        BigDecimal totalTVA = BigDecimal.ZERO;
        BigDecimal totalSoldeCordon = BigDecimal.ZERO;
        BigDecimal totalSoldeTVA = BigDecimal.ZERO;
        for (CertificatCredit c : misEnPlace) {
            totalCordon = totalCordon.add(nz(c.getMontantCordon()));
            totalTVA = totalTVA.add(nz(c.getMontantTVAInterieure()));
            totalSoldeCordon = totalSoldeCordon.add(nz(c.getSoldeCordon()));
            totalSoldeTVA = totalSoldeTVA.add(nz(c.getSoldeTVA()));
        }

        List<CertificatCreditDto> dtos = misEnPlace.stream().map(this::toDto).collect(Collectors.toList());
        return CertificatCreditJournalDto.builder()
                .from(from)
                .to(to)
                .certificats(PageResponse.of(dtos, page, size))
                .nombreCredits(misEnPlace.size())
                .totalMontantCordon(totalCordon)
                .totalMontantTVAInterieure(totalTVA)
                .totalSoldeCordon(totalSoldeCordon)
                .totalSoldeTVA(totalSoldeTVA)
                .build();
    }

    /** Fiche consolidée d'un crédit d'impôt par sa référence lisible (ex. CR-01/2025). */
    @Transactional(readOnly = true)
    public CertificatCreditFicheDto getFicheByReference(String reference, AuthenticatedUser user) {
        CertificatCredit c = repository.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Certificat de crédit non trouvé pour la référence: " + reference));
        if (user != null && !canAccessCertificat(c.getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
        return buildFiche(c, user);
    }

    private CertificatCreditFicheDto buildFiche(CertificatCredit c, AuthenticatedUser user) {
        DemandeCorrection demande = c.getDemandeCorrection();
        mr.gov.finances.sgci.domain.entity.Convention convention = demande != null ? demande.getConvention() : null;
        mr.gov.finances.sgci.domain.entity.Marche marche = demande != null ? demande.getMarche() : null;
        mr.gov.finances.sgci.domain.entity.AutoriteContractante autorite = demande != null
                ? demande.getAutoriteContractante() : null;

        GroupementDto groupementDto = null;
        if (demande != null && demande.getGroupement() != null && demande.getGroupement().getId() != null) {
            groupementDto = groupementService.findById(demande.getGroupement().getId());
        }
        return CertificatCreditFicheDto.builder()
                .certificat(toDto(c))
                .entreprise(toEntrepriseFicheDto(c.getEntreprise()))
                .groupement(groupementDto)
                .convention(toConventionFicheDto(convention))
                .marche(toMarcheFicheDto(marche))
                .autoriteContractante(toAutoriteFicheDto(autorite))
                .intituleMarche(demande != null ? demande.getIntituleMarche() : null)
                .documents(documentService.findByCertificatCreditId(c.getId()))
                .utilisations(utilisationCreditService.findByCertificatCreditId(c.getId(), user))
                .tvaStock(utilisationCreditService.findTvaStockByCertificat(c.getId()))
                .build();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static boolean matchesText(String criterion, String value) {
        if (criterion == null || criterion.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase().contains(criterion.toLowerCase());
    }

    private static boolean withinPeriod(Instant date, Instant from, Instant to) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private Instant journalDate(CertificatCredit c) {
        return c.getDateMiseEnPlace() != null ? c.getDateMiseEnPlace() : c.getDateEmission();
    }

    private static String marcheNumero(CertificatCredit c) {
        return c.getDemandeCorrection() != null && c.getDemandeCorrection().getMarche() != null
                ? c.getDemandeCorrection().getMarche().getNumeroMarche() : null;
    }

    private static String conventionReference(CertificatCredit c) {
        return c.getDemandeCorrection() != null && c.getDemandeCorrection().getConvention() != null
                ? c.getDemandeCorrection().getConvention().getReference() : null;
    }

    private static String conventionProjet(CertificatCredit c) {
        return c.getDemandeCorrection() != null && c.getDemandeCorrection().getConvention() != null
                ? c.getDemandeCorrection().getConvention().getProjectReference() : null;
    }

    private static Long autoriteContractanteId(CertificatCredit c) {
        return c.getDemandeCorrection() != null && c.getDemandeCorrection().getAutoriteContractante() != null
                ? c.getDemandeCorrection().getAutoriteContractante().getId() : null;
    }

    private EntrepriseDto toEntrepriseFicheDto(Entreprise e) {
        if (e == null) {
            return null;
        }
        return EntrepriseDto.builder()
                .id(e.getId())
                .raisonSociale(e.getRaisonSociale())
                .nomCommercial(e.getNomCommercial())
                .activite(e.getActivite())
                .autre(e.getAutre())
                .nif(e.getNif())
                .adresse(e.getAdresse())
                .situationFiscale(e.getSituationFiscale())
                .entrepriseEtrangere(e.isEntrepriseEtrangere())
                .registreCommerceEtranger(e.getRegistreCommerceEtranger())
                .build();
    }

    private ConventionDto toConventionFicheDto(mr.gov.finances.sgci.domain.entity.Convention convention) {
        if (convention == null) {
            return null;
        }
        return ConventionDto.builder()
                .id(convention.getId())
                .reference(convention.getReference())
                .projectReference(convention.getProjectReference())
                .intitule(convention.getIntitule())
                .bailleurId(convention.getBailleur() != null ? convention.getBailleur().getId() : null)
                .bailleurNom(convention.getBailleur() != null ? convention.getBailleur().getNom() : null)
                .dateSignature(convention.getDateSignature())
                .dateFin(convention.getDateFin())
                .montantDevise(convention.getMontantDevise())
                .montantMru(convention.getMontantMru())
                .deviseOrigine(convention.getDeviseOrigine())
                .tauxChange(convention.getTauxChange())
                .statut(convention.getStatut())
                .autoriteContractanteId(convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getNom() : null)
                .dateCreation(convention.getDateCreation())
                .build();
    }

    private MarcheDto toMarcheFicheDto(mr.gov.finances.sgci.domain.entity.Marche marche) {
        if (marche == null) {
            return null;
        }
        return MarcheDto.builder()
                .id(marche.getId())
                .conventionId(marche.getConvention() != null ? marche.getConvention().getId() : null)
                .demandeCorrectionId(marche.getDemandeCorrection() != null ? marche.getDemandeCorrection().getId() : null)
                .numeroMarche(marche.getNumeroMarche())
                .reference(marche.getReference())
                .intitule(marche.getIntitule())
                .dateSignature(marche.getDateSignature())
                .montantContratHt(marche.getMontantContratHt())
                .statut(marche.getStatut())
                .build();
    }

    private AutoriteContractanteDto toAutoriteFicheDto(mr.gov.finances.sgci.domain.entity.AutoriteContractante a) {
        if (a == null) {
            return null;
        }
        return AutoriteContractanteDto.builder()
                .id(a.getId())
                .nom(a.getNom())
                .code(a.getCode())
                .contact(a.getContact())
                .ministereTutelleNom(a.getMinistereTutelleNom())
                .ministereTutelleCode(a.getMinistereTutelleCode())
                .build();
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByEntreprise(Long entrepriseId) {
        return repository.findByEntrepriseIdOrderByDateEmissionDescIdDesc(entrepriseId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByStatut(StatutCertificat statut, AuthenticatedUser user) {
        List<CertificatCredit> list = resolveCertificatList(user, statut);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    private List<CertificatCredit> resolveCertificatList(AuthenticatedUser user, StatutCertificat statut) {
        if (user == null || user.getUserId() == null) {
            if (statut == null) {
                return repository.findAllByOrderByDateEmissionDescIdDesc();
            }
            return repository.findByStatutOrderByDateEmissionDescIdDesc(statut);
        }

        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role role = user.getRole();

        List<CertificatCredit> base;
        if (role == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            base = repository.findAllByAutoriteContractanteId(acId);
        } else if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
            base = repository.findAllByDelegueId(u.getId());
        } else if (role == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
            }
            base = repository.findByEntrepriseIdOrderByDateEmissionDescIdDesc(entId);
        } else {
            if (statut == null) {
                return repository.findAllByOrderByDateEmissionDescIdDesc();
            }
            return repository.findByStatutOrderByDateEmissionDescIdDesc(statut);
        }

        if (statut == null) {
            return base;
        }
        return base.stream().filter(c -> c.getStatut() == statut).collect(Collectors.toList());
    }

    /** Accessible aux services voisins : le périmètre d'un certificat est la même règle partout. */
    public boolean canAccessCertificat(Long certificatId, AuthenticatedUser user) {
        if (certificatId == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || user.getRole() == null) {
            return false;
        }

        if (user.getRole() == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                return false;
            }
            return repository.findById(certificatId)
                    .map(c -> c.getDemandeCorrection() != null
                            && c.getDemandeCorrection().getAutoriteContractante() != null
                            && c.getDemandeCorrection().getAutoriteContractante().getId().equals(acId))
                    .orElse(false);
        }

        if (user.getRole() == Role.AUTORITE_UPM || user.getRole() == Role.AUTORITE_UEP) {
            return repository.existsAccessByDelegue(u.getId(), certificatId);
        }

        if (user.getRole() == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                return false;
            }
            return repository.findById(certificatId)
                    .map(c -> c.getEntreprise() != null
                            && c.getEntreprise().getId().equals(entId))
                    .orElse(false);
        }

        return true;
    }

    /**
     * Au plus un certificat non {@link StatutCertificat#ANNULE} par demande de correction.
     *
     * @param excludeCertificatId certificat courant à exclure (brouillon existant), ou {@code null} à la création
     */
    private void assertAtMostOneActiveCertificatPourDemande(Long demandeCorrectionId, Long excludeCertificatId) {
        if (demandeCorrectionId == null) {
            return;
        }
        long autres;
        if (excludeCertificatId == null) {
            autres = repository.countByDemandeCorrectionIdAndStatutNot(demandeCorrectionId, StatutCertificat.ANNULE);
        } else {
            autres = repository.countByDemandeCorrectionIdAndStatutNotAndIdNot(
                    demandeCorrectionId, StatutCertificat.ANNULE, excludeCertificatId);
        }
        if (autres > 0) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Un certificat de crédit actif existe déjà pour cette demande de correction");
        }
    }

    /** Mise à jour du contenu (PUT) : réservée aux déposants, pas aux acteurs de file. */
    private void assertDeposantPeutModifierDemandeMiseEnPlace(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        Role r = user.getRole();
        if (r != Role.AUTORITE_CONTRACTANTE && r != Role.AUTORITE_UPM && r != Role.AUTORITE_UEP && r != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Modification réservée au déposant (autorité contractante, délégué ou entreprise)");
        }
    }

    @Transactional
    public CertificatCreditDto create(CreateCertificatCreditRequest request) {
        boolean brouillon = Boolean.TRUE.equals(request.getBrouillon());
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        if (!brouillon && request.getLettreCorrectionId() == null && request.getDemandeCorrectionId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La demande de correction ou la lettre de correction est obligatoire pour la mise en place du crédit d'impôt");
        }

        LettreCorrection lettreCorrection = null;
        DemandeCorrection demandeCorrection = null;

        if (request.getLettreCorrectionId() != null) {
            lettreCorrection = lettreCorrectionRepository.findById(request.getLettreCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Lettre de correction non trouvée: " + request.getLettreCorrectionId()));
            demandeCorrection = lettreCorrection.getFeuilleEvaluation() != null
                    ? lettreCorrection.getFeuilleEvaluation().getDemandeCorrection()
                    : null;
        } else if (request.getDemandeCorrectionId() != null) {
            demandeCorrection = demandeCorrectionRepository.findById(request.getDemandeCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + request.getDemandeCorrectionId()));
        }

        if (!brouillon) {
            assertMiseEnPlaceTrigger(lettreCorrection, demandeCorrection);
        }

        if (demandeCorrection != null && demandeCorrection.getId() != null) {
            assertAtMostOneActiveCertificatPourDemande(demandeCorrection.getId(), null);
        }

        if (!brouillon && demandeCorrection != null && demandeCorrection.getEntreprise() != null
                && demandeCorrection.getEntreprise().getId() != null
                && !demandeCorrection.getEntreprise().getId().equals(entreprise.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Incohérence: l'entreprise de la demande de correction ne correspond pas à l'entreprise du certificat");
        }

        String numero = "CERT-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // soldeCordon = droits hors TVA seulement (b), pas b+d : la TVA est suivie séparément dans tvaImportationDouane
        BigDecimal defaultSolde = request.getDroitsEtTaxesDouaneHorsTva() != null
                ? request.getDroitsEtTaxesDouaneHorsTva()
                : request.getMontantCordon();
        BigDecimal soldeCordon = request.getSoldeCordon() != null ? request.getSoldeCordon() : defaultSolde;
        BigDecimal soldeTVA = request.getSoldeTVA() != null ? request.getSoldeTVA() : request.getMontantTVAInterieure();
        StatutCertificat initialStatut = brouillon ? StatutCertificat.BROUILLON : StatutCertificat.ENVOYEE;
        CertificatCredit entity = CertificatCredit.builder()
                .numero(numero)
                .reference(referenceSequenceGenerator.next(ReferenceSequenceGenerator.PREFIX_CERTIFICAT))
                .dateEmission(Instant.now())
                .dateValidite(request.getDateValidite())
                .montantCordon(request.getMontantCordon())
                .montantTVAInterieure(request.getMontantTVAInterieure())
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTVA)
                .statut(initialStatut)
                .entreprise(entreprise)
                .lettreCorrection(lettreCorrection)
                .demandeCorrection(demandeCorrection)
                .build();
        applyRecapFromCreateRequest(entity, request);
        assertRecapitulatifCoherence(entity);
        entity = repository.save(entity);

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "CertificatCredit", String.valueOf(entity.getId()), result);
        return result;
    }

    /**
     * Prise en charge par un acteur DGI / DGD / DGTCP : {@link StatutCertificat#ENVOYEE} → {@link StatutCertificat#EN_CONTROLE}.
     * Délègue à {@link #updateStatut(Long, StatutCertificat, AuthenticatedUser)} (rattachement GED, notifications).
     */
    @Transactional
    public CertificatCreditDto prendreEnCharge(Long id, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (entity.getStatut() != StatutCertificat.ENVOYEE) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Prise en charge réservée aux certificats en statut ENVOYEE (statut actuel: " + entity.getStatut() + ")");
        }
        return updateStatut(id, StatutCertificat.EN_CONTROLE, user);
    }

    @Transactional
    public CertificatCreditDto soumettreBrouillon(Long id, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé");
        }
        if (entity.getStatut() != StatutCertificat.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Soumission réservée aux certificats en brouillon (statut: " + entity.getStatut() + ")");
        }
        if (entity.getDemandeCorrection() != null && entity.getDemandeCorrection().getId() != null) {
            assertAtMostOneActiveCertificatPourDemande(entity.getDemandeCorrection().getId(), entity.getId());
        }
        assertMiseEnPlaceTrigger(entity.getLettreCorrection(), entity.getDemandeCorrection());
        workflow.validateTransition(StatutCertificat.BROUILLON, StatutCertificat.ENVOYEE);
        entity.setStatut(StatutCertificat.ENVOYEE);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        notifyCertificat(entity, StatutCertificat.ENVOYEE);
        return result;
    }

    @Transactional
    public CertificatCreditDto updateBrouillon(Long id, CreateCertificatCreditRequest request, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé");
        }
        assertDeposantPeutModifierDemandeMiseEnPlace(user);
        if (entity.getStatut() != StatutCertificat.BROUILLON && entity.getStatut() != StatutCertificat.ENVOYEE) {
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE,
                    "Modification réservée aux statuts BROUILLON et ENVOYEE (avant prise en charge par les services)");
        }

        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        LettreCorrection lettreCorrection = null;
        DemandeCorrection demandeCorrection = null;
        if (request.getLettreCorrectionId() != null) {
            lettreCorrection = lettreCorrectionRepository.findById(request.getLettreCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Lettre de correction non trouvée"));
            demandeCorrection = lettreCorrection.getFeuilleEvaluation() != null
                    ? lettreCorrection.getFeuilleEvaluation().getDemandeCorrection()
                    : null;
        } else if (request.getDemandeCorrectionId() != null) {
            demandeCorrection = demandeCorrectionRepository.findById(request.getDemandeCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée"));
        }

        if (demandeCorrection != null && demandeCorrection.getId() != null) {
            assertAtMostOneActiveCertificatPourDemande(demandeCorrection.getId(), id);
        }

        entity.setEntreprise(entreprise);
        entity.setLettreCorrection(lettreCorrection);
        entity.setDemandeCorrection(demandeCorrection);
        entity.setDateValidite(request.getDateValidite());
        entity.setMontantCordon(request.getMontantCordon());
        entity.setMontantTVAInterieure(request.getMontantTVAInterieure());
        BigDecimal defaultSoldeUpd = request.getDroitsEtTaxesDouaneHorsTva() != null
                ? request.getDroitsEtTaxesDouaneHorsTva()
                : request.getMontantCordon();
        BigDecimal soldeCordon = request.getSoldeCordon() != null ? request.getSoldeCordon() : defaultSoldeUpd;
        BigDecimal soldeTVA = request.getSoldeTVA() != null ? request.getSoldeTVA() : request.getMontantTVAInterieure();
        entity.setSoldeCordon(soldeCordon);
        entity.setSoldeTVA(soldeTVA);
        applyRecapFromCreateRequest(entity, request);
        assertRecapitulatifCoherence(entity);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        return result;
    }

    /**
     * Correction administrateur (ADMIN_SI) d'informations d'un certificat de crédit, à tout moment
     * quel que soit le statut (y compris après ouverture). Patch partiel, motif obligatoire,
     * journalisée dans l'audit sous {@link AuditAction#ADMIN_CORRECTION}.
     * <p>
     * Sécurité financière : la correction de montantCordon/montantTVAInterieure est refusée dès
     * qu'une demande d'utilisation existe déjà sur ce certificat, pour ne jamais désynchroniser un
     * solde déjà engagé. Les autres champs (récapitulatif, date de validité) restent corrigeables
     * sans cette contrainte.
     */
    @Transactional
    public CertificatCreditDto adminCorrectInfo(Long id, AdminCorrectionCertificatRequest request, String motif, AuthenticatedUser user) {
        assertAdminOverride(user, motif);
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));

        boolean touchesMontants = request.getMontantCordon() != null || request.getMontantTVAInterieure() != null;
        if (touchesMontants && !utilisationCreditRepository.findByCertificatCreditId(id).isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Correction du montant cordon / TVA intérieure impossible : des demandes d'utilisation existent déjà sur ce certificat (risque de désynchronisation des soldes)");
        }

        if (request.getDateValidite() != null) {
            entity.setDateValidite(request.getDateValidite());
        }
        if (request.getMontantCordon() != null) {
            entity.setMontantCordon(request.getMontantCordon());
        }
        if (request.getMontantTVAInterieure() != null) {
            entity.setMontantTVAInterieure(request.getMontantTVAInterieure());
        }
        if (request.getValeurDouaneFournitures() != null) {
            entity.setValeurDouaneFournitures(request.getValeurDouaneFournitures());
        }
        if (request.getDroitsEtTaxesDouaneHorsTva() != null) {
            entity.setDroitsEtTaxesDouaneHorsTva(request.getDroitsEtTaxesDouaneHorsTva());
        }
        if (request.getTaxesConsommation() != null) {
            entity.setTaxesConsommation(request.getTaxesConsommation());
        }
        if (request.getTvaImportationDouane() != null) {
            entity.setTvaImportationDouane(request.getTvaImportationDouane());
        }
        if (request.getMontantMarcheHt() != null) {
            entity.setMontantMarcheHt(request.getMontantMarcheHt());
        }
        if (request.getTvaCollecteeTravaux() != null) {
            entity.setTvaCollecteeTravaux(request.getTvaCollecteeTravaux());
        }

        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", String.valueOf(id), result, motif);
        return result;
    }

    /**
     * Prise en charge du certificat par l'administrateur, à la place de la DGI, de la DGD ou de la
     * DGTCP ({@code ENVOYEE → EN_CONTROLE}).
     *
     * <p>Premier maillon permettant à l'administrateur de mener la mise en place de bout en bout,
     * sans rendre la main aux directions.
     */
    @Transactional
    public CertificatCreditDto adminPrendreEnCharge(Long id, String motif, AuthenticatedUser user) {
        assertAdminOverride(user, motif);
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));

        StatutCertificat actuel = entity.getStatut();
        if (actuel == StatutCertificat.EN_CONTROLE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Le certificat est déjà en contrôle");
        }
        if (actuel != StatutCertificat.ENVOYEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "La prise en charge suppose un certificat au statut ENVOYEE. Statut actuel: " + actuel);
        }

        workflow.validateTransition(actuel, StatutCertificat.EN_CONTROLE);
        entity.setStatut(StatutCertificat.EN_CONTROLE);
        entity = repository.save(entity);

        // Même rattachement GED que la prise en charge ordinaire (voir updateStatut).
        if (entity.getDemandeCorrection() != null && entity.getDemandeCorrection().getId() != null) {
            dossierGedService.attachCertificatToDossier(entity.getDemandeCorrection().getId(), entity.getId());
        }

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", String.valueOf(id), result, motif);
        workflowNotificationHelper.certificatStatut(entity, StatutCertificat.EN_CONTROLE.name(), user, null);
        return result;
    }

    /**
     * Saisie des montants du récapitulatif par l'administrateur, à la place de la DGTCP.
     *
     * <p>Les montants conditionnent le visa DGTCP : sans cette action, l'administrateur devrait
     * réclamer leur saisie à la direction avant de pouvoir viser à sa place. Le traitement est
     * strictement celui de {@link #updateMontants}, soldes provisoires compris.
     */
    @Transactional
    public CertificatCreditDto adminRenseignerMontants(Long id,
                                                      UpdateCertificatCreditMontantsRequest request,
                                                      String motif,
                                                      AuthenticatedUser user) {
        assertAdminOverride(user, motif);
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));

        // Après ouverture, les montants alimentent des soldes déjà consommés : passer par la
        // correction administrateur, qui refuse l'opération si des utilisations existent.
        if (entity.getStatut() == StatutCertificat.OUVERT
                || entity.getStatut() == StatutCertificat.MODIFIE
                || entity.getStatut() == StatutCertificat.CLOTURE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le crédit est déjà ouvert : utilisez la correction administrateur pour ajuster les montants. Statut actuel: "
                            + entity.getStatut());
        }

        applyMontants(entity, request);
        entity = repository.save(entity);

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", String.valueOf(id), result, motif);
        return result;
    }

    /**
     * Validation présidentielle prononcée par l'administrateur à la place du Président.
     *
     * <p>Pendant exact de {@code DemandeCorrectionService.adminAdopterPourPresident}. S'arrête
     * volontairement à {@code VALIDE_PRESIDENT} : l'ouverture du crédit, qui initialise les soldes,
     * relève de {@link #adminOuvrirCredit}, sous une permission distincte.
     *
     * <p>Le certificat signé doit être présent — s'y substituer sans produire la pièce ne ferait
     * que fabriquer un statut vide.
     */
    @Transactional
    public CertificatCreditDto adminValiderPourPresident(Long id, String motif, AuthenticatedUser user) {
        assertAdminOverride(user, motif);
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));

        StatutCertificat actuel = entity.getStatut();
        if (actuel == StatutCertificat.VALIDE_PRESIDENT || actuel == StatutCertificat.EN_OUVERTURE_DGTCP
                || actuel == StatutCertificat.OUVERT || actuel == StatutCertificat.MODIFIE
                || actuel == StatutCertificat.CLOTURE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Validation impossible: le certificat est déjà validé. Statut actuel: " + actuel);
        }
        if (actuel != StatutCertificat.EN_VALIDATION_PRESIDENT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le certificat doit être en statut EN_VALIDATION_PRESIDENT : les visas requis "
                            + visaRequirementResolver.requiredRolesForCertificat(entity)
                            + " doivent être posés au préalable. Statut actuel: " + actuel);
        }

        documentService.assertActiveDocumentPresent(id,
                mr.gov.finances.sgci.domain.enums.TypeDocument.CERTIFICAT_CREDIT_IMPOTS.name(),
                "avant validation Président (à téléverser avec le visa administrateur)");

        workflow.validateTransition(actuel, StatutCertificat.VALIDE_PRESIDENT);
        entity.setStatut(StatutCertificat.VALIDE_PRESIDENT);
        entity = repository.save(entity);

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", String.valueOf(id), result, motif);
        // actor renseigné (contrairement à notifyCertificat) pour tracer l'administrateur substitué.
        workflowNotificationHelper.certificatStatut(entity, StatutCertificat.VALIDE_PRESIDENT.name(), user, null);
        return result;
    }

    /**
     * Ouverture du crédit prononcée par l'administrateur à la place du Président ou de la DGTCP.
     *
     * <p>Action délibérément distincte de {@link #adminValiderPourPresident} : elle produit un effet
     * financier — initialisation des soldes — et exige donc que la validation présidentielle ait déjà
     * eu lieu. Le statut {@code EN_VALIDATION_PRESIDENT} n'est pas accepté ici.
     */
    @Transactional
    public CertificatCreditDto adminOuvrirCredit(Long id, String motif, AuthenticatedUser user) {
        assertAdminOverride(user, motif);
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));

        StatutCertificat actuel = entity.getStatut();
        if (actuel == StatutCertificat.OUVERT) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Le crédit est déjà ouvert");
        }
        if (actuel != StatutCertificat.VALIDE_PRESIDENT && actuel != StatutCertificat.EN_OUVERTURE_DGTCP) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "L'ouverture administrateur suppose la validation présidentielle acquise : statut attendu "
                            + StatutCertificat.VALIDE_PRESIDENT + " ou " + StatutCertificat.EN_OUVERTURE_DGTCP
                            + ". Statut actuel: " + actuel);
        }

        workflow.validateTransition(actuel, StatutCertificat.OUVERT);
        applyOuvertureInitialisation(entity);
        entity.setStatut(StatutCertificat.OUVERT);
        entity = repository.save(entity);

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", String.valueOf(id), result, motif);
        workflowNotificationHelper.certificatStatut(entity, StatutCertificat.OUVERT.name(), user, null);
        return result;
    }

    private void assertAdminOverride(AuthenticatedUser user, String motif) {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Correction administrateur réservée à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de la correction administrateur est obligatoire");
        }
    }

    /**
     * Suppression définitive d'un brouillon uniquement. Les certificats déjà en circuit se gèrent par annulation (statut ANNULE).
     */
    @Transactional
    public void deleteBrouillon(Long id, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé");
        }
        if (entity.getStatut() != StatutCertificat.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Suppression réservée aux brouillons. Pour un certificat déjà soumis, utilisez l'annulation (statut ANNULE).");
        }
        for (UtilisationCredit u : utilisationCreditRepository.findByCertificatCreditId(id)) {
            if (u.getStatut() != StatutUtilisation.BROUILLON) {
                throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Suppression impossible : une utilisation liée n'est pas en brouillon (statut: " + u.getStatut() + ")");
            }
            utilisationCreditService.deleteBrouillon(u.getId(), user);
        }
        decisionCertificatCreditRepository.findByCertificatCreditId(id).forEach(decisionCertificatCreditRepository::delete);
        documentCertificatCreditRepository.findByCertificatCreditId(id).forEach(documentCertificatCreditRepository::delete);
        tvaDeductibleStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(id).forEach(tvaDeductibleStockRepository::delete);
        dossierGedService.clearCertificatFromDossierIfPresent(id);
        auditService.log(AuditAction.DELETE, "CertificatCredit", String.valueOf(id), null);
        repository.delete(entity);
    }

    @Transactional
    public CertificatCreditDto updateStatut(Long id, StatutCertificat statut, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
        StatutCertificat fromStatut = entity.getStatut();
        if (fromStatut == StatutCertificat.BROUILLON
                && (statut == StatutCertificat.ENVOYEE || statut == StatutCertificat.EN_CONTROLE)) {
            return soumettreBrouillon(id, user);
        }
        workflow.validateTransition(entity.getStatut(), statut);

        assertActorCanTransition(entity, statut, user);

        if (statut == StatutCertificat.OUVERT && fromStatut != StatutCertificat.OUVERT) {
            applyOuvertureInitialisation(entity);
        }

        entity.setStatut(statut);
        entity = repository.save(entity);

        if (fromStatut == StatutCertificat.ENVOYEE && statut == StatutCertificat.EN_CONTROLE
                && entity.getDemandeCorrection() != null && entity.getDemandeCorrection().getId() != null) {
            dossierGedService.attachCertificatToDossier(entity.getDemandeCorrection().getId(), entity.getId());
        }
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        notifyCertificat(entity, statut);
        return result;
    }

    /**
     * Initialisation appliquée à l'ouverture du crédit : date de mise en place et les trois soldes.
     *
     * <p>Ces soldes sont la source de vérité des {@code UtilisationCredit} : ce bloc ne doit jamais
     * être dupliqué. Appelé par {@link #updateStatut} et par {@link #adminOuvrirCredit}.
     */
    private void applyOuvertureInitialisation(CertificatCredit entity) {
        assertMontantsRenseignes(entity);
        if (entity.getDateMiseEnPlace() == null) {
            entity.setDateMiseEnPlace(Instant.now());
        }
        // soldeCordon = droits hors TVA (b) seulement — la TVA est suivie séparément
        BigDecimal droits = entity.getDroitsEtTaxesDouaneHorsTva() != null
                ? entity.getDroitsEtTaxesDouaneHorsTva().add(nz(entity.getTaxesConsommation()))
                : (entity.getMontantCordon() != null ? entity.getMontantCordon() : BigDecimal.ZERO);
        BigDecimal montantTVA = entity.getMontantTVAInterieure() != null ? entity.getMontantTVAInterieure() : BigDecimal.ZERO;
        BigDecimal tvaAccordee = entity.getTvaImportationDouaneAccordee() != null
                ? entity.getTvaImportationDouaneAccordee() : BigDecimal.ZERO;

        if (entity.getSoldeCordon() == null) {
            entity.setSoldeCordon(droits);
        }
        if (entity.getSoldeTVA() == null) {
            entity.setSoldeTVA(montantTVA);
        }
        // Initialiser tvaImportationDouane si absent ou nul — c'est le quota TVA cordon disponible (d)
        if (entity.getTvaImportationDouane() == null
                || entity.getTvaImportationDouane().compareTo(BigDecimal.ZERO) == 0) {
            entity.setTvaImportationDouane(tvaAccordee);
        }
    }

    @Transactional
    public CertificatCreditDto updateMontants(Long id, UpdateCertificatCreditMontantsRequest request, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut renseigner les montants");
        }
        CertificatCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        applyMontants(entity, request);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        return result;
    }

    /**
     * Écriture des montants du récapitulatif et synchronisation des soldes provisoires.
     *
     * <p>Partagée entre la saisie DGTCP ({@link #updateMontants}) et la saisie administrateur
     * ({@link #adminRenseignerMontants}) : les deux doivent produire exactement le même état,
     * faute de quoi un certificat instruit par l'administrateur afficherait des soldes différents.
     */
    private void applyMontants(CertificatCredit entity, UpdateCertificatCreditMontantsRequest request) {

        entity.setMontantCordon(request.getMontantCordon());
        entity.setMontantTVAInterieure(request.getMontantTVAInterieure());
        applyRecapFromMontantsRequest(entity, request);

        // Synchroniser les soldes tant que le crédit n'est pas ouvert (sinon incohérence avec les utilisations)
        if (entity.getStatut() != StatutCertificat.OUVERT) {
            // soldeCordon = b (droits hors TVA), pas b+d
            // Hors TVA : les taxes de consommation rejoignent les droits dans le solde cordon.
            BigDecimal droitsUpd = request.getDroitsEtTaxesDouaneHorsTva() != null
                    ? request.getDroitsEtTaxesDouaneHorsTva().add(nz(request.getTaxesConsommation()))
                    : request.getMontantCordon();
            if (entity.getSoldeCordon() == null || BigDecimal.ZERO.compareTo(entity.getSoldeCordon()) == 0) {
                entity.setSoldeCordon(droitsUpd);
            }
            if (entity.getSoldeTVA() == null || BigDecimal.ZERO.compareTo(entity.getSoldeTVA()) == 0) {
                entity.setSoldeTVA(request.getMontantTVAInterieure());
            }
            // Initialiser tvaImportationDouane si absent
            if (entity.getTvaImportationDouane() == null
                    || entity.getTvaImportationDouane().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal tvaAcc = entity.getTvaImportationDouaneAccordee();
                if (tvaAcc != null && tvaAcc.compareTo(BigDecimal.ZERO) > 0) {
                    entity.setTvaImportationDouane(tvaAcc);
                }
            }
        }

        assertRecapitulatifCoherence(entity);
    }

    private void applyRecapFromCreateRequest(CertificatCredit entity, CreateCertificatCreditRequest request) {
        if (entity == null || request == null) {
            return;
        }
        if (request.getValeurDouaneFournitures() != null) {
            entity.setValeurDouaneFournitures(request.getValeurDouaneFournitures());
        }
        if (request.getDroitsEtTaxesDouaneHorsTva() != null) {
            entity.setDroitsEtTaxesDouaneHorsTva(request.getDroitsEtTaxesDouaneHorsTva());
        }
        if (request.getTaxesConsommation() != null) {
            entity.setTaxesConsommation(request.getTaxesConsommation());
        }
        if (request.getTvaImportationDouane() != null) {
            entity.setTvaImportationDouaneAccordee(request.getTvaImportationDouane());
            entity.setTvaImportationDouane(request.getTvaImportationDouane());
        }
        if (request.getMontantMarcheHt() != null) {
            entity.setMontantMarcheHt(request.getMontantMarcheHt());
        }
        if (request.getTvaCollecteeTravaux() != null) {
            entity.setTvaCollecteeTravaux(request.getTvaCollecteeTravaux());
        }
    }

    private void applyRecapFromMontantsRequest(CertificatCredit entity, UpdateCertificatCreditMontantsRequest request) {
        if (entity == null || request == null) {
            return;
        }
        if (request.getValeurDouaneFournitures() != null) {
            entity.setValeurDouaneFournitures(request.getValeurDouaneFournitures());
        }
        if (request.getDroitsEtTaxesDouaneHorsTva() != null) {
            entity.setDroitsEtTaxesDouaneHorsTva(request.getDroitsEtTaxesDouaneHorsTva());
        }
        if (request.getTaxesConsommation() != null) {
            entity.setTaxesConsommation(request.getTaxesConsommation());
        }
        if (request.getTvaImportationDouane() != null) {
            entity.setTvaImportationDouaneAccordee(request.getTvaImportationDouane());
            entity.setTvaImportationDouane(request.getTvaImportationDouane());
        }
        if (request.getMontantMarcheHt() != null) {
            entity.setMontantMarcheHt(request.getMontantMarcheHt());
        }
        if (request.getTvaCollecteeTravaux() != null) {
            entity.setTvaCollecteeTravaux(request.getTvaCollecteeTravaux());
        }
    }

    /**
     * Si le récapitulatif (lignes b, d, g) et les montants agrégés sont renseignés, vérifie
     * {@code montantCordon ≈ b + d} (crédit extérieur) et {@code montantTVAInterieure ≈ g − d} (crédit intérieur net).
     */
    /** (d) utilisé pour les formules récap : accord figé, sinon anciennes lignes sans colonne accordee. */
    private static BigDecimal resolveTvaImportationDouanePourRecap(CertificatCredit c) {
        if (c == null) {
            return null;
        }
        if (c.getTvaImportationDouaneAccordee() != null) {
            return c.getTvaImportationDouaneAccordee();
        }
        return c.getTvaImportationDouane();
    }

    private void assertRecapitulatifCoherence(CertificatCredit c) {
        if (c == null) {
            return;
        }
        BigDecimal b = c.getDroitsEtTaxesDouaneHorsTva();
        BigDecimal cons = c.getTaxesConsommation();
        BigDecimal d = resolveTvaImportationDouanePourRecap(c);
        BigDecimal g = c.getTvaCollecteeTravaux();
        BigDecimal mc = c.getMontantCordon();
        BigDecimal mt = c.getMontantTVAInterieure();
        if (b != null && d != null && mc != null) {
            BigDecimal e = b.add(d).add(nz(cons));
            if (!approxEqual(e, mc)) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Récapitulatif incohérent : montantCordon doit correspondre au crédit extérieur (b + c + d) = "
                                + e.setScale(2, RoundingMode.HALF_UP) + " (montantCordon=" + mc + ")");
            }
        }
        // La TVA nette (g - d) ne concerne que les dossiers comportant un crédit intérieur.
        // Quand celui-ci est nul, la ligne est sans objet et ne doit rien exiger.
        if (g != null && d != null && mt != null && mt.signum() > 0) {
            BigDecimal h = g.subtract(d);
            if (!approxEqual(h, mt)) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Récapitulatif incohérent : montant TVA intérieure doit correspondre à la TVA nette (g − d) = "
                                + h.setScale(2, RoundingMode.HALF_UP) + " (montantTVAInterieure=" + mt + ")");
            }
        }
    }

    private static boolean approxEqual(BigDecimal x, BigDecimal y) {
        return x.subtract(y).abs().compareTo(RECAP_TOLERANCE_MRU) <= 0;
    }

    private void assertActorCanTransition(CertificatCredit entity, StatutCertificat to, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        StatutCertificat from = entity.getStatut();
        Role role = user.getRole();

        if (to == StatutCertificat.INCOMPLETE || to == StatutCertificat.A_RECONTROLER) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transition manuelle vers " + to
                            + " interdite : ce statut est géré automatiquement par le système (via rejet temporaire / résolution de documents).");
        }

        if (to == StatutCertificat.EN_VALIDATION_PRESIDENT) {
            java.util.Set<Role> visasRequis = visaRequirementResolver.requiredRolesForCertificat(entity);
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transition manuelle vers EN_VALIDATION_PRESIDENT interdite : "
                            + "ce statut est attribué automatiquement lorsque les visas requis " + visasRequis + " sont validés.");
        }

        if (to == StatutCertificat.EN_CONTROLE) {
            if (role != Role.DGI && role != Role.DGD && role != Role.DGTCP) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Seuls DGI, DGD ou DGTCP peuvent prendre en charge (ENVOYEE) ou remettre le certificat en contrôle");
            }
        }

        if (to == StatutCertificat.VALIDE_PRESIDENT && role != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul le Président peut valider le certificat");
        }

        if (to == StatutCertificat.EN_OUVERTURE_DGTCP && role != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut passer en ouverture");
        }

        if (to == StatutCertificat.OUVERT) {
            boolean byPresident = role == Role.PRESIDENT
                    && (from == StatutCertificat.VALIDE_PRESIDENT || from == StatutCertificat.EN_VALIDATION_PRESIDENT);
            boolean byDgtcp = role == Role.DGTCP
                    && (from == StatutCertificat.EN_OUVERTURE_DGTCP);
            if (!byPresident && !byDgtcp) {
                throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                        "Seul le Président peut ouvrir le crédit après son visa, ou DGTCP après la phase EN_OUVERTURE_DGTCP");
            }
        }

        if (to == StatutCertificat.ANNULE) {
            if (role == Role.ENTREPRISE) {
                mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
                Long myEnt = effectiveIdentityService.resolveEntrepriseId(user, u);
                if (myEnt == null || entity.getEntreprise() == null
                        || !entity.getEntreprise().getId().equals(myEnt)) {
                    throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Annulation réservée au titulaire du certificat");
                }
                return;
            }
            if (role != Role.AUTORITE_CONTRACTANTE && role != Role.AUTORITE_UPM
                    && role != Role.AUTORITE_UEP && role != Role.PRESIDENT) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Rôle non autorisé à annuler le certificat");
            }
        }
    }

    private void assertMiseEnPlaceTrigger(LettreCorrection lettreCorrection, DemandeCorrection demandeCorrection) {
        if (demandeCorrection == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La demande de correction est obligatoire pour la mise en place du crédit d'impôt");
        }
        if (lettreCorrection != null) {
            if (!Boolean.TRUE.equals(lettreCorrection.getSignee())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La lettre de correction doit être signée");
            }
            if (!Boolean.TRUE.equals(lettreCorrection.getNotifiee())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La lettre de correction doit être notifiée");
            }
        }
        if (demandeCorrection.getMarche() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat doit être rattaché à un marché (via la demande de correction)");
        }

        StatutDemande statutDemande = demandeCorrection.getStatut();
        if (statutDemande != StatutDemande.ADOPTEE && statutDemande != StatutDemande.NOTIFIEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La demande de correction doit être visée (ADOPTEE/NOTIFIEE) avant la mise en place du crédit d'impôt");
        }

        if (demandeCorrection.getMarche().getDateSignature() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le contrat (marché) doit être signé");
        }
    }

    private void assertMontantsRenseignes(CertificatCredit entity) {
        if (entity == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat invalide");
        }
        java.util.Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCertificat(entity);
        boolean cordonRequis = requiredRoles.contains(Role.DGD);
        boolean tvaRequise = requiredRoles.contains(Role.DGI);
        if (cordonRequis) {
            if (entity.getMontantCordon() == null || entity.getMontantCordon().compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le montant cordon (crédit extérieur) doit être renseigné avant l'ouverture du crédit");
            }
        }
        if (tvaRequise) {
            if (entity.getMontantTVAInterieure() == null || entity.getMontantTVAInterieure().compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Le montant TVA intérieure (crédit intérieur) doit être renseigné avant l'ouverture du crédit");
            }
        }
        assertRecapitulatifCoherence(entity);
    }

    private CertificatCreditDto toDto(CertificatCredit c) {
        Entreprise e = c.getEntreprise();
        Long demandeCorrectionId = c.getDemandeCorrection() != null ? c.getDemandeCorrection().getId() : null;
        AutoriteContractante autorite = c.getDemandeCorrection() != null ? c.getDemandeCorrection().getAutoriteContractante() : null;
        Long marcheId = c.getDemandeCorrection() != null && c.getDemandeCorrection().getMarche() != null
                ? c.getDemandeCorrection().getMarche().getId()
                : null;
        BigDecimal bRec = c.getDroitsEtTaxesDouaneHorsTva();
        BigDecimal dPourRecap = resolveTvaImportationDouanePourRecap(c);
        BigDecimal dRestant = c.getTvaImportationDouane();
        BigDecimal gRec = c.getTvaCollecteeTravaux();
        BigDecimal consRec = c.getTaxesConsommation();
        BigDecimal creditExterieurRecap = null;
        if (bRec != null && dPourRecap != null) {
            creditExterieurRecap = bRec.add(dPourRecap).add(nz(consRec));
        }
        BigDecimal creditInterieurNetRecap = null;
        if (gRec != null && dPourRecap != null) {
            creditInterieurNetRecap = gRec.subtract(dPourRecap);
        }
        BigDecimal totalCreditImpotRecap = null;
        if (creditExterieurRecap != null && creditInterieurNetRecap != null) {
            totalCreditImpotRecap = creditExterieurRecap.add(creditInterieurNetRecap);
        }
        return CertificatCreditDto.builder()
                .id(c.getId())
                .numero(c.getNumero())
                .reference(c.getReference())
                .dateEmission(c.getDateEmission())
                .dateValidite(c.getDateValidite())
                .dateMiseEnPlace(c.getDateMiseEnPlace())
                .montantCordon(c.getMontantCordon())
                .montantTVAInterieure(c.getMontantTVAInterieure())
                .soldeCordon(c.getSoldeCordon())
                .soldeTVA(c.getSoldeTVA())
                .valeurDouaneFournitures(c.getValeurDouaneFournitures())
                .droitsEtTaxesDouaneHorsTva(bRec)
                .taxesConsommation(consRec)
                .tvaImportationDouaneAccordee(c.getTvaImportationDouaneAccordee())
                .tvaImportationDouane(dRestant)
                .montantMarcheHt(c.getMontantMarcheHt())
                .tvaCollecteeTravaux(gRec)
                .creditExterieurRecap(creditExterieurRecap)
                .creditInterieurNetRecap(creditInterieurNetRecap)
                .totalCreditImpotRecap(totalCreditImpotRecap)
                .statut(c.getStatut())
                .entrepriseId(e != null ? e.getId() : null)
                .entrepriseRaisonSociale(e != null ? e.getRaisonSociale() : null)
                .demandeCorrectionId(demandeCorrectionId)
                .marcheId(marcheId)
                .autoriteContractanteNom(autorite != null ? autorite.getNom() : null)
                .autoriteContractanteMinistereTutelleNom(autorite != null ? autorite.getMinistereTutelleNom() : null)
                .autoriteContractanteMinistereTutelleCode(autorite != null ? autorite.getMinistereTutelleCode() : null)
                .build();
    }

    private void notifyCertificat(CertificatCredit certificat, StatutCertificat statut) {
        workflowNotificationHelper.certificatStatut(certificat, statut.name(), null, null);
    }
}
