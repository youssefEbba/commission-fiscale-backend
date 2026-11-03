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
public class UtilisationBrouillonService {

    private final UtilisationCreditOperations operations;
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request) {
        return operations.create(request);
    }

    public UtilisationCreditDto create(CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        return operations.create(request, user);
    }

    public void deleteBrouillon(Long id, AuthenticatedUser user) {
        operations.deleteBrouillon(id, user);
    }

    public UtilisationCreditDto soumettreBrouillon(Long id, AuthenticatedUser user) {
        return operations.soumettreBrouillon(id, user);
    }

    public UtilisationCreditDto update(Long id, CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        return operations.update(id, request, user);
    }

    public UtilisationCreditDto adminCorrectInfo(Long id, AdminCorrectionUtilisationRequest request, String motif, AuthenticatedUser user) {
        return operations.adminCorrectInfo(id, request, motif, user);
    }

}
