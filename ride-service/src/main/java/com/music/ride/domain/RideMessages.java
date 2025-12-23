package com.music.ride.domain;

import com.music.actor.core.Message;

import java.time.Instant;
import java.util.List;

/**
 * Messages utilisés par les acteurs Ride.
 */
public sealed interface RideMessages {
    
    /**
     * Message pour rejoindre la file d'attente.
     */
    record JoinQueue(String ticketId, String rideId) implements Message {}
    
    /**
     * Message pour démarrer un cycle.
     */
    record StartCycle(String rideId) implements Message {}
    
    /**
     * Message interne pour signaler la fin d'un cycle.
     */
    record CycleTick(String rideId) implements Message {}
    
    /**
     * Message pour signaler une panne.
     */
    record ReportFault(String rideId, FaultType faultType, String description) implements Message {}
    
    /**
     * Message pour réparer une attraction.
     */
    record RepairComplete(String rideId) implements Message {}
    
    /**
     * Message pour obtenir l'état d'une attraction.
     */
    record GetState(String rideId) implements Message {}
    
    /**
     * Réponse avec l'état d'une attraction.
     */
    record RideState(
            String rideId,
            String name,
            int queueSize,
            List<String> inCycle,
            int capacity,
            int cycleDurationSec,
            boolean closed,
            FaultType currentFault
    ) implements Message {}
    
    /**
     * Événement émis quand un cycle démarre.
     */
    record CycleStarted(
            String rideId,
            List<String> passengers,
            Instant timestamp
    ) implements Message {}
    
    /**
     * Événement émis quand un cycle se termine.
     */
    record CycleFinished(
            String rideId,
            List<String> passengers,
            Instant timestamp
    ) implements Message {}
    
    /**
     * Notification d'entrée d'un visiteur (depuis Gate Service).
     */
    record VisitorEntryNotification(
            String ticketId,
            String gateId,
            Instant entryTime
    ) implements Message {}
    
    /**
     * Types de pannes.
     */
    enum FaultType {
        NONE,
        MECHANICAL,
        ELECTRICAL,
        SAFETY,
        WEATHER
    }
}
