package mr.gov.finances.sgci.repository;

import jakarta.persistence.LockModeType;
import mr.gov.finances.sgci.domain.entity.ReferenceSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, Long> {

    /**
     * Charge la ligne de séquence en la verrouillant (PESSIMISTIC_WRITE) afin de sérialiser les
     * incréments concurrents et éviter les collisions de références.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReferenceSequence s where s.sequenceKey = :key")
    Optional<ReferenceSequence> findByKeyForUpdate(@Param("key") String key);

    Optional<ReferenceSequence> findBySequenceKey(String sequenceKey);
}
