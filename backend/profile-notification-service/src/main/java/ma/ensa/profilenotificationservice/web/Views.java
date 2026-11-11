package ma.ensa.profilenotificationservice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.ensa.profilenotificationservice.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public final class Views {

    private Views() {
    }

    public record PreferencesRequest(
            @NotNull Boolean sms,
            @NotNull Boolean email,
            @NotNull Boolean appointmentReminder,
            @NotBlank
            @Size(max = 16)
            @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?", message = "must be a language tag such as fr or fr-FR")
            String language
    ) {
    }

    public record PreferencesView(String subject, boolean sms, boolean email,
                                  boolean appointmentReminder, String language) {
    }

    public record CreateNotificationRequest(
            @NotBlank @Size(max = 255) String subject,
            @NotNull NotificationType type,
            @NotBlank @Size(max = 1000) String message
    ) {
    }

    public record NotificationView(UUID id, String subject, NotificationType type,
                                   String message, boolean read, Instant createdAt) {
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
    }
}
