package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LettreCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.LettreCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificatCreditService {

    private final CertificatCreditRepository repository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final LettreCorrectionRepository lettreCorrectionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CertificatCreditWorkflow workflow;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final DocumentCertificatCreditService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final DossierGedService dossierGedService;

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findAll(AuthenticatedUser user) {
        List<CertificatCredit> list = resolveCertificatList(user, null);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificatCreditDto findById(Long id, AuthenticatedUser user) {
        CertificatCredit c = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Certificat de crédit non trouvé: " + id));
        if (!canAccessCertificat(c.getId(), user)) {
            throw new RuntimeException("Accès refusé: certificat hors périmètre");
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByEntreprise(Long entrepriseId) {
        return repository.findByEntrepriseId(entrepriseId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByStatut(StatutCertificat statut, AuthenticatedUser user) {
        List<CertificatCredit> list = resolveCertificatList(user, statut);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    private List<CertificatCredit> resolveCertificatList(AuthenticatedUser user, StatutCertificat statut) {
        if (user == null || user.getUserId() == null) {
            if (statut == null) {
                return repository.findAll();
            }
            return repository.findByStatut(statut);
        }

        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Role role = u.getRole();

        List<CertificatCredit> base;
        if (role == Role.AUTORITE_CONTRACTANTE) {
            if (u.getAutoriteContractante() == null) {
                throw new RuntimeException("Aucune autorité contractante liée à l'utilisateur");
            }
            base = repository.findAllByAutoriteContractanteId(u.getAutoriteContractante().getId());
        } else if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
            base = repository.findAllByDelegueId(u.getId());
        } else {
            if (statut == null) {
                return repository.findAll();
            }
            return repository.findByStatut(statut);
        }

        if (statut == null) {
            return base;
        }
        return base.stream().filter(c -> c.getStatut() == statut).collect(Collectors.toList());
    }

    private boolean canAccessCertificat(Long certificatId, AuthenticatedUser user) {
        if (certificatId == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || u.getRole() == null) {
            return false;
        }

        if (u.getRole() == Role.AUTORITE_CONTRACTANTE) {
            if (u.getAutoriteContractante() == null) {
                return false;
            }
            return repository.findById(certificatId)
                    .map(c -> c.getDemandeCorrection() != null
                            && c.getDemandeCorrection().getAutoriteContractante() != null
                            && c.getDemandeCorrection().getAutoriteContractante().getId().equals(u.getAutoriteContractante().getId()))
                    .orElse(false);
        }

        if (u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP) {
            return repository.existsAccessByDelegue(u.getId(), certificatId);
        }

        return true;
    }

    @Transactional
    public CertificatCreditDto create(CreateCertificatCreditRequest request) {
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));

        if (request.getLettreCorrectionId() == null && request.getDemandeCorrectionId() == null) {
            throw new RuntimeException("La demande de correction ou la lettre de correction est obligatoire pour la mise en place du crédit d'impôt");
        }

        LettreCorrection lettreCorrection = null;
        DemandeCorrection demandeCorrection = null;

        if (request.getLettreCorrectionId() != null) {
            lettreCorrection = lettreCorrectionRepository.findById(request.getLettreCorrectionId())
                    .orElseThrow(() -> new RuntimeException("Lettre de correction non trouvée: " + request.getLettreCorrectionId()));
            demandeCorrection = lettreCorrection.getFeuilleEvaluation() != null
                    ? lettreCorrection.getFeuilleEvaluation().getDemandeCorrection()
                    : null;
        } else if (request.getDemandeCorrectionId() != null) {
            demandeCorrection = demandeCorrectionRepository.findById(request.getDemandeCorrectionId())
                    .orElseThrow(() -> new RuntimeException("Demande de correction non trouvée: " + request.getDemandeCorrectionId()));
        }

        assertMiseEnPlaceTrigger(lettreCorrection, demandeCorrection);

        if (demandeCorrection != null && demandeCorrection.getEntreprise() != null
                && demandeCorrection.getEntreprise().getId() != null
                && !demandeCorrection.getEntreprise().getId().equals(entreprise.getId())) {
            throw new RuntimeException("Incohérence: l'entreprise de la demande de correction ne correspond pas à l'entreprise du certificat");
        }

        String numero = "CERT-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal soldeCordon = request.getSoldeCordon() != null ? request.getSoldeCordon() : request.getMontantCordon();
        BigDecimal soldeTVA = request.getSoldeTVA() != null ? request.getSoldeTVA() : request.getMontantTVAInterieure();
        CertificatCredit entity = CertificatCredit.builder()
                .numero(numero)
                .dateEmission(Instant.now())
                .dateValidite(request.getDateValidite())
                .montantCordon(request.getMontantCordon())
                .montantTVAInterieure(request.getMontantTVAInterieure())
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTVA)
                .statut(StatutCertificat.DEMANDE)
                .entreprise(entreprise)
                .lettreCorrection(lettreCorrection)
                .demandeCorrection(demandeCorrection)
                .build();
        entity = repository.save(entity);

        if (demandeCorrection != null && demandeCorrection.getId() != null) {
            dossierGedService.attachCertificatToDossier(demandeCorrection.getId(), entity.getId());
        }

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "CertificatCredit", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public CertificatCreditDto updateStatut(Long id, StatutCertificat statut, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Certificat de crédit non trouvé: " + id));
        StatutCertificat fromStatut = entity.getStatut();
        workflow.validateTransition(entity.getStatut(), statut);

        assertActorCanTransition(fromStatut, statut, user);

        if (statut == StatutCertificat.EN_VERIFICATION_DGI || statut == StatutCertificat.OUVERT) {
            requirementValidator.assertRequiredDocumentsPresent(
                    ProcessusDocument.MISE_EN_PLACE_CI,
                    documentService.findActiveDocumentTypes(entity.getId())
            );
        }

        if (statut == StatutCertificat.OUVERT && fromStatut != StatutCertificat.OUVERT) {
            assertMontantsRenseignes(entity);
            BigDecimal montantCordon = entity.getMontantCordon() != null ? entity.getMontantCordon() : BigDecimal.ZERO;
            BigDecimal montantTVA = entity.getMontantTVAInterieure() != null ? entity.getMontantTVAInterieure() : BigDecimal.ZERO;
            if (entity.getSoldeCordon() == null) {
                entity.setSoldeCordon(montantCordon);
            }
            if (entity.getSoldeTVA() == null) {
                entity.setSoldeTVA(montantTVA);
            }
        }

        entity.setStatut(statut);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        notifyCertificat(entity, statut);
        return result;
    }

    @Transactional
    public CertificatCreditDto updateMontants(Long id, UpdateCertificatCreditMontantsRequest request, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut renseigner les montants");
        }
        CertificatCredit entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Certificat de crédit non trouvé: " + id));

        entity.setMontantCordon(request.getMontantCordon());
        entity.setMontantTVAInterieure(request.getMontantTVAInterieure());

        // Synchroniser les soldes tant que le crédit n'est pas ouvert (sinon incohérence avec les utilisations)
        if (entity.getStatut() != StatutCertificat.OUVERT) {
            if (entity.getSoldeCordon() == null || BigDecimal.ZERO.compareTo(entity.getSoldeCordon()) == 0) {
                entity.setSoldeCordon(request.getMontantCordon());
            }
            if (entity.getSoldeTVA() == null || BigDecimal.ZERO.compareTo(entity.getSoldeTVA()) == 0) {
                entity.setSoldeTVA(request.getMontantTVAInterieure());
            }
        }

        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        return result;
    }

    private void assertActorCanTransition(StatutCertificat from, StatutCertificat to, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        Role role = user.getRole();

        if (to == StatutCertificat.EN_VERIFICATION_DGI && role != Role.DGI) {
            throw new RuntimeException("Seul DGI peut passer le certificat en vérification");
        }
        if (to == StatutCertificat.EN_VALIDATION_PRESIDENT && role != Role.PRESIDENT) {
            throw new RuntimeException("Seul le Président peut valider cette étape");
        }
        if (to == StatutCertificat.VALIDE_PRESIDENT && role != Role.PRESIDENT) {
            throw new RuntimeException("Seul le Président peut valider le certificat");
        }
        if (to == StatutCertificat.EN_OUVERTURE_DGTCP) {
            boolean allowed = role == Role.DGTCP || (role == Role.DGI && from == StatutCertificat.EN_VERIFICATION_DGI);
            if (!allowed) {
                throw new RuntimeException("Seul DGTCP (ou DGI après vérification) peut passer en ouverture DGTCP");
            }
        }
        if (to == StatutCertificat.OUVERT && role != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut ouvrir le crédit");
        }

        if (to == StatutCertificat.ANNULE) {
            if (role != Role.AUTORITE_CONTRACTANTE && role != Role.DGI && role != Role.PRESIDENT && role != Role.DGTCP) {
                throw new RuntimeException("Rôle non autorisé à annuler le certificat");
            }
        }
    }

    private void assertMiseEnPlaceTrigger(LettreCorrection lettreCorrection, DemandeCorrection demandeCorrection) {
        if (demandeCorrection == null) {
            throw new RuntimeException("La demande de correction est obligatoire pour la mise en place du crédit d'impôt");
        }
        if (lettreCorrection != null) {
            if (!Boolean.TRUE.equals(lettreCorrection.getSignee())) {
                throw new RuntimeException("La lettre de correction doit être signée");
            }
            if (!Boolean.TRUE.equals(lettreCorrection.getNotifiee())) {
                throw new RuntimeException("La lettre de correction doit être notifiée");
            }
        }
        if (demandeCorrection.getMarche() == null) {
            throw new RuntimeException("Le certificat doit être rattaché à un marché (via la demande de correction)");
        }

        StatutDemande statutDemande = demandeCorrection.getStatut();
        if (statutDemande != StatutDemande.ADOPTEE && statutDemande != StatutDemande.NOTIFIEE) {
            throw new RuntimeException("La demande de correction doit être visée (ADOPTEE/NOTIFIEE) avant la mise en place du crédit d'impôt");
        }

        if (demandeCorrection.getMarche().getDateSignature() == null) {
            throw new RuntimeException("Le contrat (marché) doit être signé");
        }
    }

    private void assertMontantsRenseignes(CertificatCredit entity) {
        if (entity == null) {
            throw new RuntimeException("Certificat invalide");
        }
        if (entity.getMontantCordon() == null || entity.getMontantTVAInterieure() == null) {
            throw new RuntimeException("Les montants (cordon et TVA intérieure) doivent être renseignés avant l'ouverture du crédit");
        }
        if (entity.getMontantCordon().compareTo(BigDecimal.ZERO) <= 0 || entity.getMontantTVAInterieure().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Les montants (cordon et TVA intérieure) doivent être strictement supérieurs à zéro");
        }
    }

    private CertificatCreditDto toDto(CertificatCredit c) {
        Entreprise e = c.getEntreprise();
        Long demandeCorrectionId = c.getDemandeCorrection() != null ? c.getDemandeCorrection().getId() : null;
        Long marcheId = c.getDemandeCorrection() != null && c.getDemandeCorrection().getMarche() != null
                ? c.getDemandeCorrection().getMarche().getId()
                : null;
        return CertificatCreditDto.builder()
                .id(c.getId())
                .numero(c.getNumero())
                .dateEmission(c.getDateEmission())
                .dateValidite(c.getDateValidite())
                .montantCordon(c.getMontantCordon())
                .montantTVAInterieure(c.getMontantTVAInterieure())
                .soldeCordon(c.getSoldeCordon())
                .soldeTVA(c.getSoldeTVA())
                .statut(c.getStatut())
                .entrepriseId(e != null ? e.getId() : null)
                .entrepriseRaisonSociale(e != null ? e.getRaisonSociale() : null)
                .demandeCorrectionId(demandeCorrectionId)
                .marcheId(marcheId)
                .build();
    }

    private void notifyCertificat(CertificatCredit certificat, StatutCertificat statut) {
        if (certificat == null || certificat.getEntreprise() == null) {
            return;
        }
        List<Long> userIds = utilisateurRepository.findByEntrepriseId(certificat.getEntreprise().getId())
                .stream()
                .map(mr.gov.finances.sgci.domain.entity.Utilisateur::getId)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        payload.put("numero", certificat.getNumero());
        String message = "Certificat " + certificat.getNumero() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.CERTIFICAT_STATUT_CHANGE,
                "CertificatCredit", certificat.getId(), message, payload);
    }
}
