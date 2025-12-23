package com.music.actor.runtime;

import com.music.actor.core.ActorRef;
import com.music.actor.core.Message;
import com.music.actor.logging.ActorLogger;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Référence vers un acteur distant sur un autre microservice.
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
    
    public RemoteActorRef(String id, String serviceName, String serviceUrl, 
                          WebClient webClient, ActorLogger logger) {
        this.id = id;
        this.serviceName = serviceName;
        this.serviceUrl = serviceUrl;
        this.webClient = webClient;
        this.logger = logger;
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
        
        webClient.post()
                .uri(serviceUrl + "/actors/" + id + "/tell")
                .bodyValue(new RemoteMessage(message.getClass().getName(), serializeMessage(message)))
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> logger.log(ActorLogger.LogLevel.ERROR, id,
                        "Remote tell failed: %s", e.getMessage()))
                .subscribe();
    }
    
    @Override
    public <T> CompletableFuture<T> ask(Message message, Duration timeout) {
        logger.logRemoteCommunication("local", serviceName, id, message.getClass().getSimpleName());
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        webClient.post()
                .uri(serviceUrl + "/actors/" + id + "/ask")
                .bodyValue(new RemoteMessage(message.getClass().getName(), serializeMessage(message)))
                .retrieve()
                .bodyToMono(RemoteResponse.class)
                .timeout(timeout)
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
                        future::completeExceptionally
                );
        
        return future;
    }
    
    @Override
    public boolean isLocal() {
        return false;
    }
    
    @Override
    public String remoteAddress() {
        return serviceUrl;
    }
    
    private String serializeMessage(Message message) {
        // Sérialisation simple en JSON (en production, utiliser Jackson)
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
            return mapper.readValue(response.result(), resultClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response", e);
        }
    }
    
    /**
     * Message envoyé à un acteur distant.
     */
    public record RemoteMessage(String messageType, String payload) {}
    
    /**
     * Réponse d'un acteur distant.
     */
    public record RemoteResponse(String resultType, String result, String error) {}
}
