package mr.gov.finances.sgci.service.utilisation;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UtilisationCertificatService {

    private final UtilisationCreditOperations operations;
    public UtilisationCreditDto emettreCertificatUtilisation(Long id, AuthenticatedUser user) {
        return operations.emettreCertificatUtilisation(id, user);
    }

    public UtilisationCreditDto reserverNumeroCertificatUtilisation(Long id, AuthenticatedUser user) {
        return operations.reserverNumeroCertificatUtilisation(id, user);
    }

    public void emettreApresDepotCertificatSigne(Long id, AuthenticatedUser user) {
        operations.emettreApresDepotCertificatSigne(id, user);
    }

    public UtilisationCreditDto adminEmettreCertificatUtilisation(Long id, String motif, MultipartFile file, AuthenticatedUser user) throws IOException {
        return operations.adminEmettreCertificatUtilisation(id, motif, file, user);
    }

    public CertificatUtilisationEmissionDto etatEmissionCertificat(Long id, AuthenticatedUser user) {
        return operations.etatEmissionCertificat(id, user);
    }

    public UtilisationCreditDto updateStatut(Long id, StatutUtilisation statut, AuthenticatedUser user) {
        return operations.updateStatut(id, statut, user);
    }

}
