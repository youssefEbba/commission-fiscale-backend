package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.SecteurActivite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecteurActiviteRepository extends JpaRepository<SecteurActivite, Long> {

    boolean existsByCode(String code);

    List<SecteurActivite> findAllByOrderByLibelleAsc();
}
