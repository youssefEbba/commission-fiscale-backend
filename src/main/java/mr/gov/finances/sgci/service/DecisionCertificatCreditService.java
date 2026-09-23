package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DecisionCertificatCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.document.DocumentCodeValidator;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.AdminVisaCertificatResultDto;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import mr.gov.finances.sgci.web.dto.DocumentCertificatCreditDto;
import mr.gov.finances.sgci.web.dto.VisaCertificatStatutDto;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionCertificatCreditService {

    private static final Set<Role> VISA_REQUIRED_ROLES = EnumSet.of(Role.DGI, Role.DGD, Role.DGTCP);
    private static final Set<StatutCertificat> DECISION_ALLOWED_STATUTS = EnumSet.of(
            StatutCertificat.EN_CONTROLE, StatutCertificat.INCOMPLETE, StatutCertificat.A_RECONTROLER
    );

    /**
     * Rôles susceptibles de porter un visa sur le certificat, dans l'ordre d'affichage, la
     * validation du Président en dernier. DGB dispose d'une file d'attente mais ne vise pas le
     * certificat : il est volontairement absent.
     */
    private static final List<Role> VISA_ROLES_ORDER = List.of(Role.DGI, Role.DGD, Role.DGTCP, Role.PRESIDENT);

    private final DecisionCertificatCreditRepository decisionRepository;
    private final CertificatCreditRepository certificatRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AuditService auditService;
    private final WorkflowNotificationHelper workflowNotificationHelper;
    private final DocumentRequirementValidator documentRequirementValidator;
    private final VisaRequirementResolver visaRequirementResolver;
    private final CertificatCreditWorkflow workflow;
    private final CertificatCreditService certificatCreditService;
    private final DocumentCertificatCreditService documentCertificatCreditService;

    @Transactional
    public DecisionCreditDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        DecisionCertificatCredit decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Décision certificat non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long certificatId = decision.getCertificatCredit() != null ? decision.getCertificatCredit().getId() : null;
        if (certificatId != null) {
            boolean anyOpen = !decisionRepository.findByCertificatCreditIdAndDecisionAndRejetTempStatus(
                    certificatId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!anyOpen) {
                CertificatCredit certificat = certificatRepository.findById(certificatId).orElse(null);
                if (certificat != null && certificat.getStatut() == StatutCertificat.INCOMPLETE) {
                    certificat.setStatut(StatutCertificat.A_RECONTROLER);
                    certificatRepository.save(certificat);
                    workflowNotificationHelper.certificatRejetTempResolu(certificat, user);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCreditDto saveDecision(Long certificatCreditId,
                                         DecisionCorrectionType decision,
                                         String motifRejet,
                                         Set<String> documentsDemandes,
                                         AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();

        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + certificatCreditId));

        assertDecisionPossible(certificat, role);

        if (decision == DecisionCorrectionType.REJET_TEMP) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
            }
            if (documentsDemandes == null || documentsDemandes.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
            }
            Set<String> normalizedDocuments = documentsDemandes.stream()
                    .map(DocumentCodeValidator::normalize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            if (normalizedDocuments.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La liste des documents demandés est obligatoire");
            }
            documentRequirementValidator.assertDocumentsConfiguredForProcessus(ProcessusDocument.MISE_EN_PLACE_CI, normalizedDocuments);
            documentsDemandes = normalizedDocuments;
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (decision == DecisionCorrectionType.VISA) {
            assertVisaMembrePossible(certificat, role);
        }

        DecisionCertificatCredit entity = DecisionCertificatCredit.builder()
                .certificatCredit(certificat)
                .role(role)
                .build();

        entity.setDecision(decision);
        entity.setMotifRejet(decision == DecisionCorrectionType.REJET_TEMP ? motifRejet : null);
        if (decision == DecisionCorrectionType.REJET_TEMP) {
            entity.setDocumentsDemandes(new HashSet<>(documentsDemandes));
            entity.setRejetTempStatus(RejetTempStatus.OUVERT);
            entity.setRejetTempResolvedAt(null);
            certificat.setStatut(StatutCertificat.INCOMPLETE);
        } else {
            entity.getDocumentsDemandes().clear();
            entity.setRejetTempStatus(RejetTempStatus.RESOLU);
            entity.setRejetTempResolvedAt(Instant.now());
        }
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);

        entity = decisionRepository.save(entity);
        certificatRepository.save(certificat);

        if (decision == DecisionCorrectionType.VISA) {
            tryAutoTransitionToPresident(certificat);
        }

        workflowNotificationHelper.certificatDecision(certificat, decision.name(), user, motifRejet,
                decision == DecisionCorrectionType.REJET_TEMP ? entity.getDocumentsDemandes() : null,
                entity.getId());

        auditService.log(AuditAction.UPDATE, "DecisionCertificatCredit",
                String.valueOf(entity.getId()), toDto(entity));

        return toDto(entity);
    }

    /**
     * Vérifie si les 3 visas (DGI, DGD, DGTCP) sont présents et qu'aucun
     * rejet temporaire n'est ouvert. Si oui, auto-transition vers EN_VALIDATION_PRESIDENT.
     */
    private void tryAutoTransitionToPresident(CertificatCredit certificat) {
        Long certId = certificat.getId();

        boolean anyOpenRejet = !decisionRepository.findByCertificatCreditIdAndDecisionAndRejetTempStatus(
                certId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT).isEmpty();
        if (anyOpenRejet) {
            return;
        }

        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCertificat(certificat);
        boolean allVisas = requiredRoles.stream().allMatch(r ->
                decisionRepository.existsByCertificatCreditIdAndRoleAndDecision(
                        certId, r, DecisionCorrectionType.VISA));

        if (allVisas) {
            workflow.validateTransition(certificat.getStatut(), StatutCertificat.EN_VALIDATION_PRESIDENT);
            certificat.setStatut(StatutCertificat.EN_VALIDATION_PRESIDENT);
            certificatRepository.save(certificat);

            workflowNotificationHelper.certificatPresidentValidation(certificat);
        }
    }

    /**
     * Socle commun aux décisions VISA et REJET_TEMP : rôle habilité, rôle concerné par les
     * enveloppes du certificat, statut ouvert aux décisions, et absence de visa déjà posé.
     */
    private void assertDecisionPossible(CertificatCredit certificat, Role role) {
        if (!VISA_REQUIRED_ROLES.contains(role)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Rôle non autorisé pour la décision mise en place: " + role
                            + ". Seuls DGI, DGD et DGTCP peuvent apposer un visa ou rejet temporaire.");
        }

        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCertificat(certificat);
        if (!requiredRoles.contains(role)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Rôle non concerné par ce certificat (crédit associé nul): " + role
                            + ". Visas requis: " + requiredRoles);
        }

        if (!DECISION_ALLOWED_STATUTS.contains(certificat.getStatut())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le certificat doit être en statut EN_CONTROLE, INCOMPLETE ou A_RECONTROLER "
                            + "pour recevoir un visa ou rejet. Statut actuel: " + certificat.getStatut());
        }

        boolean visaAlreadyForRole = decisionRepository.existsByCertificatCreditIdAndRoleAndDecision(
                certificat.getId(), role, DecisionCorrectionType.VISA);
        if (visaAlreadyForRole) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Décision impossible: un visa a déjà été accordé par " + role
                            + ". Le visa clôture les interactions sur cette demande.");
        }
    }

    /**
     * Garde-fous d'un visa de membre, identiques que le visa soit posé par le rôle titulaire
     * lui-même ou par l'administrateur à sa place.
     */
    private void assertVisaMembrePossible(CertificatCredit certificat, Role role) {
        assertDecisionPossible(certificat, role);

        boolean openRejetForRole = decisionRepository.existsByCertificatCreditIdAndRoleAndDecisionAndRejetTempStatus(
                certificat.getId(), role, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT);
        if (openRejetForRole) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "VISA impossible: un ou plusieurs rejets temporaires sont encore ouverts pour ce rôle. "
                            + "Résolvez-les via PUT .../decisions/{id}/resolve pour chaque rejet concerné.");
        }

        if (role == Role.DGTCP) {
            assertMontantsRenseignes(certificat);
        }
    }

    private void assertMontantsRenseignes(CertificatCredit entity) {
        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCertificat(entity);
        boolean cordonRequis = requiredRoles.contains(Role.DGD);
        boolean tvaRequise = requiredRoles.contains(Role.DGI);
        if (cordonRequis
                && (entity.getMontantCordon() == null || entity.getMontantCordon().compareTo(BigDecimal.ZERO) <= 0)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "DGTCP doit renseigner le montant cordon (crédit extérieur), strictement supérieur à zéro, avant d'apposer le visa");
        }
        if (tvaRequise
                && (entity.getMontantTVAInterieure() == null || entity.getMontantTVAInterieure().compareTo(BigDecimal.ZERO) <= 0)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "DGTCP doit renseigner le montant TVA intérieure (crédit intérieur), strictement supérieur à zéro, avant d'apposer le visa");
        }
    }

    /**
     * Code du document exigé avant le visa de ce rôle, {@code null} si aucun.
     *
     * <p>Les visas DGI / DGD / DGTCP n'exigent aucune pièce sur la mise en place : la seule pièce
     * conditionnante est le certificat signé du Président. Le paramètre {@code certificat} est
     * conservé pour permettre une exigence conditionnelle ultérieure.
     */
    private String codeDocumentRequisPourVisa(CertificatCredit certificat, Role role) {
        if (role == Role.PRESIDENT) {
            return TypeDocument.CERTIFICAT_CREDIT_IMPOTS.name();
        }
        return null;
    }

    /** Statuts à partir desquels la validation présidentielle est acquise. */
    private static boolean validationPresidentAcquise(StatutCertificat statut) {
        return statut == StatutCertificat.VALIDE_PRESIDENT
                || statut == StatutCertificat.EN_OUVERTURE_DGTCP
                || statut == StatutCertificat.OUVERT
                || statut == StatutCertificat.MODIFIE
                || statut == StatutCertificat.CLOTURE;
    }

    /**
     * Refuse la validation présidentielle si le certificat n'est pas au stade attendu. Mêmes règles
     * que {@code CertificatCreditService.adminValiderPourPresident}, vérifiées ici en amont pour
     * qu'un visa refusé ne laisse pas de fichier orphelin sur le stockage.
     */
    private void assertValidationPresidentPossible(CertificatCredit certificat) {
        StatutCertificat actuel = certificat.getStatut();
        if (validationPresidentAcquise(actuel)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Validation impossible: le certificat est déjà validé. Statut actuel: " + actuel);
        }
        if (actuel != StatutCertificat.EN_VALIDATION_PRESIDENT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le certificat doit être en statut EN_VALIDATION_PRESIDENT : les visas requis "
                            + visaRequirementResolver.requiredRolesForCertificat(certificat)
                            + " doivent être posés au préalable. Statut actuel: " + actuel);
        }
    }

    /**
     * État des visas du point de vue de l'administrateur.
     *
     * <p>La possibilité de viser est déduite des assertions réellement appliquées plutôt que d'une
     * réécriture des règles : l'écran ne peut donc pas diverger du comportement du service.
     */
    @Transactional(readOnly = true)
    public List<VisaCertificatStatutDto> visaStatuts(Long certificatCreditId) {
        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Certificat de crédit non trouvé: " + certificatCreditId));
        return buildVisaStatuts(certificat);
    }

    private List<VisaCertificatStatutDto> buildVisaStatuts(CertificatCredit certificat) {
        Long certId = certificat.getId();
        Set<Role> requiredRoles = visaRequirementResolver.requiredRolesForCertificat(certificat);
        List<DecisionCertificatCredit> decisions = decisionRepository.findByCertificatCreditId(certId);
        Set<String> documentsActifs = new HashSet<>(documentCertificatCreditService.findActiveDocumentTypes(certId));

        List<VisaCertificatStatutDto> statuts = new ArrayList<>();
        for (Role role : VISA_ROLES_ORDER) {
            String codeDocument = codeDocumentRequisPourVisa(certificat, role);
            boolean documentPresent = codeDocument == null || documentsActifs.contains(codeDocument);

            VisaCertificatStatutDto.VisaCertificatStatutDtoBuilder builder = VisaCertificatStatutDto.builder()
                    .role(role)
                    .codeDocumentRequis(codeDocument)
                    .documentRequisPresent(documentPresent);

            if (role == Role.PRESIDENT) {
                boolean pose = validationPresidentAcquise(certificat.getStatut());
                boolean auStade = certificat.getStatut() == StatutCertificat.EN_VALIDATION_PRESIDENT;
                String blocage = null;
                if (pose) {
                    blocage = "Certificat déjà validé";
                } else if (!auStade) {
                    blocage = "Validation possible uniquement en statut EN_VALIDATION_PRESIDENT. Statut actuel: "
                            + certificat.getStatut();
                } else if (!documentPresent) {
                    // N'empêche pas de viser : la pièce peut être fournie dans le même appel.
                    blocage = "Le certificat signé (" + codeDocument + ") doit être téléversé avec la validation";
                }
                statuts.add(builder.requis(true).pose(pose)
                        .visableParAdmin(!pose && auStade)
                        .motifBlocage(blocage).build());
                continue;
            }

            DecisionCertificatCredit visa = decisions.stream()
                    .filter(d -> d.getRole() == role && d.getDecision() == DecisionCorrectionType.VISA)
                    .findFirst().orElse(null);
            boolean rejetOuvert = decisions.stream().anyMatch(d -> d.getRole() == role
                    && d.getDecision() == DecisionCorrectionType.REJET_TEMP
                    && d.getRejetTempStatus() == RejetTempStatus.OUVERT);

            builder.requis(requiredRoles.contains(role))
                    .pose(visa != null)
                    .rejetTempOuvert(rejetOuvert);
            if (visa != null) {
                builder.decisionId(visa.getId())
                        .datePose(visa.getDateDecision())
                        .utilisateurId(visa.getUtilisateur() != null ? visa.getUtilisateur().getId() : null)
                        .utilisateurNom(visa.getUtilisateur() != null ? visa.getUtilisateur().getNomComplet() : null)
                        .visaParAdmin(Boolean.TRUE.equals(visa.getVisaParAdmin()));
            }

            try {
                assertVisaMembrePossible(certificat, role);
                statuts.add(builder.visableParAdmin(true).build());
            } catch (ApiException e) {
                statuts.add(builder.visableParAdmin(false).motifBlocage(e.getMessage()).build());
            }
        }
        return statuts;
    }

    /**
     * Visa posé par l'administrateur (ADMIN_SI) à la place d'un membre de la commission
     * (DGI / DGD / DGTCP), ou validation prononcée à la place du Président.
     *
     * <p>Pendant exact de {@code DecisionCorrectionService.adminVisaPourRole}. L'ouverture du crédit
     * qui suit la validation présidentielle reste une action distincte, sous sa propre permission.
     */
    @Transactional
    public AdminVisaCertificatResultDto adminVisaPourRole(Long certificatCreditId,
                                                         Role roleCible,
                                                         String motif,
                                                         MultipartFile file,
                                                         AuthenticatedUser user) throws IOException {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Visa administrateur réservé à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le motif du visa administrateur est obligatoire");
        }
        if (roleCible == null || !VISA_ROLES_ORDER.contains(roleCible)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Rôle de visa invalide. Rôles attendus: " + VISA_ROLES_ORDER);
        }
        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Certificat de crédit non trouvé: " + certificatCreditId));

        // Contrôles métier d'abord : un visa refusé ne doit pas laisser de fichier orphelin.
        if (roleCible == Role.PRESIDENT) {
            assertValidationPresidentPossible(certificat);
        } else {
            assertVisaMembrePossible(certificat, roleCible);
        }

        String codeDocumentRequis = codeDocumentRequisPourVisa(certificat, roleCible);
        boolean fichierFourni = file != null && !file.isEmpty();
        if (fichierFourni && codeDocumentRequis == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Aucun document n'est attendu pour le visa " + roleCible + " sur ce certificat");
        }

        DocumentCertificatCreditDto document = null;
        if (fichierFourni) {
            document = documentCertificatCreditService.adminReplace(
                    certificatCreditId, codeDocumentRequis, motif, file, user);
        }
        if (codeDocumentRequis != null) {
            documentCertificatCreditService.assertActiveDocumentPresent(certificatCreditId, codeDocumentRequis,
                    "avant le visa " + roleCible + " (à téléverser avec le visa administrateur)");
        }

        DecisionCreditDto decisionDto = null;
        if (roleCible == Role.PRESIDENT) {
            certificatCreditService.adminValiderPourPresident(certificatCreditId, motif, user);
        } else {
            decisionDto = enregistrerVisaAdminMembre(certificat, roleCible, motif, user);
        }

        CertificatCredit rafraichi = certificatRepository.findById(certificatCreditId).orElse(certificat);
        return AdminVisaCertificatResultDto.builder()
                .certificat(certificatCreditService.findById(certificatCreditId, user))
                .decision(decisionDto)
                .document(document)
                .visas(buildVisaStatuts(rafraichi))
                .build();
    }

    /** Création du visa d'un membre (DGI / DGD / DGTCP) posé par l'administrateur à sa place. */
    private DecisionCreditDto enregistrerVisaAdminMembre(CertificatCredit certificat,
                                                         Role roleCible,
                                                         String motif,
                                                         AuthenticatedUser user) {
        assertVisaMembrePossible(certificat, roleCible);
        Utilisateur administrateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        StatutCertificat statutAvantVisa = certificat.getStatut();

        DecisionCertificatCredit entity = DecisionCertificatCredit.builder()
                .certificatCredit(certificat)
                .role(roleCible)
                .build();
        entity.setDecision(DecisionCorrectionType.VISA);
        entity.setMotifRejet(null);
        entity.getDocumentsDemandes().clear();
        entity.setRejetTempStatus(RejetTempStatus.RESOLU);
        entity.setRejetTempResolvedAt(Instant.now());
        entity.setDateDecision(Instant.now());
        // L'auteur réel reste l'administrateur ; le rôle substitué est porté par le champ role.
        entity.setUtilisateur(administrateur);
        entity.setVisaParAdmin(Boolean.TRUE);
        entity.setMotifAdmin(motif);

        entity = decisionRepository.save(entity);
        tryAutoTransitionToPresident(certificat);

        DecisionCreditDto dto = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "DecisionCertificatCredit",
                String.valueOf(entity.getId()), dto, motif);
        workflowNotificationHelper.certificatDecision(certificat, DecisionCorrectionType.VISA.name(), user,
                motif, null, entity.getId());
        if (certificat.getStatut() != statutAvantVisa) {
            workflowNotificationHelper.certificatStatut(certificat, certificat.getStatut().name(), user, null);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<DecisionCreditDto> findByCertificat(Long certificatCreditId) {
        return decisionRepository.findByCertificatCreditId(certificatCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DecisionCreditDto toDto(DecisionCertificatCredit entity) {
        return DecisionCreditDto.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .decision(entity.getDecision())
                .motifRejet(entity.getMotifRejet())
                .documentsDemandes(entity.getDocumentsDemandes())
                .dateDecision(entity.getDateDecision())
                .rejetTempStatus(entity.getRejetTempStatus())
                .rejetTempResolvedAt(entity.getRejetTempResolvedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .visaParAdmin(Boolean.TRUE.equals(entity.getVisaParAdmin()))
                .motifAdmin(entity.getMotifAdmin())
                .rejetTempResponses(entity.getRejetTempResponses() != null
                        ? entity.getRejetTempResponses().stream().map(this::toResponseDto).collect(Collectors.toList())
                        : java.util.List.of())
                .build();
    }

    private mr.gov.finances.sgci.web.dto.RejetTempResponseDto toResponseDto(RejetTempResponse entity) {
        return mr.gov.finances.sgci.web.dto.RejetTempResponseDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .documentUrl(entity.getDocumentUrl())
                .codeDocument(entity.getCodeDocument())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }
}
