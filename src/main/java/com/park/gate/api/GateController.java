package com.park.gate.api;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gate")
@Validated
public class GateController {
    
    private final GateService gateService;
    
    public GateController(GateService gateService) {
        this.gateService = gateService;
    }
    
    @PostMapping("/{gateId}/scan")
    public ResponseEntity<ScanResponse> scan(
            @PathVariable @NotBlank String gateId,
            @RequestParam @NotBlank String ticketId) {
        gateService.handleScan(gateId, ticketId);
        return ResponseEntity.accepted().body(new ScanResponse("SCAN_ACCEPTED", gateId, ticketId));
    }
    
    @GetMapping("/{gateId}/status")
    public ResponseEntity<String> status(@PathVariable String gateId) {
        return ResponseEntity.ok("Gate " + gateId + " is operational");
    }
    
    public record ScanResponse(String status, String gateId, String ticketId) {}
}
