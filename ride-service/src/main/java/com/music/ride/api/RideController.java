package com.music.ride.api;

import com.music.ride.domain.RideActor;
import com.music.ride.domain.RideMessages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    
    @GetMapping
    public ResponseEntity<Set<String>> listRides() {
        return ResponseEntity.ok(rideService.getAvailableRides());
    }
    
    @GetMapping("/{rideId}/state")
    public ResponseEntity<RideMessages.RideState> getState(@PathVariable String rideId) {
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            RideMessages.RideState state = rideService.getState(rideId)
                    .get(5, TimeUnit.SECONDS);
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/{rideId}/join")
    public ResponseEntity<Map<String, Object>> joinQueue(
            @PathVariable String rideId,
            @RequestParam String ticketId) {
        
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            RideActor.JoinResult result = rideService.joinQueueSync(rideId, ticketId)
                    .get(5, TimeUnit.SECONDS);
            
            if (result.success()) {
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", result.message(),
                        "rideId", rideId,
                        "ticketId", ticketId
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "ERROR",
                        "message", result.message()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/{rideId}/start-cycle")
    public ResponseEntity<Map<String, Object>> startCycle(@PathVariable String rideId) {
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            RideActor.CycleResult result = rideService.startCycle(rideId)
                    .get(5, TimeUnit.SECONDS);
            
            if (result.success()) {
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", result.message(),
                        "passengers", result.passengers()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "ERROR",
                        "message", result.message()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/{rideId}/report-fault")
    public ResponseEntity<Map<String, String>> reportFault(
            @PathVariable String rideId,
            @RequestParam RideMessages.FaultType faultType,
            @RequestParam(defaultValue = "") String description) {
        
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        rideService.reportFault(rideId, faultType, description);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Fault reported for " + rideId,
                "faultType", faultType.name()
        ));
    }
    
    @PostMapping("/{rideId}/repair")
    public ResponseEntity<Map<String, String>> repairComplete(@PathVariable String rideId) {
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        rideService.repairComplete(rideId);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Repair complete for " + rideId
        ));
    }
    
    @PostMapping("/{rideId}/block")
    public ResponseEntity<Map<String, String>> blockRide(@PathVariable String rideId) {
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        rideService.blockRide(rideId);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Ride " + rideId + " blocked"
        ));
    }
    
    @PostMapping("/{rideId}/unblock")
    public ResponseEntity<Map<String, String>> unblockRide(@PathVariable String rideId) {
        if (!rideService.rideExists(rideId)) {
            return ResponseEntity.notFound().build();
        }
        
        rideService.unblockRide(rideId);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Ride " + rideId + " unblocked"
        ));
    }
}
