package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.SousTraitance;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.SousTraitanceRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateSousTraitanceOnboardingRequest;
import mr.gov.finances.sgci.web.dto.CreateSousTraitanceRequest;
import mr.gov.finances.sgci.web.dto.EntrepriseDto;
import mr.gov.finances.sgci.web.dto.SousTraitanceDto;
import mr.gov.finances.sgci.web.dto.SousTraitanceOnboardingResultDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SousTraitanceService {

    private final UtilisateurRepository utilisateurRepository;
    private final CertificatCreditRepository certificatRepository;
    private final SousTraitanceRepository repository;
    private final EntrepriseRepository entrepriseRepository;
    private final DocumentSousTraitanceService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<SousTraitanceDto> findAll(AuthenticatedUser user, Long sousTraitantEntrepriseIdFilter) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        if (user.getRole() == Role.ENTREPRISE) {
            Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
            if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                return List.of();
            }
            Long entrepriseId = u.getEntreprise().getId();
            List<SousTraitanceDto> list = repository.findByCertificatCreditEntrepriseIdOrSousTraitantEntrepriseId(entrepriseId, entrepriseId)
                    .stream()
                    .distinct()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            if (sousTraitantEntrepriseIdFilter != null) {
                return list.stream()
                        .filter(d -> Objects.equals(d.getSousTraitantEntrepriseId(), sousTraitantEntrepriseIdFilter))
                        .collect(Collectors.toList());
            }
            return list;
        }
        if (sousTraitantEntrepriseIdFilter != null) {
            return repository.findAll().stream()
                    .filter(st -> st.getSousTraitantEntreprise() != null
                            && Objects.equals(st.getSousTraitantEntreprise().getId(), sousTraitantEntrepriseIdFilter))
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Entreprises déjà présentes comme sous-traitantes sur au moins un certificat du titulaire connecté.
     */
    @Transactional(readOnly = true)
    public List<EntrepriseDto> findSousTraitantEntreprisesForTitulaire(AuthenticatedUser user) {
        if (user == null || user.getRole() != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Réservé à l'entreprise titulaire");
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
            return List.of();
        }
        return repository.findDistinctSousTraitantEntreprisesForTitulaire(u.getEntreprise().getId()).stream()
                .map(this::entrepriseToDto)
                .collect(Collectors.toList());
    }

    /**
     * Toutes les entreprises ayant été désignées comme sous-traitantes sur au moins une ligne de sous-traitance.
     */
    @Transactional(readOnly = true)
    public List<EntrepriseDto> findDistinctSousTraitantEntreprisesGlobally() {
        return repository.findDistinctSousTraitantEntreprises().stream()
                .map(this::entrepriseToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SousTraitanceDto findByCertificatCreditId(Long certificatCreditId, AuthenticatedUser user) {
        CertificatCredit c = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé"));
        SousTraitance st = repository.findByCertificatCreditId(certificatCreditId).orElse(null);
        if (st == null) {
            return null;
        }
        assertCanViewSousTraitance(st, user, c);
        return toDto(st);
    }

    private void assertCanViewSousTraitance(SousTraitance st, AuthenticatedUser user, CertificatCredit cert) {
        if (user == null || user.getRole() == null) {
            return;
        }
        if (user.getRole() != Role.ENTREPRISE && user.getRole() != Role.SOUS_TRAITANT) {
            return;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        Long myEnt = u.getEntreprise().getId();
        Long titId = cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        if (user.getRole() == Role.ENTREPRISE && titId != null && titId.equals(myEnt)) {
            return;
        }
        if (user.getRole() == Role.SOUS_TRAITANT) {
            Long stEnt = st.getSousTraitantEntreprise() != null ? st.getSousTraitantEntreprise().getId() : null;
            if (stEnt != null && stEnt.equals(myEnt)) {
                return;
            }
        }
        throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: sous-traitance hors périmètre");
    }

    @Transactional(readOnly = true)
    public SousTraitanceDto findById(Long id, AuthenticatedUser user) {
        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + id));
        CertificatCredit c = st.getCertificatCredit();
        if (c == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Sous-traitance incohérente: certificat manquant");
        }
        assertCanViewSousTraitance(st, user, c);
        return toDto(st);
    }

    @Transactional
    public SousTraitanceDto create(CreateSousTraitanceRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule l'entreprise attributaire peut soumettre une sous-traitance");
        }

        CertificatCredit c = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé"));
        if (c.getStatut() != StatutCertificat.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat doit être OUVERT");
        }

        Utilisateur demandeur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (demandeur.getEntreprise() == null || demandeur.getEntreprise().getId() == null
                || c.getEntreprise() == null || c.getEntreprise().getId() == null
                || !demandeur.getEntreprise().getId().equals(c.getEntreprise().getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: certificat ne correspond pas à l'entreprise");
        }

        Entreprise sousTraitantEntreprise = entrepriseRepository.findById(request.getSousTraitantEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise sous-traitante non trouvée"));

        SousTraitance st = repository.findByCertificatCreditId(c.getId()).orElse(null);
        if (st == null) {
            st = SousTraitance.builder().certificatCredit(c).build();
        }

        st.setSousTraitantEntreprise(sousTraitantEntreprise);
        st.setContratEnregistre(Boolean.TRUE.equals(request.getContratEnregistre()));
        st.setVolumes(request.getVolumes());
        st.setQuantites(request.getQuantites());
        st.setStatut(StatutSousTraitance.DEMANDE);
        st.setDateAutorisation(null);

        st = repository.save(st);
        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.CREATE, "SousTraitance", String.valueOf(st.getId()), result);

        notificationService.notifyUsers(
                utilisateurRepository.findByRole(Role.DGTCP).stream().map(Utilisateur::getId).collect(Collectors.toList()),
                NotificationType.SOUS_TRAITANCE,
                "SousTraitance",
                st.getId(),
                "Nouvelle demande de sous-traitance",
                Collections.singletonMap("id", st.getId())
        );

        return result;
    }

    @Transactional
    public SousTraitanceOnboardingResultDto onboard(CreateSousTraitanceOnboardingRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule l'entreprise attributaire peut créer un sous-traitant");
        }

        CertificatCredit c = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé"));
        if (c.getStatut() != StatutCertificat.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat doit être OUVERT");
        }

        Utilisateur demandeur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (demandeur.getEntreprise() == null || demandeur.getEntreprise().getId() == null
                || c.getEntreprise() == null || c.getEntreprise().getId() == null
                || !demandeur.getEntreprise().getId().equals(c.getEntreprise().getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: certificat ne correspond pas à l'entreprise");
        }

        String nif = request.getSousTraitantEntrepriseNif() != null ? request.getSousTraitantEntrepriseNif().trim() : null;
        if (nif != null && nif.isEmpty()) {
            nif = null;
        }

        Entreprise sousTraitantEntreprise = null;
        if (nif != null) {
            sousTraitantEntreprise = entrepriseRepository.findByNif(nif).orElse(null);
        }
        if (sousTraitantEntreprise == null) {
            sousTraitantEntreprise = Entreprise.builder()
                    .raisonSociale(request.getSousTraitantEntrepriseRaisonSociale())
                    .nif(nif)
                    .adresse(request.getSousTraitantEntrepriseAdresse())
                    .situationFiscale(request.getSousTraitantEntrepriseSituationFiscale())
                    .build();
            sousTraitantEntreprise = entrepriseRepository.save(sousTraitantEntreprise);
            auditService.log(AuditAction.CREATE, "Entreprise", String.valueOf(sousTraitantEntreprise.getId()), Map.of(
                    "id", sousTraitantEntreprise.getId(),
                    "raisonSociale", sousTraitantEntreprise.getRaisonSociale() != null ? sousTraitantEntreprise.getRaisonSociale() : "",
                    "nif", sousTraitantEntreprise.getNif() != null ? sousTraitantEntreprise.getNif() : ""
            ));
        }

        CreateSousTraitanceRequest stRequest = new CreateSousTraitanceRequest();
        stRequest.setCertificatCreditId(request.getCertificatCreditId());
        stRequest.setSousTraitantEntrepriseId(sousTraitantEntreprise.getId());
        stRequest.setContratEnregistre(request.getContratEnregistre());
        stRequest.setVolumes(request.getVolumes());
        stRequest.setQuantites(request.getQuantites());

        SousTraitanceDto st = create(stRequest, user);

        return SousTraitanceOnboardingResultDto.builder()
                .sousTraitantEntrepriseId(sousTraitantEntreprise.getId())
                .sousTraitance(st)
                .build();
    }

    @Transactional
    public SousTraitanceDto autoriserByDgtcp(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut autoriser une sous-traitance");
        }

        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + id));

        if (st.getStatut() == StatutSousTraitance.AUTORISEE) {
            return toDto(st);
        }
        if (st.getStatut() == StatutSousTraitance.REFUSEE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Sous-traitance déjà refusée");
        }
        if (st.getStatut() != StatutSousTraitance.DEMANDE && st.getStatut() != StatutSousTraitance.EN_COURS) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule une demande en attente peut être autorisée par DGTCP");
        }

        requirementValidator.assertRequiredDocumentsPresent(
                ProcessusDocument.SOUS_TRAITANCE,
                documentService.findActiveDocumentTypes(st.getId())
        );

        st.setStatut(StatutSousTraitance.AUTORISEE);
        st.setDateAutorisation(Instant.now());
        st = repository.save(st);

        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.UPDATE, "SousTraitance", String.valueOf(st.getId()), result);
        return result;
    }

    @Transactional
    public SousTraitanceDto refuserByDgtcp(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut refuser une sous-traitance");
        }

        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + id));
        if (st.getStatut() == StatutSousTraitance.AUTORISEE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Sous-traitance déjà autorisée");
        }
        if (st.getStatut() == StatutSousTraitance.REFUSEE) {
            return toDto(st);
        }
        if (st.getStatut() != StatutSousTraitance.DEMANDE && st.getStatut() != StatutSousTraitance.EN_COURS) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule une demande en attente peut être refusée par DGTCP");
        }

        st.setStatut(StatutSousTraitance.REFUSEE);
        st.setDateAutorisation(null);
        st = repository.save(st);

        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.UPDATE, "SousTraitance", String.valueOf(st.getId()), result);
        return result;
    }

    @Transactional
    public SousTraitanceDto suspendreParTitulaire(Long id, AuthenticatedUser user) {
        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + id));
        requireTitulaireOnSousTraitance(st, user);
        if (st.getStatut() != StatutSousTraitance.AUTORISEE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule une sous-traitance autorisée peut être suspendue");
        }
        st.setStatut(StatutSousTraitance.SUSPENDUE);
        st = repository.save(st);
        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.UPDATE, "SousTraitance", String.valueOf(st.getId()),
                Map.of("action", "suspend_titulaire", "statut", st.getStatut().name()));
        return result;
    }

    @Transactional
    public SousTraitanceDto reactiverParTitulaire(Long id, AuthenticatedUser user) {
        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + id));
        requireTitulaireOnSousTraitance(st, user);
        if (st.getStatut() != StatutSousTraitance.SUSPENDUE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule une sous-traitance suspendue peut être réactivée par le titulaire");
        }
        st.setStatut(StatutSousTraitance.AUTORISEE);
        if (st.getDateAutorisation() == null) {
            st.setDateAutorisation(Instant.now());
        }
        st = repository.save(st);
        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.UPDATE, "SousTraitance", String.valueOf(st.getId()),
                Map.of("action", "reactivate_titulaire", "statut", st.getStatut().name()));
        return result;
    }

    @Transactional
    public SousTraitanceDto revoquerParTitulaire(Long id, AuthenticatedUser user) {
        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Sous-traitance non trouvée: " + id));
        requireTitulaireOnSousTraitance(st, user);
        if (st.getStatut() == StatutSousTraitance.REFUSEE) {
            return toDto(st);
        }
        st.setStatut(StatutSousTraitance.REVOQUEE);
        st.setDateAutorisation(null);
        st = repository.save(st);
        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.UPDATE, "SousTraitance", String.valueOf(st.getId()),
                Map.of("action", "revoke_titulaire", "statut", st.getStatut().name()));
        return result;
    }

    private void requireTitulaireOnSousTraitance(SousTraitance st, AuthenticatedUser user) {
        if (user == null || user.getRole() != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Réservé à l'entreprise titulaire du certificat");
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (u.getEntreprise() == null || st.getCertificatCredit() == null || st.getCertificatCredit().getEntreprise() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Données invalides");
        }
        if (!u.getEntreprise().getId().equals(st.getCertificatCredit().getEntreprise().getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: vous n'êtes pas le titulaire de ce certificat");
        }
    }

    private EntrepriseDto entrepriseToDto(Entreprise e) {
        if (e == null) {
            return null;
        }
        return EntrepriseDto.builder()
                .id(e.getId())
                .raisonSociale(e.getRaisonSociale())
                .nif(e.getNif())
                .adresse(e.getAdresse())
                .situationFiscale(e.getSituationFiscale())
                .build();
    }

    public void assertSousTraitantEntrepriseAuthorizedOnCertificat(Long certificatCreditId, Long sousTraitantEntrepriseId) {
        if (certificatCreditId == null || sousTraitantEntrepriseId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: paramètres manquants");
        }
        repository.findByCertificatCreditIdAndSousTraitantEntrepriseIdAndStatut(certificatCreditId, sousTraitantEntrepriseId, StatutSousTraitance.AUTORISEE)
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: entreprise sous-traitante non autorisée sur ce certificat"));
    }

    private SousTraitanceDto toDto(SousTraitance st) {
        if (st == null) {
            return null;
        }
        CertificatCredit c = st.getCertificatCredit();
        return SousTraitanceDto.builder()
                .id(st.getId())
                .certificatCreditId(c != null ? c.getId() : null)
                .certificatNumero(c != null ? c.getNumero() : null)
                .entrepriseSourceId(c != null && c.getEntreprise() != null ? c.getEntreprise().getId() : null)

                .sousTraitantEntrepriseId(st.getSousTraitantEntreprise() != null ? st.getSousTraitantEntreprise().getId() : null)
                .sousTraitantEntrepriseRaisonSociale(st.getSousTraitantEntreprise() != null ? st.getSousTraitantEntreprise().getRaisonSociale() : null)
                .sousTraitantEntrepriseNif(st.getSousTraitantEntreprise() != null ? st.getSousTraitantEntreprise().getNif() : null)

                .contratEnregistre(st.getContratEnregistre())
                .volumes(st.getVolumes())
                .quantites(st.getQuantites())
                .dateAutorisation(st.getDateAutorisation())
                .statut(st.getStatut())
                .build();
    }
}
