package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.DocumentSousTraitance;
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
import mr.gov.finances.sgci.web.dto.SousTraitanceDto;
import mr.gov.finances.sgci.web.dto.SousTraitanceOnboardingResultDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    public List<SousTraitanceDto> findAll(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }

        if (user.getRole() == Role.ENTREPRISE) {
            Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                return List.of();
            }
            Long entrepriseId = u.getEntreprise().getId();
            return repository.findByCertificatCreditEntrepriseIdOrSousTraitantEntrepriseId(entrepriseId, entrepriseId)
                    .stream()
                    .distinct()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SousTraitanceDto findById(Long id, AuthenticatedUser user) {
        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sous-traitance non trouvée: " + id));

        if (user != null && user.getRole() == Role.ENTREPRISE) {
            Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            Long entId = u.getEntreprise() != null ? u.getEntreprise().getId() : null;
            Long sourceEntId = st.getCertificatCredit() != null && st.getCertificatCredit().getEntreprise() != null
                    ? st.getCertificatCredit().getEntreprise().getId()
                    : null;
            if (entId == null || sourceEntId == null || !entId.equals(sourceEntId)) {
                throw new RuntimeException("Accès refusé: sous-traitance hors périmètre");
            }
        }

        return toDto(st);
    }

    @Transactional
    public SousTraitanceDto create(CreateSousTraitanceRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw new RuntimeException("Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw new RuntimeException("Seule l'entreprise attributaire peut soumettre une sous-traitance");
        }

        CertificatCredit c = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> new RuntimeException("Certificat non trouvé"));
        if (c.getStatut() != StatutCertificat.OUVERT) {
            throw new RuntimeException("Le certificat doit être OUVERT");
        }

        Utilisateur demandeur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (demandeur.getEntreprise() == null || demandeur.getEntreprise().getId() == null
                || c.getEntreprise() == null || c.getEntreprise().getId() == null
                || !demandeur.getEntreprise().getId().equals(c.getEntreprise().getId())) {
            throw new RuntimeException("Accès refusé: certificat ne correspond pas à l'entreprise");
        }

        Entreprise sousTraitantEntreprise = entrepriseRepository.findById(request.getSousTraitantEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise sous-traitante non trouvée"));

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
            throw new RuntimeException("Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw new RuntimeException("Seule l'entreprise attributaire peut créer un sous-traitant");
        }

        CertificatCredit c = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> new RuntimeException("Certificat non trouvé"));
        if (c.getStatut() != StatutCertificat.OUVERT) {
            throw new RuntimeException("Le certificat doit être OUVERT");
        }

        Utilisateur demandeur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (demandeur.getEntreprise() == null || demandeur.getEntreprise().getId() == null
                || c.getEntreprise() == null || c.getEntreprise().getId() == null
                || !demandeur.getEntreprise().getId().equals(c.getEntreprise().getId())) {
            throw new RuntimeException("Accès refusé: certificat ne correspond pas à l'entreprise");
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
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut autoriser une sous-traitance");
        }

        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sous-traitance non trouvée: " + id));

        if (st.getStatut() == StatutSousTraitance.AUTORISEE) {
            return toDto(st);
        }
        if (st.getStatut() == StatutSousTraitance.REFUSEE) {
            throw new RuntimeException("Sous-traitance déjà refusée");
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
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut refuser une sous-traitance");
        }

        SousTraitance st = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sous-traitance non trouvée: " + id));
        if (st.getStatut() == StatutSousTraitance.AUTORISEE) {
            throw new RuntimeException("Sous-traitance déjà autorisée");
        }

        st.setStatut(StatutSousTraitance.REFUSEE);
        st.setDateAutorisation(null);
        st = repository.save(st);

        SousTraitanceDto result = toDto(st);
        auditService.log(AuditAction.UPDATE, "SousTraitance", String.valueOf(st.getId()), result);
        return result;
    }

    public void assertSousTraitantEntrepriseAuthorizedOnCertificat(Long certificatCreditId, Long sousTraitantEntrepriseId) {
        if (certificatCreditId == null || sousTraitantEntrepriseId == null) {
            throw new RuntimeException("Accès refusé: paramètres manquants");
        }
        repository.findByCertificatCreditIdAndSousTraitantEntrepriseIdAndStatut(certificatCreditId, sousTraitantEntrepriseId, StatutSousTraitance.AUTORISEE)
                .orElseThrow(() -> new RuntimeException("Accès refusé: entreprise sous-traitante non autorisée sur ce certificat"));
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
