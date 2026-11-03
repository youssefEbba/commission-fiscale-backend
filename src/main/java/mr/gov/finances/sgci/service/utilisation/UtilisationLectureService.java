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
public class UtilisationLectureService {

    private final UtilisationCreditOperations operations;
    public List<UtilisationCreditDto> findAll() {
        return operations.findAll();
    }

    public List<UtilisationCreditDto> findAllVisible(AuthenticatedUser auth, Boolean demandeurSousTraitantOnly, Long sousTraitantEntrepriseId) {
        return operations.findAllVisible(auth, demandeurSousTraitantOnly, sousTraitantEntrepriseId);
    }

    public UtilisationCreditDto findById(Long id, AuthenticatedUser user) {
        return operations.findById(id, user);
    }

    public List<UtilisationCreditDto> findByCertificatCreditId(Long certificatCreditId, AuthenticatedUser user) {
        return operations.findByCertificatCreditId(certificatCreditId, user);
    }

}
