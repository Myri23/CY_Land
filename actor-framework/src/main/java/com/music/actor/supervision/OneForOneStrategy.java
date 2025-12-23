package com.music.actor.supervision;

import com.music.actor.core.ActorRef;

import java.util.function.Function;

/**
 * Stratégie de supervision "One-For-One".
 * 
 * Quand un enfant échoue, seul cet enfant est affecté par la directive.
 * Les autres enfants continuent normalement.
 * 
 * C'est la stratégie par défaut et la plus courante.
 */
public class OneForOneStrategy implements SupervisorStrategy {
    
    private final int maxRestarts;
    private final long withinTimeRangeMs;
    private final Function<Throwable, SupervisorDirective> decider;
    
    /**
     * Crée une stratégie avec une fonction de décision personnalisée.
     * 
     * @param maxRestarts Nombre max de redémarrages autorisés
     * @param withinTimeRangeMs Fenêtre de temps pour le comptage
     * @param decider Fonction qui décide de l'action selon l'exception
     */
    public OneForOneStrategy(int maxRestarts, long withinTimeRangeMs, 
                              Function<Throwable, SupervisorDirective> decider) {
        this.maxRestarts = maxRestarts;
        this.withinTimeRangeMs = withinTimeRangeMs;
        this.decider = decider;
    }
    
    /**
     * Crée une stratégie avec les paramètres par défaut.
     * Par défaut : 10 redémarrages max en 60 secondes, RESTART pour toute exception.
     */
    public OneForOneStrategy() {
        this(10, 60_000, cause -> SupervisorDirective.RESTART);
    }
    
    /**
     * Crée une stratégie avec limites personnalisées mais décision par défaut.
     */
    public OneForOneStrategy(int maxRestarts, long withinTimeRangeMs) {
        this(maxRestarts, withinTimeRangeMs, OneForOneStrategy::defaultDecider);
    }
    
    @Override
    public SupervisorDirective decide(ActorRef child, Throwable cause, int failureCount) {
        // Si trop de redémarrages, arrêter l'acteur
        if (failureCount >= maxRestarts) {
            System.err.printf("[SUPERVISOR] Actor %s exceeded max restarts (%d), stopping%n", 
                    child.id(), maxRestarts);
            return SupervisorDirective.STOP;
        }
        
        return decider.apply(cause);
    }
    
    @Override
    public int maxRestarts() {
        return maxRestarts;
    }
    
    @Override
    public long withinTimeRangeMs() {
        return withinTimeRangeMs;
    }
    
    @Override
    public boolean isAllForOne() {
        return false;
    }
    
    /**
     * Décideur par défaut basé sur le type d'exception.
     */
    public static SupervisorDirective defaultDecider(Throwable cause) {
        if (cause instanceof IllegalArgumentException) {
            // Erreur de validation : reprendre sans redémarrer
            return SupervisorDirective.RESUME;
        } else if (cause instanceof IllegalStateException) {
            // État invalide : redémarrer pour réinitialiser
            return SupervisorDirective.RESTART;
        } else if (cause instanceof NullPointerException) {
            // Bug de programmation : redémarrer
            return SupervisorDirective.RESTART;
        } else if (cause instanceof OutOfMemoryError) {
            // Erreur fatale : escalader
            return SupervisorDirective.ESCALATE;
        } else if (cause instanceof Error) {
            // Erreurs JVM : escalader
            return SupervisorDirective.ESCALATE;
        }
        // Par défaut : redémarrer
        return SupervisorDirective.RESTART;
    }
    
    /**
     * Builder pour créer une stratégie personnalisée.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int maxRestarts = 10;
        private long withinTimeRangeMs = 60_000;
        private Function<Throwable, SupervisorDirective> decider = OneForOneStrategy::defaultDecider;
        
        public Builder maxRestarts(int max) {
            this.maxRestarts = max;
            return this;
        }
        
        public Builder withinTimeRange(long ms) {
            this.withinTimeRangeMs = ms;
            return this;
        }
        
        public Builder decider(Function<Throwable, SupervisorDirective> decider) {
            this.decider = decider;
            return this;
        }
        
        public OneForOneStrategy build() {
            return new OneForOneStrategy(maxRestarts, withinTimeRangeMs, decider);
        }
    }
}
