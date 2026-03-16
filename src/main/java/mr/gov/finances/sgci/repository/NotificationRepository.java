package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId);

    long countByUtilisateurIdAndReadFalse(Long utilisateurId);

    Optional<Notification> findByIdAndUtilisateurId(Long id, Long utilisateurId);
}
