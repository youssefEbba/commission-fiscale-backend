package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.EnqueteSatisfaction;
import mr.gov.finances.sgci.domain.enums.StatutEnquete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnqueteSatisfactionRepository extends JpaRepository<EnqueteSatisfaction, Long> {

    List<EnqueteSatisfaction> findAllByOrderByDateDebutDescIdDesc();

    List<EnqueteSatisfaction> findByStatut(StatutEnquete statut);
}
