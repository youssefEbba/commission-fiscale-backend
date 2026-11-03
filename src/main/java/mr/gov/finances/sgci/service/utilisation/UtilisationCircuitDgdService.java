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
public class UtilisationCircuitDgdService {

    private final UtilisationCreditOperations operations;
    public UtilisationCreditDto visaDgd(Long id, String decisionsJson, MultipartFile file, AuthenticatedUser user) throws IOException {
        return operations.visaDgd(id, decisionsJson, file, user);
    }

    public UtilisationCreditDto saisirCheque(Long id, SaisirChequeRequest request, MultipartFile file, AuthenticatedUser user) throws IOException {
        return operations.saisirCheque(id, request, file, user);
    }

    public UtilisationCreditDto transmettreAuPresident(Long id, AuthenticatedUser user) {
        return operations.transmettreAuPresident(id, user);
    }

}
