package com.park.gate.api;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
     * Scanne un ticket à une porte.
     * 
     * <p>Le traitement est asynchrone : la réponse est immédiate (202 Accepted),
     * et le traitement réel est effectué par l'acteur GateActor.
     *
     * @param gateId identifiant de la porte (path variable)
     * @param ticketId identifiant du ticket (query param)
     * @return 202 Accepted
     */
    @PostMapping("/{gateId}/scan")
    public ResponseEntity<ScanResponse> scan(
            @PathVariable @NotBlank String gateId,
            @RequestParam @NotBlank String ticketId) {
        
        gateService.handleScan(gateId, ticketId);
        
        return ResponseEntity
                .accepted()
                .body(new ScanResponse("SCAN_ACCEPTED", gateId, ticketId));
    }
    
    /**
     * Health check simple pour la porte.
     */
    @GetMapping("/{gateId}/status")
    public ResponseEntity<String> status(@PathVariable String gateId) {
        return ResponseEntity.ok("Gate " + gateId + " is operational");
    }
    
    // DTO de réponse
    public record ScanResponse(String status, String gateId, String ticketId) {}
}
