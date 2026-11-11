package ma.ensa.profilenotificationservice.web;

import jakarta.validation.Valid;
import ma.ensa.profilenotificationservice.domain.ProfileNotificationService;
import ma.ensa.profilenotificationservice.security.CurrentUser;
import ma.ensa.profilenotificationservice.web.Views.CreateNotificationRequest;
import ma.ensa.profilenotificationservice.web.Views.NotificationView;
import ma.ensa.profilenotificationservice.web.Views.PreferencesRequest;
import ma.ensa.profilenotificationservice.web.Views.PreferencesView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ProfileNotificationController {

    private final ProfileNotificationService service;

    public ProfileNotificationController(ProfileNotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/profile/preferences")
    public PreferencesView preferences(CurrentUser user) {
        return service.getPreferences(user);
    }

    @PutMapping("/api/profile/preferences")
    public PreferencesView updatePreferences(CurrentUser user,
                                              @Valid @RequestBody PreferencesRequest request) {
        return service.updatePreferences(user, request);
    }

    @GetMapping("/api/notifications")
    public List<NotificationView> notifications(CurrentUser user) {
        return service.notifications(user);
    }

    @PostMapping("/api/notifications/{id}/read")
    public NotificationView markRead(CurrentUser user, @PathVariable UUID id) {
        return service.markRead(user, id);
    }

    @PostMapping("/api/notifications")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationView createNotification(CurrentUser user,
                                               @Valid @RequestBody CreateNotificationRequest request) {
        return service.createNotification(user, request);
    }
}
