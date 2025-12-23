package com.music.gate.domain;

import com.music.actor.core.Message;

import java.time.Instant;

/**
 * Messages utilisés par les acteurs Gate.
 */
public sealed interface GateMessages {
    
    /**
     * Message pour scanner un ticket à une porte.
     */
    record ScanTicket(String ticketId, String gateId) implements Message {}
    
    /**
     * Message pour vérifier le statut d'une porte.
     */
    record GetStatus(String gateId) implements Message {}
    
    /**
     * Réponse au statut d'une porte.
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
     * Événement émis quand un visiteur entre.
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
     * Événement émis quand un ticket est refusé.
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
     * Message pour notifier une attraction de l'entrée d'un visiteur.
     * Utilisé pour la communication inter-services.
     */
    record NotifyVisitorEntry(
            String ticketId,
            String gateId,
            Instant entryTime
    ) implements Message {}
}
