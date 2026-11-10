package ma.ensa.queueservice.domain;

import ma.ensa.queueservice.security.CurrentUser;
import ma.ensa.queueservice.web.Views.CounterView;
import ma.ensa.queueservice.web.Views.EstablishmentView;
import ma.ensa.queueservice.web.Views.QueueView;
import ma.ensa.queueservice.web.Views.TicketView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class QueueService {

    private static final Set<TicketStatus> ACTIVE = Set.of(TicketStatus.WAITING, TicketStatus.CALLED);

    private final EstablishmentRepository establishments;
    private final ServiceQueueRepository queues;
    private final TicketRepository tickets;

    public QueueService(EstablishmentRepository establishments, ServiceQueueRepository queues, TicketRepository tickets) {
        this.establishments = establishments;
        this.queues = queues;
        this.tickets = tickets;
    }

    // ---------------------------------------------------------------- establishments

    @Transactional(readOnly = true)
    public List<EstablishmentView> establishments(String query, String category) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return establishments.findAll().stream()
                .filter(e -> category == null || category.isBlank() || category.equalsIgnoreCase(e.getCategory()))
                .filter(e -> q.isEmpty() || (e.getName() + " " + e.getCity()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(Establishment::getName))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstablishmentView establishment(String id) {
        return establishments.findById(id)
                .map(this::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown establishment " + id));
    }

    // ---------------------------------------------------------------- client side

    public TicketView takeTicket(CurrentUser user, Long queueId) {
        ServiceQueue queue = queues.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + queueId));
        if (tickets.existsByQueueIdAndOwnerSubjectAndStatusIn(queueId, user.subject(), ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "you already hold a ticket in this queue");
        }
        Ticket ticket = tickets.save(new Ticket(queue, queue.nextNumber(), user.subject(), user.username()));
        return toView(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketView> myActiveTickets(CurrentUser user) {
        return tickets.findByOwnerSubjectAndStatusInOrderByCreatedAtDesc(user.subject(), ACTIVE).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketView myTicket(CurrentUser user, UUID id) {
        return toView(owned(user, id));
    }

    public TicketView cancel(CurrentUser user, UUID id) {
        Ticket ticket = owned(user, id);
        if (ticket.getStatus() != TicketStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only a waiting ticket can be cancelled");
        }
        ticket.close(TicketStatus.CANCELLED);
        return toView(ticket);
    }

    // ---------------------------------------------------------------- agent side

    @Transactional(readOnly = true)
    public CounterView counter(CurrentUser user, Long queueId) {
        user.requireStaff();
        return counterView(queue(queueId));
    }

    /**
     * Closes whoever is at the counter as served, then calls the next waiting ticket.
     */
    public CounterView callNext(CurrentUser user, Long queueId) {
        user.requireStaff();
        ServiceQueue queue = queues.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + queueId));
        tickets.findByQueueIdAndStatusOrderByNumberAsc(queueId, TicketStatus.CALLED)
                .forEach(current -> current.close(TicketStatus.SERVED));
        tickets.findFirstByQueueIdAndStatusOrderByNumberAsc(queueId, TicketStatus.WAITING)
                .ifPresent(Ticket::call);
        return counterView(queue);
    }

    public CounterView close(CurrentUser user, UUID ticketId, TicketStatus outcome) {
        user.requireStaff();
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown ticket"));
        if (!ticket.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ticket is already closed");
        }
        ticket.close(outcome);
        return counterView(ticket.getQueue());
    }

    // ---------------------------------------------------------------- mapping

    private Ticket owned(CurrentUser user, UUID id) {
        return tickets.findById(id)
                .filter(t -> t.getOwnerSubject().equals(user.subject()))
                // same answer for "not yours" and "does not exist": ids are not to be probed
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown ticket"));
    }

    private ServiceQueue queue(Long id) {
        return queues.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + id));
    }

    private CounterView counterView(ServiceQueue queue) {
        TicketView current = tickets.findByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.CALLED).stream()
                .reduce((first, second) -> second)
                .map(this::toView)
                .orElse(null);
        List<TicketView> waiting = tickets.findByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.WAITING).stream()
                .map(this::toView)
                .toList();
        return new CounterView(toView(queue), current, waiting);
    }

    private EstablishmentView toView(Establishment e) {
        List<QueueView> queueViews = queues.findByEstablishmentIdOrderByIdAsc(e.getId()).stream()
                .map(this::toView)
                .toList();
        long waiting = queueViews.stream().mapToLong(QueueView::waiting).sum();
        long estimate = queueViews.stream().mapToLong(QueueView::estimatedWaitMinutes).max().orElse(0);
        return new EstablishmentView(e.getId(), e.getName(), e.getCategory(), e.getCity(), e.getShortName(),
                e.getColor(), e.getTint(), e.getAddress(), e.getOpensAt(), e.getClosesAt(),
                waiting, estimate, queueViews);
    }

    private QueueView toView(ServiceQueue q) {
        long waiting = tickets.countByQueueIdAndStatus(q.getId(), TicketStatus.WAITING);
        return new QueueView(q.getId(), q.getName(), q.getPrefix(), q.getCounter(),
                waiting, waiting * q.getAvgServiceMinutes());
    }

    private TicketView toView(Ticket t) {
        ServiceQueue q = t.getQueue();
        Establishment e = q.getEstablishment();
        long position = 0;
        if (t.getStatus() == TicketStatus.WAITING) {
            position = tickets.countByQueueIdAndStatusAndNumberLessThan(q.getId(), TicketStatus.WAITING, t.getNumber()) + 1;
        }
        // the person at position 1 still waits for whoever is at the counter
        long estimate = position * q.getAvgServiceMinutes();
        return new TicketView(t.getId(), t.getCode(), t.getStatus(), e.getId(), e.getName(), e.getShortName(),
                e.getColor(), e.getTint(), q.getId(), q.getName(), q.getCounter(), t.getOwnerName(),
                position, estimate, t.getCreatedAt(), t.getCalledAt());
    }
}
