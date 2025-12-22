package com.park.gate.api;

import com.park.gate.persistence.GateEntity;
import com.park.gate.persistence.GateRepository;
import com.park.gate.persistence.GateType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API REST d'administration pour les portes.
 */
@RestController
@RequestMapping("/admin/gates")
public class GateAdminController {
    
    private final GateRepository gateRepository;
    
    public GateAdminController(GateRepository gateRepository) {
        this.gateRepository = gateRepository;
    }
    
    /**
     * Liste toutes les portes.
     */
    @GetMapping
    public ResponseEntity<List<GateEntity>> listGates() {
        return ResponseEntity.ok(gateRepository.findAll());
    }
    
    /**
     * Recupere une porte par son ID.
     */
    @GetMapping("/{gateId}")
    public ResponseEntity<GateEntity> getGate(@PathVariable String gateId) {
        return gateRepository.findByGateId(gateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Cree une nouvelle porte.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createGate(
            @RequestParam String gateId,
            @RequestParam String name,
            @RequestParam GateType type) {
        
        if (gateRepository.existsByGateId(gateId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "Gate " + gateId + " already exists"));
        }
        
        GateEntity gate = new GateEntity(null, gateId, name, type);
        gateRepository.save(gate);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Gate " + gateId + " created successfully"
        ));
    }
    
    /**
     * Modifie une porte existante.
     */
    @PutMapping("/{gateId}")
    public ResponseEntity<Map<String, String>> updateGate(
            @PathVariable String gateId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) GateType type) {
        
        return gateRepository.findByGateId(gateId)
                .map(gate -> {
                    if (name != null) {
                        gate.setName(name);
                    }
                    if (type != null) {
                        gate.setType(type);
                    }
                    gateRepository.save(gate);
                    return ResponseEntity.ok(Map.of(
                            "status", "SUCCESS",
                            "message", "Gate " + gateId + " updated successfully"
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Supprime une porte.
     */
    @DeleteMapping("/{gateId}")
    public ResponseEntity<Map<String, String>> deleteGate(@PathVariable String gateId) {
        if (!gateRepository.existsByGateId(gateId)) {
            return ResponseEntity.notFound().build();
        }
        
        gateRepository.deleteByGateId(gateId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Gate " + gateId + " deleted successfully"
        ));
    }
}
