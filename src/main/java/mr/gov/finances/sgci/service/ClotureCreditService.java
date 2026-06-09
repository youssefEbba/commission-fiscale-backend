package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.ClotureCredit;
import mr.gov.finances.sgci.domain.entity.TvaDeductibleStock;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.WorkflowEventCode;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ClotureCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CertificatClotureQueueItemDto;
import mr.gov.finances.sgci.web.dto.ClotureCreditDto;
import mr.gov.finances.sgci.web.dto.CreateClotureCreditRequest;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClotureCreditService {

    private static final EnumSet<StatutCertificat> QUEUE_STATUTS = EnumSet.of(StatutCertificat.OUVERT, StatutCertificat.MODIFIE);

    private static final EnumSet<StatutUtilisation> UTILISATION_TERMINEES = EnumSet.of(
            StatutUtilisation.LIQUIDEE,
            StatutUtilisation.APUREE,
            StatutUtilisation.REJETEE,
            StatutUtilisation.CLOTUREE
    );

    private static final EnumSet<StatutTransfert> TRANSFERT_EN_COURS = EnumSet.of(
            StatutTransfert.DEMANDE,
            StatutTransfert.EN_COURS,
            StatutTransfert.VALIDE,
            StatutTransfert.INCOMPLETE,
            StatutTransfert.A_RECONTROLER
    );

    private final CertificatCreditRepository certificatCreditRepository;
    private final ClotureCreditRepository clotureCreditRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final TransfertCreditRepository transfertCreditRepository;
    private final TvaDeductibleStockRepository tvaDeductibleStockRepository;
    private final CertificatCreditWorkflow workflow;
    private final AuditService auditService;
    private final WorkflowNotificationHelper workflowNotificationHelper;

    /**
     * Tous les certificats OUVERT / MODIFIE non encore clôturés définitivement,
     * avec indicateur d'éligibilité (le filtre « solde zéro uniquement » masquait la majorité des dossiers).
     */
    @Transactional(readOnly = true)
    public List<CertificatClotureQueueItemDto> findClotureQueue(AuthenticatedUser user) {
        assertDgtcp(user);
        return certificatCreditRepository.findByStatutInOrderByDateEmissionDescIdDesc(QUEUE_STATUTS).stream()
                .filter(this::notYetFinalise)
                .map(this::toQueueItem)
                .collect(Collectors.toList());
    }

    /** Compatibilité : identifiants des certificats éligibles à une proposition immédiate. */
    @Transactional(readOnly = true)
    public List<Long> findEligibleCertificatIds(AuthenticatedUser user) {
        return findClotureQueue(user).stream()
                .filter(CertificatClotureQueueItemDto::isEligiblePourCloture)
                .map(CertificatClotureQueueItemDto::getCertificatCreditId)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClotureCreditDto proposer(CreateClotureCreditRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête invalide");
        }
        assertDgtcp(user);

        CertificatCredit c = certificatCreditRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé"));

        if (!QUEUE_STATUTS.contains(c.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat doit être OUVERT ou MODIFIE");
        }

        CertificatClotureQueueItemDto evaluation = toQueueItem(c);
        if (!evaluation.isEligiblePourCloture()) {
            String detail = evaluation.getMotifsNonEligibilite() != null && !evaluation.getMotifsNonEligibilite().isEmpty()
                    ? String.join(" ; ", evaluation.getMotifsNonEligibilite())
                    : "Certificat non éligible à la clôture";
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, detail);
        }

        ClotureCredit existing = clotureCreditRepository.findByCertificatCreditId(c.getId()).orElse(null);
        if (existing != null && existing.getDateCloture() != null) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Ce certificat est déjà clôturé/annulé");
        }
        if (existing != null && existing.getApprouvee() == null) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Une proposition de clôture est déjà en attente de validation par le Président");
        }

        BigDecimal soldeCordon = nz(c.getSoldeCordon());
        BigDecimal soldeTva = nz(c.getSoldeTVA());
        BigDecimal soldeRestant = soldeCordon.add(soldeTva);

        ClotureCredit cc = existing != null ? existing : new ClotureCredit();
        cc.setCertificatCredit(c);
        cc.setDateProposition(Instant.now());
        cc.setMotif(request.getMotif());
        cc.setTypeOperation(request.getTypeOperation());
        cc.setSoldeRestant(soldeRestant);
        cc.setApprouvee(null);
        cc.setDateCloture(null);

        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.CREATE, "ClotureCredit", String.valueOf(cc.getId()), dto);
        workflowNotificationHelper.cloture(cc, WorkflowEventCode.CLOTURE_PROPOSEE, user);
        return dto;
    }

    /**
     * File des propositions selon le rôle :
     * <ul>
     *   <li>Président : en attente de validation ({@code approuvee == null})</li>
     *   <li>DGTCP : toutes les propositions non finalisées ({@code dateCloture == null}), y compris approuvées à finaliser</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<ClotureCreditDto> findPropositions(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        List<ClotureCredit> list = switch (user.getRole()) {
            case PRESIDENT -> clotureCreditRepository.findByApprouveeIsNull();
            case DGTCP -> clotureCreditRepository.findByDateClotureIsNullOrderByDatePropositionDesc();
            default -> List.of();
        };
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    /** @deprecated utiliser {@link #findPropositions(AuthenticatedUser)} */
    @Transactional(readOnly = true)
    public List<ClotureCreditDto> findPendingPropositions() {
        return clotureCreditRepository.findByApprouveeIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ClotureCreditDto validerParPresident(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul le Président peut valider");
        }

        ClotureCredit cc = clotureCreditRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Proposition non trouvée"));

        if (cc.getApprouvee() != null) {
            return toDto(cc);
        }

        cc.setApprouvee(true);
        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.UPDATE, "ClotureCredit", String.valueOf(cc.getId()), Map.of("approuvee", true));
        workflowNotificationHelper.cloture(cc, WorkflowEventCode.CLOTURE_APPROUVEE, user);
        return dto;
    }

    @Transactional
    public ClotureCreditDto rejeterParPresident(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul le Président peut rejeter");
        }

        ClotureCredit cc = clotureCreditRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Proposition non trouvée"));

        if (cc.getApprouvee() != null) {
            return toDto(cc);
        }

        cc.setApprouvee(false);
        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.UPDATE, "ClotureCredit", String.valueOf(cc.getId()), Map.of("approuvee", false));
        workflowNotificationHelper.cloture(cc, WorkflowEventCode.CLOTURE_REJETEE, user);
        return dto;
    }

    @Transactional
    public ClotureCreditDto finaliserParDgtcp(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut finaliser");
        }

        ClotureCredit cc = clotureCreditRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Proposition non trouvée"));

        if (!Boolean.TRUE.equals(cc.getApprouvee())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La proposition n'est pas approuvée");
        }
        if (cc.getDateCloture() != null) {
            return toDto(cc);
        }

        CertificatCredit c = cc.getCertificatCredit();
        if (c == null || c.getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat manquant");
        }

        StatutCertificat to = cc.getTypeOperation() != null && cc.getTypeOperation().name().equals("ANNULATION")
                ? StatutCertificat.ANNULE
                : StatutCertificat.CLOTURE;

        workflow.validateTransition(c.getStatut(), to);
        c.setStatut(to);
        certificatCreditRepository.save(c);

        cc.setDateCloture(Instant.now());
        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.UPDATE, "ClotureCredit", String.valueOf(cc.getId()), Map.of("finalisee", true, "statutCertificat", to.name()));
        workflowNotificationHelper.cloture(cc, WorkflowEventCode.CLOTURE_FINALISEE, user);
        return dto;
    }

    private void assertDgtcp(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut consulter la file de clôture");
        }
    }

    private boolean notYetFinalise(CertificatCredit c) {
        ClotureCredit cc = clotureCreditRepository.findByCertificatCreditId(c.getId()).orElse(null);
        return cc == null || cc.getDateCloture() == null;
    }

    private CertificatClotureQueueItemDto toQueueItem(CertificatCredit c) {
        BigDecimal soldeCordon = nz(c.getSoldeCordon());
        BigDecimal soldeTva = nz(c.getSoldeTVA());
        BigDecimal tvaImport = nz(c.getTvaImportationDouane());
        BigDecimal stockRestant = sumStockTvaRestant(c.getId());

        ClotureCredit cc = clotureCreditRepository.findByCertificatCreditId(c.getId()).orElse(null);
        boolean propositionEnCours = cc != null && cc.getApprouvee() == null && cc.getDateCloture() == null;

        List<String> motifs = new ArrayList<>();
        if (propositionEnCours) {
            motifs.add("Proposition de clôture en attente de validation par le Président");
        }

        long utilisationsOuvertes = utilisationCreditRepository.countByCertificatCreditIdAndStatutNotIn(
                c.getId(), UTILISATION_TERMINEES);
        if (utilisationsOuvertes > 0) {
            motifs.add(utilisationsOuvertes + " utilisation(s) de crédit encore en cours");
        }

        if (transfertCreditRepository.existsByCertificatCreditIdAndStatutIn(c.getId(), TRANSFERT_EN_COURS)) {
            motifs.add("Demande de transfert de crédit en cours");
        }

        boolean soldeZero = soldeCordon.compareTo(BigDecimal.ZERO) == 0 && soldeTva.compareTo(BigDecimal.ZERO) == 0;
        boolean expire = c.getDateValidite() != null && c.getDateValidite().isBefore(Instant.now());

        if (!soldeZero && !expire) {
            if (soldeCordon.compareTo(BigDecimal.ZERO) > 0) {
                motifs.add("Solde cordon (e) restant : " + soldeCordon + " MRU");
            }
            if (soldeTva.compareTo(BigDecimal.ZERO) > 0) {
                motifs.add("Solde TVA intérieure (h) restant : " + soldeTva + " MRU");
            }
        }
        if (tvaImport.compareTo(BigDecimal.ZERO) > 0) {
            motifs.add("TVA importation (d) non épuisée : " + tvaImport + " MRU");
        }
        if (stockRestant.compareTo(BigDecimal.ZERO) > 0) {
            motifs.add("Stock TVA déductible restant : " + stockRestant + " MRU");
        }

        boolean eligible = motifs.isEmpty() && (soldeZero || expire);

        String raisonSociale = c.getEntreprise() != null ? c.getEntreprise().getRaisonSociale() : null;

        return CertificatClotureQueueItemDto.builder()
                .certificatCreditId(c.getId())
                .numero(c.getNumero())
                .statut(c.getStatut())
                .dateEmission(c.getDateEmission())
                .dateValidite(c.getDateValidite())
                .entrepriseRaisonSociale(raisonSociale)
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTva)
                .tvaImportationDouane(tvaImport)
                .stockTvaDeductibleRestant(stockRestant)
                .propositionEnCours(propositionEnCours)
                .clotureCreditId(cc != null ? cc.getId() : null)
                .eligiblePourCloture(eligible)
                .motifsNonEligibilite(motifs.isEmpty() ? List.of() : List.copyOf(motifs))
                .build();
    }

    private BigDecimal sumStockTvaRestant(Long certificatId) {
        return tvaDeductibleStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(certificatId).stream()
                .map(TvaDeductibleStock::getMontantRestant)
                .map(ClotureCreditService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private ClotureCreditDto toDto(ClotureCredit cc) {
        CertificatCredit c = cc.getCertificatCredit();
        return ClotureCreditDto.builder()
                .id(cc.getId())
                .dateProposition(cc.getDateProposition())
                .dateCloture(cc.getDateCloture())
                .motif(cc.getMotif())
                .typeOperation(cc.getTypeOperation())
                .soldeRestant(cc.getSoldeRestant())
                .approuvee(cc.getApprouvee())
                .certificatCreditId(c != null ? c.getId() : null)
                .certificatNumero(c != null ? c.getNumero() : null)
                .build();
    }
}
