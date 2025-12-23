package com.music.actor.logging;

import java.time.Instant;

/**
 * Interface pour le système de logging des acteurs.
 * Permet de tracer toutes les activités des acteurs de manière structurée.
 */
public interface ActorLogger {
    
    /**
     * Log la création d'un acteur.
     */
    void logActorCreated(String actorId, String actorPath, String parentId);
    
    /**
     * Log l'arrêt d'un acteur.
     */
    void logActorStopped(String actorId, String reason);
    
    /**
     * Log la réception d'un message.
     */
    void logMessageReceived(String actorId, String messageType, String messageId);
    
    /**
     * Log le traitement d'un message.
     */
    void logMessageProcessed(String actorId, String messageType, String messageId, long durationMs);
    
    /**
     * Log l'envoi d'un message.
     */
    void logMessageSent(String fromActorId, String toActorId, String messageType, String messageId);
    
    /**
     * Log une erreur dans un acteur.
     */
    void logActorError(String actorId, String messageType, Throwable error);
    
    /**
     * Log le redémarrage d'un acteur.
     */
    void logActorRestarted(String actorId, int restartCount, String reason);
    
    /**
     * Log le blocage d'un acteur.
     */
    void logActorBlocked(String actorId);
    
    /**
     * Log le déblocage d'un acteur.
     */
    void logActorUnblocked(String actorId);
    
    /**
     * Log un changement d'état de l'acteur.
     */
    void logStateChange(String actorId, String fromState, String toState);
    
    /**
     * Log une communication inter-service.
     */
    void logRemoteCommunication(String fromService, String toService, String actorId, String messageType);
    
    /**
     * Log personnalisé pour les acteurs.
     */
    void log(LogLevel level, String actorId, String message, Object... args);
    
    /**
     * Niveaux de log supportés.
     */
    enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * Entrée de log structurée.
     */
    record LogEntry(
            Instant timestamp,
            LogLevel level,
            String actorId,
            String actorPath,
            String eventType,
            String message,
            String messageId,
            String messageType,
            Long durationMs,
            String errorClass,
            String errorMessage,
            String serviceName
    ) {}
}
