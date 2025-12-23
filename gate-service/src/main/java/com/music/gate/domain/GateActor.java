package com.music.gate.domain;

import com.music.actor.core.*;
import com.music.actor.runtime.LocalActorRef;
import com.music.gate.messaging.GateEventPublisher;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Acteur représentant une porte d'entrée du parc.
 * 
 * Fonctionnalités :
 * - Validation des tickets
 * - Anti-doublon (un ticket ne peut entrer qu'une fois)
 * - Publication d'événements vers RabbitMQ
 * - Communication avec le Ride Service
 * - Snapshot de l'état pour la récupération
 * - Réception des alertes de fermeture/réouverture des attractions
 */
public class GateActor implements Actor {
    
    private final String gateId;
    private final String gateName;
    private final GateType gateType;
    private final GateEventPublisher eventPublisher;
    
    // État de l'acteur
    private final Set<String> scannedTickets;
    private final Set<String> closedRides;  // Attractions actuellement fermées
    private Instant lastScanTime;
    private boolean operational;
    
    public GateActor(String gateId, String gateName, GateType gateType, 
                     GateEventPublisher eventPublisher) {
        this.gateId = gateId;
        this.gateName = gateName;
        this.gateType = gateType;
        this.eventPublisher = eventPublisher;
        this.scannedTickets = new HashSet<>();
        this.closedRides = new HashSet<>();
        this.operational = true;
    }
    
    @Override
    public void preStart(ActorContext context) {
        // Restaurer l'état si disponible
        GateState savedState = context.restore(GateState.class);
        if (savedState != null) {
            scannedTickets.addAll(savedState.scannedTickets());
            lastScanTime = savedState.lastScanTime();
            System.out.printf("[GATE %s] State restored: %d tickets already scanned%n", 
                    gateId, scannedTickets.size());
        }
        System.out.printf("[GATE %s] Started - Type: %s%n", gateId, gateType);
    }
    
    @Override
    public void postStop(ActorContext context) {
        System.out.printf("[GATE %s] Stopped - Total visitors: %d%n", gateId, scannedTickets.size());
    }
    
    @Override
    public void preRestart(Throwable reason, Message message, ActorContext context) {
        System.out.printf("[GATE %s] Restarting due to: %s%n", gateId, reason.getMessage());
        // Sauvegarder l'état avant le redémarrage
        context.snapshot(new GateState(Set.copyOf(scannedTickets), lastScanTime));
    }
    
    @Override
    public void onReceive(Message message, ActorContext context) {
        switch (message) {
            case GateMessages.ScanTicket scan -> handleScan(scan, context);
            case GateMessages.GetStatus ignored -> handleGetStatus(context);
            case NotificationHandlerActor.RideClosedAlert alert -> handleRideClosedAlert(alert, context);
            case NotificationHandlerActor.RideReopenedAlert alert -> handleRideReopenedAlert(alert, context);
            default -> System.out.printf("[GATE %s] Unknown message: %s%n", 
                    gateId, message.getClass().getSimpleName());
        }
    }
    
    private void handleScan(GateMessages.ScanTicket scan, ActorContext context) {
        String ticketId = scan.ticketId();
        
        // Vérifier si le ticket a déjà été scanné (anti-doublon)
        if (scannedTickets.contains(ticketId)) {
            System.out.printf("[GATE %s] Ticket %s REJECTED - Already scanned%n", gateId, ticketId);
            
            var rejection = new GateMessages.TicketRejected(ticketId, gateId, "Ticket already used");
            eventPublisher.publishRejection(rejection);
            
            // Répondre si c'est une requête ask
            replyIfAsk(context, new ScanResult(false, "Ticket already scanned", ticketId, gateId));
            return;
        }
        
        // Accepter le ticket
        scannedTickets.add(ticketId);
        lastScanTime = Instant.now();
        
        // Sauvegarder l'état
        context.snapshot(new GateState(Set.copyOf(scannedTickets), lastScanTime));
        
        // Publier l'événement
        var event = new GateMessages.VisitorEntered(ticketId, gateId);
        eventPublisher.publish(event);
        
        // Informer le visiteur des attractions fermées s'il y en a
        if (!closedRides.isEmpty()) {
            System.out.printf("[GATE %s] Ticket %s ACCEPTED - Note: %d attraction(s) closed: %s%n", 
                    gateId, ticketId, closedRides.size(), closedRides);
        } else {
            System.out.printf("[GATE %s] Ticket %s ACCEPTED - Visitor entered%n", gateId, ticketId);
        }
        
        // Notifier le Ride Service (communication inter-services)
        notifyRideService(ticketId, context);
        
        // Répondre si c'est une requête ask
        replyIfAsk(context, new ScanResult(true, "Entry granted", ticketId, gateId));
    }
    
    private void handleGetStatus(ActorContext context) {
        var status = new GateMessages.GateStatus(
                gateId, gateName, gateType, operational, scannedTickets.size(), lastScanTime
        );
        replyIfAsk(context, status);
    }
    
    /**
     * Gère l'alerte de fermeture d'une attraction.
     */
    private void handleRideClosedAlert(NotificationHandlerActor.RideClosedAlert alert, ActorContext context) {
        closedRides.add(alert.rideId());
        System.out.printf("[GATE %s] Received alert: Attraction %s is CLOSED%n", gateId, alert.rideId());
    }
    
    /**
     * Gère l'alerte de réouverture d'une attraction.
     */
    private void handleRideReopenedAlert(NotificationHandlerActor.RideReopenedAlert alert, ActorContext context) {
        closedRides.remove(alert.rideId());
        System.out.printf("[GATE %s] Received alert: Attraction %s is REOPENED%n", gateId, alert.rideId());
    }
    
    private void notifyRideService(String ticketId, ActorContext context) {
        try {
            // Obtenir une référence vers le Ride Service via Eureka
            ActorRef rideServiceActor = context.lookupRemote("ride-service", "visitor-tracker");
            if (rideServiceActor != null) {
                rideServiceActor.tell(new GateMessages.NotifyVisitorEntry(ticketId, gateId, Instant.now()));
            }
        } catch (Exception e) {
            // Le Ride Service n'est peut-être pas disponible, ce n'est pas critique
            System.out.printf("[GATE %s] Could not notify ride-service: %s%n", gateId, e.getMessage());
        }
    }
    
    private void replyIfAsk(ActorContext context, Object response) {
        if (context instanceof LocalActorRef.AskContext askContext) {
            askContext.reply(response);
        }
    }
    
    public int getVisitorCount() {
        return scannedTickets.size();
    }
    
    public Set<String> getClosedRides() {
        return Set.copyOf(closedRides);
    }
    
    // ==================== Inner Records ====================
    
    /**
     * État persistable de l'acteur.
     */
    public record GateState(Set<String> scannedTickets, Instant lastScanTime) {}
    
    /**
     * Résultat d'un scan de ticket.
     */
    public record ScanResult(boolean success, String message, String ticketId, String gateId) {}
}
