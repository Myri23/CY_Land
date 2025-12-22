package com.park.gate.domain;

import java.time.Instant;

/**
 * Événement émis lorsqu'un visiteur entre par une porte.
 * Publié sur RabbitMQ pour être consommé par d'autres services.
 */
public record VisitorEntered(
        String ticketId,
        String gateId,
        Instant timestamp
) {
    public VisitorEntered(String ticketId, String gateId) {
        this(ticketId, gateId, Instant.now());
    }
}
