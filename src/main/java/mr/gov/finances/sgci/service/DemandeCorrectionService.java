package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.workflow.DemandeCorrectionWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeCorrectionService {

    private final DemandeCorrectionRepository demandeRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final DemandeCorrectionWorkflow workflow;
    private final AuditService auditService;

    private static String generateNumero() {
        return "DC-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findAll() {
        return demandeRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandeCorrectionDto findById(Long id) {
        return demandeRepository.findById(id).map(this::toDto).orElseThrow(
                () -> new RuntimeException("Demande de correction non trouvée: " + id));
    }

    @Transactional
    public DemandeCorrectionDto create(CreateDemandeCorrectionRequest request) {
        AutoriteContractante autorite = autoriteRepository.findById(request.getAutoriteContractanteId())
                .orElseThrow(() -> new RuntimeException("Autorité contractante non trouvée"));
        DemandeCorrection entity = DemandeCorrection.builder()
                .numero(generateNumero())
                .dateDepot(Instant.now())
                .statut(StatutDemande.RECUE)
                .autoriteContractante(autorite)
                .build();
        entity = demandeRepository.save(entity);
        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "DemandeCorrection", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public DemandeCorrectionDto updateStatut(Long id, StatutDemande statut) {
        DemandeCorrection entity = demandeRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Demande de correction non trouvée: " + id));
        workflow.validateTransition(entity.getStatut(), statut);
        entity.setStatut(statut);
        entity = demandeRepository.save(entity);
        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(id), result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByAutoriteContractante(Long autoriteId) {
        return demandeRepository.findByAutoriteContractanteId(autoriteId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByStatut(StatutDemande statut) {
        return demandeRepository.findByStatut(statut).stream().map(this::toDto).collect(Collectors.toList());
    }

    private DemandeCorrectionDto toDto(DemandeCorrection d) {
        return DemandeCorrectionDto.builder()
                .id(d.getId())
                .numero(d.getNumero())
                .dateDepot(d.getDateDepot())
                .statut(d.getStatut())
                .dateCreation(d.getDateCreation())
                .dateModification(d.getDateModification())
                .autoriteContractanteId(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getNom() : null)
                .documents(d.getDocuments() != null ? d.getDocuments().stream().map(this::documentToDto).collect(Collectors.toList()) : List.of())
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
}
