package com.music.actor.supervision;

/**
 * Directives de supervision inspirées d'Akka.
 * Définit ce que le superviseur doit faire quand un enfant échoue.
 */
public enum SupervisorDirective {
    
    /**
     * Reprend l'acteur enfant avec son état actuel.
     * Le message ayant causé l'erreur est perdu.
     */
    RESUME,
    
    /**
     * Redémarre l'acteur enfant (appelle preRestart/postRestart).
     * L'état est réinitialisé, la mailbox est conservée.
     */
    RESTART,
    
    /**
     * Arrête définitivement l'acteur enfant.
     */
    STOP,
    
    /**
     * Escalade l'erreur au superviseur parent.
     * Utilisé quand le superviseur courant ne sait pas gérer l'erreur.
     */
    ESCALATE
}
