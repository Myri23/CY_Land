package com.park.gate.domain;

import com.park.actor.core.*;
import com.park.gate.messaging.GateEventPublisher;

import java.util.HashSet;
import java.util.Set;

public class GateActor implements Actor {
    
    private final String gateId;
    private final GateEventPublisher eventPublisher;
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
        
        if (scannedTickets.contains(ticketId)) {
            System.out.printf("[GATE %s] Ticket %s deja scanne - REJETE%n", gateId, ticketId);
            return;
        }
        
        scannedTickets.add(ticketId);
        context.snapshot(Set.copyOf(scannedTickets));
        
        var event = new VisitorEntered(ticketId, gateId);
        eventPublisher.publish(event);
        
        System.out.printf("[GATE %s] Ticket %s ACCEPTE - Visiteur entre%n", gateId, ticketId);
    }
    
    public int visitorCount() {
        return scannedTickets.size();
    }
}
