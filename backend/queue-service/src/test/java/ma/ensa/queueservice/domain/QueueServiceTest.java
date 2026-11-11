package ma.ensa.queueservice.domain;

import ma.ensa.queueservice.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueueServiceTest {

    private EstablishmentRepository establishments;
    private ServiceQueueRepository queues;
    private TicketRepository tickets;
    private QueueService service;

    private final CurrentUser owner = new CurrentUser("client-1", "client", Set.of("client"));
    private final CurrentUser agent = new CurrentUser("agent-1", "agent", Set.of(CurrentUser.AGENT));

    @BeforeEach
    void setUp() {
        establishments = mock(EstablishmentRepository.class);
        queues = mock(ServiceQueueRepository.class);
        tickets = mock(TicketRepository.class);
        service = new QueueService(establishments, queues, tickets);
    }

    @Test
    void takingTicketAssignsNextNumberToAuthenticatedOwner() {
        ServiceQueue queue = mock(ServiceQueue.class);
        Establishment establishment = mock(Establishment.class);
        when(queues.findByIdForUpdate(12L)).thenReturn(Optional.of(queue));
        when(queue.nextNumber()).thenReturn(7);
        when(queue.getPrefix()).thenReturn("A");
        when(queue.getId()).thenReturn(12L);
        when(queue.getEstablishment()).thenReturn(establishment);
        when(establishment.getId()).thenReturn("clinic");
        when(establishment.getName()).thenReturn("Clinic");
        when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tickets.countByQueueIdAndStatusAndNumberLessThan(12L, TicketStatus.WAITING, 7)).thenReturn(0L);

        var result = service.takeTicket(owner, 12L);

        assertEquals("A-007", result.code());
        assertEquals(TicketStatus.WAITING, result.status());
        var saved = forClass(Ticket.class);
        verify(tickets).save(saved.capture());
        assertEquals(owner.subject(), saved.getValue().getOwnerSubject());
        assertEquals(queue, saved.getValue().getQueue());
    }

    @Test
    void takingSecondActiveTicketInSameQueueConflicts() {
        when(queues.findByIdForUpdate(12L)).thenReturn(Optional.of(mock(ServiceQueue.class)));
        when(tickets.existsByQueueIdAndOwnerSubjectAndStatusIn(12L, owner.subject(),
                Set.of(TicketStatus.WAITING, TicketStatus.CALLED))).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.takeTicket(owner, 12L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void clientCannotReadAnotherUsersTicket() {
        Ticket ticket = mock(Ticket.class);
        when(tickets.findById(any(UUID.class))).thenReturn(Optional.of(ticket));
        when(ticket.getOwnerSubject()).thenReturn("client-2");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.myTicket(owner, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void clientCannotReadStaffCounter() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.counter(owner, 12L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(queues, tickets);
    }

    @Test
    void callingNextServesCurrentTicketAndCallsOldestWaitingTicket() {
        ServiceQueue queue = mock(ServiceQueue.class);
        Ticket current = mock(Ticket.class);
        Ticket next = mock(Ticket.class);
        when(queues.findByIdForUpdate(12L)).thenReturn(Optional.of(queue));
        when(queue.getId()).thenReturn(12L);
        when(tickets.findByQueueIdAndStatusOrderByNumberAsc(12L, TicketStatus.CALLED))
                .thenReturn(List.of(current), List.of());
        when(tickets.findFirstByQueueIdAndStatusOrderByNumberAsc(12L, TicketStatus.WAITING))
                .thenReturn(Optional.of(next));
        when(tickets.findByQueueIdAndStatusOrderByNumberAsc(12L, TicketStatus.WAITING))
                .thenReturn(List.of());

        service.callNext(agent, 12L);

        verify(current).close(TicketStatus.SERVED);
        verify(next).call();
    }

}
