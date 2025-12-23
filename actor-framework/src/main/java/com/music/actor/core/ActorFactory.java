package com.music.actor.core;

/**
 * Factory pour créer des instances d'acteurs.
 * Utilisée par le runtime pour instancier les acteurs de manière lazy.
 */
@FunctionalInterface
public interface ActorFactory {
    
    /**
     * Crée une nouvelle instance d'acteur.
     * 
     * @param id Identifiant assigné à l'acteur
     * @return Nouvelle instance de l'acteur
     */
    Actor create(String id);
}
