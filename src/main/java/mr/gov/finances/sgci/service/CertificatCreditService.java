package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificatCreditService {

    private final CertificatCreditRepository repository;
    private final EntrepriseRepository entrepriseRepository;
    private final CertificatCreditWorkflow workflow;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificatCreditDto findById(Long id) {
        return repository.findById(id).map(this::toDto).orElseThrow(
                () -> new RuntimeException("Certificat de crédit non trouvé: " + id));
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByEntreprise(Long entrepriseId) {
        return repository.findByEntrepriseId(entrepriseId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByStatut(StatutCertificat statut) {
        return repository.findByStatut(statut).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public CertificatCreditDto create(CreateCertificatCreditRequest request) {
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
        String numero = "CERT-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal soldeCordon = request.getSoldeCordon() != null ? request.getSoldeCordon() : request.getMontantCordon();
        BigDecimal soldeTVA = request.getSoldeTVA() != null ? request.getSoldeTVA() : (request.getMontantTVAInterieure() != null ? request.getMontantTVAInterieure() : BigDecimal.ZERO);
        CertificatCredit entity = CertificatCredit.builder()
                .numero(numero)
                .dateEmission(Instant.now())
                .dateValidite(request.getDateValidite())
                .montantCordon(request.getMontantCordon())
                .montantTVAInterieure(request.getMontantTVAInterieure() != null ? request.getMontantTVAInterieure() : BigDecimal.ZERO)
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTVA)
                .statut(StatutCertificat.DEMANDE)
                .entreprise(entreprise)
                .lettreCorrection(null)
                .build();
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "CertificatCredit", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public CertificatCreditDto updateStatut(Long id, StatutCertificat statut) {
        CertificatCredit entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Certificat de crédit non trouvé: " + id));
        workflow.validateTransition(entity.getStatut(), statut);
        entity.setStatut(statut);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        return result;
    }

    private CertificatCreditDto toDto(CertificatCredit c) {
        Entreprise e = c.getEntreprise();
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
                .build();
    }
}
