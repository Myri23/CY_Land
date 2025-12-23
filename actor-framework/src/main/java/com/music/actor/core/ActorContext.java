package com.music.actor.core;

import java.util.List;
import java.util.Optional;

/**
 * Contexte d'exécution d'un acteur.
 * Fournit l'accès aux fonctionnalités du runtime depuis l'intérieur d'un acteur.
 */
public interface ActorContext {
    
    /**
     * Identifiant de l'acteur courant.
     */
    String id();
    
    /**
     * Chemin complet de l'acteur dans la hiérarchie.
     */
    String path();
    
    /**
     * Référence vers soi-même.
     */
    ActorRef self();
    
    /**
     * Référence vers l'expéditeur du message courant (si disponible).
     */
    Optional<ActorRef> sender();
    
    /**
     * Référence vers l'acteur parent (superviseur).
     */
    Optional<ActorRef> parent();
    
    /**
     * Liste des enfants de cet acteur.
     */
    List<ActorRef> children();
    
    /**
     * Recherche un acteur local par son identifiant.
     * 
     * @param actorId Identifiant de l'acteur
     * @return Référence vers l'acteur ou null si non trouvé
     */
    ActorRef lookup(String actorId);
    
    /**
     * Recherche un acteur distant sur un autre microservice.
     * Utilise Eureka pour la découverte de service.
     * 
     * @param serviceName Nom du microservice (enregistré dans Eureka)
     * @param actorId Identifiant de l'acteur sur ce service
     * @return Référence vers l'acteur distant
     */
    ActorRef lookupRemote(String serviceName, String actorId);
    
    /**
     * Crée un acteur enfant (supervisé par l'acteur courant).
     * 
     * @param id Identifiant du nouvel acteur
     * @param factory Fonction de création de l'acteur
     * @return Référence vers le nouvel acteur
     */
    ActorRef createChild(String id, ActorFactory factory);
    
    /**
     * Arrête un acteur enfant.
     * 
     * @param child Référence vers l'enfant à arrêter
     */
    void stopChild(ActorRef child);
    
    /**
     * Sauvegarde l'état de l'acteur (pour la récupération après crash).
     * 
     * @param state État à persister
     */
    void snapshot(Object state);
    
    /**
     * Restaure l'état de l'acteur depuis la dernière sauvegarde.
     * 
     * @param type Classe de l'état à restaurer
     * @return État restauré ou null si aucun snapshot
     */
    <T> T restore(Class<T> type);
    
    /**
     * Planifie l'envoi d'un message à soi-même après un délai.
     * 
     * @param message Le message à envoyer
     * @param delayMs Délai en millisecondes
     */
    void scheduleOnce(Message message, long delayMs);
    
    /**
     * Planifie l'envoi périodique d'un message à soi-même.
     * 
     * @param message Le message à envoyer
     * @param initialDelayMs Délai initial en millisecondes
     * @param periodMs Période en millisecondes
     * @return Identifiant du scheduler pour annulation
     */
    String schedulePeriodic(Message message, long initialDelayMs, long periodMs);
    
    /**
     * Annule un scheduler périodique.
     * 
     * @param schedulerId Identifiant retourné par schedulePeriodic
     */
    void cancelSchedule(String schedulerId);
    
    /**
     * Accès au système d'acteurs.
     */
    ActorSystem system();
}
