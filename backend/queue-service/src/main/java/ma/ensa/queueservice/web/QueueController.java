package ma.ensa.queueservice.web;

import jakarta.validation.Valid;
import ma.ensa.queueservice.domain.QueueService;
import ma.ensa.queueservice.domain.TicketStatus;
import ma.ensa.queueservice.security.CurrentUser;
import ma.ensa.queueservice.web.Views.CounterView;
import ma.ensa.queueservice.web.Views.EstablishmentView;
import ma.ensa.queueservice.web.Views.TakeTicketRequest;
import ma.ensa.queueservice.web.Views.TicketView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Every route is reached through the gateway at /api/**, which has already verified the token.
 * Taking a {@link CurrentUser} parameter is what makes a route require an authenticated caller.
 */
@RestController
@RequestMapping("/api")
public class QueueController {

    private final QueueService service;

    public QueueController(QueueService service) {
        this.service = service;
    }

    // ---- client screens: /search, /establishment/:id, /success, /tickets

    @GetMapping("/establishments")
    public List<EstablishmentView> establishments(CurrentUser user,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String category) {
        return service.establishments(q, category);
    }

    @GetMapping("/establishments/{id}")
    public EstablishmentView establishment(CurrentUser user, @PathVariable String id) {
        return service.establishment(id);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView takeTicket(CurrentUser user, @Valid @RequestBody TakeTicketRequest request) {
        return service.takeTicket(user, request.queueId());
    }

    @GetMapping("/tickets/me")
    public List<TicketView> myTickets(CurrentUser user) {
        return service.myActiveTickets(user);
    }

    @GetMapping("/tickets/{id}")
    public TicketView ticket(CurrentUser user, @PathVariable UUID id) {
        return service.myTicket(user, id);
    }

    @PostMapping("/tickets/{id}/cancel")
    public TicketView cancel(CurrentUser user, @PathVariable UUID id) {
        return service.cancel(user, id);
    }

    // ---- counter screen (/tenant, "Guichet"): agent or admin role

    @GetMapping("/queues/{id}/counter")
    public CounterView counter(CurrentUser user, @PathVariable Long id) {
        return service.counter(user, id);
    }

    @PostMapping("/queues/{id}/call-next")
    public CounterView callNext(CurrentUser user, @PathVariable Long id) {
        return service.callNext(user, id);
    }

    @PostMapping("/tickets/{id}/served")
    public CounterView served(CurrentUser user, @PathVariable UUID id) {
        return service.close(user, id, TicketStatus.SERVED);
    }

    @PostMapping("/tickets/{id}/no-show")
    public CounterView noShow(CurrentUser user, @PathVariable UUID id) {
        return service.close(user, id, TicketStatus.NO_SHOW);
    }
}
