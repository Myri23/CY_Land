package com.music.gate.api;

import com.music.actor.core.ActorRef;
import com.music.actor.core.ActorSystem;
import com.music.actor.logging.ActorLogger;
import com.music.actor.scalability.AutoScalingActorPool;
import com.music.actor.scalability.ScalingConfig;
import com.music.actor.supervision.OneForOneStrategy;
import com.music.gate.domain.GateActor;
import com.music.gate.domain.GateMessages;
import com.music.gate.domain.GateType;
import com.music.gate.messaging.GateEventPublisher;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des portes avec auto-scaling.
 */
@Service
public class GateService {
    
    private final ActorSystem actorSystem;
    private final GateEventPublisher eventPublisher;
    private final ActorLogger logger;
    
    private final Map<String, ActorRef> gateActors = new ConcurrentHashMap<>();
    private AutoScalingActorPool scannerPool;
    
    public GateService(ActorSystem actorSystem, GateEventPublisher eventPublisher, ActorLogger logger) {
        this.actorSystem = actorSystem;
        this.eventPublisher = eventPublisher;
        this.logger = logger;
    }
    
    @PostConstruct
    public void initialize() {
        // Créer les portes par défaut avec supervision
        createGate("G1", "Entrée Principale", GateType.MAIN_GATE);
        createGate("G2", "Entrée Secondaire", GateType.MAIN_GATE);
        createGate("VIP", "Entrée VIP", GateType.VIP_GATE);
        
        // Créer un pool auto-scalable pour les scanners de tickets
        ScalingConfig config = ScalingConfig.builder()
                .minInstances(2)
                .maxInstances(10)
                .scaleUpThreshold(70)
                .scaleDownThreshold(20)
                .cooldownPeriod(30_000)
                .build();
        
        scannerPool = new AutoScalingActorPool(
                "ticket-scanners",
                actorSystem,
                id -> new TicketScannerActor(id, eventPublisher),
                config,
                logger,
                ref -> 0 // TODO: implémenter le comptage des messages en attente
        );
        
        printStartupBanner();
    }
    
    /**
     * Crée une nouvelle porte.
     */
    public ActorRef createGate(String gateId, String name, GateType type) {
        var strategy = OneForOneStrategy.builder()
                .maxRestarts(5)
                .withinTimeRange(60_000)
                .build();
        
        ActorRef gateRef = actorSystem.actorOf(gateId, 
                id -> new GateActor(id, name, type, eventPublisher),
                strategy);
        
        gateActors.put(gateId, gateRef);
        return gateRef;
    }
    
    /**
     * Scan un ticket à une porte (asynchrone).
     */
    public void handleScan(String gateId, String ticketId) {
        ActorRef gate = gateActors.get(gateId);
        if (gate != null) {
            gate.tell(new GateMessages.ScanTicket(ticketId, gateId));
        } else {
            // Utiliser le pool de scanners pour les portes dynamiques
            ActorRef scanner = scannerPool.getActor();
            scanner.tell(new GateMessages.ScanTicket(ticketId, gateId));
        }
    }
    
    /**
     * Scan un ticket avec attente de réponse (ask pattern).
     */
    public CompletableFuture<GateActor.ScanResult> handleScanSync(String gateId, String ticketId) {
        ActorRef gate = gateActors.get(gateId);
        if (gate == null) {
            return CompletableFuture.completedFuture(
                    new GateActor.ScanResult(false, "Gate not found", ticketId, gateId));
        }
        
        return gate.ask(new GateMessages.ScanTicket(ticketId, gateId), Duration.ofSeconds(5));
    }
    
    /**
     * Récupère le statut d'une porte (ask pattern).
     */
    public CompletableFuture<GateMessages.GateStatus> getGateStatus(String gateId) {
        ActorRef gate = gateActors.get(gateId);
        if (gate == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Gate not found: " + gateId));
        }
        
        return gate.ask(new GateMessages.GetStatus(gateId), Duration.ofSeconds(5));
    }
    
    /**
     * Bloque une porte.
     */
    public void blockGate(String gateId) {
        actorSystem.block(gateId);
    }
    
    /**
     * Débloque une porte.
     */
    public void unblockGate(String gateId) {
        actorSystem.unblock(gateId);
    }
    
    /**
     * Supprime une porte.
     */
    public void deleteGate(String gateId) {
        ActorRef gate = gateActors.remove(gateId);
        if (gate != null) {
            actorSystem.stop(gate);
        }
    }
    
    /**
     * Récupère les métriques du pool de scanners.
     */
    public AutoScalingActorPool.PoolMetrics getScannerPoolMetrics() {
        return scannerPool.getMetrics();
    }
    
    public Map<String, ActorRef> getGates() {
        return Map.copyOf(gateActors);
    }
    
    private void printStartupBanner() {
        System.out.println("===========================================");
        System.out.println("  GATE SERVICE READY");
        System.out.println("  Gates: " + gateActors.size());
        System.out.println("  Scanner Pool: " + scannerPool.size() + " workers");
        System.out.println();
        System.out.println("  ENDPOINTS:");
        System.out.println("  - POST /gate/{gateId}/scan?ticketId=XXX");
        System.out.println("  - POST /gate/{gateId}/scan-sync?ticketId=XXX");
        System.out.println("  - GET  /gate/{gateId}/status");
        System.out.println("  - POST /gate/{gateId}/block");
        System.out.println("  - POST /gate/{gateId}/unblock");
        System.out.println("  - GET  /actors (actor management)");
        System.out.println("===========================================");
    }
    
    /**
     * Acteur de scanning de tickets pour le pool.
     */
    private static class TicketScannerActor extends GateActor {
        public TicketScannerActor(String id, GateEventPublisher publisher) {
            super(id, "Scanner-" + id, GateType.MAIN_GATE, publisher);
        }
    }
}
