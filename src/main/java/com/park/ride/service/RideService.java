package com.park.ride.service;

import com.park.actor.core.ActorContext;
import com.park.actor.core.ActorRef;
import com.park.ride.actor.*;
import com.park.ride.messaging.in.ReportFault;
import com.park.ride.messaging.in.StartCycle;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service central pour la gestion des attractions.
 * Utilise une approche simplifiee sans le runtime complet pour la demo.
 */
@Service
public class RideService {
    
    private final Map<String, RideActor> rides = new HashMap<>();
    private final SimpleActorContext context = new SimpleActorContext();
    
    public RideService() {
        registerRide(new RollerCoaster());
        registerRide(new GrandeRoue());
        registerRide(new SimulateurVR());
        
        System.out.println("===========================================");
        System.out.println("  RIDE SERVICE READY");
        System.out.println("  Attractions actives: " + rides.size());
        System.out.println();
        System.out.println("  Attractions disponibles:");
        rides.forEach((id, ride) -> 
            System.out.printf("  - %s (capacite: %d, duree: %ds)%n", 
                    id, ride.getCapacity(), ride.getCycleDurationSec()));
        System.out.println("===========================================");
    }
    
    private void registerRide(RideActor ride) {
        rides.put(ride.getRideId().toLowerCase(), ride);
    }
    
    public void joinQueue(String rideId, String ticketId) {
        RideActor ride = getRide(rideId);
        if (ride != null) {
            ride.addToQueue(ticketId);
            
            if (ride.getQueueSize() >= ride.getCapacity() && ride.getInCycle().isEmpty()) {
                System.out.printf("[RIDE %s] Queue pleine, demarrage automatique du cycle%n", rideId);
                ride.onReceive(new StartCycle(), context);
            }
        }
    }
    
    public void startCycle(String rideId) {
        RideActor ride = getRide(rideId);
        if (ride != null) {
            ride.onReceive(new StartCycle(), context);
        }
    }
    
    public void reportFault(String rideId, String faultType, String description) {
        RideActor ride = getRide(rideId);
        if (ride != null) {
            ride.onReceive(new ReportFault(rideId, faultType, description), context);
        }
    }
    
    public RideState getState(String rideId) {
        RideActor ride = getRide(rideId);
        if (ride == null) {
            return null;
        }
        return new RideState(
                ride.getRideId(),
                ride.getQueueSize(),
                ride.getInCycle(),
                ride.getCapacity(),
                ride.getCycleDurationSec(),
                ride.isClosed()
        );
    }
    
    public Set<String> getAvailableRides() {
        return rides.keySet();
    }
    
    private RideActor getRide(String rideId) {
        RideActor ride = rides.get(rideId.toLowerCase());
        if (ride == null) {
            System.err.printf("[ERROR] Attraction inconnue: %s%n", rideId);
        }
        return ride;
    }
    
    public record RideState(
            String rideId,
            int queueSize,
            List<String> inCycle,
            int capacity,
            int cycleDurationSec,
            boolean closed
    ) {}
    
    private static class SimpleActorContext implements ActorContext {
        @Override
        public String id() {
            return "ride-service";
        }
        
        @Override
        public ActorRef self() {
            return new ActorRef() {
                @Override
                public String id() {
                    return "ride-service";
                }
                
                @Override
                public void tell(com.park.actor.core.Message message) {
                    System.out.println("[CONTEXT] Message ignore: " + message.getClass().getSimpleName());
                }
            };
        }
        
        @Override
        public ActorRef lookup(String actorId) {
            return null;
        }
        
        @Override
        public void snapshot(Object state) {
            System.out.println("[SNAPSHOT] Ride state saved");
        }
        
        @Override
        public <T> T restore(Class<T> type) {
            return null;
        }
    }
}
