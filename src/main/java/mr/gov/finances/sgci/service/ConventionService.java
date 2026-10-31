package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.convention.ConventionDisponibilitePolicy;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DocumentConvention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.TypeDocumentConvention;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.BailleurRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DocumentConventionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateConventionRequest;
import mr.gov.finances.sgci.web.dto.DocumentConventionDto;
import mr.gov.finances.sgci.web.dto.EntiteLieeDto;
import mr.gov.finances.sgci.web.dto.UpdateConventionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ConventionService {

    private final ConventionRepository conventionRepository;
    private final BailleurRepository bailleurRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final mr.gov.finances.sgci.repository.MarcheRepository marcheRepository;
    private final mr.gov.finances.sgci.repository.DemandeCorrectionRepository demandeCorrectionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentConventionRepository documentConventionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    /**
     * Le rôle et le rattachement <em>effectifs</em>, impersonation comprise.
     *
     * <p>Lire {@code Utilisateur.getRole()} en base revenait à ignorer l'impersonation : un agent
     * de la Commission agissant pour une autorité contractante garde le rôle COMMISSION_RELAIS en
     * base, ne tombait donc pas dans la branche des autorités, et ressortait avec la totalité des
     * conventions — liste et accès direct par identifiant.
     */
    private final EffectiveIdentityService effectiveIdentityService;

    /**
     * La dernière convention ajoutée en tête de liste.
     *
     * <p>L'identifiant, et non une date : il est attribué dans l'ordre d'insertion et ne manque
     * jamais. Le tri se fait ici, au moment de rendre la liste, pour couvrir d'un seul coup tous
     * les chemins de lecture — périmètre de chaque rôle, recherche, filtres.
     */
    private static final java.util.Comparator<Convention> PLUS_RECENT_D_ABORD =
            java.util.Comparator.comparing(Convention::getId,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));

    @Transactional(readOnly = true)
    public List<ConventionDto> findAll(AuthenticatedUser user, String q) {
        if (q == null || q.isBlank()) {
            List<Convention> list = resolveConventionList(user, null);
            return list.stream().sorted(PLUS_RECENT_D_ABORD).map(this::toDto).collect(Collectors.toList());
        }
        String trimmed = q.trim();
        return conventionRepository.searchByReferenceIntituleOrProject(trimmed).stream()
                .filter(c -> canAccessConvention(c.getId(), user))
                .sorted(PLUS_RECENT_D_ABORD)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConventionDto findById(Long id, AuthenticatedUser user) {
        Convention c = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        if (!canAccessConvention(c.getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: convention hors périmètre");
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<ConventionDto> findByStatut(StatutConvention statut, AuthenticatedUser user) {
        List<Convention> list = resolveConventionList(user, statut);
        return list.stream().sorted(PLUS_RECENT_D_ABORD).map(this::toDto).collect(Collectors.toList());
    }

    private List<Convention> resolveConventionList(AuthenticatedUser user, StatutConvention statut) {
        if (user == null || user.getUserId() == null) {
            if (statut == null) {
                return conventionRepository.findAll();
            }
            return conventionRepository.findByStatut(statut);
        }

        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        // Le rôle porté par le jeton, et non celui de la ligne en base : c'est le seul qui reflète
        // l'impersonation d'un agent de la Commission agissant pour une autorité contractante.
        Role role = effectiveIdentityService.effectiveRole(user);

        // Chaque autorité ne voit que son périmètre : ce qu'elle a créé ou ce dont elle est titulaire.
        // Les conventions étaient jusqu'ici un référentiel partagé — décision revue en recette.
        if (role == Role.AUTORITE_CONTRACTANTE
                || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            List<Convention> perimetre = statut == null
                    ? conventionRepository.findAllPourAutorite(acId)
                    : conventionRepository.findAllPourAutoriteAndStatut(acId, statut);
            return perimetre;
        }

        List<Convention> toutes = statut == null
                ? conventionRepository.findAll()
                : conventionRepository.findByStatut(statut);
        return toutes;
    }

    @Transactional(readOnly = true)
    public List<ConventionDto> findByBailleurId(Long bailleurId, AuthenticatedUser user) {
        if (bailleurId == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "bailleurId obligatoire");
        }
        bailleurRepository.findById(bailleurId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Bailleur non trouvé: " + bailleurId));
        return conventionRepository.findByBailleurId(bailleurId).stream()
                .filter(c -> canAccessConvention(c.getId(), user))
                .sorted(PLUS_RECENT_D_ABORD)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public boolean canAccessConvention(Long conventionId, AuthenticatedUser user) {
        if (conventionId == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || u.getRole() == null) {
            return false;
        }
        Convention convention = conventionRepository.findById(conventionId).orElse(null);
        if (convention == null) {
            return false;
        }
        // Sans ce contrôle, restreindre la liste n'aurait aucune valeur : l'accès direct par
        // identifiant rouvrirait tout ce que la liste vient de masquer.
        Role roleEffectif = effectiveIdentityService.effectiveRole(user);
        if (roleEffectif == Role.AUTORITE_CONTRACTANTE
                || roleEffectif == Role.AUTORITE_UPM
                || roleEffectif == Role.AUTORITE_UEP) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                return false;
            }
            // Toute autorité reliée à la convention, et celle qui l'a créée.
            return convention.concerne(acId);
        }
        return true;
    }

    /**
     * Ouvre ou ferme une convention aux nouveaux rattachements.
     *
     * <p>Idempotent : le front peut double-cliquer, et rejouer l'état courant ne doit rien casser.
     */
    @Transactional
    public ConventionDto changerActivation(Long id, boolean actif, AuthenticatedUser user) {
        Convention convention = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        // Indispensable depuis que l'autorité contractante détient la permission : sans ce contrôle,
        // elle fermerait par identifiant la convention de n'importe quelle autre autorité.
        if (!canAccessConvention(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED,
                    "Accès refusé : convention hors périmètre");
        }
        // Une convention annulée ne se rouvre pas aux rattachements. Sans ce contrôle, la
        // réactiver rendait à nouveau vraie ConventionDisponibilitePolicy.estActive, donc rouvrait
        // le dépôt de demandes de correction sur une convention qu'on avait annulée. La cohérence
        // était à sens unique : l'annulation est refusée sur une convention VALIDE, mais rien
        // n'empêchait de défaire une annulation.
        if (actif && convention.getStatut() == StatutConvention.ANNULEE) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Réactivation impossible : la convention est annulée");
        }
        convention.setActif(actif);
        convention = conventionRepository.save(convention);
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.UPDATE, "Convention", String.valueOf(id), result);
        return result;
    }

    @Transactional
    public ConventionDto create(CreateConventionRequest request, AuthenticatedUser user) {
        Long userId = user != null ? user.getUserId() : null;
        if (conventionRepository.findByReference(request.getReference()).isPresent()) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Référence de convention déjà utilisée");
        }
        Utilisateur creator = userId != null ? utilisateurRepository.findById(userId).orElse(null) : null;
        AutoriteContractante autorite = resolveAutorite(request.getAutoriteContractanteId(), user, creator);
        // L'autorité créatrice est celle pour laquelle on agit : la sienne, ou celle que la
        // commission relais représente. Le compte relais n'en a aucune en base.
        AutoriteContractante maStructure = effectiveIdentityService.requireEffectiveAutorite(user, creator);
        AutoriteContractante creeParAc = maStructure != null ? maStructure : autorite;
        List<Bailleur> bailleurs = resolveBailleurs(request.getBailleurIds(), request.getBailleurId());
        Bailleur bailleur = bailleurs.isEmpty() ? null : bailleurs.get(0);
        Convention convention = Convention.builder()
                .reference(request.getReference())
                .intitule(request.getIntitule())
                .projectReference(request.getProjectReference())
                .bailleur(bailleur)
                .dateSignature(request.getDateSignature())
                .dateFin(request.getDateFin())
                .montantDevise(request.getMontantDevise())
                .montantMru(request.getMontantMru())
                .deviseOrigine(request.getDeviseOrigine())
                .tauxChange(request.getTauxChange())
                .statut(StatutConvention.EN_ATTENTE)
                .autoriteContractante(autorite)
                .creeParAutoriteContractante(creeParAc)
                .build();
        convention.getBailleurs().addAll(bailleurs);
        convention = conventionRepository.save(convention);
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.CREATE, "Convention", String.valueOf(convention.getId()), result);
        notifyConventionCreated(convention, userId);
        return result;
    }

    /**
     * Modifie une convention : par son autorité, et tant que la DGB ne l'a pas validée.
     *
     * <p>Aucune modification n'était possible jusqu'ici, pour personne : une erreur de saisie dans la
     * référence, l'intitulé, les dates ou les montants restait définitive. Trois règles, décidées
     * avec la Commission :
     *
     * <ul>
     *   <li><b>Jusqu'à quand</b> — seulement en attente de validation. Une fois validée, les marchés
     *       et les demandes s'y adossent : la modifier en place changerait ce sur quoi ils ont été
     *       instruits. Rejetée ou annulée, elle est close.</li>
     *   <li><b>Par qui</b> — l'autorité titulaire <em>et</em> l'autorité qui l'a créée pour elle : ce
     *       sont les deux qui la voient. En relais, l'autorité représentée.</li>
     *   <li><b>Quoi</b> — tout ce qui se saisit à la création, sauf l'autorité titulaire, qui
     *       déplacerait la convention dans le périmètre d'une autre. La référence peut changer si elle
     *       reste unique.</li>
     * </ul>
     */
    @Transactional
    public ConventionDto update(Long id, UpdateConventionRequest request, AuthenticatedUser user) {
        Convention convention = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        assertAutoriteSurConventionEnAttente(convention, user, Geste.MODIFIER);

        String reference = request.getReference().trim();
        conventionRepository.findByReference(reference)
                .filter(autre -> !autre.getId().equals(convention.getId()))
                .ifPresent(autre -> {
                    throw ApiException.conflict(ApiErrorCode.CONFLICT, "Référence de convention déjà utilisée");
                });

        convention.setReference(reference);
        convention.setIntitule(request.getIntitule().trim());
        convention.setProjectReference(request.getProjectReference());
        List<Bailleur> bailleurs = resolveBailleurs(request.getBailleurIds(), request.getBailleurId());
        convention.setBailleur(bailleurs.isEmpty() ? null : bailleurs.get(0));
        convention.getBailleurs().clear();
        convention.getBailleurs().addAll(bailleurs);
        convention.setDateSignature(request.getDateSignature());
        convention.setDateFin(request.getDateFin());
        convention.setMontantDevise(request.getMontantDevise());
        convention.setMontantMru(request.getMontantMru());
        convention.setDeviseOrigine(request.getDeviseOrigine());
        convention.setTauxChange(request.getTauxChange());

        Convention enregistree = conventionRepository.save(convention);
        ConventionDto result = toDto(enregistree);
        auditService.log(AuditAction.UPDATE, "Convention", String.valueOf(id), result);
        return result;
    }

    /**
     * Annule une convention : par son autorité, et tant que la DGB ne l'a pas validée.
     *
     * <p>Même règle que la modification, décidée avec la Commission : l'autorité titulaire ou
     * créatrice, sur une convention en attente. C'est le pendant de l'annulation d'une demande de
     * correction, que l'autorité peut déjà faire.
     *
     * <p><b>Pourquoi une route à part.</b> Les directions annulent par {@code PATCH .../statut}, qui
     * accepte n'importe quel statut cible et ne vérifie pas le rôle selon la cible. Ouvrir cette
     * route à l'autorité lui aurait permis de <em>valider</em> sa propre convention. Celle-ci ne
     * sait faire qu'une chose : annuler.
     */
    @Transactional
    public ConventionDto annulerParAutorite(Long id, String motif, AuthenticatedUser user) {
        Convention convention = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        assertAutoriteSurConventionEnAttente(convention, user, Geste.ANNULER);
        String motifNettoye = motif != null && !motif.isBlank() ? motif.trim() : null;
        // L'écriture, la trace d'audit et la notification restent celles de l'annulation par une
        // direction : une seule façon d'annuler, deux portes d'entrée.
        return updateStatut(id, StatutConvention.ANNULEE, user.getUserId(), motifNettoye);
    }

    /** Ce que l'autorité fait sur sa convention en attente. Les messages en dépendent. */
    private enum Geste {
        MODIFIER("modifier", "Modification"),
        ANNULER("annuler", "Annulation");

        final String verbe;
        final String nom;

        Geste(String verbe, String nom) {
            this.verbe = verbe;
            this.nom = nom;
        }
    }

    /**
     * L'appelant est une autorité de la convention et celle-ci attend sa validation — ou, pour la
     * modifier, l'appelant est le Président ou l'administrateur, à tout statut (décision de l'équipe,
     * octobre 2026 : ce sont eux qui gèrent conventions, bailleurs et marchés).
     */
    private void assertAutoriteSurConventionEnAttente(Convention convention, AuthenticatedUser user, Geste geste) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = effectiveIdentityService.effectiveRole(user);
        if (geste == Geste.MODIFIER && (role == Role.PRESIDENT || role == Role.ADMIN_SI)) {
            if (convention.getStatut() == StatutConvention.ANNULEE) {
                throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE, "Modification impossible : la convention est annulée");
            }
            return;
        }
        if (role != Role.AUTORITE_CONTRACTANTE && role != Role.AUTORITE_UPM && role != Role.AUTORITE_UEP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seule l'autorité contractante titulaire ou créatrice peut " + geste.verbe + " une convention");
        }
        Utilisateur enBase = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, enBase);
        if (!convention.concerne(acId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé : convention hors périmètre");
        }
        StatutConvention statut = convention.getStatut();
        if (statut != StatutConvention.EN_ATTENTE) {
            String raison = switch (statut) {
                case VALIDE -> " impossible : la convention a déjà été validée par la DGB";
                case REJETE -> " impossible : la convention a été rejetée";
                case ANNULEE -> " impossible : la convention est annulée";
                default -> " impossible dans le statut : " + statut;
            };
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE, geste.nom + raison);
        }
    }

    @Transactional
    public DocumentConventionDto uploadDocument(Long conventionId, TypeDocumentConvention type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        assertConventionEditable(convention);
        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentConvention doc = DocumentConvention.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .convention(convention)
                .build();
        doc = documentConventionRepository.save(doc);
        DocumentConventionDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentConvention", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional
    public DocumentConventionDto replaceDocument(Long conventionId, Long documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        assertConventionEditable(convention);
        DocumentConvention doc = documentConventionRepository.findByIdAndConventionId(documentId, conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document convention non trouvé: " + documentId));

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);
        doc.setNomFichier(originalFilename != null ? originalFilename : file.getName());
        doc.setChemin(fileUrl);
        doc.setDateUpload(Instant.now());
        doc.setTaille(file.getSize());
        doc = documentConventionRepository.save(doc);
        DocumentConventionDto result = toDto(doc);
        auditService.log(AuditAction.UPDATE, "DocumentConvention", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional
    public void deleteDocument(Long conventionId, Long documentId) {
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        assertConventionEditable(convention);
        DocumentConvention doc = documentConventionRepository.findByIdAndConventionId(documentId, conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document convention non trouvé: " + documentId));
        documentConventionRepository.delete(doc);
        auditService.log(AuditAction.DELETE, "DocumentConvention", String.valueOf(documentId), null);
    }

    @Transactional(readOnly = true)
    public List<DocumentConventionDto> findDocuments(Long conventionId) {
        return documentConventionRepository.findByConventionId(conventionId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ConventionDto updateStatut(Long id, StatutConvention statut, Long userId, String motifRejet) {
        Convention convention = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        if (statut == StatutConvention.ANNULEE && convention.getStatut() == StatutConvention.VALIDE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Annulation impossible: la convention est déjà validée");
        }
        convention.setStatut(statut);
        if (statut == StatutConvention.VALIDE || statut == StatutConvention.REJETE || statut == StatutConvention.ANNULEE) {
            convention.setValideParUserId(userId);
            convention.setDateValidation(java.time.Instant.now());
            convention.setMotifRejet((statut == StatutConvention.REJETE || statut == StatutConvention.ANNULEE) ? motifRejet : null);
        }
        convention = conventionRepository.save(convention);
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.UPDATE, "Convention", String.valueOf(id), result);
        notifyConvention(convention, statut, motifRejet, userId);
        return result;
    }

    /**
     * Les bailleurs d'une convention, le principal en tête : la liste si elle est fournie, sinon le
     * seul bailleur principal. Sans doublon.
     */
    private List<Bailleur> resolveBailleurs(List<Long> bailleurIds, Long bailleurId) {
        List<Long> ids = bailleurIds != null ? bailleurIds
                : (bailleurId != null ? List.of(bailleurId) : List.of());
        return ids.stream().filter(Objects::nonNull).distinct().map(this::resolveBailleur).collect(Collectors.toList());
    }

    /**
     * Relie une autorité contractante à la convention. Le Président et l'administrateur seulement.
     *
     * <p>Une convention peut concerner plusieurs autorités (décision de l'équipe, octobre 2026).
     * Reliée, l'autorité voit la convention et peut y loger ses marchés et ses demandes.
     */
    @Transactional
    public ConventionDto lierAutorite(Long conventionId, Long autoriteId, AuthenticatedUser user) {
        assertGestionnaireDesLiens(user);
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        AutoriteContractante autorite = autoriteRepository.findById(autoriteId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Autorité contractante non trouvée: " + autoriteId));
        if (!convention.relieeA(autoriteId)) {
            convention.getAutorites().addAll(convention.autoritesLiees());
            convention.getAutorites().add(autorite);
            convention = conventionRepository.save(convention);
        }
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.UPDATE, "Convention", String.valueOf(conventionId), result);
        return result;
    }

    /**
     * Délie une autorité de la convention. Le Président et l'administrateur seulement, et à deux
     * conditions : l'autorité n'y a encore ni marché ni demande de correction, et il en reste une
     * autre. Délier l'autorité principale fait de la suivante la principale.
     */
    @Transactional
    public ConventionDto delierAutorite(Long conventionId, Long autoriteId, AuthenticatedUser user) {
        assertGestionnaireDesLiens(user);
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        if (!convention.relieeA(autoriteId)) {
            return toDto(convention);
        }
        List<AutoriteContractante> restantes = convention.autoritesLiees().stream()
                .filter(a -> !autoriteId.equals(a.getId()))
                .collect(Collectors.toList());
        if (restantes.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Une convention reste reliée à au moins une autorité contractante");
        }
        if (marcheRepository.existsByConventionAndAutorite(conventionId, autoriteId)) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Impossible de délier cette autorité : elle a déjà un marché sous cette convention");
        }
        if (demandeCorrectionRepository.existsByConventionIdAndAutoriteContractanteId(conventionId, autoriteId)) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Impossible de délier cette autorité : elle a déjà une demande de correction sous cette convention");
        }
        convention.getAutorites().clear();
        convention.getAutorites().addAll(restantes);
        convention.setAutoriteContractante(restantes.get(0));
        convention = conventionRepository.save(convention);
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.UPDATE, "Convention", String.valueOf(conventionId), result);
        return result;
    }

    private static void assertGestionnaireDesLiens(AuthenticatedUser user) {
        Role role = user != null ? user.getRole() : null;
        if (role != Role.PRESIDENT && role != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seuls le Président et l'administrateur relient ou délient une autorité contractante");
        }
    }

    private Bailleur resolveBailleur(Long bailleurId) {
        if (bailleurId == null) {
            return null;
        }
        return bailleurRepository.findById(bailleurId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Bailleur non trouvé: " + bailleurId));
    }

    /**
     * L'autorité titulaire de la convention : celle de la requête, sinon celle pour laquelle on agit.
     *
     * <p>« Celle pour laquelle on agit » se lit dans l'identité effective, pas sur le compte en base :
     * en relais, le compte « commission_relais » n'a aucune autorité, et la création échouait sur
     * « Aucune autorité contractante liée à l'utilisateur » alors que la prise de relais avait réussi.
     */
    private AutoriteContractante resolveAutorite(Long autoriteContractanteId, AuthenticatedUser user,
                                                 Utilisateur enBase) {
        if (autoriteContractanteId != null) {
            return autoriteRepository.findById(autoriteContractanteId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Autorité contractante non trouvée"));
        }
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (enBase == null) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé");
        }
        AutoriteContractante autorite = effectiveIdentityService.requireEffectiveAutorite(user, enBase);
        if (autorite == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }
        return autorite;
    }

    private ConventionDto toDto(Convention convention) {
        return ConventionDto.builder()
                .id(convention.getId())
                .reference(convention.getReference())
                .projectReference(convention.getProjectReference())
                .intitule(convention.getIntitule())
                .bailleurId(convention.getBailleur() != null ? convention.getBailleur().getId() : null)
                .bailleurNom(convention.getBailleur() != null ? convention.getBailleur().getNom() : null)
                .bailleurDetails(convention.getBailleur() != null ? convention.getBailleur().getDetails() : null)
                .dateSignature(convention.getDateSignature())
                .dateFin(convention.getDateFin())
                .montantDevise(convention.getMontantDevise())
                .montantMru(convention.getMontantMru())
                .deviseOrigine(convention.getDeviseOrigine())
                .tauxChange(convention.getTauxChange())
                .statut(convention.getStatut())
                .autoriteContractanteId(convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getNom() : null)
                .creeParAutoriteContractanteId(convention.getCreeParAutoriteContractante() != null
                        ? convention.getCreeParAutoriteContractante().getId() : null)
                .creeParAutoriteContractanteNom(convention.getCreeParAutoriteContractante() != null
                        ? convention.getCreeParAutoriteContractante().getNom() : null)
                .autorites(convention.autoritesLiees().stream()
                        .map(a -> new EntiteLieeDto(a.getId(), a.getNom())).collect(Collectors.toList()))
                .bailleurs(convention.bailleursLies().stream()
                        .map(b -> new EntiteLieeDto(b.getId(), b.getNom())).collect(Collectors.toList()))
                .dateCreation(convention.getDateCreation())
                .valideParUserId(convention.getValideParUserId())
                .dateValidation(convention.getDateValidation())
                .actif(ConventionDisponibilitePolicy.estActive(convention))
                .motifRejet(convention.getMotifRejet())
                .documents(convention.getDocuments() != null
                        ? convention.getDocuments().stream().map(this::toDto).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private DocumentConventionDto toDto(DocumentConvention doc) {
        return DocumentConventionDto.builder()
                .id(doc.getId())
                .type(doc.getType())
                .nomFichier(doc.getNomFichier())
                .chemin(doc.getChemin())
                .dateUpload(doc.getDateUpload())
                .taille(doc.getTaille())
                .build();
    }

    private List<Long> resolveCommissionMemberUserIds() {
        return Stream.of(Role.PRESIDENT, Role.DGD, Role.DGTCP, Role.DGI, Role.DGB)
                .flatMap(r -> utilisateurRepository.findByRole(r).stream())
                .map(Utilisateur::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> resolveConventionNotificationRecipients(Convention convention) {
        List<Long> acUsers = convention.autoritesLiees().stream()
                .flatMap(a -> utilisateurRepository.findByAutoriteContractanteId(a.getId()).stream())
                .map(Utilisateur::getId)
                .collect(Collectors.toList());
        List<Long> commission = resolveCommissionMemberUserIds();
        return Stream.concat(acUsers.stream(), commission.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    private void notifyConventionCreated(Convention convention, Long userId) {
        if (convention == null) {
            return;
        }
        List<Long> userIds = resolveConventionNotificationRecipients(convention);
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "CONVENTION_CREATED");
        payload.put("reference", convention.getReference());
        if (userId != null) {
            payload.put("acteurUserId", userId);
        }
        String message = "Nouvelle convention: " + convention.getReference();
        notificationService.notifyUsers(userIds, NotificationType.CONVENTION_STATUT_CHANGE,
                "Convention", convention.getId(), message, payload);
    }

    private void notifyConvention(Convention convention, StatutConvention statut, String motifRejet, Long userId) {
        if (convention == null) {
            return;
        }
        List<Long> userIds = resolveConventionNotificationRecipients(convention);
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        if (motifRejet != null && !motifRejet.isBlank()) {
            payload.put("motifRejet", motifRejet);
        }
        if (userId != null) {
            payload.put("acteurUserId", userId);
        }
        String message = "Convention " + convention.getReference() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.CONVENTION_STATUT_CHANGE,
                "Convention", convention.getId(), message, payload);
    }

    private void assertConventionEditable(Convention convention) {
        if (convention == null) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée");
        }
        if (convention.getStatut() == StatutConvention.VALIDE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Modification des documents interdite: convention déjà validée");
        }
        if (convention.getStatut() == StatutConvention.ANNULEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Modification des documents interdite: convention annulée");
        }
    }
}
