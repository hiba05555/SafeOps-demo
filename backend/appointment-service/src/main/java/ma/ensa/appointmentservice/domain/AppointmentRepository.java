package ma.ensa.appointmentservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    boolean existsByOwnerSubjectAndStartsAtAndStatus(String ownerSubject, Instant startsAt, AppointmentStatus status);

    List<Appointment> findByOwnerSubjectOrderByStartsAtDesc(String ownerSubject);

    List<Appointment> findByEstablishmentIdOrderByStartsAtAsc(String establishmentId);
}
