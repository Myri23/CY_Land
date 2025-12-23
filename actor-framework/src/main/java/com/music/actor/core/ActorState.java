package com.music.actor.core;

/**
 * États possibles d'un acteur dans son cycle de vie.
 */
public enum ActorState {
    
    /**
     * Acteur créé mais pas encore démarré.
     */
    CREATED,
    
    /**
     * Acteur en cours de démarrage (preStart en cours).
     */
    STARTING,
    
    /**
     * Acteur opérationnel, traite les messages.
     */
    RUNNING,
    
    /**
     * Acteur suspendu, les messages s'accumulent mais ne sont pas traités.
     */
    BLOCKED,
    
    /**
     * Acteur en cours de redémarrage après une erreur.
     */
    RESTARTING,
    
    /**
     * Acteur en cours d'arrêt.
     */
    STOPPING,
    
    /**
     * Acteur arrêté définitivement.
     */
    STOPPED,
    
    /**
     * Acteur en erreur fatale (ne peut plus être redémarré).
     */
    FAILED
}
