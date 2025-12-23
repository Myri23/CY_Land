package com.park.gate.persistence;

import java.time.Instant;

public class GateEntity {
    
    private Long id;
    private String gateId;
    private String name;
    private GateType type;
    private Instant createdAt;
    
    public GateEntity() {
        this.createdAt = Instant.now();
    }
    
    public GateEntity(Long id, String gateId, String name, GateType type) {
        this.id = id;
        this.gateId = gateId;
        this.name = name;
        this.type = type;
        this.createdAt = Instant.now();
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGateId() { return gateId; }
    public void setGateId(String gateId) { this.gateId = gateId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public GateType getType() { return type; }
    public void setType(GateType type) { this.type = type; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
