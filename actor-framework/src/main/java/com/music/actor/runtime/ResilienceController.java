package com.music.actor.runtime;

import com.music.actor.core.ActorRef;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API REST pour monitorer la résilience des communications inter-services.
 * 
 * Expose les métriques des Circuit Breakers pour chaque service distant.
 */
@RestController
@RequestMapping("/resilience")
public class ResilienceController {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ActorSystemImpl actorSystem;
    
    public ResilienceController(CircuitBreakerRegistry circuitBreakerRegistry,
                                 ActorSystemImpl actorSystem) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.actorSystem = actorSystem;
    }
    
    /**
     * Liste tous les circuit breakers et leur état.
     */
    @GetMapping("/circuit-breakers")
    public ResponseEntity<List<CircuitBreakerInfo>> getAllCircuitBreakers() {
        List<CircuitBreakerInfo> infos = circuitBreakerRegistry.getAllCircuitBreakers()
                .stream()
                .map(this::toCircuitBreakerInfo)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(infos);
    }
    
    /**
     * Détails d'un circuit breaker spécifique.
     */
    @GetMapping("/circuit-breakers/{name}")
    public ResponseEntity<CircuitBreakerInfo> getCircuitBreaker(@PathVariable String name) {
        return circuitBreakerRegistry.find(name)
                .map(this::toCircuitBreakerInfo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Résumé de la santé des communications inter-services.
     */
    @GetMapping("/health")
    public ResponseEntity<ResilienceHealthSummary> getResilienceHealth() {
        List<CircuitBreaker> allBreakers = circuitBreakerRegistry.getAllCircuitBreakers()
                .stream()
                .toList();
        
        long openCount = allBreakers.stream()
                .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
                .count();
        
        long halfOpenCount = allBreakers.stream()
                .filter(cb -> cb.getState() == CircuitBreaker.State.HALF_OPEN)
                .count();
        
        long closedCount = allBreakers.stream()
                .filter(cb -> cb.getState() == CircuitBreaker.State.CLOSED)
                .count();
        
        String overallStatus = openCount > 0 ? "DEGRADED" : 
                              halfOpenCount > 0 ? "RECOVERING" : "HEALTHY";
        
        return ResponseEntity.ok(new ResilienceHealthSummary(
                overallStatus,
                allBreakers.size(),
                (int) closedCount,
                (int) halfOpenCount,
                (int) openCount
        ));
    }
    
    /**
     * Métriques des acteurs distants.
     */
    @GetMapping("/remote-actors")
    public ResponseEntity<List<RemoteActorStatus>> getRemoteActorStatus() {
        List<RemoteActorStatus> statuses = actorSystem.getAllActors().stream()
                .filter(ref -> !ref.isLocal())
                .filter(ref -> ref instanceof RemoteActorRef)
                .map(ref -> (RemoteActorRef) ref)
                .map(remote -> new RemoteActorStatus(
                        remote.id(),
                        remote.remoteAddress(),
                        remote.getCircuitBreakerState().name(),
                        remote.getCircuitBreakerMetrics()
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(statuses);
    }
    
    private CircuitBreakerInfo toCircuitBreakerInfo(CircuitBreaker cb) {
        var metrics = cb.getMetrics();
        return new CircuitBreakerInfo(
                cb.getName(),
                cb.getState().name(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfNotPermittedCalls(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfBufferedCalls()
        );
    }
    
    // ==================== Records ====================
    
    public record CircuitBreakerInfo(
            String name,
            String state,
            int successfulCalls,
            int failedCalls,
            long notPermittedCalls,
            float failureRate,
            float slowCallRate,
            int bufferedCalls
    ) {}
    
    public record ResilienceHealthSummary(
            String status,
            int totalCircuitBreakers,
            int closedCount,
            int halfOpenCount,
            int openCount
    ) {}
    
    public record RemoteActorStatus(
            String actorId,
            String remoteAddress,
            String circuitBreakerState,
            RemoteActorRef.CircuitBreakerMetrics metrics
    ) {}
}
