package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.ApurerTVAInterieureRequest;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.LiquiderUtilisationDouaneRequest;
import mr.gov.finances.sgci.web.dto.TvaDeductibleStockDto;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.workflow.UtilisationCreditWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisationCreditService {

    private final UtilisationCreditRepository repository;
    private final CertificatCreditRepository certificatRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SousTraitanceService sousTraitanceService;
    private final UtilisationCreditWorkflow workflow;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final DocumentUtilisationCreditService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final TvaDeductibleStockRepository tvaStockRepository;

    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Liste filtrée selon le rôle : titulaire voit toutes les demandes sur ses certificats (dont sous-traitants) ;
     * sous-traitant voit uniquement ses propres demandes ; services (DGD, DGTCP, …) voient tout.
     *
     * @param demandeurSousTraitantOnly si {@code true} et rôle titulaire, ne garde que les demandes où
     *                                  {@link UtilisationCreditDto#getDemandeurEstSousTraitant()} est vrai.
     * @param sousTraitantEntrepriseId  si renseigné et rôle titulaire, ne garde que les demandes dont
     *                                  l'entreprise demandeuse est cette id et {@code demandeurEstSousTraitant}.
     */
    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findAllVisible(
            AuthenticatedUser auth,
            Boolean demandeurSousTraitantOnly,
            Long sousTraitantEntrepriseId
    ) {
        List<UtilisationCredit> rows = resolveVisibleEntities(auth);
        List<UtilisationCreditDto> dtos = rows.stream().map(this::toDto).collect(Collectors.toList());
        if (auth != null && auth.getRole() == Role.ENTREPRISE && sousTraitantEntrepriseId != null) {
            dtos = dtos.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDemandeurEstSousTraitant())
                            && sousTraitantEntrepriseId.equals(d.getEntrepriseId()))
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(demandeurSousTraitantOnly) && auth != null && auth.getRole() == Role.ENTREPRISE) {
            return dtos.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDemandeurEstSousTraitant()))
                    .collect(Collectors.toList());
        }
        return dtos;
    }

    private List<UtilisationCredit> resolveVisibleEntities(AuthenticatedUser auth) {
        if (auth == null || auth.getUserId() == null) {
            return repository.findAll();
        }
        Utilisateur u = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role r = u.getRole();
        if (r == Role.SOUS_TRAITANT) {
            if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                return List.of();
            }
            return repository.findByEntrepriseId(u.getEntreprise().getId());
        }
        if (r == Role.ENTREPRISE) {
            if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                return List.of();
            }
            return repository.findByCertificatCredit_Entreprise_Id(u.getEntreprise().getId());
        }
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public UtilisationCreditDto findById(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (user != null) {
            assertCanViewUtilisation(user, entity);
        }
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findByCertificatCreditId(Long certificatCreditId, AuthenticatedUser user) {
        CertificatCredit cert = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé"));
        if (user != null) {
            assertCanAccessCertificatUtilisations(user, cert);
        }
        return repository.findByCertificatCreditId(certificatCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private void assertCanViewUtilisation(AuthenticatedUser auth, UtilisationCredit u) {
        Utilisateur logged = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role r = logged.getRole();
        if (r != Role.ENTREPRISE && r != Role.SOUS_TRAITANT) {
            return;
        }
        if (logged.getEntreprise() == null || logged.getEntreprise().getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        Long eid = logged.getEntreprise().getId();
        CertificatCredit cert = u.getCertificatCredit();
        Long titId = cert != null && cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        if (r == Role.SOUS_TRAITANT) {
            if (u.getEntreprise() != null && u.getEntreprise().getId().equals(eid)) {
                return;
            }
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: utilisation hors périmètre");
        }
        if (titId != null && titId.equals(eid)) {
            return;
        }
        if (u.getEntreprise() != null && u.getEntreprise().getId().equals(eid)) {
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: utilisation hors périmètre");
    }

    private void assertCanAccessCertificatUtilisations(AuthenticatedUser auth, CertificatCredit cert) {
        Utilisateur logged = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role r = logged.getRole();
        if (r != Role.ENTREPRISE && r != Role.SOUS_TRAITANT) {
            return;
        }
        if (logged.getEntreprise() == null || logged.getEntreprise().getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        Long eid = logged.getEntreprise().getId();
        Long titId = cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        if (r == Role.ENTREPRISE && titId != null && titId.equals(eid)) {
            return;
        }
        if (r == Role.SOUS_TRAITANT) {
            sousTraitanceService.assertSousTraitantEntrepriseAuthorizedOnCertificat(cert.getId(), eid);
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
    }

    @Transactional(readOnly = true)
    public List<TvaDeductibleStockDto> findTvaStockByCertificat(Long certificatCreditId) {
        return tvaStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(certificatCreditId)
                .stream()
                .map(this::toStockDto)
                .collect(Collectors.toList());
    }

    private TvaDeductibleStockDto toStockDto(mr.gov.finances.sgci.domain.entity.TvaDeductibleStock s) {
        BigDecimal initial = s.getMontantInitial() != null ? s.getMontantInitial() : BigDecimal.ZERO;
        BigDecimal restant = s.getMontantRestant() != null ? s.getMontantRestant() : BigDecimal.ZERO;
        String numeroDeclaration = s.getUtilisationDouane() != null
                ? s.getUtilisationDouane().getNumeroDeclaration() : null;
        return TvaDeductibleStockDto.builder()
                .id(s.getId())
                .utilisationDouaneId(s.getUtilisationDouane() != null ? s.getUtilisationDouane().getId() : null)
                .numeroDeclaration(numeroDeclaration)
                .montantInitial(initial)
                .montantRestant(restant)
                .montantConsomme(initial.subtract(restant))
                .dateCreation(s.getDateCreation())
                .epuise(restant.compareTo(BigDecimal.ZERO) == 0)
                .build();
    }

    @Transactional
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request) {
        return create(request, null);
    }

    @Transactional
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        CertificatCredit certificat = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé"));
        if (certificat.getStatut() != StatutCertificat.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le crédit doit être OUVERT pour créer une utilisation");
        }
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        // Contrôle d'accès création
        if (user != null && user.getRole() != null) {
            Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

            if (u.getRole() == Role.ENTREPRISE || u.getRole() == Role.SOUS_TRAITANT) {
                if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
                }
                if (!u.getEntreprise().getId().equals(entreprise.getId())) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: entreprise requête invalide");
                }
                if (certificat.getEntreprise() == null || certificat.getEntreprise().getId() == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat sans entreprise");
                }
                Long userEntrepriseId = u.getEntreprise().getId();
                Long certificatEntrepriseId = certificat.getEntreprise().getId();
                if (!certificatEntrepriseId.equals(userEntrepriseId)) {
                    // Entreprise sous-traitante: doit être autorisée sur le certificat
                    sousTraitanceService.assertSousTraitantEntrepriseAuthorizedOnCertificat(certificat.getId(), userEntrepriseId);
                }
            }
        }

        UtilisationCredit entity;
        if (request.getType() == TypeUtilisation.DOUANIER) {
            UtilisationDouaniere d = new UtilisationDouaniere();
            mapBase(d, request, certificat, entreprise);
            d.setNumeroDeclaration(request.getNumeroDeclaration());
            d.setNumeroBulletin(request.getNumeroBulletin());
            d.setDateDeclaration(request.getDateDeclaration());
            d.setMontantDroits(request.getMontantDroits());
            d.setMontantTVA(request.getMontantTVA());
            d.setEnregistreeSYDONIA(request.getEnregistreeSYDONIA());

            if (d.getMontant() == null) {
                BigDecimal droits = d.getMontantDroits() != null ? d.getMontantDroits() : BigDecimal.ZERO;
                BigDecimal tva = d.getMontantTVA() != null ? d.getMontantTVA() : BigDecimal.ZERO;
                BigDecimal total = droits.add(tva);
                d.setMontant(total.compareTo(BigDecimal.ZERO) > 0 ? total : null);
            }
            entity = d;
        } else {
            UtilisationTVAInterieure t = new UtilisationTVAInterieure();
            mapBase(t, request, certificat, entreprise);
            t.setTypeAchat(resolveTypeAchat(request));
            t.setNumeroFacture(request.getNumeroFacture());
            t.setDateFacture(request.getDateFacture());
            t.setMontantTVA(request.getMontantTVAInterieure());
            t.setNumeroDecompte(request.getNumeroDecompte());

            if (t.getMontant() == null) {
                t.setMontant(t.getMontantTVA());
            }
            entity = t;
        }
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "UtilisationCredit", String.valueOf(entity.getId()), result);
        notifyActorsOnCreation(entity);
        return result;
    }

    private void notifyActorsOnCreation(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return;
        }
        List<Role> targetRoles;
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            targetRoles = List.of(Role.DGD);
        } else {
            targetRoles = List.of(Role.DGTCP);
        }
        String typeName = utilisation.getType() == TypeUtilisation.DOUANIER ? "douanière" : "TVA intérieure";
        String message = "Nouvelle demande d'utilisation " + typeName + " à traiter";
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", utilisation.getType().name());
        payload.put("statut", StatutUtilisation.DEMANDEE.name());
        payload.put("utilisationId", utilisation.getId());

        for (Role targetRole : targetRoles) {
            List<Long> userIds = utilisateurRepository.findByRole(targetRole)
                    .stream()
                    .map(mr.gov.finances.sgci.domain.entity.Utilisateur::getId)
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                notificationService.notifyUsers(userIds, NotificationType.UTILISATION_STATUT_CHANGE,
                        "UtilisationCredit", utilisation.getId(), message, payload);
            }
        }
    }

    @Transactional
    public UtilisationCreditDto apurerTVAInterieure(Long id, ApurerTVAInterieureRequest request, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (!(entity instanceof UtilisationTVAInterieure t)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type TVA intérieure");
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.APUREE);
        assertActorCanTransition(entity, StatutUtilisation.APUREE, user);
        assertRequiredDocumentsPresent(entity);

        BigDecimal tvaCollectee = t.getMontantTVA() != null ? t.getMontantTVA() : BigDecimal.ZERO;
        if (tvaCollectee.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Montant TVA collectée invalide (doit être >= 0)");
        }

        CertificatCredit certificat = t.getCertificatCredit();
        if (certificat == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat manquant");
        }

        // Calcul FIFO du stock disponible
        BigDecimal tvaDeductibleDispo = tvaStockRepository
                .findByCertificatCreditIdOrderByDateCreationAsc(certificat.getId())
                .stream()
                .map(s -> s.getMontantRestant() != null ? s.getMontantRestant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Si non fourni: consommer tout le stock disponible (règle FIFO intégrale)
        BigDecimal tvaDeductible;
        if (request == null || request.getTvaDeductibleUtilisee() == null) {
            tvaDeductible = tvaDeductibleDispo;
        } else {
            tvaDeductible = request.getTvaDeductibleUtilisee();
            if (tvaDeductible.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "tvaDeductibleUtilisee doit être >= 0");
            }
            if (tvaDeductible.compareTo(tvaDeductibleDispo) > 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "TVA déductible insuffisante (disponible=" + tvaDeductibleDispo
                                + ", demandée=" + tvaDeductible + ")");
            }
        }

        BigDecimal soldeAvant = certificat.getSoldeTVA() != null ? certificat.getSoldeTVA() : BigDecimal.ZERO;

        consumeTvaDeductible(certificat.getId(), tvaDeductible);

        BigDecimal tvaNette = tvaCollectee.subtract(tvaDeductible);
        t.setTvaDeductibleUtilisee(tvaDeductible);
        t.setTvaNette(tvaNette);
        t.setSoldeTVAAvant(soldeAvant);

        BigDecimal creditUtilise = BigDecimal.ZERO;
        BigDecimal paiementEntreprise = BigDecimal.ZERO;
        BigDecimal report = BigDecimal.ZERO;
        BigDecimal soldeApres = soldeAvant;

        int cmp = tvaNette.compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            // cas 1: neutre
        } else if (cmp > 0) {
            // cas 2
            if (soldeAvant.compareTo(tvaNette) >= 0) {
                creditUtilise = tvaNette;
                soldeApres = soldeAvant.subtract(tvaNette);
            } else {
                creditUtilise = soldeAvant;
                paiementEntreprise = tvaNette.subtract(soldeAvant);
                soldeApres = BigDecimal.ZERO;
            }
        } else {
            // cas 3
            report = tvaNette.abs();
            soldeApres = soldeAvant.add(report);
        }

        certificat.setSoldeTVA(soldeApres);
        certificatRepository.save(certificat);

        t.setCreditInterieurUtilise(creditUtilise);
        t.setPaiementEntreprise(paiementEntreprise);
        t.setReportANouveau(report);
        t.setSoldeTVAApres(soldeApres);

        t.setStatut(StatutUtilisation.APUREE);
        t.setDateLiquidation(Instant.now());

        entity = repository.save(t);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisation(entity, StatutUtilisation.APUREE);
        return result;
    }

    private void consumeTvaDeductible(Long certificatCreditId, BigDecimal amount) {
        BigDecimal remaining = amount;
        List<mr.gov.finances.sgci.domain.entity.TvaDeductibleStock> stocks = tvaStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(certificatCreditId);
        for (mr.gov.finances.sgci.domain.entity.TvaDeductibleStock s : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal rest = s.getMontantRestant() != null ? s.getMontantRestant() : BigDecimal.ZERO;
            if (rest.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = rest.min(remaining);
            s.setMontantRestant(rest.subtract(take));
            remaining = remaining.subtract(take);
            tvaStockRepository.save(s);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Consommation TVA déductible impossible (reste=" + remaining + ")");
        }
    }

    private void assertRequiredDocumentsPresent(UtilisationCredit utilisation) {
        ProcessusDocument processus = resolveProcessus(utilisation);
        List<TypeDocument> presentTypes = documentService.findActiveDocumentTypes(utilisation.getId());

        // Base: contraintes déclaratives via DocumentRequirement
        requirementValidator.assertRequiredDocumentsPresent(processus, presentTypes);

        // Complément: règles conditionnelles TVA intérieure (achat local vs décompte)
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE && utilisation instanceof UtilisationTVAInterieure t) {
            Set<TypeDocument> present = Set.copyOf(presentTypes);
            TypeAchat typeAchat = t.getTypeAchat();
            if (typeAchat == TypeAchat.ACHAT_LOCAL) {
                if (!present.contains(TypeDocument.FACTURE) || !present.contains(TypeDocument.DECLARATION_TVA)) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                            "Documents obligatoires manquants (Achat local): "
                                    + (present.contains(TypeDocument.FACTURE) ? "" : "FACTURE ")
                                    + (present.contains(TypeDocument.DECLARATION_TVA) ? "" : "DECLARATION_TVA"));
                }
            } else if (typeAchat == TypeAchat.DECOMPTE) {
                if (!present.contains(TypeDocument.DECOMPTE)) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Document obligatoire manquant (Décompte): DECOMPTE");
                }
            }
        }
    }

    private TypeAchat resolveTypeAchat(CreateUtilisationCreditRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getTypeAchat() != null) {
            return request.getTypeAchat();
        }
        String numeroDecompte = request.getNumeroDecompte();
        if (numeroDecompte != null && !numeroDecompte.trim().isEmpty()) {
            return TypeAchat.DECOMPTE;
        }
        return TypeAchat.ACHAT_LOCAL;
    }

    private void mapBase(UtilisationCredit u, CreateUtilisationCreditRequest r, CertificatCredit c, Entreprise e) {
        u.setDateDemande(Instant.now());
        u.setMontant(r.getMontant());
        u.setStatut(StatutUtilisation.DEMANDEE);
        u.setCertificatCredit(c);
        u.setEntreprise(e);
    }

    @Transactional
    public UtilisationCreditDto liquiderDouane(Long id, LiquiderUtilisationDouaneRequest request, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type Douane");
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.LIQUIDEE);
        assertActorCanTransition(entity, StatutUtilisation.LIQUIDEE, user);

        BigDecimal droits = request != null && request.getMontantDroits() != null ? request.getMontantDroits() : BigDecimal.ZERO;
        BigDecimal tva = request != null && request.getMontantTVA() != null ? request.getMontantTVA() : BigDecimal.ZERO;
        if (droits.compareTo(BigDecimal.ZERO) < 0 || tva.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Montants d'imputation invalides (doivent être >= 0)");
        }
        BigDecimal total = droits.add(tva);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le montant total imputé doit être > 0");
        }

        d.setMontantDroits(droits);
        d.setMontantTVA(tva);
        d.setMontant(total);

        CertificatCredit certificat = d.getCertificatCredit();
        BigDecimal soldeCordonAvant = certificat.getSoldeCordon() != null ? certificat.getSoldeCordon() : BigDecimal.ZERO;
        d.setSoldeCordonAvant(soldeCordonAvant);

        d.setStatut(StatutUtilisation.LIQUIDEE);
        d.setDateLiquidation(Instant.now());
        debitSolde(d);

        BigDecimal soldeCordonApres = d.getCertificatCredit().getSoldeCordon() != null
                ? d.getCertificatCredit().getSoldeCordon() : BigDecimal.ZERO;
        d.setSoldeCordonApres(soldeCordonApres);

        BigDecimal tvaImport = d.getMontantTVA() != null ? d.getMontantTVA() : BigDecimal.ZERO;
        if (tvaImport.compareTo(BigDecimal.ZERO) > 0) {
            tvaStockRepository.save(mr.gov.finances.sgci.domain.entity.TvaDeductibleStock.builder()
                    .certificatCredit(d.getCertificatCredit())
                    .utilisationDouane(d)
                    .montantInitial(tvaImport)
                    .montantRestant(tvaImport)
                    .dateCreation(Instant.now())
                    .build());
        }

        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisation(entity, StatutUtilisation.LIQUIDEE);
        return result;
    }

    @Transactional
    public UtilisationCreditDto updateStatut(Long id, StatutUtilisation statut, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        workflow.validateTransition(entity.getStatut(), statut);

        assertActorCanTransition(entity, statut, user);

        if (entity.getType() == TypeUtilisation.DOUANIER && statut == StatutUtilisation.LIQUIDEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Liquidation Douane: veuillez utiliser POST /{id}/liquidation-douane avec les montants d'imputation");
        }
        if (entity.getType() == TypeUtilisation.TVA_INTERIEURE && statut == StatutUtilisation.APUREE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Apurement TVA: veuillez utiliser POST /{id}/apurement-tva (calcul FIFO automatique)");
        }

        if (statut == StatutUtilisation.EN_VERIFICATION) {
            assertRequiredDocumentsPresent(entity);
        }

        entity.setStatut(statut);
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisation(entity, statut);
        return result;
    }

    private ProcessusDocument resolveProcessus(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return ProcessusDocument.UTILISATION_CI;
        }
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            return ProcessusDocument.UTILISATION_CI_DOUANE;
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        }
        return ProcessusDocument.UTILISATION_CI;
    }

    private void assertActorCanTransition(UtilisationCredit utilisation, StatutUtilisation to, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (utilisation == null || utilisation.getType() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Type d'utilisation manquant");
        }
        Role role = user.getRole();

        // Verrouillage métier par type (TDR)
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            if (to != StatutUtilisation.INCOMPLETE
                    && to != StatutUtilisation.A_RECONTROLER
                    && to != StatutUtilisation.EN_VERIFICATION
                    && to != StatutUtilisation.VISE
                    && to != StatutUtilisation.LIQUIDEE
                    && to != StatutUtilisation.REJETEE) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Transition non autorisée (Douane): vers " + to);
            }
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (to != StatutUtilisation.INCOMPLETE
                    && to != StatutUtilisation.A_RECONTROLER
                    && to != StatutUtilisation.EN_VERIFICATION
                    && to != StatutUtilisation.VALIDEE
                    && to != StatutUtilisation.APUREE
                    && to != StatutUtilisation.REJETEE) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Transition non autorisée (TVA intérieure): vers " + to);
            }
        }

        if (to == StatutUtilisation.INCOMPLETE || to == StatutUtilisation.A_RECONTROLER) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transition " + to + " est gérée automatiquement par le système (rejet temporaire / résolution)");
        }

        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            if (to == StatutUtilisation.EN_VERIFICATION || to == StatutUtilisation.VISE) {
                if (role != Role.DGD) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGD peut traiter cette étape Douane");
                }
                return;
            }

            if (to == StatutUtilisation.LIQUIDEE) {
                if (role != Role.DGTCP) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut liquider l'utilisation Douane");
                }
                return;
            }

            if (to == StatutUtilisation.REJETEE) {
                if (role != Role.DGD && role != Role.DGTCP) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGD ou DGTCP peut rejeter l'utilisation Douane");
                }
                return;
            }
            return;
        }

        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (role != Role.DGTCP) {
                throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut traiter les utilisations TVA intérieure");
            }
        }
    }

    private void debitSolde(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getCertificatCredit() == null) {
            return;
        }
        CertificatCredit certificat = utilisation.getCertificatCredit();
        BigDecimal montant = utilisation.getMontant() != null ? utilisation.getMontant() : BigDecimal.ZERO;
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            BigDecimal solde = certificat.getSoldeCordon() != null ? certificat.getSoldeCordon() : BigDecimal.ZERO;
            if (solde.compareTo(montant) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Solde cordon insuffisant pour imputer l'utilisation (solde=" + solde + ", montant=" + montant + ")");
            }
            certificat.setSoldeCordon(solde.subtract(montant));
        } else if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            BigDecimal solde = certificat.getSoldeTVA() != null ? certificat.getSoldeTVA() : BigDecimal.ZERO;
            if (solde.compareTo(montant) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Solde TVA insuffisant pour imputer l'utilisation (solde=" + solde + ", montant=" + montant + ")");
            }
            certificat.setSoldeTVA(solde.subtract(montant));
        }
        certificatRepository.save(certificat);
    }

    private UtilisationCreditDto toDto(UtilisationCredit u) {
        CertificatCredit cert = u.getCertificatCredit();
        Long titId = cert != null && cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        String titRs = cert != null && cert.getEntreprise() != null ? cert.getEntreprise().getRaisonSociale() : null;
        Long demandeurId = u.getEntreprise() != null ? u.getEntreprise().getId() : null;
        boolean demandeurSt = titId != null && demandeurId != null && !titId.equals(demandeurId);

        UtilisationCreditDto.UtilisationCreditDtoBuilder b = UtilisationCreditDto.builder()
                .id(u.getId())
                .type(u.getType())
                .dateDemande(u.getDateDemande())
                .montant(u.getMontant())
                .statut(u.getStatut())
                .dateLiquidation(u.getDateLiquidation())
                .certificatCreditId(cert != null ? cert.getId() : null)
                .entrepriseId(demandeurId)
                .certificatTitulaireEntrepriseId(titId)
                .certificatTitulaireRaisonSociale(titRs)
                .demandeurEstSousTraitant(demandeurSt);

        if (u instanceof UtilisationDouaniere d) {
            b.numeroDeclaration(d.getNumeroDeclaration())
                    .numeroBulletin(d.getNumeroBulletin())
                    .dateDeclaration(d.getDateDeclaration())
                    .montantDroits(d.getMontantDroits())
                    .montantTVADouane(d.getMontantTVA())
                    .enregistreeSYDONIA(d.getEnregistreeSYDONIA())
                    .soldeCordonAvant(d.getSoldeCordonAvant())
                    .soldeCordonApres(d.getSoldeCordonApres());
        } else if (u instanceof UtilisationTVAInterieure t) {
            b.typeAchat(t.getTypeAchat())
                    .numeroFacture(t.getNumeroFacture())
                    .dateFacture(t.getDateFacture())
                    .montantTVAInterieure(t.getMontantTVA())
                    .numeroDecompte(t.getNumeroDecompte())
                    .tvaDeductibleUtilisee(t.getTvaDeductibleUtilisee())
                    .tvaNette(t.getTvaNette())
                    .creditInterieurUtilise(t.getCreditInterieurUtilise())
                    .paiementEntreprise(t.getPaiementEntreprise())
                    .reportANouveau(t.getReportANouveau())
                    .soldeTVAAvant(t.getSoldeTVAAvant())
                    .soldeTVAApres(t.getSoldeTVAApres());
        }

        return b.build();
    }

    private void notifyUtilisation(UtilisationCredit utilisation, StatutUtilisation statut) {
        if (utilisation == null || utilisation.getEntreprise() == null) {
            return;
        }
        List<Long> userIds = utilisateurRepository.findByEntrepriseId(utilisation.getEntreprise().getId())
                .stream()
                .map(mr.gov.finances.sgci.domain.entity.Utilisateur::getId)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        payload.put("type", utilisation.getType() != null ? utilisation.getType().name() : null);
        String message = "Utilisation de crédit statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.UTILISATION_STATUT_CHANGE,
                "UtilisationCredit", utilisation.getId(), message, payload);
    }
}
