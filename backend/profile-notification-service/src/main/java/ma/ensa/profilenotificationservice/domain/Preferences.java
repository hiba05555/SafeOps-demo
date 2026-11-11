package ma.ensa.profilenotificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profile_preferences")
public class Preferences {

    @Id
    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false)
    private boolean sms;

    @Column(nullable = false)
    private boolean email;

    @Column(name = "appointment_reminder", nullable = false)
    private boolean appointmentReminder;

    @Column(nullable = false, length = 16)
    private String language;

    protected Preferences() {
    }

    public Preferences(String subject) {
        this.subject = subject;
        this.sms = true;
        this.email = false;
        this.appointmentReminder = true;
        this.language = "fr";
    }

    public void update(boolean sms, boolean email, boolean appointmentReminder, String language) {
        this.sms = sms;
        this.email = email;
        this.appointmentReminder = appointmentReminder;
        this.language = language;
    }

    public String getSubject() {
        return subject;
    }

    public boolean isSms() {
        return sms;
    }

    public boolean isEmail() {
        return email;
    }

    public boolean isAppointmentReminder() {
        return appointmentReminder;
    }

    public String getLanguage() {
        return language;
    }
}
