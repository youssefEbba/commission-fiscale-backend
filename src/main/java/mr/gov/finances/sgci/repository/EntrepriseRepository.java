package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.Entreprise;

import java.util.Optional;

@Repository
public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {

    Optional<Entreprise> findByNif(String nif);
    boolean existsByNif(String nif);
}
