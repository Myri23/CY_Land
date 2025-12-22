package com.park.gate.domain;

import com.park.actor.core.*;
import com.park.gate.messaging.GateEventPublisher;

import java.util.HashSet;
import java.util.Set;

/**
 * Acteur représentant une porte d'entrée du parc.
 * 
 * <p>Responsabilités :
 * <ul>
 *   <li>Valider les scans de tickets</li>
 *   <li>Éviter les doublons (un ticket ne peut entrer qu'une fois)</li>
 *   <li>Publier les événements VisitorEntered</li>
 * </ul>
 * 
 * <p>Chaque porte (G1, G2, etc.) a son propre acteur avec son état isolé.
 */
public class GateActor implements Actor {
    
    private final String gateId;
    private final GateEventPublisher eventPublisher;
    
    // État interne : tickets déjà scannés (anti-doublon)
    private final Set<String> scannedTickets = new HashSet<>();
    
    public GateActor(String gateId, GateEventPublisher eventPublisher) {
        this.gateId = gateId;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public void onReceive(Message message, ActorContext context) {
        if (message instanceof ScanTicket scan) {
            handleScan(scan, context);
        }
    }
    
    private void handleScan(ScanTicket scan, ActorContext context) {
        String ticketId = scan.ticketId();
        
        // Vérification anti-doublon
        if (scannedTickets.contains(ticketId)) {
            System.out.printf("[GATE %s] Ticket %s déjà scanné - REJETÉ%n", gateId, ticketId);
            return;
        }
        
        // Accepter le ticket
        scannedTickets.add(ticketId);
        
        // Snapshot de l'état (pour récupération future)
        context.snapshot(Set.copyOf(scannedTickets));
        
        // Publier l'événement
        var event = new VisitorEntered(ticketId, gateId);
        eventPublisher.publish(event);
        
        System.out.printf("[GATE %s] Ticket %s ACCEPTÉ - Visiteur entré%n", gateId, ticketId);
    }
    
    /**
     * Retourne le nombre de visiteurs entrés par cette porte.
     */
    public int visitorCount() {
        return scannedTickets.size();
    }
}
