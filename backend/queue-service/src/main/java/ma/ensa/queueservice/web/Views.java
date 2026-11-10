package ma.ensa.queueservice.web;

import ma.ensa.queueservice.domain.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON shapes sent to the frontend. Field names follow what src/App.tsx already uses
 * (short, color, tint...) so the screens can switch from mock data with little change.
 */
public final class Views {

    private Views() {
    }

    public record EstablishmentView(
            String id,
            String name,
            String category,
            String city,
            String shortName,
            String color,
            String tint,
            String address,
            String opensAt,
            String closesAt,
            long waiting,
            long estimatedWaitMinutes,
            List<QueueView> queues
    ) {
    }

    public record QueueView(
            Long id,
            String name,
            String prefix,
            String counter,
            long waiting,
            long estimatedWaitMinutes
    ) {
    }

    public record TicketView(
            UUID id,
            String code,
            TicketStatus status,
            String establishmentId,
            String establishmentName,
            String establishmentShortName,
            String color,
            String tint,
            Long queueId,
            String queueName,
            String counter,
            String ownerName,
            // 1 = next to be called; 0 once the ticket is no longer waiting
            long position,
            long estimatedWaitMinutes,
            Instant createdAt,
            Instant calledAt
    ) {
    }

    /**
     * What an agent sees at a counter: who is being served and who is next.
     */
    public record CounterView(
            QueueView queue,
            TicketView current,
            List<TicketView> waiting
    ) {
    }

    public record TakeTicketRequest(@jakarta.validation.constraints.NotNull Long queueId) {
    }
}
