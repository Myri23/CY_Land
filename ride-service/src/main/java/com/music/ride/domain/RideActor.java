package com.music.ride.domain;

import com.music.actor.core.*;
import com.music.actor.runtime.LocalActorRef;
import com.music.ride.messaging.RideEventPublisher;

import java.time.Instant;
import java.util.*;

/**
 * Acteur représentant une attraction du parc.
 * 
 * Fonctionnalités :
 * - Gestion de la file d'attente
 * - Cycles automatiques ou manuels
 * - Gestion des pannes
 * - Communication avec Gate Service
 */
public class RideActor implements Actor {
    
    private final String rideId;
    private final String rideName;
    private final int capacity;
    private final int cycleDurationSec;
    private final RideEventPublisher eventPublisher;
    
    // État
    private final Deque<String> queue;
    private List<String> inCycle;
    private boolean closed;
    private RideMessages.FaultType currentFault;
    private int totalRiders;
    
    public RideActor(String rideId, String rideName, int capacity, 
                     int cycleDurationSec, RideEventPublisher eventPublisher) {
        this.rideId = rideId;
        this.rideName = rideName;
        this.capacity = capacity;
        this.cycleDurationSec = cycleDurationSec;
        this.eventPublisher = eventPublisher;
        
        this.queue = new ArrayDeque<>();
        this.inCycle = new ArrayList<>();
        this.closed = false;
        this.currentFault = RideMessages.FaultType.NONE;
        this.totalRiders = 0;
    }
    
    @Override
    public void preStart(ActorContext context) {
        // Restaurer l'état si disponible
        RideState savedState = context.restore(RideState.class);
        if (savedState != null) {
            queue.addAll(savedState.queue());
            inCycle = new ArrayList<>(savedState.inCycle());
            closed = savedState.closed();
            currentFault = savedState.currentFault();
            totalRiders = savedState.totalRiders();
            System.out.printf("[RIDE %s] State restored: queue=%d, closed=%s%n",
                    rideId, queue.size(), closed);
        }
        System.out.printf("[RIDE %s] Started - Capacity: %d, Cycle: %ds%n",
                rideId, capacity, cycleDurationSec);
    }
    
    @Override
    public void postStop(ActorContext context) {
        System.out.printf("[RIDE %s] Stopped - Total riders: %d%n", rideId, totalRiders);
    }
    
    @Override
    public void preRestart(Throwable reason, Message message, ActorContext context) {
        System.out.printf("[RIDE %s] Restarting due to: %s%n", rideId, reason.getMessage());
        saveState(context);
    }
    
    @Override
    public void onReceive(Message message, ActorContext context) {
        switch (message) {
            case RideMessages.JoinQueue join -> handleJoinQueue(join, context);
            case RideMessages.StartCycle ignored -> handleStartCycle(context);
            case RideMessages.CycleTick ignored -> handleCycleTick(context);
            case RideMessages.ReportFault fault -> handleReportFault(fault, context);
            case RideMessages.RepairComplete ignored -> handleRepairComplete(context);
            case RideMessages.GetState ignored -> handleGetState(context);
            case RideMessages.VisitorEntryNotification notification -> 
                handleVisitorEntry(notification, context);
            default -> System.out.printf("[RIDE %s] Unknown message: %s%n",
                    rideId, message.getClass().getSimpleName());
        }
    }
    
    private void handleJoinQueue(RideMessages.JoinQueue join, ActorContext context) {
        if (closed) {
            System.out.printf("[RIDE %s] REJECTED: Attraction closed%n", rideId);
            replyIfAsk(context, new JoinResult(false, "Attraction closed", rideId));
            return;
        }
        
        queue.addLast(join.ticketId());
        System.out.printf("[RIDE %s] Ticket %s joined queue (size=%d)%n",
                rideId, join.ticketId(), queue.size());
        
        saveState(context);
        
        // Auto-start si la queue atteint la capacité
        if (queue.size() >= capacity && inCycle.isEmpty()) {
            System.out.printf("[RIDE %s] Queue full, auto-starting cycle%n", rideId);
            context.self().tell(new RideMessages.StartCycle(rideId));
        }
        
        replyIfAsk(context, new JoinResult(true, "Added to queue", rideId));
    }
    
