package mr.gov.finances.sgci.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;

import java.util.Optional;

@Repository
public interface AutoriteContractanteRepository extends JpaRepository<AutoriteContractante, Long> {

    Optional<AutoriteContractante> findByCode(String code);
    boolean existsByCode(String code);
}
