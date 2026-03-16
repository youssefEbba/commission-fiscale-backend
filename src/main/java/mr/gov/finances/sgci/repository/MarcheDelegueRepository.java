package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.MarcheDelegue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarcheDelegueRepository extends JpaRepository<MarcheDelegue, Long> {

    boolean existsByMarcheIdAndDelegueId(Long marcheId, Long delegueId);

    Optional<MarcheDelegue> findByMarcheIdAndDelegueId(Long marcheId, Long delegueId);

    List<MarcheDelegue> findAllByMarcheId(Long marcheId);
}
