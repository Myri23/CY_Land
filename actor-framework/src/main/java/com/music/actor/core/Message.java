package com.music.actor.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Interface de base pour tous les messages échangés entre acteurs.
 * Les messages sont immutables et sérialisables pour la communication inter-microservices.
 */
public interface Message extends Serializable {
    
    /**
     * Identifiant unique du message pour le traçage.
     */
    default String messageId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Timestamp de création du message.
     */
    default Instant timestamp() {
        return Instant.now();
    }
}
