package com.music.actor.supervision;

import com.music.actor.core.ActorRef;

/**
 * Stratégie de supervision pour gérer les erreurs des acteurs enfants.
 * Inspirée du modèle de supervision hiérarchique d'Akka.
 */
public interface SupervisorStrategy {
    
    /**
     * Détermine quelle action prendre quand un enfant échoue.
     * 
     * @param child L'acteur enfant qui a échoué
     * @param cause L'exception qui a causé l'échec
     * @param failureCount Nombre de fois que cet enfant a échoué
     * @return Directive indiquant l'action à prendre
     */
    SupervisorDirective decide(ActorRef child, Throwable cause, int failureCount);
    
    /**
     * Nombre maximum de redémarrages autorisés dans la fenêtre de temps.
     */
    int maxRestarts();
    
    /**
     * Fenêtre de temps (en ms) pour compter les redémarrages.
     */
    long withinTimeRangeMs();
    
    /**
     * Indique si cette stratégie s'applique à tous les enfants (AllForOne)
     * ou seulement à l'enfant en erreur (OneForOne).
     */
    boolean isAllForOne();
}
