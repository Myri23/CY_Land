package com.park.gate.domain;

import com.park.actor.core.Message;

/**
 * Message envoyé à un GateActor pour scanner un ticket.
 */
public record ScanTicket(String ticketId) implements Message {
}
