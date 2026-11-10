package ma.ensa.queueservice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * A place people queue at: a clinic, an administration office...
 * The id is a readable slug ("clinic", "agency") because the frontend routes on it.
 */
@Entity
public class Establishment {

    @Id
    private String id;
    private String name;
    private String category;
    private String city;
    private String shortName;
    private String color;
    private String tint;
    private String address;
    private String opensAt;
    private String closesAt;

    protected Establishment() {
    }

    public Establishment(String id, String name, String category, String city, String shortName,
                         String color, String tint, String address, String opensAt, String closesAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.city = city;
        this.shortName = shortName;
        this.color = color;
        this.tint = tint;
        this.address = address;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getCity() { return city; }
    public String getShortName() { return shortName; }
    public String getColor() { return color; }
    public String getTint() { return tint; }
    public String getAddress() { return address; }
    public String getOpensAt() { return opensAt; }
    public String getClosesAt() { return closesAt; }
}
