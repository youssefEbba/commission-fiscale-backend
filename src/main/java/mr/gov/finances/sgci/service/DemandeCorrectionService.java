package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.DqeLigne;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.FiscaliteInterieure;
import mr.gov.finances.sgci.domain.entity.LigneImportation;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.entity.DemandeCorrectionRejet;
import mr.gov.finances.sgci.domain.entity.Recapitulatif;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionRejetDto;
import mr.gov.finances.sgci.web.dto.DqeDto;
import mr.gov.finances.sgci.web.dto.DqeLigneDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.web.dto.FiscaliteInterieureDto;
import mr.gov.finances.sgci.web.dto.LigneImportationDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.ModeleFiscalDto;
import mr.gov.finances.sgci.web.dto.RecapitulatifDto;
import mr.gov.finances.sgci.workflow.DemandeCorrectionWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeCorrectionService {

    private final DemandeCorrectionRepository demandeRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final MarcheRepository marcheRepository;
    private final ConventionRepository conventionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DemandeCorrectionWorkflow workflow;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final DocumentService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final DossierGedService dossierGedService;

    private static String generateNumero() {
        return "DC-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findAll(AuthenticatedUser user) {
        List<DemandeCorrection> list = resolveDemandeList(user);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandeCorrectionDto findById(Long id, AuthenticatedUser user) {
        DemandeCorrection dc = demandeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande de correction non trouvée: " + id));
        if (!canAccessDemandeCorrection(dc.getId(), user)) {
            throw new RuntimeException("Accès refusé: demande hors périmètre");
        }
        return toDto(dc);
    }

    private List<DemandeCorrection> resolveDemandeList(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            return demandeRepository.findAllByOrderByDateDepotDescIdDesc();
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Role role = u.getRole();

        if (role == Role.AUTORITE_CONTRACTANTE) {
            if (u.getAutoriteContractante() == null) {
                throw new RuntimeException("Aucune autorité contractante liée à l'utilisateur");
            }
            return demandeRepository.findByAutoriteContractanteIdOrderByDateDepotDescIdDesc(u.getAutoriteContractante().getId());
        }

        if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
            return demandeRepository.findByDelegueId(u.getId());
        }

        if (role == Role.ENTREPRISE) {
            if (u.getEntreprise() == null) {
                throw new RuntimeException("Aucune entreprise liée à l'utilisateur");
            }
            return demandeRepository.findByEntrepriseIdOrderByDateDepotDescIdDesc(u.getEntreprise().getId());
        }

        return demandeRepository.findAllByOrderByDateDepotDescIdDesc();
    }

    private boolean canAccessDemandeCorrection(Long demandeId, AuthenticatedUser user) {
        if (demandeId == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || u.getRole() == null) {
            return false;
        }

        if (u.getRole() == Role.AUTORITE_CONTRACTANTE) {
            if (u.getAutoriteContractante() == null) {
                return false;
            }
            return demandeRepository.findById(demandeId)
                    .map(dc -> dc.getAutoriteContractante() != null
                            && dc.getAutoriteContractante().getId().equals(u.getAutoriteContractante().getId()))
                    .orElse(false);
        }

        if (u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP) {
            return demandeRepository.existsAccessByDelegue(u.getId(), demandeId);
        }

        if (u.getRole() == Role.ENTREPRISE) {
            if (u.getEntreprise() == null) {
                return false;
            }
            return demandeRepository.findById(demandeId)
                    .map(dc -> dc.getEntreprise() != null
                            && dc.getEntreprise().getId().equals(u.getEntreprise().getId()))
                    .orElse(false);
        }

        return true;
    }

    @Transactional
    public DemandeCorrectionDto create(CreateDemandeCorrectionRequest request) {
        AutoriteContractante autorite = autoriteRepository.findById(request.getAutoriteContractanteId())
                .orElseThrow(() -> new RuntimeException("Autorité contractante non trouvée"));
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        Convention convention = conventionRepository.findById(request.getConventionId())
                .orElseThrow(() -> new RuntimeException("Convention non trouvée"));
        Marche marche = null;
        if (request.getMarcheId() != null) {
            marche = marcheRepository.findById(request.getMarcheId())
                    .orElseThrow(() -> new RuntimeException("Marché non trouvé: " + request.getMarcheId()));
            if (marche.getDemandeCorrection() != null) {
                throw new RuntimeException("Le marché est déjà associé à une correction");
            }

            if (marche.getConvention() == null || marche.getConvention().getId() == null) {
                throw new RuntimeException("Le marché n'est rattaché à aucune convention");
            }
            if (!marche.getConvention().getId().equals(request.getConventionId())) {
                throw new RuntimeException("Le marché n'appartient pas à la convention sélectionnée");
            }

            Long marcheAcId = marche.getConvention().getAutoriteContractante() != null
                    ? marche.getConvention().getAutoriteContractante().getId()
                    : null;
            if (marcheAcId == null || !marcheAcId.equals(request.getAutoriteContractanteId())) {
                throw new RuntimeException("Le marché est hors périmètre de l'autorité contractante sélectionnée");
            }
        }
        DemandeCorrection entity = DemandeCorrection.builder()
                .numero(generateNumero())
                .dateDepot(Instant.now())
                .statut(StatutDemande.RECUE)
                .autoriteContractante(autorite)
                .entreprise(entreprise)
                .convention(convention)
                .build();
        if (marche != null) {
            entity.setMarche(marche);
            marche.setDemandeCorrection(entity);
        }
        ModeleFiscal modeleFiscal = toModeleFiscalEntity(request.getModeleFiscal(), entity);
        Dqe dqe = toDqeEntity(request.getDqe(), entity);
        entity.setModeleFiscal(modeleFiscal);
        entity.setDqe(dqe);
        entity = demandeRepository.save(entity);

        dossierGedService.ensureCreatedForDemandeCorrection(entity.getId());

        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "DemandeCorrection", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public DemandeCorrectionDto updateStatut(Long id, StatutDemande statut, AuthenticatedUser user, String motifRejet, Boolean decisionFinale) {
        DemandeCorrection entity = demandeRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Demande de correction non trouvée: " + id));

        if (!canAccessDemandeCorrection(id, user)) {
            throw new RuntimeException("Accès refusé: demande hors périmètre");
        }

        if (user != null && user.getRole() != null
                && (user.getRole() == Role.AUTORITE_CONTRACTANTE || user.getRole() == Role.ENTREPRISE)
                && statut != StatutDemande.ANNULEE) {
            throw new RuntimeException("Accès refusé: action non autorisée");
        }

        workflow.validateTransition(entity.getStatut(), statut);

        if (statut == StatutDemande.RECEVABLE || statut == StatutDemande.EN_EVALUATION) {
            requirementValidator.assertRequiredDocumentsPresent(
                    ProcessusDocument.CORRECTION_OFFRE_FISCALE,
                    documentService.findActiveDocumentTypes(entity.getId())
            );
        }

        boolean finale = Boolean.TRUE.equals(decisionFinale);
        if (statut == StatutDemande.ADOPTEE) {
            applyParallelValidation(entity, user);
            if (finale) {
                assertFinalDecisionRole(user);
                entity.setStatut(StatutDemande.ADOPTEE);
                entity.setMotifRejet(null);
            }
        } else if (statut == StatutDemande.REJETEE) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw new RuntimeException("Le motif de rejet est obligatoire");
            }
            DemandeCorrectionRejet rejet = createRejet(entity, user, motifRejet);
            entity.getRejets().add(rejet);
            if (finale) {
                assertFinalDecisionRole(user);
                entity.setStatut(StatutDemande.REJETEE);
                entity.setMotifRejet(motifRejet);
            }
        } else if (finale) {
            throw new RuntimeException("Décision finale non supportée pour le statut: " + statut);
        } else {
            entity.setStatut(statut);
        }
        entity = demandeRepository.save(entity);
        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(id), result);
        notifyDemandeCorrection(entity, statut, motifRejet, user, finale);
        return result;
    }

    private void applyParallelValidation(DemandeCorrection entity, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role == Role.PRESIDENT) {
            return;
        }
        if (role == Role.DGD) {
            entity.setValidationDgd(true);
            entity.setValidationDgdUserId(user.getUserId());
            entity.setValidationDgdDate(Instant.now());
        } else if (role == Role.DGTCP) {
            entity.setValidationDgtcp(true);
            entity.setValidationDgtcpUserId(user.getUserId());
            entity.setValidationDgtcpDate(Instant.now());
        } else if (role == Role.DGI) {
            entity.setValidationDgi(true);
            entity.setValidationDgiUserId(user.getUserId());
            entity.setValidationDgiDate(Instant.now());
        } else if (role == Role.DGB) {
            entity.setValidationDgb(true);
            entity.setValidationDgbUserId(user.getUserId());
            entity.setValidationDgbDate(Instant.now());
        } else {
            throw new RuntimeException("Rôle non autorisé pour la validation: " + role);
        }
    }

    private void assertFinalDecisionRole(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP && user.getRole() != Role.PRESIDENT) {
            throw new RuntimeException("Rôle non autorisé pour la décision finale: " + user.getRole());
        }
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByAutoriteContractante(Long autoriteId) {
        return demandeRepository.findByAutoriteContractanteIdOrderByDateDepotDescIdDesc(autoriteId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByEntreprise(Long entrepriseId) {
        return demandeRepository.findByEntrepriseIdOrderByDateDepotDescIdDesc(entrepriseId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByDelegue(Long delegueId, AuthenticatedUser user) {
        if (user == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if ((user.getRole() == Role.AUTORITE_UPM || user.getRole() == Role.AUTORITE_UEP)
                && (delegueId == null || !delegueId.equals(user.getUserId()))) {
            throw new RuntimeException("Accès refusé: vous ne pouvez consulter que vos propres demandes");
        }
        return demandeRepository.findByDelegueId(delegueId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByStatut(StatutDemande statut) {
        return demandeRepository.findByStatutOrderByDateDepotDescIdDesc(statut).stream().map(this::toDto).collect(Collectors.toList());
    }

    private DemandeCorrectionDto toDto(DemandeCorrection d) {
        return DemandeCorrectionDto.builder()
                .id(d.getId())
                .numero(d.getNumero())
                .dateDepot(d.getDateDepot())
                .statut(d.getStatut())
                .validationDgd(d.isValidationDgd())
                .validationDgtcp(d.isValidationDgtcp())
                .validationDgi(d.isValidationDgi())
                .validationDgb(d.isValidationDgb())
                .validationDgdUserId(d.getValidationDgdUserId())
                .validationDgdDate(d.getValidationDgdDate())
                .validationDgtcpUserId(d.getValidationDgtcpUserId())
                .validationDgtcpDate(d.getValidationDgtcpDate())
                .validationDgiUserId(d.getValidationDgiUserId())
                .validationDgiDate(d.getValidationDgiDate())
                .validationDgbUserId(d.getValidationDgbUserId())
                .validationDgbDate(d.getValidationDgbDate())
                .motifRejet(d.getMotifRejet())
                .dateCreation(d.getDateCreation())
                .dateModification(d.getDateModification())
                .autoriteContractanteId(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getNom() : null)
                .entrepriseId(d.getEntreprise() != null ? d.getEntreprise().getId() : null)
                .entrepriseRaisonSociale(d.getEntreprise() != null ? d.getEntreprise().getRaisonSociale() : null)
                .conventionId(d.getConvention() != null ? d.getConvention().getId() : null)
                .modeleFiscal(toModeleFiscalDto(d.getModeleFiscal()))
                .dqe(toDqeDto(d.getDqe()))
                .marche(toMarcheDto(d.getMarche()))
                .documents(d.getDocuments() != null ? d.getDocuments().stream().map(this::documentToDto).collect(Collectors.toList()) : List.of())
                .rejets(d.getRejets() != null ? d.getRejets().stream().map(this::toRejetDto).collect(Collectors.toList()) : List.of())
                .decisions(d.getDecisions() != null ? d.getDecisions().stream().map(this::toDecisionDto).collect(Collectors.toList()) : List.of())
                .build();
    }

    private DemandeCorrectionRejet createRejet(DemandeCorrection entity, AuthenticatedUser user, String motifRejet) {
        if (user == null || user.getUserId() == null) {
            throw new RuntimeException("Utilisateur non authentifié pour le rejet");
        }
        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return DemandeCorrectionRejet.builder()
                .demandeCorrection(entity)
                .utilisateur(utilisateur)
                .motifRejet(motifRejet)
                .dateRejet(Instant.now())
                .build();
    }

    private DemandeCorrectionRejetDto toRejetDto(DemandeCorrectionRejet rejet) {
        return DemandeCorrectionRejetDto.builder()
                .id(rejet.getId())
                .motifRejet(rejet.getMotifRejet())
                .dateRejet(rejet.getDateRejet())
                .utilisateurId(rejet.getUtilisateur() != null ? rejet.getUtilisateur().getId() : null)
                .utilisateurNom(rejet.getUtilisateur() != null ? rejet.getUtilisateur().getNomComplet() : null)
                .build();
    }

    private DecisionCorrectionDto toDecisionDto(DecisionCorrection decision) {
        return DecisionCorrectionDto.builder()
                .id(decision.getId())
                .role(decision.getRole())
                .decision(decision.getDecision())
                .motifRejet(decision.getMotifRejet())
                .documentsDemandes(decision.getDocumentsDemandes())
                .dateDecision(decision.getDateDecision())
                .rejetTempStatus(decision.getRejetTempStatus())
                .rejetTempResolvedAt(decision.getRejetTempResolvedAt())
                .utilisateurId(decision.getUtilisateur() != null ? decision.getUtilisateur().getId() : null)
                .utilisateurNom(decision.getUtilisateur() != null ? decision.getUtilisateur().getNomComplet() : null)
                .rejetTempResponses(decision.getRejetTempResponses() != null
                        ? decision.getRejetTempResponses().stream().map(this::toRejetTempResponseDto).collect(Collectors.toList())
                        : java.util.List.of())
                .build();
    }

    private mr.gov.finances.sgci.web.dto.RejetTempResponseDto toRejetTempResponseDto(mr.gov.finances.sgci.domain.entity.RejetTempResponse entity) {
        return mr.gov.finances.sgci.web.dto.RejetTempResponseDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .documentUrl(entity.getDocumentUrl())
                .documentType(entity.getDocumentType())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }

    private ModeleFiscal toModeleFiscalEntity(ModeleFiscalDto dto, DemandeCorrection demande) {
        ModeleFiscal modele = ModeleFiscal.builder()
                .referenceDossier(dto != null ? dto.getReferenceDossier() : null)
                .typeProjet(dto != null ? dto.getTypeProjet() : null)
                .afficherNomenclature(dto != null ? dto.getAfficherNomenclature() : null)
                .dateCreation(dto != null ? dto.getDateCreation() : null)
                .dateModification(dto != null ? dto.getDateModification() : null)
                .demandeCorrection(demande)
                .build();

        List<LigneImportation> lignes = dto != null && dto.getImportations() != null
                ? dto.getImportations().stream().map(this::toLigneImportationEntity).collect(Collectors.toList())
                : new ArrayList<>();
        lignes.forEach(ligne -> ligne.setModeleFiscal(modele));
        modele.setImportations(lignes);

        FiscaliteInterieure fiscalite = dto != null && dto.getFiscaliteInterieure() != null
                ? toFiscaliteInterieureEntity(dto.getFiscaliteInterieure())
                : FiscaliteInterieure.builder().build();
        fiscalite.setModeleFiscal(modele);
        modele.setFiscaliteInterieure(fiscalite);

        Recapitulatif recapitulatif = dto != null && dto.getRecapitulatif() != null
                ? toRecapitulatifEntity(dto.getRecapitulatif())
                : Recapitulatif.builder().build();
        recapitulatif.setModeleFiscal(modele);
        modele.setRecapitulatif(recapitulatif);

        return modele;
    }

    private LigneImportation toLigneImportationEntity(LigneImportationDto dto) {
        return LigneImportation.builder()
                .designation(dto.getDesignation())
                .unite(dto.getUnite())
                .quantite(dto.getQuantite())
                .prixUnitaire(dto.getPrixUnitaire())
                .nomenclature(dto.getNomenclature())
                .tauxDD(dto.getTauxDD())
                .tauxRS(dto.getTauxRS())
                .tauxPSC(dto.getTauxPSC())
                .tauxTVA(dto.getTauxTVA())
                .valeurDouane(dto.getValeurDouane())
                .dd(dto.getDd())
                .rs(dto.getRs())
                .psc(dto.getPsc())
                .baseTVA(dto.getBaseTVA())
                .tvaDouane(dto.getTvaDouane())
                .totalTaxes(dto.getTotalTaxes())
                .build();
    }

    private FiscaliteInterieure toFiscaliteInterieureEntity(FiscaliteInterieureDto dto) {
        return FiscaliteInterieure.builder()
                .montantHT(dto.getMontantHT())
                .tauxTVA(dto.getTauxTVA())
                .autresTaxes(dto.getAutresTaxes())
                .tvaCollectee(dto.getTvaCollectee())
                .tvaDeductible(dto.getTvaDeductible())
                .tvaNette(dto.getTvaNette())
                .creditInterieur(dto.getCreditInterieur())
                .build();
    }

    private Recapitulatif toRecapitulatifEntity(RecapitulatifDto dto) {
        return Recapitulatif.builder()
                .creditExterieur(dto.getCreditExterieur())
                .creditInterieur(dto.getCreditInterieur())
                .creditTotal(dto.getCreditTotal())
                .build();
    }

    private Dqe toDqeEntity(DqeDto dto, DemandeCorrection demande) {
        Dqe dqe = Dqe.builder()
                .numeroAAOI(dto != null ? dto.getNumeroAAOI() : null)
                .projet(dto != null ? dto.getProjet() : null)
                .lot(dto != null ? dto.getLot() : null)
                .tauxTVA(dto != null ? dto.getTauxTVA() : null)
                .totalHT(dto != null ? dto.getTotalHT() : null)
                .montantTVA(dto != null ? dto.getMontantTVA() : null)
                .totalTTC(dto != null ? dto.getTotalTTC() : null)
                .demandeCorrection(demande)
                .build();

        List<DqeLigne> lignes = dto != null && dto.getLignes() != null
                ? dto.getLignes().stream().map(this::toDqeLigneEntity).collect(Collectors.toList())
                : new ArrayList<>();
        lignes.forEach(ligne -> ligne.setDqe(dqe));
        dqe.setLignes(lignes);
        return dqe;
    }

    private DqeLigne toDqeLigneEntity(DqeLigneDto dto) {
        return DqeLigne.builder()
                .designation(dto.getDesignation())
                .unite(dto.getUnite())
                .quantite(dto.getQuantite())
                .prixUnitaireHT(dto.getPrixUnitaireHT())
                .montantHT(dto.getMontantHT())
                .build();
    }

    private ModeleFiscalDto toModeleFiscalDto(ModeleFiscal modele) {
        if (modele == null) {
            return null;
        }
        return ModeleFiscalDto.builder()
                .id(modele.getId())
                .referenceDossier(modele.getReferenceDossier())
                .typeProjet(modele.getTypeProjet())
                .afficherNomenclature(modele.getAfficherNomenclature())
                .dateCreation(modele.getDateCreation())
                .dateModification(modele.getDateModification())
                .importations(modele.getImportations() != null
                        ? modele.getImportations().stream().map(this::toLigneImportationDto).collect(Collectors.toList())
                        : List.of())
                .fiscaliteInterieure(modele.getFiscaliteInterieure() != null ? toFiscaliteInterieureDto(modele.getFiscaliteInterieure()) : null)
                .recapitulatif(modele.getRecapitulatif() != null ? toRecapitulatifDto(modele.getRecapitulatif()) : null)
                .build();
    }

    private LigneImportationDto toLigneImportationDto(LigneImportation entity) {
        return LigneImportationDto.builder()
                .id(entity.getId())
                .designation(entity.getDesignation())
                .unite(entity.getUnite())
                .quantite(entity.getQuantite())
                .prixUnitaire(entity.getPrixUnitaire())
                .nomenclature(entity.getNomenclature())
                .tauxDD(entity.getTauxDD())
                .tauxRS(entity.getTauxRS())
                .tauxPSC(entity.getTauxPSC())
                .tauxTVA(entity.getTauxTVA())
                .valeurDouane(entity.getValeurDouane())
                .dd(entity.getDd())
                .rs(entity.getRs())
                .psc(entity.getPsc())
                .baseTVA(entity.getBaseTVA())
                .tvaDouane(entity.getTvaDouane())
                .totalTaxes(entity.getTotalTaxes())
                .build();
    }

    private FiscaliteInterieureDto toFiscaliteInterieureDto(FiscaliteInterieure entity) {
        return FiscaliteInterieureDto.builder()
                .id(entity.getId())
                .montantHT(entity.getMontantHT())
                .tauxTVA(entity.getTauxTVA())
                .autresTaxes(entity.getAutresTaxes())
                .tvaCollectee(entity.getTvaCollectee())
                .tvaDeductible(entity.getTvaDeductible())
                .tvaNette(entity.getTvaNette())
                .creditInterieur(entity.getCreditInterieur())
                .build();
    }

    private RecapitulatifDto toRecapitulatifDto(Recapitulatif entity) {
        return RecapitulatifDto.builder()
                .id(entity.getId())
                .creditExterieur(entity.getCreditExterieur())
                .creditInterieur(entity.getCreditInterieur())
                .creditTotal(entity.getCreditTotal())
                .build();
    }

    private DqeDto toDqeDto(Dqe dqe) {
        if (dqe == null) {
            return null;
        }
        return DqeDto.builder()
                .id(dqe.getId())
                .numeroAAOI(dqe.getNumeroAAOI())
                .projet(dqe.getProjet())
                .lot(dqe.getLot())
                .tauxTVA(dqe.getTauxTVA())
                .totalHT(dqe.getTotalHT())
                .montantTVA(dqe.getMontantTVA())
                .totalTTC(dqe.getTotalTTC())
                .lignes(dqe.getLignes() != null
                        ? dqe.getLignes().stream().map(this::toDqeLigneDto).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private DqeLigneDto toDqeLigneDto(DqeLigne entity) {
        return DqeLigneDto.builder()
                .id(entity.getId())
                .designation(entity.getDesignation())
                .unite(entity.getUnite())
                .quantite(entity.getQuantite())
                .prixUnitaireHT(entity.getPrixUnitaireHT())
                .montantHT(entity.getMontantHT())
                .build();
    }

    private MarcheDto toMarcheDto(Marche marche) {
        if (marche == null) {
            return null;
        }
        return MarcheDto.builder()
                .id(marche.getId())
                .demandeCorrectionId(marche.getDemandeCorrection() != null ? marche.getDemandeCorrection().getId() : null)
                .numeroMarche(marche.getNumeroMarche())
                .dateSignature(marche.getDateSignature())
                .montantContratTtc(marche.getMontantContratTtc())
                .statut(marche.getStatut())
                .build();
    }

    private DocumentDto documentToDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .type(doc.getType())
                .nomFichier(doc.getNomFichier())
                .chemin(doc.getChemin())
                .dateUpload(doc.getDateUpload())
                .taille(doc.getTaille())
                .build();
    }

    private void notifyDemandeCorrection(DemandeCorrection entity,
                                         StatutDemande statut,
                                         String motifRejet,
                                         AuthenticatedUser user,
                                         boolean finale) {
        List<Long> userIds = resolveRelatedUserIds(entity);
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        payload.put("finale", finale);
        if (motifRejet != null && !motifRejet.isBlank()) {
            payload.put("motifRejet", motifRejet);
        }
        if (user != null && user.getRole() != null) {
            payload.put("acteurRole", user.getRole().name());
            payload.put("acteurUserId", user.getUserId());
        }
        String message = "Demande de correction " + entity.getNumero() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.CORRECTION_STATUT_CHANGE,
                "DemandeCorrection", entity.getId(), message, payload);
    }

    private List<Long> resolveRelatedUserIds(DemandeCorrection entity) {
        if (entity == null) {
            return List.of();
        }
        List<Long> entrepriseUsers = entity.getEntreprise() != null
                ? utilisateurRepository.findByEntrepriseId(entity.getEntreprise().getId()).stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList())
                : List.of();
        List<Long> autoriteUsers = entity.getAutoriteContractante() != null
                ? utilisateurRepository.findByAutoriteContractanteId(entity.getAutoriteContractante().getId()).stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList())
                : List.of();
        return Stream.concat(entrepriseUsers.stream(), autoriteUsers.stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
