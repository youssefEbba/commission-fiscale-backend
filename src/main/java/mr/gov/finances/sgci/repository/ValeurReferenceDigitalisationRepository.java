package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.ValeurReferenceDigitalisation;
import mr.gov.finances.sgci.domain.enums.IndicateurReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValeurReferenceDigitalisationRepository extends JpaRepository<ValeurReferenceDigitalisation, Long> {

    Optional<ValeurReferenceDigitalisation> findByCode(IndicateurReference code);
}
