package com.park.ride.actor;

import com.park.actor.core.Actor;
import com.park.actor.core.ActorContext;
import com.park.actor.core.Message;
import com.park.ride.messaging.in.CycleTick;
import com.park.ride.messaging.in.ReportFault;
import com.park.ride.messaging.in.StartCycle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Acteur representant une attraction du parc.
 * Gere la file d'attente et les cycles de l'attraction.
 */
public class RideActor implements Actor {
    
    private final String rideId;
    private final int capacity;
    private final int cycleDurationSec;
    private final Deque<String> queue = new ArrayDeque<>();
    private List<String> inCycle = new ArrayList<>();
    private boolean closed = false;
    
    public RideActor(String rideId, int capacity, int cycleDurationSec) {
        this.rideId = rideId;
        this.capacity = capacity;
        this.cycleDurationSec = cycleDurationSec;
    }
    
    @Override
    public void onReceive(Message message, ActorContext context) {
        if (message instanceof StartCycle) {
            handleStartCycle(context);
        } else if (message instanceof CycleTick) {
            handleCycleTick(context);
        } else if (message instanceof ReportFault rf) {
            handleReportFault(rf, context);
        }
    }
    
    private void handleStartCycle(ActorContext context) {
        if (closed || queue.isEmpty()) {
            System.out.printf("[RIDE %s] Impossible de demarrer le cycle (ferme=%s, queue=%d)%n", 
                    rideId, closed, queue.size());
            return;
        }
        
        List<String> passengers = new ArrayList<>();
        while (!queue.isEmpty() && passengers.size() < capacity) {
            passengers.add(queue.removeFirst());
        }
        inCycle = passengers;
        
        context.snapshot(new RideState(new ArrayList<>(queue), inCycle, closed));
        
        System.out.printf("[RIDE %s] Cycle demarre avec %d passagers: %s%n", 
                rideId, inCycle.size(), inCycle);
        
        context.self().tell(new CycleTick());
    }
    
    private void handleCycleTick(ActorContext context) {
        if (inCycle.isEmpty()) {
            return;
        }
        
        System.out.printf("[RIDE %s] Cycle termine pour %d passagers%n", rideId, inCycle.size());
        
        inCycle = new ArrayList<>();
        
        context.snapshot(new RideState(new ArrayList<>(queue), inCycle, closed));
        
        if (!queue.isEmpty() && !closed) {
            System.out.printf("[RIDE %s] Demarrage automatique du prochain cycle (queue=%d)%n", 
                    rideId, queue.size());
            context.self().tell(new StartCycle());
        }
    }
    
    private void handleReportFault(ReportFault fault, ActorContext context) {
        System.out.printf("[RIDE %s] PANNE SIGNALEE: %s - %s%n", 
                rideId, fault.faultType(), fault.description());
        closed = true;
        context.snapshot(new RideState(new ArrayList<>(queue), inCycle, closed));
    }
    
    public void addToQueue(String ticketId) {
        if (!closed) {
            queue.addLast(ticketId);
            System.out.printf("[RIDE %s] Ticket %s ajoute a la queue (taille=%d)%n", 
                    rideId, ticketId, queue.size());
        } else {
            System.out.printf("[RIDE %s] REFUSE: Attraction fermee%n", rideId);
        }
    }
    
    public void addToCycle(String passengerId) {
        if (inCycle.size() < capacity) {
            inCycle.add(passengerId);
        }
    }
    
    public String getRideId() {
        return rideId;
    }
    
    public int getCapacity() {
        return capacity;
    }
    
    public int getCycleDurationSec() {
        return cycleDurationSec;
    }
    
    public int getQueueSize() {
        return queue.size();
    }
    
    public Deque<String> getQueue() {
        return queue;
    }
    
    public List<String> getInCycle() {
        return new ArrayList<>(inCycle);
    }
    
    public boolean isClosed() {
        return closed;
    }
    
    public void setClosed(boolean closed) {
        this.closed = closed;
    }
    
    public record RideState(List<String> queue, List<String> inCycle, boolean closed) {
    }
}
