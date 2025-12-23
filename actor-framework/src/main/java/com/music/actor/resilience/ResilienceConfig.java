package com.music.actor.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration Resilience4j pour la tolérance aux pannes inter-services.
 * 
 * Fonctionnalités :
 * - Circuit Breaker : Évite les appels répétés à un service défaillant
 * - Retry : Réessaie automatiquement les appels échoués
 */
@Configuration
public class ResilienceConfig {
    
    /**
     * Configuration du Circuit Breaker pour les appels inter-services.
     * 
     * - Ouvre le circuit après 5 échecs consécutifs
     * - Reste ouvert pendant 30 secondes avant de réessayer
     * - Passe en mode half-open pour tester la récupération
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Nombre d'appels minimum avant d'évaluer le taux d'échec
                .minimumNumberOfCalls(5)
                // Taux d'échec pour ouvrir le circuit (50%)
                .failureRateThreshold(50)
                // Durée en état ouvert avant de passer en half-open
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // Nombre d'appels permis en état half-open
                .permittedNumberOfCallsInHalfOpenState(3)
                // Fenêtre glissante basée sur le nombre d'appels
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                // Enregistrer les exceptions qui comptent comme échecs
                .recordExceptions(
                        java.io.IOException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.reactive.function.client.WebClientRequestException.class
                )
                // Ignorer certaines exceptions (ne comptent pas comme échecs)
                .ignoreExceptions(
                        IllegalArgumentException.class
                )
                .build();
        
        return CircuitBreakerRegistry.of(config);
    }
    
    /**
     * Configuration du Retry pour les appels inter-services.
     * 
     * - Maximum 3 tentatives
     * - Délai exponentiel entre les tentatives
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                // Nombre maximum de tentatives (incluant l'appel initial)
                .maxAttempts(3)
                // Délai initial entre les tentatives
                .waitDuration(Duration.ofMillis(500))
                // Backoff exponentiel : 500ms, 1s, 2s
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(Duration.ofMillis(500), 2.0))
                // Exceptions qui déclenchent un retry
                .retryExceptions(
                        java.io.IOException.class,
                        java.util.concurrent.TimeoutException.class,
                        org.springframework.web.reactive.function.client.WebClientRequestException.class
                )
                // Exceptions qui ne déclenchent pas de retry
                .ignoreExceptions(
                        IllegalArgumentException.class,
                        IllegalStateException.class
                )
                .build();
        
        return RetryRegistry.of(config);
    }
    
    /**
     * Circuit breaker dédié aux communications avec le Gate Service.
     */
    @Bean
    public CircuitBreaker gateServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("gate-service");
    }
    
    /**
     * Circuit breaker dédié aux communications avec le Ride Service.
     */
    @Bean
    public CircuitBreaker rideServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("ride-service");
    }
    
    /**
     * Retry dédié aux communications inter-services.
     */
    @Bean
    public Retry remoteActorRetry(RetryRegistry registry) {
        return registry.retry("remote-actor");
    }
}
