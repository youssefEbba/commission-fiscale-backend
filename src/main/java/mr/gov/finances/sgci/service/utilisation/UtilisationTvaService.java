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
public class UtilisationTvaService {

    private final UtilisationCreditOperations operations;
    public List<TvaDeductibleStockDto> findTvaStockByCertificat(Long certificatCreditId) {
        return operations.findTvaStockByCertificat(certificatCreditId);
    }

    public UtilisationCreditDto deposerQuittanceDgi(Long id, String numeroQuittance, Instant dateQuittance, BigDecimal montant, MultipartFile file, AuthenticatedUser user) throws IOException {
        return operations.deposerQuittanceDgi(id, numeroQuittance, dateQuittance, montant, file, user);
    }

    public UtilisationCreditDto apurerTVAInterieure(Long id, ApurerTVAInterieureRequest request, AuthenticatedUser user) {
        return operations.apurerTVAInterieure(id, request, user);
    }

}
