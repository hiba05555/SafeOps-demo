package ma.ensa.queueservice.domain;

public enum TicketStatus {
    WAITING,
    // an agent called it to a counter
    CALLED,
    SERVED,
    NO_SHOW,
    CANCELLED
}
