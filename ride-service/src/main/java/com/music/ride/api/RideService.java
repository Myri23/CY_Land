package com.music.ride.api;

import com.music.actor.core.ActorRef;
import com.music.actor.core.ActorSystem;
import com.music.actor.logging.ActorLogger;
import com.music.actor.supervision.AllForOneStrategy;
import com.music.ride.domain.RideActor;
import com.music.ride.domain.RideMessages;
import com.music.ride.messaging.RideEventPublisher;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des attractions.
 */
@Service
public class RideService {
    
    private final ActorSystem actorSystem;
    private final RideEventPublisher eventPublisher;
    private final ActorLogger logger;
    
    private final Map<String, ActorRef> rideActors = new ConcurrentHashMap<>();
    
    public RideService(ActorSystem actorSystem, RideEventPublisher eventPublisher, ActorLogger logger) {
        this.actorSystem = actorSystem;
        this.eventPublisher = eventPublisher;
        this.logger = logger;
    }
    
    @PostConstruct
    public void initialize() {
        // Créer les attractions avec supervision AllForOne
        var strategy = AllForOneStrategy.builder()
                .maxRestarts(3)
                .withinTimeRange(120_000)
                .build();
        
        createRide("rc", "RollerCoaster", 20, 60, strategy);
        createRide("gr", "GrandeRoue", 12, 120, strategy);
        createRide("vr", "SimulateurVR", 8, 180, strategy);
        
        // Créer l'acteur de tracking des visiteurs
        actorSystem.actorOf("visitor-tracker", id -> new VisitorTrackerActor(id));
        
        printStartupBanner();
    }
    
    private void createRide(String rideId, String name, int capacity, int cycleDuration,
                           AllForOneStrategy strategy) {
        ActorRef rideRef = actorSystem.actorOf(rideId,
                id -> new RideActor(id, name, capacity, cycleDuration, eventPublisher),
                strategy);
        rideActors.put(rideId, rideRef);
    }
    
    public void joinQueue(String rideId, String ticketId) {
        ActorRef ride = rideActors.get(rideId.toLowerCase());
        if (ride != null) {
            ride.tell(new RideMessages.JoinQueue(ticketId, rideId));
        }
    }
    
    public CompletableFuture<RideActor.JoinResult> joinQueueSync(String rideId, String ticketId) {
        ActorRef ride = rideActors.get(rideId.toLowerCase());
        if (ride == null) {
            return CompletableFuture.completedFuture(
                    new RideActor.JoinResult(false, "Ride not found", rideId));
        }
        return ride.ask(new RideMessages.JoinQueue(ticketId, rideId), Duration.ofSeconds(5));
    }
    
    public CompletableFuture<RideActor.CycleResult> startCycle(String rideId) {
        ActorRef ride = rideActors.get(rideId.toLowerCase());
        if (ride == null) {
            return CompletableFuture.completedFuture(
                    new RideActor.CycleResult(false, "Ride not found", List.of()));
        }
        return ride.ask(new RideMessages.StartCycle(rideId), Duration.ofSeconds(5));
    }
    
    public void reportFault(String rideId, RideMessages.FaultType faultType, String description) {
        ActorRef ride = rideActors.get(rideId.toLowerCase());
        if (ride != null) {
            ride.tell(new RideMessages.ReportFault(rideId, faultType, description));
        }
    }
    
    public void repairComplete(String rideId) {
        ActorRef ride = rideActors.get(rideId.toLowerCase());
        if (ride != null) {
            ride.tell(new RideMessages.RepairComplete(rideId));
        }
    }
    
    public CompletableFuture<RideMessages.RideState> getState(String rideId) {
        ActorRef ride = rideActors.get(rideId.toLowerCase());
        if (ride == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Ride not found: " + rideId));
        }
        return ride.ask(new RideMessages.GetState(rideId), Duration.ofSeconds(5));
    }
    
    public void blockRide(String rideId) {
        actorSystem.block(rideId.toLowerCase());
    }
    
    public void unblockRide(String rideId) {
        actorSystem.unblock(rideId.toLowerCase());
    }
    
    public Set<String> getAvailableRides() {
        return rideActors.keySet();
    }
    
    public boolean rideExists(String rideId) {
        return rideActors.containsKey(rideId.toLowerCase());
    }
    
    private void printStartupBanner() {
        System.out.println("===========================================");
        System.out.println("  RIDE SERVICE READY");
        System.out.println("  Attractions: " + rideActors.size());
        System.out.println();
        System.out.println("  Available rides:");
        rideActors.keySet().forEach(id -> System.out.println("  - " + id.toUpperCase()));
        System.out.println();
        System.out.println("  ENDPOINTS:");
        System.out.println("  - GET  /rides");
        System.out.println("  - GET  /rides/{id}/state");
        System.out.println("  - POST /rides/{id}/join?ticketId=XXX");
        System.out.println("  - POST /rides/{id}/start-cycle");
        System.out.println("  - POST /rides/{id}/report-fault");
        System.out.println("  - POST /rides/{id}/repair");
        System.out.println("===========================================");
    }
    
    private static class VisitorTrackerActor implements com.music.actor.core.Actor {
        private final String id;
        private final Set<String> visitorsInPark = ConcurrentHashMap.newKeySet();
        
        VisitorTrackerActor(String id) {
            this.id = id;
        }
        
        @Override
        public void onReceive(com.music.actor.core.Message message,
                             com.music.actor.core.ActorContext context) {
            if (message instanceof RideMessages.VisitorEntryNotification notification) {
                visitorsInPark.add(notification.ticketId());
                System.out.printf("[TRACKER] Visitor %s in park (total: %d)%n",
                        notification.ticketId(), visitorsInPark.size());
            }
        }
    }
}
