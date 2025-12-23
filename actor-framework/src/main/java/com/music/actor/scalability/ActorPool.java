package com.music.actor.scalability;

import com.music.actor.core.ActorFactory;
import com.music.actor.core.ActorRef;

import java.util.List;

/**
 * Interface pour la gestion de la scalabilité des acteurs.
 * Permet de créer/supprimer dynamiquement des instances selon la charge.
 */
public interface ActorPool {
    
    /**
     * Nom du pool d'acteurs.
     */
    String name();
    
    /**
     * Récupère un acteur du pool (round-robin ou selon la charge).
     */
    ActorRef getActor();
    
    /**
     * Retourne tous les acteurs du pool.
     */
    List<ActorRef> getAllActors();
    
    /**
     * Nombre actuel d'acteurs dans le pool.
     */
    int size();
    
    /**
     * Nombre minimum d'acteurs.
     */
    int minSize();
    
    /**
     * Nombre maximum d'acteurs.
     */
    int maxSize();
    
    /**
     * Ajoute un acteur au pool.
     */
    ActorRef scaleUp();
    
    /**
     * Retire un acteur du pool.
     */
    void scaleDown();
    
    /**
     * Métriques du pool.
     */
    PoolMetrics getMetrics();
    
    /**
     * Métriques de charge du pool.
     */
    record PoolMetrics(
            String poolName,
            int currentSize,
            int minSize,
            int maxSize,
            long totalMessagesProcessed,
            double averageProcessingTimeMs,
            int pendingMessages,
            double utilizationPercent
    ) {}
}
