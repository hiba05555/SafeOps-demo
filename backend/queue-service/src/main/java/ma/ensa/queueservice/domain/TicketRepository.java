package ma.ensa.queueservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOwnerSubjectAndStatusInOrderByCreatedAtDesc(String ownerSubject, Collection<TicketStatus> statuses);

    boolean existsByQueueIdAndOwnerSubjectAndStatusIn(Long queueId, String ownerSubject, Collection<TicketStatus> statuses);

    List<Ticket> findByQueueIdAndStatusOrderByNumberAsc(Long queueId, TicketStatus status);

    Optional<Ticket> findFirstByQueueIdAndStatusOrderByNumberAsc(Long queueId, TicketStatus status);

    long countByQueueIdAndStatus(Long queueId, TicketStatus status);

    // people still ahead of a waiting ticket
    long countByQueueIdAndStatusAndNumberLessThan(Long queueId, TicketStatus status, int number);
}
