package com.music.actor.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Référence vers un acteur, permettant l'envoi de messages.
 * ActorRef est l'unique moyen de communiquer avec un acteur.
 * 
 * Inspiré d'Akka : on ne manipule jamais directement un acteur,
 * on passe toujours par sa référence.
 */
public interface ActorRef {
    
    /**
     * Identifiant unique de l'acteur.
     */
    String id();
    
    /**
     * Chemin complet de l'acteur dans la hiérarchie.
     * Format: /service/parent/actor-id
     */
    String path();
    
    /**
     * Envoi asynchrone d'un message (fire-and-forget).
     * Le pattern "tell" ne bloque pas et ne retourne aucun résultat.
     * 
     * @param message Le message à envoyer
     */
    void tell(Message message);
    
    /**
     * Envoi asynchrone avec réponse attendue (request-response).
     * Le pattern "ask" retourne une CompletableFuture pour récupérer la réponse.
     * 
     * @param message Le message à envoyer
     * @param timeout Durée maximale d'attente de la réponse
     * @param <T> Type de la réponse attendue
     * @return CompletableFuture contenant la réponse
     */
    <T> CompletableFuture<T> ask(Message message, Duration timeout);
    
    /**
     * Envoi asynchrone avec réponse attendue (timeout par défaut de 5 secondes).
     */
    default <T> CompletableFuture<T> ask(Message message) {
        return ask(message, Duration.ofSeconds(5));
    }
    
    /**
     * Indique si cet acteur est local ou distant (sur un autre microservice).
     */
    boolean isLocal();
    
    /**
     * Pour les acteurs distants, retourne l'URL du microservice.
     */
    default String remoteAddress() {
        return null;
    }
}
