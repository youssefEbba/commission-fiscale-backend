package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.entity.AuditLog;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.AuditLogRepository;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le journal d'audit doit conserver l'instantané de l'objet, motif compris.
 *
 * <p>Régression : le service fabriquait son propre {@code ObjectMapper}, dépourvu du module
 * JavaTime. Tout DTO portant un {@code Instant} — c'est-à-dire presque tous — échouait à la
 * sérialisation et n'était enregistré que sous la forme {@code {"error":"serialization"}}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditSnapshotIT {

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void conserve_l_instantane_d_un_objet_portant_des_dates() {
        CertificatCreditDto dto = CertificatCreditDto.builder()
                .id(4242L)
                .numero("CI-AUDIT-TEST")
                .dateEmission(Instant.parse("2026-01-15T10:00:00Z"))
                .montantCordon(new BigDecimal("1234.56"))
                .build();

        auditService.log(AuditAction.ADMIN_CORRECTION, "CertificatCredit", "4242", dto, "motif de test");

        AuditLog dernier = auditLogRepository.findAll().stream()
                .filter(a -> "4242".equals(a.getEntityId()))
                .max(Comparator.comparing(AuditLog::getId))
                .orElseThrow(() -> new AssertionError("aucune entrée d'audit enregistrée"));

        assertFalse(dernier.getObjectSnapshot().contains("serialization"),
                "l'instantané n'a pas pu être sérialisé : " + dernier.getObjectSnapshot());
        assertTrue(dernier.getObjectSnapshot().contains("CI-AUDIT-TEST"), "le numéro doit figurer dans l'instantané");
        assertTrue(dernier.getObjectSnapshot().contains("2026-01-15"), "la date doit figurer dans l'instantané");
        assertTrue(dernier.getObjectSnapshot().contains("1234.56"), "les montants doivent figurer dans l'instantané");
        assertTrue("motif de test".equals(dernier.getMotif()), "le motif doit être conservé");
        assertTrue(dernier.getTimestamp() != null, "l'horodatage doit être renseigné");
    }
}
