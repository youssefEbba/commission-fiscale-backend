package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Bailleur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BailleurRepository extends JpaRepository<Bailleur, Long> {
    Optional<Bailleur> findByNom(String nom);
    boolean existsByNom(String nom);
}
