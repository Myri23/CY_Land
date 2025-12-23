package com.music.gate.domain;

import com.music.actor.core.Actor;
import com.music.actor.core.ActorContext;
import com.music.actor.core.Message;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acteur qui reçoit les notifications des autres services (notamment Ride Service).
 * 
 * Gère les notifications de :
 * - Fermeture/réouverture des attractions
 * - Changements de statut des attractions
 * - Alertes de sécurité
 */
public class NotificationHandlerActor implements Actor {
    
    private final String id;
    
    // État des attractions (rideId -> status)
    private final Map<String, RideStatus> rideStatuses = new ConcurrentHashMap<>();
    
    public NotificationHandlerActor(String id) {
        this.id = id;
    }
    
    @Override
    public void preStart(ActorContext context) {
        System.out.printf("[NOTIFICATION-HANDLER] Started - Ready to receive notifications%n");
    }
    
    @Override
    public void postStop(ActorContext context) {
        System.out.printf("[NOTIFICATION-HANDLER] Stopped%n");
    }
    
    @Override
    public void onReceive(Message message, ActorContext context) {
        switch (message) {
            case RideStatusNotification notification -> handleRideStatusNotification(notification, context);
            case GetRideStatuses ignored -> handleGetRideStatuses(context);
            default -> System.out.printf("[NOTIFICATION-HANDLER] Unknown message: %s%n", 
                    message.getClass().getSimpleName());
        }
    }
    
    private void handleRideStatusNotification(RideStatusNotification notification, ActorContext context) {
        String rideId = notification.rideId();
        String status = notification.status();
        
        // Mettre à jour le statut local
        RideStatus rideStatus = new RideStatus(rideId, status, Instant.now());
        rideStatuses.put(rideId, rideStatus);
        
        System.out.printf("[NOTIFICATION-HANDLER] Ride %s status changed to: %s%n", rideId, status);
        
        // Actions selon le statut
        switch (status) {
            case "RIDE_CLOSED" -> handleRideClosed(rideId, context);
            case "RIDE_REOPENED" -> handleRideReopened(rideId, context);
            default -> System.out.printf("[NOTIFICATION-HANDLER] Unknown status: %s%n", status);
        }
    }
    
    private void handleRideClosed(String rideId, ActorContext context) {
        // Notifier les portes pour qu'elles puissent informer les visiteurs
        System.out.printf("[NOTIFICATION-HANDLER] Broadcasting: Attraction %s is CLOSED%n", rideId);
        
        // On pourrait envoyer des messages aux acteurs de portes ici
        // Pour l'instant, on log simplement l'information
        broadcastToGates(context, new RideClosedAlert(rideId, Instant.now()));
    }
    
    private void handleRideReopened(String rideId, ActorContext context) {
        System.out.printf("[NOTIFICATION-HANDLER] Broadcasting: Attraction %s is REOPENED%n", rideId);
        
        // On pourrait envoyer des messages aux acteurs de portes ici
        broadcastToGates(context, new RideReopenedAlert(rideId, Instant.now()));
    }
    
    private void broadcastToGates(ActorContext context, Message alert) {
        // Envoyer aux portes principales (G1, G2, VIP)
        String[] gateIds = {"G1", "G2", "VIP"};
        for (String gateId : gateIds) {
            try {
                var gateRef = context.lookup(gateId);
                if (gateRef != null) {
                    gateRef.tell(alert);
                }
            } catch (Exception e) {
                // La porte n'existe peut-être pas, ce n'est pas critique
            }
        }
    }
    
    private void handleGetRideStatuses(ActorContext context) {
        // Répondre avec les statuts actuels
        if (context instanceof com.music.actor.runtime.LocalActorRef.AskContext askContext) {
            askContext.reply(Map.copyOf(rideStatuses));
        }
    }
    
    // ==================== Inner Records ====================
    
    /**
     * Notification de changement de statut d'une attraction.
     * Reçue depuis le Ride Service.
     */
    public record RideStatusNotification(String rideId, String status) implements Message {}
    
    /**
     * Demande de récupération des statuts des attractions.
     */
    public record GetRideStatuses() implements Message {}
    
    /**
     * Statut d'une attraction.
     */
    public record RideStatus(String rideId, String status, Instant lastUpdate) {}
    
    /**
     * Alerte de fermeture d'attraction (envoyée aux portes).
     */
    public record RideClosedAlert(String rideId, Instant timestamp) implements Message {}
    
    /**
     * Alerte de réouverture d'attraction (envoyée aux portes).
     */
    public record RideReopenedAlert(String rideId, Instant timestamp) implements Message {}
}
