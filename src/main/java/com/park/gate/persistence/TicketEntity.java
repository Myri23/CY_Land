package com.park.gate.persistence;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Entité représentant un ticket.
 * Stockée en mémoire (pas de JPA pour simplifier).
 */
public class TicketEntity {
    
    private Long id;
    private String ticketId;
    private TicketType type;
    private TicketAgeCategory ageCategory;
    private LocalDate dateValidity;
    private Integer priceCents;
    private boolean used;
    private String usedAtGate;
    private Instant usedAt;
    private Instant createdAt;
    
    public TicketEntity() {
        this.createdAt = Instant.now();
        this.used = false;
    }
    
    public TicketEntity(Long id, String ticketId, TicketType type, TicketAgeCategory ageCategory, 
                        LocalDate dateValidity, Integer priceCents) {
        this.id = id;
        this.ticketId = ticketId;
        this.type = type;
        this.ageCategory = ageCategory;
        this.dateValidity = dateValidity;
        this.priceCents = priceCents;
        this.used = false;
        this.createdAt = Instant.now();
    }
    
    /**
     * Vérifie si le ticket peut accéder à un type de porte donné.
     */
    public boolean canAccessGate(GateType gateType) {
        if (type == TicketType.VIP_TICKET) {
            return true; // VIP peut accéder à tout
        }
        // CLASSIC_TICKET ne peut accéder qu'aux MAIN_GATE
        return gateType == GateType.MAIN_GATE;
    }
    
    /**
     * Vérifie si le ticket est valide aujourd'hui.
     */
    public boolean isValidToday() {
        return dateValidity != null && !dateValidity.isBefore(LocalDate.now());
    }
    
    /**
     * Marque le ticket comme utilisé.
     */
    public void markAsUsed(String gateId) {
        this.used = true;
        this.usedAtGate = gateId;
        this.usedAt = Instant.now();
    }
    
    // Getters et Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
    
    public TicketType getType() {
        return type;
    }
    
    public void setType(TicketType type) {
        this.type = type;
    }
    
    public TicketAgeCategory getAgeCategory() {
        return ageCategory;
    }
    
    public void setAgeCategory(TicketAgeCategory ageCategory) {
        this.ageCategory = ageCategory;
    }
    
    public LocalDate getDateValidity() {
        return dateValidity;
    }
    
    public void setDateValidity(LocalDate dateValidity) {
        this.dateValidity = dateValidity;
    }
    
    public Integer getPriceCents() {
        return priceCents;
    }
    
    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }
    
    public boolean isUsed() {
        return used;
    }
    
    public void setUsed(boolean used) {
        this.used = used;
    }
    
    public String getUsedAtGate() {
        return usedAtGate;
    }
    
    public void setUsedAtGate(String usedAtGate) {
        this.usedAtGate = usedAtGate;
    }
    
    public Instant getUsedAt() {
        return usedAt;
    }
    
    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
