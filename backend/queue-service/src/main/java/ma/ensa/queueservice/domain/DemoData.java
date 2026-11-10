package ma.ensa.queueservice.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the two establishments the frontend was designed around, once, on an empty database.
 */
@Component
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final EstablishmentRepository establishments;
    private final ServiceQueueRepository queues;

    public DemoData(EstablishmentRepository establishments, ServiceQueueRepository queues) {
        this.establishments = establishments;
        this.queues = queues;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (establishments.count() > 0) {
            return;
        }
        Establishment clinic = establishments.save(new Establishment("clinic", "Clinique Al Amal", "Clinique",
                "Casablanca", "CA", "#41a98a", "#e0f4ec", "Rue Abou Alaa Zahar, Casablanca", "08:30", "16:30"));
        Establishment agency = establishments.save(new Establishment("agency", "Agence Guéliz", "Administration",
                "Marrakech", "AG", "#df8d62", "#fff0e6", "Avenue Mohammed V, Marrakech", "08:30", "16:30"));

        for (Establishment e : new Establishment[]{clinic, agency}) {
            queues.save(new ServiceQueue(e, "Dépôt de dossier", "A", 3, "Guichet 3"));
            queues.save(new ServiceQueue(e, "Retrait de documents", "B", 2, "Guichet 1"));
        }
        log.info("seeded demo establishments and queues");
    }
}
