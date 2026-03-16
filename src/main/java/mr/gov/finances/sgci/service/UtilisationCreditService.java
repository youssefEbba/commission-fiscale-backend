package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
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
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.LiquiderUtilisationDouaneRequest;
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

    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UtilisationCreditDto findById(Long id) {
        return repository.findById(id).map(this::toDto).orElseThrow(
                () -> new RuntimeException("Utilisation de crédit non trouvée: " + id));
    }

    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findByCertificatCreditId(Long certificatCreditId) {
        return repository.findByCertificatCreditId(certificatCreditId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request) {
        return create(request, null);
    }

    @Transactional
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        CertificatCredit certificat = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> new RuntimeException("Certificat de crédit non trouvé"));
        if (certificat.getStatut() != StatutCertificat.OUVERT) {
            throw new RuntimeException("Le crédit doit être OUVERT pour créer une utilisation");
        }
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));

        // Contrôle d'accès création
        if (user != null && user.getRole() != null) {
            mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            if (u.getRole() == Role.ENTREPRISE) {
                if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                    throw new RuntimeException("Aucune entreprise liée à l'utilisateur");
                }
                if (!u.getEntreprise().getId().equals(entreprise.getId())) {
                    throw new RuntimeException("Accès refusé: entreprise requête invalide");
                }
                if (certificat.getEntreprise() == null || certificat.getEntreprise().getId() == null) {
                    throw new RuntimeException("Certificat sans entreprise");
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
        return result;
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
                    throw new RuntimeException("Documents obligatoires manquants (Achat local): "
                            + (present.contains(TypeDocument.FACTURE) ? "" : "FACTURE ")
                            + (present.contains(TypeDocument.DECLARATION_TVA) ? "" : "DECLARATION_TVA"));
                }
            } else if (typeAchat == TypeAchat.DECOMPTE) {
                if (!present.contains(TypeDocument.DECOMPTE)) {
                    throw new RuntimeException("Document obligatoire manquant (Décompte): DECOMPTE");
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
        UtilisationCredit entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Utilisation de crédit non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw new RuntimeException("Cette utilisation n'est pas de type Douane");
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.LIQUIDEE);
        assertActorCanTransition(entity, StatutUtilisation.LIQUIDEE, user);

        BigDecimal droits = request != null && request.getMontantDroits() != null ? request.getMontantDroits() : BigDecimal.ZERO;
        BigDecimal tva = request != null && request.getMontantTVA() != null ? request.getMontantTVA() : BigDecimal.ZERO;
        if (droits.compareTo(BigDecimal.ZERO) < 0 || tva.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Montants d'imputation invalides (doivent être >= 0)");
        }
        BigDecimal total = droits.add(tva);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Le montant total imputé doit être > 0");
        }

        d.setMontantDroits(droits);
        d.setMontantTVA(tva);
        d.setMontant(total);

        d.setStatut(StatutUtilisation.LIQUIDEE);
        d.setDateLiquidation(Instant.now());
        debitSolde(d);

        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisation(entity, StatutUtilisation.LIQUIDEE);
        return result;
    }

    @Transactional
    public UtilisationCreditDto updateStatut(Long id, StatutUtilisation statut, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Utilisation de crédit non trouvée: " + id));
        workflow.validateTransition(entity.getStatut(), statut);

        assertActorCanTransition(entity, statut, user);

        if (entity.getType() == TypeUtilisation.DOUANIER && statut == StatutUtilisation.LIQUIDEE) {
            throw new RuntimeException("Liquidation Douane: veuillez utiliser l'endpoint dédié avec saisie des montants d'imputation");
        }

        if (statut == StatutUtilisation.EN_VERIFICATION) {
            assertRequiredDocumentsPresent(entity);
        }

        entity.setStatut(statut);

        if ((entity.getType() == TypeUtilisation.DOUANIER && statut == StatutUtilisation.LIQUIDEE)
                || (entity.getType() == TypeUtilisation.TVA_INTERIEURE && statut == StatutUtilisation.APUREE)) {
            entity.setDateLiquidation(Instant.now());
            debitSolde(entity);
        }

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
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (utilisation == null || utilisation.getType() == null) {
            throw new RuntimeException("Type d'utilisation manquant");
        }
        Role role = user.getRole();

        // Verrouillage métier par type (TDR)
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            if (to != StatutUtilisation.EN_VERIFICATION
                    && to != StatutUtilisation.VISE
                    && to != StatutUtilisation.LIQUIDEE
                    && to != StatutUtilisation.REJETEE) {
                throw new RuntimeException("Transition non autorisée (Douane): vers " + to);
            }
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (to != StatutUtilisation.EN_VERIFICATION
                    && to != StatutUtilisation.VALIDEE
                    && to != StatutUtilisation.APUREE
                    && to != StatutUtilisation.REJETEE) {
                throw new RuntimeException("Transition non autorisée (TVA intérieure): vers " + to);
            }
        }

        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            if (to == StatutUtilisation.EN_VERIFICATION || to == StatutUtilisation.VISE) {
                if (role != Role.DGD) {
                    throw new RuntimeException("Seul DGD peut traiter cette étape Douane");
                }
                return;
            }

            if (to == StatutUtilisation.LIQUIDEE) {
                if (role != Role.DGTCP) {
                    throw new RuntimeException("Seul DGTCP peut liquider l'utilisation Douane");
                }
                return;
            }

            if (to == StatutUtilisation.REJETEE) {
                if (role != Role.DGD && role != Role.DGTCP) {
                    throw new RuntimeException("Seul DGD ou DGTCP peut rejeter l'utilisation Douane");
                }
                return;
            }
            return;
        }

        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (role != Role.DGTCP) {
                throw new RuntimeException("Seul DGTCP peut traiter les utilisations TVA intérieure");
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
                throw new RuntimeException("Solde cordon insuffisant pour imputer l'utilisation (solde=" + solde + ", montant=" + montant + ")");
            }
            certificat.setSoldeCordon(solde.subtract(montant));
        } else if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            BigDecimal solde = certificat.getSoldeTVA() != null ? certificat.getSoldeTVA() : BigDecimal.ZERO;
            if (solde.compareTo(montant) < 0) {
                throw new RuntimeException("Solde TVA insuffisant pour imputer l'utilisation (solde=" + solde + ", montant=" + montant + ")");
            }
            certificat.setSoldeTVA(solde.subtract(montant));
        }
        certificatRepository.save(certificat);
    }

    private UtilisationCreditDto toDto(UtilisationCredit u) {
        UtilisationCreditDto.UtilisationCreditDtoBuilder b = UtilisationCreditDto.builder()
                .id(u.getId())
                .type(u.getType())
                .dateDemande(u.getDateDemande())
                .montant(u.getMontant())
                .statut(u.getStatut())
                .dateLiquidation(u.getDateLiquidation())
                .certificatCreditId(u.getCertificatCredit() != null ? u.getCertificatCredit().getId() : null)
                .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null);

        if (u instanceof UtilisationDouaniere d) {
            b.numeroDeclaration(d.getNumeroDeclaration())
                    .numeroBulletin(d.getNumeroBulletin())
                    .dateDeclaration(d.getDateDeclaration())
                    .montantDroits(d.getMontantDroits())
                    .montantTVADouane(d.getMontantTVA())
                    .enregistreeSYDONIA(d.getEnregistreeSYDONIA());
        } else if (u instanceof UtilisationTVAInterieure t) {
            b.typeAchat(t.getTypeAchat())
                    .numeroFacture(t.getNumeroFacture())
                    .dateFacture(t.getDateFacture())
                    .montantTVAInterieure(t.getMontantTVA())
                    .numeroDecompte(t.getNumeroDecompte());
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
