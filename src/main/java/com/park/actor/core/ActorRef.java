package com.park.actor.core;

/**
 * Référence vers un acteur.
 * Permet d'envoyer des messages sans connaître l'implémentation sous-jacente.
 * 
 * <p>Une ActorRef peut pointer vers :
 * <ul>
 *   <li>Un acteur local (même JVM)</li>
 *   <li>Un acteur distant (autre micro-service) - à implémenter</li>
 * </ul>
 */
public interface ActorRef {
    
    /**
     * Identifiant unique de l'acteur.
     */
    String id();
    
    /**
     * Envoie un message à l'acteur de manière asynchrone (fire-and-forget).
     * Le message est déposé dans la mailbox de l'acteur.
     *
     * @param message le message à envoyer
     */
    void tell(Message message);
}
