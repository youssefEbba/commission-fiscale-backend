package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.ReponseEnquete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReponseEnqueteRepository extends JpaRepository<ReponseEnquete, Long> {

    boolean existsByEnqueteIdAndUtilisateurId(Long enqueteId, Long utilisateurId);

    long countByEnqueteId(Long enqueteId);

    List<ReponseEnquete> findByEnqueteId(Long enqueteId);
}
