package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Groupement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupementRepository extends JpaRepository<Groupement, Long> {

    List<Groupement> findAllByOrderByRaisonSocialeAsc();

    List<Groupement> findByActifTrueOrderByRaisonSocialeAsc();

    @Query("select distinct g from Groupement g left join fetch g.membres left join fetch g.chefDeFile where g.id = :id")
    Optional<Groupement> findByIdWithMembres(@Param("id") Long id);

    boolean existsByChefDeFileId(Long chefDeFileId);

    @Query("select count(g) > 0 from Groupement g join g.membres m where m.id = :entrepriseId")
    boolean existsByMembreId(@Param("entrepriseId") Long entrepriseId);
}
