package ma.ensa.appointmentservice.domain;

import ma.ensa.appointmentservice.security.CurrentUser;
import ma.ensa.appointmentservice.web.Views.AppointmentView;
import ma.ensa.appointmentservice.web.Views.CreateAppointmentRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointments;

    public AppointmentService(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    public AppointmentView create(CurrentUser user, CreateAppointmentRequest request) {
        if (!request.startsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointment must be in the future");
        }
        if (appointments.existsByOwnerSubjectAndStartsAtAndStatus(
                user.subject(), request.startsAt(), AppointmentStatus.BOOKED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "you already have a booked appointment at this time");
        }
        Appointment appointment = new Appointment(user.subject(), request.establishmentId(),
                request.queueId(), request.startsAt(), request.reason());
        try {
            return toView(appointments.saveAndFlush(appointment));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "you already have a booked appointment at this time", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> myAppointments(CurrentUser user) {
        return appointments.findByOwnerSubjectOrderByStartsAtDesc(user.subject()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentView get(CurrentUser user, UUID id) {
        Appointment appointment = find(id);
        if (!appointment.getOwnerSubject().equals(user.subject())) {
            throw notFound(id);
        }
        return toView(appointment);
    }

    public AppointmentView cancel(CurrentUser user, UUID id) {
        Appointment appointment = find(id);
        if (!appointment.getOwnerSubject().equals(user.subject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the appointment owner can cancel it");
        }
        requireBooked(appointment);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return toView(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> byEstablishment(CurrentUser user, String establishmentId) {
        user.requireStaff();
        if (establishmentId == null || establishmentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "establishmentId is required");
        }
        return appointments.findByEstablishmentIdOrderByStartsAtAsc(establishmentId.trim()).stream()
                .map(this::toView)
                .toList();
    }

    public AppointmentView complete(CurrentUser user, UUID id) {
        user.requireStaff();
        return closeBooked(id, AppointmentStatus.COMPLETED);
    }

    public AppointmentView noShow(CurrentUser user, UUID id) {
        user.requireStaff();
        return closeBooked(id, AppointmentStatus.NO_SHOW);
    }

    private AppointmentView closeBooked(UUID id, AppointmentStatus status) {
        Appointment appointment = find(id);
        requireBooked(appointment);
        appointment.setStatus(status);
        return toView(appointment);
    }

    private void requireBooked(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only a booked appointment can be changed");
        }
    }

    private Appointment find(UUID id) {
        return appointments.findById(id).orElseThrow(() -> notFound(id));
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown appointment " + id);
    }

    private AppointmentView toView(Appointment appointment) {
        return new AppointmentView(appointment.getId(), appointment.getOwnerSubject(), appointment.getEstablishmentId(),
                appointment.getQueueId(), appointment.getStartsAt(), appointment.getReason(),
                appointment.getStatus(), appointment.getCreatedAt());
    }
}
