package ma.ensa.profilenotificationservice.domain;

import ma.ensa.profilenotificationservice.security.CurrentUser;
import ma.ensa.profilenotificationservice.web.Views.CreateNotificationRequest;
import ma.ensa.profilenotificationservice.web.Views.NotificationView;
import ma.ensa.profilenotificationservice.web.Views.PreferencesRequest;
import ma.ensa.profilenotificationservice.web.Views.PreferencesView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProfileNotificationService {

    private final PreferencesRepository preferences;
    private final NotificationRepository notifications;

    public ProfileNotificationService(PreferencesRepository preferences, NotificationRepository notifications) {
        this.preferences = preferences;
        this.notifications = notifications;
    }

    public PreferencesView getPreferences(CurrentUser user) {
        Preferences current = preferences.findById(user.subject())
                .orElseGet(() -> preferences.save(new Preferences(user.subject())));
        return toView(current);
    }

    public PreferencesView updatePreferences(CurrentUser user, PreferencesRequest request) {
        Preferences current = preferences.findById(user.subject())
                .orElseGet(() -> new Preferences(user.subject()));
        current.update(request.sms(), request.email(), request.appointmentReminder(), request.language());
        return toView(preferences.save(current));
    }

    @Transactional(readOnly = true)
    public List<NotificationView> notifications(CurrentUser user) {
        return notifications.findBySubjectOrderByCreatedAtDesc(user.subject()).stream()
                .map(this::toView)
                .toList();
    }

    public NotificationView createNotification(CurrentUser actor, CreateNotificationRequest request) {
        actor.requireStaff();
        Notification notification = new Notification(request.subject().trim(), request.type(), request.message().trim());
        return toView(notifications.save(notification));
    }

    public NotificationView markRead(CurrentUser user, UUID id) {
        Notification notification = notifications.findById(id)
                .filter(candidate -> candidate.getSubject().equals(user.subject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown notification " + id));
        notification.markRead();
        return toView(notification);
    }

    private PreferencesView toView(Preferences preferences) {
        return new PreferencesView(preferences.getSubject(), preferences.isSms(), preferences.isEmail(),
                preferences.isAppointmentReminder(), preferences.getLanguage());
    }

    private NotificationView toView(Notification notification) {
        return new NotificationView(notification.getId(), notification.getSubject(), notification.getType(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
