package ma.ensa.queueservice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * One line inside an establishment ("Dépôt de dossier", "Retrait de documents").
 * Ticket numbers are handed out from {@link #lastNumber}, so taking a ticket must lock the row.
 */
@Entity
public class ServiceQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Establishment establishment;

    private String name;
    // letter in front of the ticket number: A-042
    private String prefix;
    private int avgServiceMinutes;
    // the counter that serves this queue, shown to the client when called
    private String counter;
    private int lastNumber;

    protected ServiceQueue() {
    }

    public ServiceQueue(Establishment establishment, String name, String prefix, int avgServiceMinutes, String counter) {
        this.establishment = establishment;
        this.name = name;
        this.prefix = prefix;
        this.avgServiceMinutes = avgServiceMinutes;
        this.counter = counter;
    }

    public int nextNumber() {
        return ++lastNumber;
    }

    public Long getId() { return id; }
    public Establishment getEstablishment() { return establishment; }
    public String getName() { return name; }
    public String getPrefix() { return prefix; }
    public int getAvgServiceMinutes() { return avgServiceMinutes; }
    public String getCounter() { return counter; }
}
