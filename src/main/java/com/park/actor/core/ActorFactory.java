package com.park.actor.core;

/**
 * Factory fonctionnelle pour créer des acteurs.
 * Utilisée par le runtime pour instancier un acteur à la demande.
 */
@FunctionalInterface
public interface ActorFactory {
    
    /**
     * Crée une instance d'acteur.
     *
     * @param id l'identifiant assigné à l'acteur
     * @return l'instance créée
     */
    Actor create(String id);
}
