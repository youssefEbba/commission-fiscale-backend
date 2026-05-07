package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.ReferentielTaxe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferentielTaxeRepository extends JpaRepository<ReferentielTaxe, Long> {

    List<ReferentielTaxe> findByActiveTrueOrderByOrdreAffichageAscCodeTaxeAsc();

    List<ReferentielTaxe> findAllByOrderByOrdreAffichageAscCodeTaxeAsc();

    Optional<ReferentielTaxe> findByCodeTaxe(String codeTaxe);

    boolean existsByCodeTaxe(String codeTaxe);

    boolean existsByCodeTaxeAndIdNot(String codeTaxe, Long id);
}
