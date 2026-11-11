package ma.ensa.profilenotificationservice.domain;

import ma.ensa.profilenotificationservice.security.CurrentUser;
import ma.ensa.profilenotificationservice.web.Views.CreateNotificationRequest;
import ma.ensa.profilenotificationservice.web.Views.PreferencesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileNotificationServiceTest {

    private PreferencesRepository preferences;
    private NotificationRepository notifications;
    private ProfileNotificationService service;
    private final CurrentUser client = new CurrentUser("client-1", "client", Set.of());
    private final CurrentUser agent = new CurrentUser("agent-1", "agent", Set.of(CurrentUser.AGENT));

    @BeforeEach
    void setUp() {
        preferences = mock(PreferencesRepository.class);
        notifications = mock(NotificationRepository.class);
        service = new ProfileNotificationService(preferences, notifications);
    }

    @Test
    void getPreferencesCreatesUiDefaultsForCurrentSubject() {
        when(preferences.findById(client.subject())).thenReturn(Optional.empty());
        when(preferences.save(any(Preferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.getPreferences(client);

        assertEquals(client.subject(), result.subject());
        assertTrue(result.sms());
        assertFalse(result.email());
        assertTrue(result.appointmentReminder());
        assertEquals("fr", result.language());
    }

    @Test
    void updatesOnlyTheCurrentUsersPreferences() {
        PreferencesRequest request = new PreferencesRequest(false, true, false, "en");
        when(preferences.findById(client.subject())).thenReturn(Optional.empty());
        when(preferences.save(any(Preferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updatePreferences(client, request);

        assertFalse(result.sms());
        assertTrue(result.email());
        assertFalse(result.appointmentReminder());
        assertEquals("en", result.language());
        assertEquals(client.subject(), result.subject());
    }

    @Test
    void listsNotificationsOnlyForCurrentSubject() {
        when(notifications.findBySubjectOrderByCreatedAtDesc(client.subject())).thenReturn(List.of());

        assertTrue(service.notifications(client).isEmpty());

        verify(notifications).findBySubjectOrderByCreatedAtDesc(client.subject());
    }

    @Test
    void onlyStaffCanCreateNotifications() {
        CreateNotificationRequest request = new CreateNotificationRequest(
                "client-1", NotificationType.TURN_APPROACHING, "Your turn is near");
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createNotification(agent, request);

        assertEquals("client-1", result.subject());
        assertEquals(NotificationType.TURN_APPROACHING, result.type());
        assertFalse(result.read());
        assertThrows(ResponseStatusException.class, () -> service.createNotification(client, request));
        verify(notifications).save(any(Notification.class));
    }

    @Test
    void ownerCanMarkTheirNotificationRead() {
        Notification notification = new Notification(client.subject(), NotificationType.APPOINTMENT_REMINDER,
                "Appointment tomorrow");
        when(notifications.findById(any(UUID.class))).thenReturn(Optional.of(notification));

        var result = service.markRead(client, UUID.randomUUID());

        assertTrue(result.read());
    }

    @Test
    void cannotMarkAnotherUsersNotificationRead() {
        Notification notification = new Notification("other-client", NotificationType.TURN_APPROACHING, "Private");
        when(notifications.findById(any(UUID.class))).thenReturn(Optional.of(notification));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markRead(client, UUID.randomUUID()));

        assertEquals(404, exception.getStatusCode().value());
        assertFalse(notification.isRead());
    }
}
