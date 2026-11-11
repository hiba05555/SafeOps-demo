package ma.ensa.appointmentservice.domain;

import ma.ensa.appointmentservice.security.CurrentUser;
import ma.ensa.appointmentservice.web.Views.CreateAppointmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {

    private AppointmentRepository appointments;
    private AppointmentService service;
    private final CurrentUser owner = new CurrentUser("client-1", "client", Set.of());

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        service = new AppointmentService(appointments);
    }

    @Test
    void booksFutureAppointmentForAuthenticatedOwner() {
        CreateAppointmentRequest request = request(Instant.parse("2099-01-01T10:00:00Z"));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(owner, request);

        assertEquals(owner.subject(), result.ownerSubject());
        assertEquals(AppointmentStatus.BOOKED, result.status());
        assertEquals(request.startsAt(), result.startsAt());
    }

    @Test
    void rejectsAppointmentInThePast() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(owner, request(Instant.parse("2000-01-01T00:00:00Z"))));

        assertEquals(400, exception.getStatusCode().value());
        verify(appointments, never()).save(any(Appointment.class));
    }

    @Test
    void rejectsSecondBookedAppointmentForSameOwnerAndStart() {
        Instant startsAt = Instant.parse("2099-01-01T10:00:00Z");
        when(appointments.existsByOwnerSubjectAndStartsAtAndStatus(
                owner.subject(), startsAt, AppointmentStatus.BOOKED)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(owner, request(startsAt)));

        assertEquals(409, exception.getStatusCode().value());
        verify(appointments, never()).save(any(Appointment.class));
    }

    @Test
    void mapsConcurrentDuplicateBookingConstraintToConflict() {
        Instant startsAt = Instant.parse("2099-01-01T10:00:00Z");
        when(appointments.saveAndFlush(any(Appointment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(owner, request(startsAt)));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void onlyOwnerCanCancelTheirBookedAppointment() {
        Appointment appointment = new Appointment("another-client", "clinic", 1L,
                Instant.parse("2099-01-01T10:00:00Z"), "Consultation");
        when(appointments.findById(any(UUID.class))).thenReturn(Optional.of(appointment));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.cancel(owner, UUID.randomUUID()));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
    }

    @Test
    void ownerCanCancelOnlyBookedAppointments() {
        Appointment appointment = new Appointment(owner.subject(), "clinic", 1L,
                Instant.parse("2099-01-01T10:00:00Z"), "Consultation");
        when(appointments.findById(any(UUID.class))).thenReturn(Optional.of(appointment));

        var result = service.cancel(owner, UUID.randomUUID());

        assertEquals(AppointmentStatus.CANCELLED, result.status());
    }

    @Test
    void ownerCannotCancelAppointmentThatIsNoLongerBooked() {
        Appointment appointment = new Appointment(owner.subject(), "clinic", 1L,
                Instant.parse("2099-01-01T10:00:00Z"), "Consultation");
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointments.findById(any(UUID.class))).thenReturn(Optional.of(appointment));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.cancel(owner, UUID.randomUUID()));

        assertEquals(409, exception.getStatusCode().value());
    }

    private CreateAppointmentRequest request(Instant startsAt) {
        return new CreateAppointmentRequest("clinic", 1L, startsAt, "Consultation");
    }
}
