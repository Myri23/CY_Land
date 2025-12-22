package com.park.actor.core;

/**
 * Interface principale du modèle d'acteurs.
 * Un acteur traite les messages de manière séquentielle dans sa propre mailbox.
 * 
 * <p>Principes clés :
 * <ul>
 *   <li>Un acteur traite UN message à la fois (pas de concurrence interne)</li>
 *   <li>La communication se fait uniquement par messages (tell, pas d'appels directs)</li>
 *   <li>Chaque acteur possède son propre état isolé</li>
 * </ul>
 */
public interface Actor {
    
    /**
     * Traite un message reçu.
     * Cette méthode est appelée séquentiellement pour chaque message de la mailbox.
     *
     * @param message le message à traiter
     * @param context le contexte d'exécution (accès à self, lookup d'autres acteurs, snapshot)
     */
    void onReceive(Message message, ActorContext context);
}
