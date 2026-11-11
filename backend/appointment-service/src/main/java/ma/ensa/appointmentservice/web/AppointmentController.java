package ma.ensa.appointmentservice.web;

import jakarta.validation.Valid;
import ma.ensa.appointmentservice.domain.AppointmentService;
import ma.ensa.appointmentservice.security.CurrentUser;
import ma.ensa.appointmentservice.web.Views.AppointmentView;
import ma.ensa.appointmentservice.web.Views.CreateAppointmentRequest;
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

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView create(CurrentUser user, @Valid @RequestBody CreateAppointmentRequest request) {
        return service.create(user, request);
    }

    @GetMapping("/me")
    public List<AppointmentView> myAppointments(CurrentUser user) {
        return service.myAppointments(user);
    }

    @GetMapping("/{id}")
    public AppointmentView get(CurrentUser user, @PathVariable UUID id) {
        return service.get(user, id);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentView cancel(CurrentUser user, @PathVariable UUID id) {
        return service.cancel(user, id);
    }

    @GetMapping
    public List<AppointmentView> byEstablishment(CurrentUser user, @RequestParam String establishmentId) {
        return service.byEstablishment(user, establishmentId);
    }

    @PostMapping("/{id}/complete")
    public AppointmentView complete(CurrentUser user, @PathVariable UUID id) {
        return service.complete(user, id);
    }

    @PostMapping("/{id}/no-show")
    public AppointmentView noShow(CurrentUser user, @PathVariable UUID id) {
        return service.noShow(user, id);
    }
}
