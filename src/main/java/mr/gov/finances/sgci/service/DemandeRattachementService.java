package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.DemandeRattachement;
import mr.gov.finances.sgci.domain.entity.DocumentDemandeRattachement;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.DemandeRattachementRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateDemandeRattachementRequest;
import mr.gov.finances.sgci.web.dto.DemandeRattachementDto;
import mr.gov.finances.sgci.web.dto.SuiviRattachementDto;
import mr.gov.finances.sgci.web.dto.DocumentDemandeRattachementDto;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.EntitePubliqueDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rattachement d'une personne à une entreprise ou à une autorité contractante, sur décision du
 * Président.
 *
 * <p>Remplace l'inscription libre, qui créait le compte <em>et</em> l'entité d'un même geste, sans
 * que personne n'ait rien validé. Ici le dépôt ne crée rien : il enregistre une demande et ses
 * justificatifs. C'est l'approbation qui fait naître l'entité, si elle manquait, puis le compte.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandeRattachementService {

    /**
     * Les seules qualités qu'une inscription peut viser.
     *
     * <p>L'ancienne route acceptait l'énumération {@code Role} au complet, {@code PRESIDENT} et
     * {@code ADMIN_SI} compris. Les comptes de la Commission se créent depuis l'administration, et
     * jamais par une demande venue de l'extérieur.
     */
    public static final Set<Role> ROLES_DEMANDABLES = EnumSet.of(
            Role.ENTREPRISE,
            Role.AUTORITE_CONTRACTANTE,
            Role.AUTORITE_UPM,
            Role.AUTORITE_UEP);

    /** Limite de la recherche publique d'entités : assez pour choisir, trop peu pour aspirer. */
    private static final int MAX_RESULTATS_PUBLICS = 20;

    private final DemandeRattachementRepository repository;
    private final DocumentRequirementRepository requirementRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AutoriteContractanteRepository autoriteContractanteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentRequirementValidator requirementValidator;
    private final DocumentRequirementService documentRequirementService;
    private final MinioService minioService;
    private final PasswordEncoder passwordEncoder;
    private final ReferenceSequenceGenerator referenceGenerator;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AuditService auditService;

    // ── le dépôt, sans compte ──────────────────────────────────────────────────────────────────

    @Transactional
    public DemandeRattachementDto deposer(CreateDemandeRattachementRequest request,
                                         Map<String, MultipartFile> justificatifs) {
        assertRoleDemandable(request.getRoleDemande());
        assertRoleCoherentAvecEntite(request.getRoleDemande(), request.getTypeEntite());
        assertIdentifiantDisponible(request.getUsername());

        DemandeRattachement demande = DemandeRattachement.builder()
                .reference(referenceGenerator.next(ReferenceSequenceGenerator.PREFIX_RATTACHEMENT))
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nomComplet(request.getNomComplet())
                .email(request.getEmail().trim())
                .telephone(request.getTelephone())
                .roleDemande(request.getRoleDemande())
                .typeEntite(request.getTypeEntite())
                .statut(StatutDemandeRattachement.EN_ATTENTE)
                .dateCreation(Instant.now())
                .build();

        rattacherLaCible(demande, request);
        demande = repository.save(demande);
        attacherJustificatifs(demande, justificatifs);

        DemandeRattachementDto result = toDto(demande);
        auditService.log(AuditAction.CREATE, "DemandeRattachement", String.valueOf(demande.getId()),
                Map.of("reference", demande.getReference(),
                        "username", demande.getUsername(),
                        "role", demande.getRoleDemande().name()));
        notifierLesValidateurs(demande);
        return result;
    }

    /**
     * Résout l'entité visée : celle choisie dans le référentiel, ou le descriptif de celle à créer.
     *
     * <p>Le descriptif n'est pas persisté comme entité : la créer au dépôt ramènerait exactement le
     * défaut que ce lot corrige — une entité née sans que personne ne l'ait validée.
     */
    private void rattacherLaCible(DemandeRattachement demande, CreateDemandeRattachementRequest request) {
        if (request.getEntiteId() != null) {
            if (request.getTypeEntite() == TypeEntiteRattachement.ENTREPRISE) {
                demande.setEntreprise(entrepriseRepository.findById(request.getEntiteId())
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                                "Entreprise non trouvée")));
            } else {
                demande.setAutoriteContractante(autoriteContractanteRepository.findById(request.getEntiteId())
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                                "Autorité contractante non trouvée")));
            }
            return;
        }
        if (request.getEntiteNom() == null || request.getEntiteNom().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Choisissez une entité existante, ou indiquez le nom de celle à créer");
        }
        assertDescriptifCoherentAvecLeType(request);
        demande.setEntiteNom(request.getEntiteNom().trim());
        demande.setEntiteSigle(request.getEntiteSigle());
        demande.setEntiteNif(request.getEntiteNif());
        demande.setEntiteAdresse(request.getEntiteAdresse());
        demande.setEntiteActivite(request.getEntiteActivite());
        demande.setEntiteMinistereTutelle(request.getEntiteMinistereTutelle());
    }

    /**
     * Enregistre les justificatifs et vérifie qu'aucune pièce obligatoire ne manque.
     *
     * <p>Chaque fichier est nommé par son code document, ce qui évite d'aligner deux tableaux
     * parallèles — l'erreur classique où le troisième fichier se retrouve classé sous le code du
     * deuxième. La validation de type et de taille est déléguée à {@link DocumentRequirementValidator},
     * déjà utilisé par les autres dépôts du projet.
     */
    private void attacherJustificatifs(DemandeRattachement demande, Map<String, MultipartFile> justificatifs) {
        Map<String, MultipartFile> fichiers = justificatifs != null ? justificatifs : Map.of();

        for (Map.Entry<String, MultipartFile> entry : fichiers.entrySet()) {
            MultipartFile file = entry.getValue();
            if (file == null || file.isEmpty()) {
                continue;
            }
            String code = entry.getKey();
            requirementValidator.validateUpload(ProcessusDocument.RATTACHEMENT_COMPTE, code, file);
            String chemin = minioService.uploadFile(file);
            demande.getDocuments().add(DocumentDemandeRattachement.builder()
                    .demandeRattachement(demande)
                    .codeDocument(code)
                    .nomFichier(file.getOriginalFilename() != null ? file.getOriginalFilename() : code)
                    .chemin(chemin)
                    .taille(file.getSize())
                    .dateUpload(Instant.now())
                    .build());
        }

        List<String> manquantes = piecesObligatoires().stream()
                .filter(code -> !fichiers.containsKey(code)
                        || fichiers.get(code) == null
                        || fichiers.get(code).isEmpty())
                .toList();
        if (!manquantes.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Justificatif obligatoire manquant : " + String.join(", ", manquantes));
        }
        repository.save(demande);
    }

    private List<String> piecesObligatoires() {
        return requirementRepository
                .findByProcessusOrderByOrdreAffichageAsc(ProcessusDocument.RATTACHEMENT_COMPTE).stream()
                .filter(r -> Boolean.TRUE.equals(r.getObligatoire()))
                .map(DocumentRequirement::getCodeDocument)
                .toList();
    }

    // ── les lectures publiques ─────────────────────────────────────────────────────────────────

    /**
     * Les justificatifs attendus, pour que le formulaire les affiche sans être authentifié.
     *
     * <p>Rend le même {@code DocumentRequirementDto} que le reste du projet : le formulaire y lit le
     * libellé, le caractère obligatoire et les types de fichiers acceptés, comme sur les autres
     * dépôts. Un DTO propre à cette route aurait obligé le front à apprendre une seconde forme.
     */
    @Transactional(readOnly = true)
    public List<DocumentRequirementDto> piecesAttendues() {
        return documentRequirementService.findByProcessus(ProcessusDocument.RATTACHEMENT_COMPTE);
    }

    /**
     * Recherche d'entité pour le formulaire public, réduite à l'identifiant et au nom.
     *
     * <p>Aucune donnée fiscale, aucun montant, aucun NIF : cette route est ouverte à tous, et elle
     * ne doit rien livrer de plus que ce qu'il faut pour choisir dans une liste. Le filtrage se fait
     * en mémoire sur un référentiel de quelques centaines de lignes, ce qui évite d'ajouter des
     * requêtes de recherche pour un besoin aussi étroit.
     */
    @Transactional(readOnly = true)
    public List<EntitePubliqueDto> rechercherEntites(TypeEntiteRattachement type, String q) {
        String terme = q != null ? q.trim().toLowerCase() : "";
        if (type == TypeEntiteRattachement.ENTREPRISE) {
            return entrepriseRepository.findAll().stream()
                    .filter(e -> e.getRaisonSociale() != null)
                    .filter(e -> terme.isEmpty() || e.getRaisonSociale().toLowerCase().contains(terme))
                    .sorted(Comparator.comparing(Entreprise::getRaisonSociale))
                    .limit(MAX_RESULTATS_PUBLICS)
                    .map(e -> new EntitePubliqueDto(e.getId(), e.getRaisonSociale()))
                    .collect(Collectors.toList());
        }
        return autoriteContractanteRepository.findAll().stream()
                .filter(a -> a.getNom() != null)
                .filter(a -> terme.isEmpty() || a.getNom().toLowerCase().contains(terme))
                .sorted(Comparator.comparing(AutoriteContractante::getNom))
                .limit(MAX_RESULTATS_PUBLICS)
                .map(a -> new EntitePubliqueDto(a.getId(), a.getNom()))
                .collect(Collectors.toList());
    }

    // ── la file du Président ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DemandeRattachementDto> lister(StatutDemandeRattachement statut) {
        List<DemandeRattachement> demandes = statut != null
                ? repository.findByStatutOrderByDateCreationDesc(statut)
                : repository.findAllByOrderByDateCreationDesc();
        return demandes.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandeRattachementDto obtenir(Long id) {
        return toDto(chargee(id));
    }

    @Transactional
    public DemandeRattachementDto approuver(Long id, AuthenticatedUser user) {
        DemandeRattachement demande = chargee(id);
        assertEnAttente(demande);
        // L'identifiant a pu être pris entre le dépôt et la décision : le revérifier ici évite un
        // échec de contrainte au moment le plus coûteux, après la création de l'entité. On ne
        // revérifie en revanche pas l'absence de demande en attente : celle qu'on instruite en est
        // une, et le contrôle se verrait lui-même.
        assertIdentifiantLibreCoteComptes(demande.getUsername());

        if (demande.porteUneEntiteNouvelle()) {
            creerEntite(demande);
        }

        Utilisateur compte = utilisateurRepository.save(Utilisateur.builder()
                .username(demande.getUsername())
                .passwordHash(demande.getPasswordHash())
                .role(demande.getRoleDemande())
                .entreprise(demande.getEntreprise())
                .autoriteContractante(demande.getAutoriteContractante())
                .nomComplet(demande.getNomComplet())
                .email(demande.getEmail())
                .actif(true)
                .build());

        demande.setUtilisateurCree(compte);
        demande.setStatut(StatutDemandeRattachement.APPROUVEE);
        demande.setDateTraitement(Instant.now());
        demande.setTraitePar(acteur(user));
        demande = repository.save(demande);

        auditService.log(AuditAction.UPDATE, "DemandeRattachement", String.valueOf(demande.getId()),
                Map.of("statut", StatutDemandeRattachement.APPROUVEE.name(),
                        "utilisateurCreeId", compte.getId()));
        emailService.sendRattachementDecision(demande.getEmail(), demande.getUsername(), true, null);
        notificationService.notifyUser(compte.getId(), NotificationType.RATTACHEMENT_TRAITEE,
                "DemandeRattachement", demande.getId(),
                "Votre rattachement a été approuvé. Vous pouvez vous connecter.",
                Map.of("statut", StatutDemandeRattachement.APPROUVEE.name()));
        return toDto(demande);
    }

    @Transactional
    public DemandeRattachementDto refuser(Long id, String motif, AuthenticatedUser user) {
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le motif de refus est obligatoire");
        }
        DemandeRattachement demande = chargee(id);
        assertEnAttente(demande);

        demande.setStatut(StatutDemandeRattachement.REFUSEE);
        demande.setMotifRefus(motif.trim());
        demande.setDateTraitement(Instant.now());
        demande.setTraitePar(acteur(user));
        demande = repository.save(demande);

        auditService.log(AuditAction.UPDATE, "DemandeRattachement", String.valueOf(demande.getId()),
                Map.of("statut", StatutDemandeRattachement.REFUSEE.name()));
        // Par courriel seulement : le demandeur refusé n'a pas de compte pour lire une notification.
        emailService.sendRattachementDecision(demande.getEmail(), demande.getUsername(), false,
                demande.getMotifRefus());
        return toDto(demande);
    }

    /**
     * Le descriptif fourni doit correspondre au type d'entité déclaré.
     *
     * <p>Les deux inscriptions ne demandent pas les mêmes informations : une entreprise a un NIF et
     * une activité, une autorité contractante a un sigle et un ministère de tutelle. L'écran
     * présente donc deux formulaires distincts — mais sans cette garde, la distinction ne vivrait
     * que dans le navigateur, et un appel direct pourrait déposer une autorité contractante munie
     * d'un NIF. {@code creerEntite} ignorerait alors silencieusement ce NIF à l'approbation : le
     * demandeur aurait saisi une donnée qui disparaît, sans que personne ne lui dise.
     *
     * <p>Ne contrôle que le cas d'une entité à créer : quand une entité existante est choisie, le
     * descriptif n'est pas lu.
     */
    private void assertDescriptifCoherentAvecLeType(CreateDemandeRattachementRequest request) {
        boolean entreprise = request.getTypeEntite() == TypeEntiteRattachement.ENTREPRISE;
        if (entreprise && renseigne(request.getEntiteMinistereTutelle())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le ministère de tutelle ne concerne pas une entreprise : "
                            + "il n'appartient qu'au formulaire d'une autorité contractante.");
        }
        if (!entreprise && (renseigne(request.getEntiteNif()) || renseigne(request.getEntiteActivite()))) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le NIF et l'activité ne concernent pas une autorité contractante : "
                            + "ils n'appartiennent qu'au formulaire d'une entreprise.");
        }
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    /**
     * Le suivi public d'une demande, par sa référence et le courriel du demandeur.
     *
     * <p><b>Le courriel n'est pas une formalité.</b> La référence est séquentielle, donc devinable :
     * sans second élément, n'importe qui pourrait compter de RT-001 à RT-050 et reconstituer la
     * liste des personnes ayant demandé un compte, avec l'entité qu'elles visent. Le demandeur, lui,
     * connaît les deux.
     *
     * <p>Une référence inconnue et un courriel qui ne correspond pas rendent <b>la même erreur</b>.
     * Les distinguer confirmerait l'existence d'une référence à qui la cherche, ce qui rendrait le
     * courriel inutile.
     */
    @Transactional(readOnly = true)
    public SuiviRattachementDto suivre(String reference, String email) {
        if (reference == null || reference.isBlank() || email == null || email.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "La référence et l'adresse e-mail utilisée lors du dépôt sont requises");
        }
        DemandeRattachement demande = repository.findByReference(reference.trim())
                .filter(d -> d.getEmail() != null && d.getEmail().equalsIgnoreCase(email.trim()))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Aucune demande ne correspond à cette référence et à cette adresse e-mail"));

        return SuiviRattachementDto.builder()
                .reference(demande.getReference())
                .statut(demande.getStatut())
                .roleDemande(demande.getRoleDemande())
                .entiteNom(nomDeLEntiteVisee(demande))
                .dateDepot(demande.getDateCreation())
                .dateTraitement(demande.getDateTraitement())
                .motifRefus(demande.getMotifRefus())
                .build();
    }

    /** L'entité choisie dans le référentiel, ou à défaut le nom de celle que la demande crée. */
    private String nomDeLEntiteVisee(DemandeRattachement demande) {
        if (demande.getEntreprise() != null) {
            return demande.getEntreprise().getRaisonSociale();
        }
        if (demande.getAutoriteContractante() != null) {
            return demande.getAutoriteContractante().getNom();
        }
        return demande.getEntiteNom();
    }

    private void creerEntite(DemandeRattachement demande) {
        if (demande.getTypeEntite() == TypeEntiteRattachement.ENTREPRISE) {
            demande.setEntreprise(entrepriseRepository.save(Entreprise.builder()
                    .raisonSociale(demande.getEntiteNom())
                    .nif(demande.getEntiteNif())
                    .adresse(demande.getEntiteAdresse())
                    .activite(demande.getEntiteActivite())
                    .build()));
            return;
        }
        demande.setAutoriteContractante(autoriteContractanteRepository.save(AutoriteContractante.builder()
                .nom(demande.getEntiteNom())
                .code(codeAutoriteDisponible(demande))
                .ministereTutelleNom(demande.getEntiteMinistereTutelle())
                .build()));
    }

    /** Le sigle demandé s'il est libre, sinon un code dérivé de l'identifiant du demandeur. */
    private String codeAutoriteDisponible(DemandeRattachement demande) {
        if (demande.getEntiteSigle() != null && !demande.getEntiteSigle().isBlank()) {
            String candidat = demande.getEntiteSigle().trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
            if (!candidat.isEmpty() && !autoriteContractanteRepository.existsByCode(candidat)) {
                return candidat;
            }
        }
        String base = "AC_" + demande.getUsername().replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        String code = base;
        int suffixe = 1;
        while (autoriteContractanteRepository.existsByCode(code)) {
            code = base + "_" + (++suffixe);
        }
        return code;
    }

    // ── contrôles et utilitaires ───────────────────────────────────────────────────────────────

    private void assertRoleDemandable(Role role) {
        if (role == null || !ROLES_DEMANDABLES.contains(role)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Qualité non recevable par une demande de rattachement. Attendu : "
                            + ROLES_DEMANDABLES.stream().map(Role::name).sorted()
                                    .collect(Collectors.joining(", ")));
        }
    }

    private void assertRoleCoherentAvecEntite(Role role, TypeEntiteRattachement type) {
        if (role == null || type == null) {
            return;
        }
        boolean roleEntreprise = role == Role.ENTREPRISE;
        boolean typeEntreprise = type == TypeEntiteRattachement.ENTREPRISE;
        if (roleEntreprise != typeEntreprise) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "La qualité demandée ne correspond pas au type d'entité visé");
        }
    }

    /** Au dépôt : l'identifiant ne doit être ni pris par un compte, ni réservé par une demande. */
    private void assertIdentifiantDisponible(String username) {
        assertIdentifiantLibreCoteComptes(username);
        if (repository.existsByUsernameAndStatut(username.trim(), StatutDemandeRattachement.EN_ATTENTE)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Une demande est déjà en attente pour ce nom d'utilisateur");
        }
    }

    private void assertIdentifiantLibreCoteComptes(String username) {
        if (username == null || username.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le nom d'utilisateur est obligatoire");
        }
        if (utilisateurRepository.existsByUsername(username.trim())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Ce nom d'utilisateur est déjà utilisé");
        }
    }

    private void assertEnAttente(DemandeRattachement demande) {
        if (demande.getStatut() != StatutDemandeRattachement.EN_ATTENTE) {
            throw ApiException.conflict(ApiErrorCode.STATUT_INCOMPATIBLE,
                    "Cette demande a déjà été traitée");
        }
    }

    private DemandeRattachement chargee(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Demande de rattachement non trouvée: " + id));
    }

    private Utilisateur acteur(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            return null;
        }
        return utilisateurRepository.findById(user.getUserId()).orElse(null);
    }

    private void notifierLesValidateurs(DemandeRattachement demande) {
        List<Long> destinataires = new ArrayList<>();
        utilisateurRepository.findByRoleAndActifTrue(Role.PRESIDENT)
                .forEach(u -> destinataires.add(u.getId()));
        utilisateurRepository.findByRoleAndActifTrue(Role.ADMIN_SI)
                .forEach(u -> destinataires.add(u.getId()));
        if (destinataires.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("demandeId", demande.getId());
        payload.put("reference", demande.getReference());
        payload.put("username", demande.getUsername());
        payload.put("role", demande.getRoleDemande().name());
        notificationService.notifyUsers(destinataires, NotificationType.RATTACHEMENT_DEMANDE,
                "DemandeRattachement", demande.getId(),
                "Demande de rattachement de " + demande.getUsername()
                        + " (" + demande.getRoleDemande().name() + ")",
                payload);
    }

    private DemandeRattachementDto toDto(DemandeRattachement d) {
        Long entiteId = d.getEntreprise() != null ? d.getEntreprise().getId()
                : d.getAutoriteContractante() != null ? d.getAutoriteContractante().getId() : null;
        String entiteNomResolu = d.getEntreprise() != null ? d.getEntreprise().getRaisonSociale()
                : d.getAutoriteContractante() != null ? d.getAutoriteContractante().getNom() : null;

        return DemandeRattachementDto.builder()
                .id(d.getId())
                .reference(d.getReference())
                .username(d.getUsername())
                .nomComplet(d.getNomComplet())
                .email(d.getEmail())
                .telephone(d.getTelephone())
                .roleDemande(d.getRoleDemande())
                .typeEntite(d.getTypeEntite())
                .entiteId(entiteId)
                .entiteNomResolu(entiteNomResolu)
                .entiteNouvelle(d.porteUneEntiteNouvelle())
                .entiteNom(d.getEntiteNom())
                .entiteSigle(d.getEntiteSigle())
                .entiteNif(d.getEntiteNif())
                .entiteAdresse(d.getEntiteAdresse())
                .entiteActivite(d.getEntiteActivite())
                .entiteMinistereTutelle(d.getEntiteMinistereTutelle())
                .statut(d.getStatut())
                .dateCreation(d.getDateCreation())
                .dateTraitement(d.getDateTraitement())
                .traiteParUsername(d.getTraitePar() != null ? d.getTraitePar().getUsername() : null)
                .motifRefus(d.getMotifRefus())
                .utilisateurCreeId(d.getUtilisateurCree() != null ? d.getUtilisateurCree().getId() : null)
                .documents(d.getDocuments().stream()
                        .map(doc -> DocumentDemandeRattachementDto.builder()
                                .id(doc.getId())
                                .codeDocument(doc.getCodeDocument())
                                .nomFichier(doc.getNomFichier())
                                .taille(doc.getTaille())
                                .dateUpload(doc.getDateUpload())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
