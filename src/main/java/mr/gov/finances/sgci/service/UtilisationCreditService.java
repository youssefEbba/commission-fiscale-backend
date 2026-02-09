package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.workflow.UtilisationCreditWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisationCreditService {

    private final UtilisationCreditRepository repository;
    private final CertificatCreditRepository certificatRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final UtilisationCreditWorkflow workflow;
    private final AuditService auditService;

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
        CertificatCredit certificat = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> new RuntimeException("Certificat de crédit non trouvé"));
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));
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
            entity = d;
        } else {
            UtilisationTVAInterieure t = new UtilisationTVAInterieure();
            mapBase(t, request, certificat, entreprise);
            t.setTypeAchat(request.getTypeAchat());
            t.setNumeroFacture(request.getNumeroFacture());
            t.setDateFacture(request.getDateFacture());
            t.setMontantTVA(request.getMontantTVAInterieure());
            t.setNumeroDecompte(request.getNumeroDecompte());
            entity = t;
        }
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "UtilisationCredit", String.valueOf(entity.getId()), result);
        return result;
    }

    private void mapBase(UtilisationCredit u, CreateUtilisationCreditRequest r, CertificatCredit c, Entreprise e) {
        u.setDateDemande(Instant.now());
        u.setMontant(r.getMontant());
        u.setStatut(StatutUtilisation.DEMANDEE);
        u.setCertificatCredit(c);
        u.setEntreprise(e);
    }

    @Transactional
    public UtilisationCreditDto updateStatut(Long id, StatutUtilisation statut) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(
                () -> new RuntimeException("Utilisation de crédit non trouvée: " + id));
        workflow.validateTransition(entity.getStatut(), statut);
        entity.setStatut(statut);
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        return result;
    }

    private UtilisationCreditDto toDto(UtilisationCredit u) {
        return UtilisationCreditDto.builder()
                .id(u.getId())
                .type(u.getType())
                .dateDemande(u.getDateDemande())
                .montant(u.getMontant())
                .statut(u.getStatut())
                .dateLiquidation(u.getDateLiquidation())
                .certificatCreditId(u.getCertificatCredit() != null ? u.getCertificatCredit().getId() : null)
                .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                .build();
    }
}
