package com.park.gate.domain;

import java.time.Instant;

public record VisitorEntered(String ticketId, String gateId, Instant timestamp) {
    public VisitorEntered(String ticketId, String gateId) {
        this(ticketId, gateId, Instant.now());
    }
}
