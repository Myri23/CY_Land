package com.park.gate.persistence;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository en mémoire pour les portes.
 * Simule une base de données pour simplifier le projet.
 */
@Repository
public class GateRepository {
    
    private final Map<String, GateEntity> gates = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    public GateRepository() {
        // Initialiser avec des portes par défaut
        save(new GateEntity(null, "G1", "Entree Principale", GateType.MAIN_GATE));
        save(new GateEntity(null, "G2", "Entree Secondaire", GateType.MAIN_GATE));
        save(new GateEntity(null, "VIP", "Entree VIP", GateType.VIP_GATE));
    }
    
    public List<GateEntity> findAll() {
        return new ArrayList<>(gates.values());
    }
    
    public Optional<GateEntity> findByGateId(String gateId) {
        return Optional.ofNullable(gates.get(gateId));
    }
    
    public boolean existsByGateId(String gateId) {
        return gates.containsKey(gateId);
    }
    
    public GateEntity save(GateEntity gate) {
        if (gate.getId() == null) {
            gate.setId(idGenerator.getAndIncrement());
        }
        gates.put(gate.getGateId(), gate);
        return gate;
    }
    
    public void deleteByGateId(String gateId) {
        gates.remove(gateId);
    }
    
    public long count() {
        return gates.size();
    }
}
