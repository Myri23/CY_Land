package com.music.gate.domain;

import com.music.actor.core.Message;

import java.time.Instant;

/**
 * Messages utilises par les acteurs Gate.
 */
public interface GateMessages {
    
    /**
     * Message pour scanner un ticket a une porte.
     */
    record ScanTicket(String ticketId, String gateId) implements Message {}
    
    /**
     * Message pour verifier le statut d'une porte.
     */
    record GetStatus(String gateId) implements Message {}
    
    /**
     * Reponse au statut d'une porte.
     */
    record GateStatus(
            String gateId,
            String name,
            GateType type,
            boolean operational,
            int visitorsToday,
            Instant lastScan
    ) implements Message {}
    
    /**
     * Evenement emis quand un visiteur entre.
     */
    record VisitorEntered(
            String ticketId,
            String gateId,
            Instant timestamp
    ) implements Message {
        public VisitorEntered(String ticketId, String gateId) {
            this(ticketId, gateId, Instant.now());
        }
    }
    
    /**
     * Evenement emis quand un ticket est refuse.
     */
    record TicketRejected(
            String ticketId,
            String gateId,
            String reason,
            Instant timestamp
    ) implements Message {
        public TicketRejected(String ticketId, String gateId, String reason) {
            this(ticketId, gateId, reason, Instant.now());
        }
    }
    
    /**
     * Message pour notifier une attraction de l'entree d'un visiteur.
     * Utilise pour la communication inter-services.
     */
    record NotifyVisitorEntry(
            String ticketId,
            String gateId,
            Instant entryTime
    ) implements Message {}
}
