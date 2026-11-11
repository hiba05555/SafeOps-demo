package ma.ensa.appointmentservice.web;

import ma.ensa.appointmentservice.domain.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public final class Views {

    private Views() {
    }

    public record CreateAppointmentRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 128)
            String establishmentId,
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Positive
            Long queueId,
            @jakarta.validation.constraints.NotNull
            Instant startsAt,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 500)
            String reason
    ) {
    }

    public record AppointmentView(
            UUID id,
            String ownerSubject,
            String establishmentId,
            Long queueId,
            Instant startsAt,
            String reason,
            AppointmentStatus status,
            Instant createdAt
    ) {
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
    }
}
