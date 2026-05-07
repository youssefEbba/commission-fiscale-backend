package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LigneBulletinLiquidationRepository extends JpaRepository<LigneBulletinLiquidation, Long> {

    List<LigneBulletinLiquidation> findByUtilisationDouaniere_IdOrderByTypeLigneAscIdAsc(Long utilisationDouaniereId);

    void deleteByUtilisationDouaniere_Id(Long utilisationDouaniereId);
}
