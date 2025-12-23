package com.music.actor.runtime;

import com.music.actor.core.ActorRef;
import com.music.actor.core.Message;
import com.music.actor.logging.ActorLogger;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Référence vers un acteur distant sur un autre microservice.
 * 
 * AMÉLIORATIONS v2.0 :
 * - Circuit Breaker : Évite les appels répétés à un service défaillant
 * - Retry avec backoff exponentiel : Réessaie automatiquement les appels échoués
 * - Timeout configurable : Évite les blocages sur les appels lents
 * - Métriques de santé : Expose l'état du circuit breaker
 * 
 * Utilise WebClient pour la communication HTTP asynchrone
 * et Eureka pour la découverte de services.
 */
public class RemoteActorRef implements ActorRef {
    
    private final String id;
    private final String serviceName;
    private final String serviceUrl;
    private final WebClient webClient;
    private final ActorLogger logger;
    
    // Resilience4j components
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    // Configuration
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    
    /**
     * Constructeur avec support Resilience4j.
     */
    public RemoteActorRef(String id, String serviceName, String serviceUrl, 
                          WebClient webClient, ActorLogger logger,
                          CircuitBreakerRegistry circuitBreakerRegistry,
                          RetryRegistry retryRegistry) {
        this.id = id;
        this.serviceName = serviceName;
        this.serviceUrl = serviceUrl;
        this.webClient = webClient;
        this.logger = logger;
        
        // Créer ou récupérer le circuit breaker pour ce service
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
        this.retry = retryRegistry.retry("remote-" + serviceName);
        
        // Enregistrer les événements du circuit breaker
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                    logger.log(ActorLogger.LogLevel.WARN, id,
                            "Circuit breaker [%s] state: %s -> %s",
                            serviceName, 
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()));
    }
    
    /**
     * Constructeur legacy (sans Resilience4j) - pour compatibilité.
     */
    public RemoteActorRef(String id, String serviceName, String serviceUrl, 
                          WebClient webClient, ActorLogger logger) {
        this.id = id;
        this.serviceName = serviceName;
        this.serviceUrl = serviceUrl;
        this.webClient = webClient;
        this.logger = logger;
        
        // Créer des instances par défaut
        this.circuitBreaker = CircuitBreaker.ofDefaults(serviceName);
        this.retry = Retry.ofDefaults("remote-" + serviceName);
    }
    
    @Override
    public String id() {
        return id;
    }
    
    @Override
    public String path() {
        return String.format("remote://%s/%s", serviceName, id);
    }
    
    @Override
    public void tell(Message message) {
        logger.logRemoteCommunication("local", serviceName, id, message.getClass().getSimpleName());
        
        // Vérifier l'état du circuit breaker avant d'envoyer
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            logger.log(ActorLogger.LogLevel.WARN, id,
                    "Circuit breaker OPEN for %s, message dropped: %s",
                    serviceName, message.getClass().getSimpleName());
            return;
        }
        
        Supplier<Mono<Void>> decoratedCall = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> executeRemoteTell(message)
        );
        
        // Exécuter avec retry
        try {
            Retry.decorateSupplier(retry, decoratedCall).get()
                    .subscribe(
                            v -> logger.log(ActorLogger.LogLevel.DEBUG, id,
                                    "Message sent successfully to %s", serviceName),
                            error -> handleError("tell", error)
                    );
        } catch (Exception e) {
            handleError("tell", e);
        }
    }
    
    private Mono<Void> executeRemoteTell(Message message) {
        return webClient.post()
                .uri(serviceUrl + "/actors/" + id + "/tell")
                .bodyValue(new RemoteMessage(message.getClass().getName(), serializeMessage(message)))
                .retrieve()
                .toBodilessEntity()
                .timeout(DEFAULT_TIMEOUT)
                .retryWhen(RetryBackoffSpec
                        .backoff(MAX_RETRY_ATTEMPTS, INITIAL_BACKOFF)
                        .filter(this::isRetryableException)
                        .doBeforeRetry(signal -> 
                            logger.log(ActorLogger.LogLevel.DEBUG, id,
                                    "Retrying tell to %s, attempt %d", 
                                    serviceName, signal.totalRetries() + 1)))
                .then();
    }
    
    @Override
    public <T> CompletableFuture<T> ask(Message message, Duration timeout) {
        logger.logRemoteCommunication("local", serviceName, id, message.getClass().getSimpleName());
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        // Vérifier l'état du circuit breaker
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            future.completeExceptionally(new CircuitBreakerOpenException(
                    "Circuit breaker is OPEN for service: " + serviceName));
            return future;
        }
        
        Supplier<Mono<RemoteResponse>> decoratedCall = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> executeRemoteAsk(message, timeout)
        );
        
        try {
            Retry.decorateSupplier(retry, decoratedCall).get()
                    .subscribe(
                            response -> {
                                try {
                                    @SuppressWarnings("unchecked")
                                    T result = (T) deserializeResponse(response);
                                    future.complete(result);
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            },
                            error -> {
                                handleError("ask", error);
                                future.completeExceptionally(error);
                            }
                    );
        } catch (Exception e) {
            handleError("ask", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    private Mono<RemoteResponse> executeRemoteAsk(Message message, Duration timeout) {
        return webClient.post()
                .uri(serviceUrl + "/actors/" + id + "/ask")
                .bodyValue(new RemoteMessage(message.getClass().getName(), serializeMessage(message)))
                .retrieve()
                .bodyToMono(RemoteResponse.class)
                .timeout(timeout)
                .retryWhen(RetryBackoffSpec
                        .backoff(MAX_RETRY_ATTEMPTS, INITIAL_BACKOFF)
                        .filter(this::isRetryableException)
                        .doBeforeRetry(signal -> 
                            logger.log(ActorLogger.LogLevel.DEBUG, id,
                                    "Retrying ask to %s, attempt %d", 
                                    serviceName, signal.totalRetries() + 1)));
    }
    
    /**
     * Détermine si une exception doit déclencher un retry.
     */
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof WebClientRequestException
                || throwable instanceof java.io.IOException
                || throwable instanceof java.util.concurrent.TimeoutException
                || (throwable.getCause() != null && isRetryableException(throwable.getCause()));
    }
    
    private void handleError(String operation, Throwable error) {
        logger.log(ActorLogger.LogLevel.ERROR, id,
                "Remote %s to %s failed: %s (Circuit state: %s)",
                operation, serviceName, error.getMessage(), circuitBreaker.getState());
    }
    
    @Override
    public boolean isLocal() {
        return false;
    }
    
    @Override
    public String remoteAddress() {
        return serviceUrl;
    }
    
    /**
     * Retourne l'état actuel du circuit breaker.
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }
    
    /**
     * Retourne les métriques du circuit breaker.
     */
    public CircuitBreakerMetrics getCircuitBreakerMetrics() {
        var metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerMetrics(
                serviceName,
                circuitBreaker.getState().name(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate()
        );
    }
    
    private String serializeMessage(Message message) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }
    
    private Object deserializeResponse(RemoteResponse response) {
        try {
            if (response.error() != null) {
                throw new RuntimeException(response.error());
            }
            
            Class<?> resultClass = Class.forName(response.resultType());
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper.readValue(response.result(), resultClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response", e);
        }
    }
    
    // ==================== Inner Records ====================
    
    /**
     * Message envoyé à un acteur distant.
     */
    public record RemoteMessage(String messageType, String payload) {}
    
    /**
     * Réponse d'un acteur distant.
     */
    public record RemoteResponse(String resultType, String result, String error) {}
    
    /**
     * Métriques du circuit breaker.
     */
    public record CircuitBreakerMetrics(
            String serviceName,
            String state,
            int successfulCalls,
            int failedCalls,
            float failureRate,
            float slowCallRate
    ) {}
    
    /**
     * Exception levée quand le circuit breaker est ouvert.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
