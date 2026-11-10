package ma.ensa.queueservice.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, Long> {

    List<ServiceQueue> findByEstablishmentIdOrderByIdAsc(String establishmentId);

    // ticket numbers come from the queue row: two clients taking a ticket at the same
    // time must not both read the same lastNumber
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ServiceQueue q where q.id = :id")
    Optional<ServiceQueue> findByIdForUpdate(@Param("id") Long id);
}
