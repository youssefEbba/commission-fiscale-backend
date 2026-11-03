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
public class UtilisationCircuitTresorService {

    private final UtilisationCreditOperations operations;
    public UtilisationCreditDto envoyerAuTresor(Long id, AuthenticatedUser user) {
        return operations.envoyerAuTresor(id, user);
    }

    public UtilisationCreditDto saisirQuittances(Long id, String quittancesJson, List<MultipartFile> files, AuthenticatedUser user) throws IOException {
        return operations.saisirQuittances(id, quittancesJson, files, user);
    }

    public UtilisationCreditDto cloturer(Long id, String motif, AuthenticatedUser user) {
        return operations.cloturer(id, motif, user);
    }

    public UtilisationCreditDto liquiderDouane(Long id, AuthenticatedUser user) {
        return operations.liquiderDouane(id, user);
    }

    public List<QuittanceTresorDto> getQuittances(Long utilisationId, AuthenticatedUser user) {
        return operations.getQuittances(utilisationId, user);
    }

}
