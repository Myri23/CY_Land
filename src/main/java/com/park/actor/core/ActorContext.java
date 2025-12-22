package com.park.actor.core;

/**
 * Contexte fourni à un acteur lors du traitement d'un message.
 * Permet à l'acteur d'interagir avec le système d'acteurs.
 */
public interface ActorContext {
    
    /**
     * Retourne l'identifiant de cet acteur.
     */
    String id();
    
    /**
     * Retourne une référence vers soi-même.
     * Utile pour s'envoyer des messages ou se passer en paramètre.
     */
    ActorRef self();
    
    /**
     * Recherche un acteur par son identifiant.
     *
     * @param actorId l'identifiant de l'acteur recherché
     * @return la référence vers l'acteur, ou null si non trouvé
     */
    ActorRef lookup(String actorId);
    
    /**
     * Sauvegarde l'état de l'acteur (snapshot).
     * Permet la récupération après redémarrage.
     *
     * @param state l'état à persister
     */
    void snapshot(Object state);
    
    /**
     * Restaure l'état précédemment sauvegardé.
     *
     * @param type le type attendu de l'état
     * @return l'état restauré, ou null si aucun snapshot
     */
    <T> T restore(Class<T> type);
}
