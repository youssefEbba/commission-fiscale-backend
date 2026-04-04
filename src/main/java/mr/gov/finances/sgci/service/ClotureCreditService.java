package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.ClotureCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ClotureCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.ClotureCreditDto;
import mr.gov.finances.sgci.web.dto.CreateClotureCreditRequest;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClotureCreditService {

    private final CertificatCreditRepository certificatCreditRepository;
    private final ClotureCreditRepository clotureCreditRepository;
    private final CertificatCreditWorkflow workflow;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Long> findEligibleCertificatIds(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut consulter la file de clôture");
        }

        Instant now = Instant.now();
        return certificatCreditRepository.findAllByOrderByDateEmissionDescIdDesc().stream()
                .filter(c -> c.getStatut() == StatutCertificat.OUVERT || c.getStatut() == StatutCertificat.MODIFIE)
                .filter(c -> {
                    BigDecimal soldeCordon = c.getSoldeCordon() != null ? c.getSoldeCordon() : BigDecimal.ZERO;
                    BigDecimal soldeTva = c.getSoldeTVA() != null ? c.getSoldeTVA() : BigDecimal.ZERO;
                    boolean soldeZero = soldeCordon.compareTo(BigDecimal.ZERO) == 0 && soldeTva.compareTo(BigDecimal.ZERO) == 0;
                    boolean expire = c.getDateValidite() != null && c.getDateValidite().isBefore(now);
                    return soldeZero || expire;
                })
                .filter(c -> {
                    ClotureCredit cc = clotureCreditRepository.findByCertificatCreditId(c.getId()).orElse(null);
                    if (cc == null) {
                        return true;
                    }
                    return cc.getDateCloture() == null;
                })
                .map(CertificatCredit::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional
    public ClotureCreditDto proposer(CreateClotureCreditRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw new RuntimeException("Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut proposer une clôture/annulation");
        }

        CertificatCredit c = certificatCreditRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> new RuntimeException("Certificat non trouvé"));

        if (c.getStatut() != StatutCertificat.OUVERT && c.getStatut() != StatutCertificat.MODIFIE) {
            throw new RuntimeException("Le certificat doit être OUVERT ou MODIFIE");
        }

        ClotureCredit existing = clotureCreditRepository.findByCertificatCreditId(c.getId()).orElse(null);
        if (existing != null && existing.getDateCloture() != null) {
            throw new RuntimeException("Ce certificat est déjà clôturé/annulé");
        }

        BigDecimal soldeCordon = c.getSoldeCordon() != null ? c.getSoldeCordon() : BigDecimal.ZERO;
        BigDecimal soldeTva = c.getSoldeTVA() != null ? c.getSoldeTVA() : BigDecimal.ZERO;
        BigDecimal soldeRestant = soldeCordon.add(soldeTva);

        ClotureCredit cc = existing != null ? existing : new ClotureCredit();
        cc.setCertificatCredit(c);
        cc.setDateProposition(Instant.now());
        cc.setMotif(request.getMotif());
        cc.setTypeOperation(request.getTypeOperation());
        cc.setSoldeRestant(soldeRestant);
        cc.setApprouvee(null);
        cc.setDateCloture(null);

        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.CREATE, "ClotureCredit", String.valueOf(cc.getId()), dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ClotureCreditDto> findPendingPropositions() {
        return clotureCreditRepository.findByApprouveeIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ClotureCreditDto validerParPresident(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.PRESIDENT) {
            throw new RuntimeException("Seul le Président peut valider");
        }

        ClotureCredit cc = clotureCreditRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposition non trouvée"));

        if (cc.getApprouvee() != null) {
            return toDto(cc);
        }

        cc.setApprouvee(true);
        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.UPDATE, "ClotureCredit", String.valueOf(cc.getId()), Map.of("approuvee", true));
        return dto;
    }

    @Transactional
    public ClotureCreditDto rejeterParPresident(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.PRESIDENT) {
            throw new RuntimeException("Seul le Président peut rejeter");
        }

        ClotureCredit cc = clotureCreditRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposition non trouvée"));

        if (cc.getApprouvee() != null) {
            return toDto(cc);
        }

        cc.setApprouvee(false);
        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.UPDATE, "ClotureCredit", String.valueOf(cc.getId()), Map.of("approuvee", false));
        return dto;
    }

    @Transactional
    public ClotureCreditDto finaliserParDgtcp(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut finaliser");
        }

        ClotureCredit cc = clotureCreditRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposition non trouvée"));

        if (!Boolean.TRUE.equals(cc.getApprouvee())) {
            throw new RuntimeException("La proposition n'est pas approuvée");
        }
        if (cc.getDateCloture() != null) {
            return toDto(cc);
        }

        CertificatCredit c = cc.getCertificatCredit();
        if (c == null || c.getId() == null) {
            throw new RuntimeException("Certificat manquant");
        }

        StatutCertificat to = cc.getTypeOperation() != null && cc.getTypeOperation().name().equals("ANNULATION")
                ? StatutCertificat.ANNULE
                : StatutCertificat.CLOTURE;

        workflow.validateTransition(c.getStatut(), to);
        c.setStatut(to);
        certificatCreditRepository.save(c);

        cc.setDateCloture(Instant.now());
        cc = clotureCreditRepository.save(cc);

        ClotureCreditDto dto = toDto(cc);
        auditService.log(AuditAction.UPDATE, "ClotureCredit", String.valueOf(cc.getId()), Map.of("finalisee", true, "statutCertificat", to.name()));
        return dto;
    }

    private ClotureCreditDto toDto(ClotureCredit cc) {
        CertificatCredit c = cc.getCertificatCredit();
        return ClotureCreditDto.builder()
                .id(cc.getId())
                .dateProposition(cc.getDateProposition())
                .dateCloture(cc.getDateCloture())
                .motif(cc.getMotif())
                .typeOperation(cc.getTypeOperation())
                .soldeRestant(cc.getSoldeRestant())
                .approuvee(cc.getApprouvee())
                .certificatCreditId(c != null ? c.getId() : null)
                .certificatNumero(c != null ? c.getNumero() : null)
                .build();
    }
}
