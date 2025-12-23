package com.music.actor.runtime;

import com.music.actor.core.*;
import com.music.actor.logging.ActorLogger;
import com.music.actor.supervision.OneForOneStrategy;
import com.music.actor.supervision.SupervisorStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.*;

/**
 * Implémentation complète du système d'acteurs.
 * 
 * AMÉLIORATIONS v2.0 :
 * - Intégration Resilience4j pour Circuit Breaker et Retry
 * - Métriques de santé des communications inter-services
 * - Gestion améliorée des erreurs de communication
 * 
 * Fonctionnalités :
 * - Gestion du cycle de vie des acteurs
 * - Communication locale et distante
 * - Découverte de services via Eureka
 * - Supervision hiérarchique
 * - Blocage/déblocage des acteurs
 * - Tolérance aux pannes avec Circuit Breaker
 */
@Component
public class ActorSystemImpl implements ActorSystem {
    
    private final String systemName;
    private final ConcurrentMap<String, LocalActorRef> actors;
    private final ExecutorService executor;
    private final ActorLogger logger;
    private final DiscoveryClient discoveryClient;
    private final WebClient.Builder webClientBuilder;
    private final SupervisorStrategy defaultStrategy;
    
    // Resilience4j registries
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    
    private final Set<String> blockedActors;
    private volatile boolean terminating = false;
    
    @Autowired
    public ActorSystemImpl(
            @Value("${spring.application.name:actor-system}") String systemName,
            ActorLogger logger,
            Optional<DiscoveryClient> discoveryClient,
            WebClient.Builder webClientBuilder,
            @Autowired(required = false) CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) RetryRegistry retryRegistry) {
        
        this.systemName = systemName;
        this.logger = logger;
        this.discoveryClient = discoveryClient.orElse(null);
        this.webClientBuilder = webClientBuilder;
        this.defaultStrategy = new OneForOneStrategy();
        
        // Utiliser les registries par défaut si non injectés
        this.circuitBreakerRegistry = circuitBreakerRegistry != null 
                ? circuitBreakerRegistry 
                : CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = retryRegistry != null 
                ? retryRegistry 
                : RetryRegistry.ofDefaults();
        
        this.actors = new ConcurrentHashMap<>();
        this.blockedActors = ConcurrentHashMap.newKeySet();
        
        // Virtual threads pour les acteurs (Java 21+)
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("actor-", 0).factory()
        );
        
        logger.log(ActorLogger.LogLevel.INFO, "system",
                "Actor system '%s' initialized with virtual threads and Resilience4j", systemName);
    }
    
    @Override
    public String name() {
        return systemName;
    }
    
    @Override
    public ActorRef actorOf(String id, ActorFactory factory) {
        return actorOf(id, factory, defaultStrategy);
    }
    
    @Override
    public ActorRef actorOf(String id, ActorFactory factory, SupervisorStrategy strategy) {
        return actors.computeIfAbsent(id, key -> {
            Actor actor = factory.create(key);
            LocalActorRef ref = new LocalActorRef(key, actor, this, null, strategy);
            ref.start();
            logger.logActorCreated(key, ref.path(), "root");
            return ref;
        });
    }
    
    @Override
    public Optional<ActorRef> findActor(String id) {
        return Optional.ofNullable(actors.get(id));
    }
    
    @Override
    public Optional<ActorRef> findActorByPath(String path) {
        return actors.values().stream()
                .filter(ref -> ref.path().equals(path))
                .map(ref -> (ActorRef) ref)
                .findFirst();
    }
    
    @Override
    public ActorRef remoteActorOf(String serviceName, String actorId) {
        if (discoveryClient == null) {
            throw new IllegalStateException("DiscoveryClient not available. Enable Eureka to use remote actors.");
        }
        
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("No instances found for service: " + serviceName);
        }
        
        // Round-robin simple
        ServiceInstance instance = instances.get(Math.abs(actorId.hashCode()) % instances.size());
        String serviceUrl = instance.getUri().toString();
        
        WebClient webClient = webClientBuilder.baseUrl(serviceUrl).build();
        
        logger.log(ActorLogger.LogLevel.DEBUG, actorId,
                "Creating remote actor ref to %s at %s (with Circuit Breaker)", serviceName, serviceUrl);
        
        // AMÉLIORATION : Utiliser le nouveau constructeur avec Resilience4j
        return new RemoteActorRef(
                actorId, 
                serviceName, 
                serviceUrl, 
                webClient, 
                logger,
                circuitBreakerRegistry,
                retryRegistry
        );
    }
    
    @Override
    public void stop(ActorRef actor) {
        if (actor instanceof LocalActorRef localRef) {
            actors.remove(actor.id());
            localRef.stop();
            logger.logActorStopped(actor.id(), "Explicit stop");
        }
    }
    
    @Override
    public void block(String actorId) {
        LocalActorRef actor = actors.get(actorId);
        if (actor != null) {
            actor.block();
            blockedActors.add(actorId);
            logger.logActorBlocked(actorId);
        }
    }
    
    @Override
    public void unblock(String actorId) {
        LocalActorRef actor = actors.get(actorId);
        if (actor != null) {
            actor.unblock();
            blockedActors.remove(actorId);
            logger.logActorUnblocked(actorId);
        }
    }
    
    @Override
    public boolean isBlocked(String actorId) {
        return blockedActors.contains(actorId);
    }
    
    @Override
    public Collection<ActorRef> getAllActors() {
        return new ArrayList<>(actors.values());
    }
    
    @Override
    public int actorCount() {
        return actors.size();
    }
    
    @Override
    @PreDestroy
    public void shutdown() {
        if (terminating) return;
        terminating = true;
        
        logger.log(ActorLogger.LogLevel.INFO, "system", "Shutting down actor system '%s'...", systemName);
        
        // Arrêter tous les acteurs
        actors.values().forEach(LocalActorRef::stop);
        actors.clear();
        
        // Arrêter l'executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.log(ActorLogger.LogLevel.INFO, "system", "Actor system '%s' stopped", systemName);
    }
    
    @Override
    public boolean isTerminating() {
        return terminating;
    }
    
    // ==================== Internal Methods ====================
    
    ExecutorService getExecutor() {
        return executor;
    }
    
    ActorLogger getLogger() {
        return logger;
    }
    
    void registerActor(LocalActorRef actor) {
        actors.put(actor.id(), actor);
    }
    
    /**
     * Retourne le registry des Circuit Breakers.
     */
    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }
    
    /**
     * Retourne le registry des Retry.
     */
    public RetryRegistry getRetryRegistry() {
        return retryRegistry;
    }
    
    /**
     * Retourne les métriques du système.
     */
    public SystemMetrics getMetrics() {
        int totalMailboxSize = actors.values().stream()
                .mapToInt(LocalActorRef::mailboxSize)
                .sum();
        
        long runningCount = actors.values().stream()
                .filter(a -> a.getState() == ActorState.RUNNING)
                .count();
        
        // Ajouter les métriques de résilience
        long openCircuitBreakers = circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN)
                .count();
        
        return new SystemMetrics(
                systemName,
                actors.size(),
                (int) runningCount,
                blockedActors.size(),
                totalMailboxSize,
                (int) openCircuitBreakers
        );
    }
    
    public record SystemMetrics(
            String systemName,
            int totalActors,
            int runningActors,
            int blockedActors,
            int totalPendingMessages,
            int openCircuitBreakers
    ) {}
}
