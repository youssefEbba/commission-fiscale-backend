package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.QuittanceTresor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuittanceTresorRepository extends JpaRepository<QuittanceTresor, Long> {

    List<QuittanceTresor> findByUtilisationDouaniere_IdOrderByDateQuittanceAscIdAsc(Long utilisationDouaniereId);

    void deleteByUtilisationDouaniere_Id(Long utilisationDouaniereId);
}
