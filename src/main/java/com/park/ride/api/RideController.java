package com.park.ride.api;

import com.park.ride.service.RideService;
import com.park.ride.service.RideService.RideState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * API REST pour la gestion des attractions.
 */
@RestController
@RequestMapping("/rides")
public class RideController {
    
    private final RideService rideService;
    
    public RideController(RideService rideService) {
        this.rideService = rideService;
    }
    
    /**
     * Liste toutes les attractions disponibles.
     */
    @GetMapping
    public ResponseEntity<Set<String>> listRides() {
        return ResponseEntity.ok(rideService.getAvailableRides());
    }
    
    /**
     * Recupere l'etat d'une attraction.
     */
    @GetMapping("/{rideId}/state")
    public ResponseEntity<RideState> getState(@PathVariable String rideId) {
        RideState state = rideService.getState(rideId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }
    
    /**
     * Ajoute un visiteur a la file d'attente d'une attraction.
     */
    @PostMapping("/{rideId}/join")
    public ResponseEntity<Map<String, Object>> joinQueue(
            @PathVariable String rideId,
            @RequestParam String ticketId) {
        
        RideState stateBefore = rideService.getState(rideId);
        if (stateBefore == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (stateBefore.closed()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Attraction " + rideId + " est fermee"
                    ));
        }
        
        rideService.joinQueue(rideId, ticketId);
        
        RideState stateAfter = rideService.getState(rideId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Ticket " + ticketId + " ajoute a la queue de " + rideId,
                "queueSize", stateAfter.queueSize(),
                "inCycle", stateAfter.inCycle()
        ));
    }
    
    /**
     * Demarre manuellement un cycle pour une attraction.
     */
    @PostMapping("/{rideId}/start-cycle")
    public ResponseEntity<Map<String, Object>> startCycle(@PathVariable String rideId) {
        RideState stateBefore = rideService.getState(rideId);
        if (stateBefore == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (stateBefore.closed()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Attraction " + rideId + " est fermee"
                    ));
        }
        
        if (stateBefore.queueSize() == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Aucun visiteur dans la queue de " + rideId
                    ));
        }
        
        rideService.startCycle(rideId);
        
        RideState stateAfter = rideService.getState(rideId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Cycle demarre pour " + rideId,
                "queueSize", stateAfter.queueSize(),
                "inCycle", stateAfter.inCycle()
        ));
    }
    
    /**
     * Signale une panne sur une attraction.
     */
    @PostMapping("/{rideId}/report-fault")
    public ResponseEntity<Map<String, String>> reportFault(
            @PathVariable String rideId,
            @RequestParam String faultType,
            @RequestParam(required = false, defaultValue = "") String description) {
        
        RideState state = rideService.getState(rideId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        
        rideService.reportFault(rideId, faultType, description);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Panne signalee pour " + rideId + ": " + faultType
        ));
    }
}
