package ma.ensa.appointmentservice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment", indexes = {
        @Index(columnList = "ownerSubject, startsAt, status"),
        @Index(columnList = "establishmentId, startsAt")
})
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "owner_subject", nullable = false, length = 255)
    private String ownerSubject;

    @Column(name = "establishment_id", nullable = false, length = 128)
    private String establishmentId;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Appointment() {
    }

    public Appointment(String ownerSubject, String establishmentId, Long queueId, Instant startsAt, String reason) {
        this.id = UUID.randomUUID();
        this.ownerSubject = ownerSubject;
        this.establishmentId = establishmentId;
        this.queueId = queueId;
        this.startsAt = startsAt;
        this.reason = reason;
        this.status = AppointmentStatus.BOOKED;
        this.createdAt = Instant.now();
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public String getEstablishmentId() {
        return establishmentId;
    }

    public Long getQueueId() {
        return queueId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public String getReason() {
        return reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
