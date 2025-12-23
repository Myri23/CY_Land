package com.music.actor.core;

import com.music.actor.supervision.SupervisorStrategy;

import java.util.Collection;
import java.util.Optional;

/**
 * Système d'acteurs - Point d'entrée principal du framework.
 * Gère le cycle de vie de tous les acteurs et la communication inter-services.
 */
public interface ActorSystem {
    
    /**
     * Nom du système (généralement le nom du microservice).
     */
    String name();
    
    /**
     * Crée ou récupère un acteur de niveau racine.
     * 
     * @param id Identifiant de l'acteur
     * @param factory Factory pour créer l'acteur s'il n'existe pas
     * @return Référence vers l'acteur
     */
    ActorRef actorOf(String id, ActorFactory factory);
    
    /**
     * Crée ou récupère un acteur avec une stratégie de supervision personnalisée.
     * 
     * @param id Identifiant de l'acteur
     * @param factory Factory pour créer l'acteur
     * @param strategy Stratégie de supervision pour les enfants
     * @return Référence vers l'acteur
     */
    ActorRef actorOf(String id, ActorFactory factory, SupervisorStrategy strategy);
    
    /**
     * Recherche un acteur local par son identifiant.
     */
    Optional<ActorRef> findActor(String id);
    
    /**
     * Recherche un acteur par son chemin complet.
     */
    Optional<ActorRef> findActorByPath(String path);
    
    /**
     * Recherche un acteur sur un microservice distant.
     * 
     * @param serviceName Nom du service dans Eureka
     * @param actorId Identifiant de l'acteur
     * @return Référence vers l'acteur distant
     */
    ActorRef remoteActorOf(String serviceName, String actorId);
    
    /**
     * Arrête un acteur et tous ses enfants.
     */
    void stop(ActorRef actor);
    
    /**
     * Bloque un acteur (suspend le traitement des messages).
     */
    void block(String actorId);
    
    /**
     * Débloque un acteur précédemment bloqué.
     */
    void unblock(String actorId);
    
    /**
     * Vérifie si un acteur est bloqué.
     */
    boolean isBlocked(String actorId);
    
    /**
     * Retourne tous les acteurs enregistrés.
     */
    Collection<ActorRef> getAllActors();
    
    /**
     * Nombre total d'acteurs dans le système.
     */
    int actorCount();
    
    /**
     * Arrête proprement le système et tous les acteurs.
     */
    void shutdown();
    
    /**
     * Vérifie si le système est en cours d'arrêt.
     */
    boolean isTerminating();
}
