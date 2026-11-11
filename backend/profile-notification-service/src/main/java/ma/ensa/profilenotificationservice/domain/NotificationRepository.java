package ma.ensa.profilenotificationservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findBySubjectOrderByCreatedAtDesc(String subject);
}
