package com.music.gate.api;

import com.music.actor.scalability.ActorPool;
import com.music.gate.domain.GateActor;
import com.music.gate.domain.GateMessages;
import com.music.gate.domain.GateType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * API REST pour le contrôle d'accès aux portes.
 */
@RestController
@RequestMapping("/gate")
@Validated
public class GateController {
    
    private final GateService gateService;
    
    public GateController(GateService gateService) {
        this.gateService = gateService;
    }
    
    /**
     * Scan asynchrone d'un ticket (fire-and-forget).
     */
    @PostMapping("/{gateId}/scan")
    public ResponseEntity<Map<String, String>> scan(
            @PathVariable @NotBlank String gateId,
            @RequestParam @NotBlank String ticketId) {
        
        gateService.handleScan(gateId, ticketId);
        
        return ResponseEntity.accepted().body(Map.of(
                "status", "SCAN_ACCEPTED",
                "gateId", gateId,
                "ticketId", ticketId,
                "mode", "async"
        ));
    }
    
    /**
     * Scan synchrone d'un ticket (ask pattern - attend la réponse).
     */
    @PostMapping("/{gateId}/scan-sync")
    public ResponseEntity<GateActor.ScanResult> scanSync(
            @PathVariable @NotBlank String gateId,
            @RequestParam @NotBlank String ticketId) {
        
        try {
            GateActor.ScanResult result = gateService.handleScanSync(gateId, ticketId)
                    .get(5, TimeUnit.SECONDS);
            
            if (result.success()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return ResponseEntity.internalServerError().body(
                    new GateActor.ScanResult(false, "Timeout or error: " + e.getMessage(), ticketId, gateId)
            );
        }
    }
    
    /**
     * Récupère le statut d'une porte.
     */
    @GetMapping("/{gateId}/status")
    public ResponseEntity<GateMessages.GateStatus> getStatus(@PathVariable String gateId) {
        try {
            GateMessages.GateStatus status = gateService.getGateStatus(gateId)
                    .get(5, TimeUnit.SECONDS);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Bloque une porte (suspend le traitement).
     */
    @PostMapping("/{gateId}/block")
    public ResponseEntity<Map<String, String>> blockGate(@PathVariable String gateId) {
        gateService.blockGate(gateId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Gate " + gateId + " blocked",
                "gateId", gateId
        ));
    }
    
    /**
     * Débloque une porte.
     */
    @PostMapping("/{gateId}/unblock")
    public ResponseEntity<Map<String, String>> unblockGate(@PathVariable String gateId) {
        gateService.unblockGate(gateId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Gate " + gateId + " unblocked",
                "gateId", gateId
        ));
    }
    
    /**
     * Crée une nouvelle porte.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createGate(
            @RequestParam String gateId,
            @RequestParam String name,
            @RequestParam GateType type) {
        
        gateService.createGate(gateId, name, type);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Gate " + gateId + " created",
                "gateId", gateId
        ));
    }
    
    /**
     * Supprime une porte.
     */
    @DeleteMapping("/{gateId}")
    public ResponseEntity<Map<String, String>> deleteGate(@PathVariable String gateId) {
        gateService.deleteGate(gateId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Gate " + gateId + " deleted",
                "gateId", gateId
        ));
    }
    
    /**
     * Liste toutes les portes.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listGates() {
        return ResponseEntity.ok(Map.of(
                "gates", gateService.getGates().keySet(),
                "count", gateService.getGates().size()
        ));
    }
    
    /**
     * Métriques du pool de scanners.
     */
    @GetMapping("/pool/metrics")
    public ResponseEntity<ActorPool.PoolMetrics> getPoolMetrics() {
        return ResponseEntity.ok(gateService.getScannerPoolMetrics());
    }
}
