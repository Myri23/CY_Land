package com.music.actor.core;

/**
 * Interface principale pour tous les acteurs du système.
 * Inspirée du modèle Akka, chaque acteur est une entité isolée qui :
 * - Possède son propre état interne (encapsulé)
 * - Communique uniquement par messages
 * - Traite un message à la fois (thread-safe)
 */
public interface Actor {
    
    /**
     * Méthode appelée pour chaque message reçu.
     * L'implémentation doit être thread-safe car un seul message est traité à la fois.
     * 
     * @param message Le message à traiter
     * @param context Le contexte de l'acteur (accès au runtime, self, etc.)
     */
    void onReceive(Message message, ActorContext context);
    
    /**
     * Callback appelé avant le démarrage de l'acteur.
     * Utilisé pour l'initialisation.
     */
    default void preStart(ActorContext context) {
        // Hook par défaut vide
    }
    
    /**
     * Callback appelé après l'arrêt de l'acteur.
     * Utilisé pour le nettoyage des ressources.
     */
    default void postStop(ActorContext context) {
        // Hook par défaut vide
    }
    
    /**
     * Callback appelé avant le redémarrage de l'acteur (après une erreur).
     * 
     * @param reason L'exception qui a causé le redémarrage
     * @param message Le message qui était en cours de traitement (peut être null)
     */
    default void preRestart(Throwable reason, Message message, ActorContext context) {
        postStop(context);
    }
    
    /**
     * Callback appelé après le redémarrage de l'acteur.
     * 
     * @param reason L'exception qui a causé le redémarrage
     */
    default void postRestart(Throwable reason, ActorContext context) {
        preStart(context);
    }
}
