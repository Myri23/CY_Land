package com.park.gate.api;

import com.park.gate.persistence.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API REST d'administration pour les tickets.
 */
@RestController
@RequestMapping("/admin/tickets")
public class TicketAdminController {
    
    private final TicketRepository ticketRepository;
    
    public TicketAdminController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }
    
    /**
     * Liste tous les tickets.
     */
    @GetMapping
    public ResponseEntity<List<TicketEntity>> listTickets() {
        return ResponseEntity.ok(ticketRepository.findAll());
    }
    
    /**
     * Recupere un ticket par son ID.
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketEntity> getTicket(@PathVariable String ticketId) {
        return ticketRepository.findByTicketId(ticketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Cree un nouveau ticket.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createTicket(
            @RequestParam String ticketId,
            @RequestParam TicketType type,
            @RequestParam(required = false, defaultValue = "ADULT") TicketAgeCategory ageCategory,
            @RequestParam LocalDate dateValidity,
            @RequestParam Integer priceCents) {
        
        if (ticketRepository.existsByTicketId(ticketId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "Ticket " + ticketId + " already exists"));
        }
        
        TicketEntity ticket = new TicketEntity(null, ticketId, type, ageCategory, dateValidity, priceCents);
        ticketRepository.save(ticket);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Ticket created successfully",
                "ticketId", ticketId
        ));
    }
    
    /**
     * Supprime un ticket.
     */
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Map<String, String>> deleteTicket(@PathVariable String ticketId) {
        if (!ticketRepository.existsByTicketId(ticketId)) {
            return ResponseEntity.notFound().build();
        }
        
        ticketRepository.deleteByTicketId(ticketId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Ticket " + ticketId + " deleted successfully"
        ));
    }
    
    /**
     * Liste les tickets valides aujourd'hui.
     */
    @GetMapping("/valid-today")
    public ResponseEntity<List<TicketEntity>> listValidToday() {
        return ResponseEntity.ok(ticketRepository.findValidToday());
    }
    
    /**
     * Valide si un ticket peut acceder a une porte.
     */
    @GetMapping("/{ticketId}/validate")
    public ResponseEntity<Map<String, Object>> validateTicket(
            @PathVariable String ticketId,
            @RequestParam GateType gateType) {
        
        return ticketRepository.findByTicketId(ticketId)
                .map(ticket -> {
                    if (ticket.isUsed()) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "valid", false,
                                "reason", "Ticket already used at " + ticket.getUsedAtGate(),
                                "ticketType", ticket.getType().name()
                        ));
                    }
                    
                    if (!ticket.isValidToday()) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "valid", false,
                                "reason", "Ticket expired or not valid today",
                                "ticketType", ticket.getType().name()
                        ));
                    }
                    
                    if (!ticket.canAccessGate(gateType)) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "valid", false,
                                "reason", "Ticket type " + ticket.getType() + " cannot access " + gateType,
                                "ticketType", ticket.getType().name()
                        ));
                    }
                    
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "valid", true,
                            "reason", "",
                            "ticketType", ticket.getType().name()
                    ));
                })
                .orElse(ResponseEntity.ok(Map.of(
                        "valid", false,
                        "reason", "Ticket not found",
                        "ticketType", "UNKNOWN"
                )));
    }
}
