package ma.ensa.appointmentservice.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final AppointmentRepository appointments;

    public DemoData(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appointments.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        appointments.save(new Appointment("demo-client-1", "clinic", 1L,
                now.plus(1, ChronoUnit.DAYS), "Consultation"));
        appointments.save(new Appointment("demo-client-2", "clinic", 2L,
                now.plus(2, ChronoUnit.DAYS), "Follow-up"));
        appointments.save(new Appointment("demo-client-1", "agency", 3L,
                now.plus(3, ChronoUnit.DAYS), "Document appointment"));
        log.info("seeded demo appointments");
    }
}
