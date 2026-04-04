package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentMarche;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMarcheRepository extends JpaRepository<DocumentMarche, Long> {

    List<DocumentMarche> findByMarcheId(Long marcheId);
    Optional<DocumentMarche> findByIdAndMarcheId(Long id, Long marcheId);
}
