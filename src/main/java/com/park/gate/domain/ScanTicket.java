package com.park.gate.domain;

import com.park.actor.core.Message;

public record ScanTicket(String ticketId) implements Message {
}