    private void handleStartCycle(ActorContext context) {
        if (closed || queue.isEmpty()) {
            System.out.printf("[RIDE %s] Cannot start cycle (closed=%s, queue=%d)%n",
                    rideId, closed, queue.size());
            replyIfAsk(context, new CycleResult(false, "Cannot start cycle", List.of()));
            return;
        }
        
        if (!inCycle.isEmpty()) {
            System.out.printf("[RIDE %s] Cycle already in progress%n", rideId);
            replyIfAsk(context, new CycleResult(false, "Cycle in progress", inCycle));
            return;
        }
        
        // Charger les passagers
        List<String> passengers = new ArrayList<>();
        while (!queue.isEmpty() && passengers.size() < capacity) {
            passengers.add(queue.removeFirst());
        }
        inCycle = passengers;
        totalRiders += passengers.size();
        
        saveState(context);
        
        // Publier l'événement
        eventPublisher.publishCycleStarted(rideId, passengers);
        
        System.out.printf("[RIDE %s] Cycle started with %d passengers: %s%n",
                rideId, passengers.size(), passengers);
        
        // Planifier la fin du cycle
        context.scheduleOnce(new RideMessages.CycleTick(rideId), cycleDurationSec * 1000L);
        
        replyIfAsk(context, new CycleResult(true, "Cycle started", passengers));
    }
    
    private void handleCycleTick(ActorContext context) {
        if (inCycle.isEmpty()) return;
        
        List<String> finishedPassengers = new ArrayList<>(inCycle);
        inCycle = new ArrayList<>();
        
        saveState(context);
        
        // Publier l'événement
        eventPublisher.publishCycleFinished(rideId, finishedPassengers);
        
        System.out.printf("[RIDE %s] Cycle finished for %d passengers%n",
                rideId, finishedPassengers.size());
        
        // Auto-démarrer le prochain cycle si des visiteurs attendent
        if (!queue.isEmpty() && !closed) {
            System.out.printf("[RIDE %s] Starting next cycle (queue=%d)%n", rideId, queue.size());
            context.self().tell(new RideMessages.StartCycle(rideId));
        }
    }
    
    private void handleReportFault(RideMessages.ReportFault fault, ActorContext context) {
        closed = true;
        currentFault = fault.faultType();
        
        saveState(context);
        
        System.out.printf("[RIDE %s] FAULT REPORTED: %s - %s%n",
                rideId, fault.faultType(), fault.description());
        
        // Notifier le Gate Service pour rediriger les visiteurs
        notifyGateService(context, "RIDE_CLOSED");
        
        replyIfAsk(context, Map.of("status", "FAULT_REGISTERED", "rideId", rideId));
    }
    
    private void handleRepairComplete(ActorContext context) {
        closed = false;
        currentFault = RideMessages.FaultType.NONE;
        
        saveState(context);
        
        System.out.printf("[RIDE %s] Repair complete, attraction reopened%n", rideId);
        
        // Notifier le Gate Service
        notifyGateService(context, "RIDE_REOPENED");
        
        replyIfAsk(context, Map.of("status", "REPAIR_COMPLETE", "rideId", rideId));
    }
    
    private void handleGetState(ActorContext context) {
        var state = new RideMessages.RideState(
                rideId, rideName, queue.size(), List.copyOf(inCycle),
                capacity, cycleDurationSec, closed, currentFault
        );
        replyIfAsk(context, state);
    }
    
    private void handleVisitorEntry(RideMessages.VisitorEntryNotification notification, 
                                    ActorContext context) {
        // Un visiteur est entré dans le parc, on peut l'ajouter à la liste des visiteurs potentiels
        System.out.printf("[RIDE %s] Visitor %s entered park via gate %s%n",
                rideId, notification.ticketId(), notification.gateId());
    }
    
    private void notifyGateService(ActorContext context, String status) {
        try {
            ActorRef gateService = context.lookupRemote("gate-service", "notification-handler");
            if (gateService != null) {
                gateService.tell(new RideStatusNotification(rideId, status));
            }
        } catch (Exception e) {
            System.out.printf("[RIDE %s] Could not notify gate-service: %s%n", rideId, e.getMessage());
        }
    }
    
    private void saveState(ActorContext context) {
        context.snapshot(new RideState(
                new ArrayList<>(queue), new ArrayList<>(inCycle),
                closed, currentFault, totalRiders
        ));
    }
    
    private void replyIfAsk(ActorContext context, Object response) {
        if (context instanceof LocalActorRef.AskContext askContext) {
            askContext.reply(response);
        }
    }
    
    // ==================== Inner Records ====================
    
    public record RideState(
            List<String> queue,
            List<String> inCycle,
            boolean closed,
            RideMessages.FaultType currentFault,
            int totalRiders
    ) {}
    
    public record JoinResult(boolean success, String message, String rideId) {}
    
    public record CycleResult(boolean success, String message, List<String> passengers) {}
    
    public record RideStatusNotification(String rideId, String status) implements Message {}
}
