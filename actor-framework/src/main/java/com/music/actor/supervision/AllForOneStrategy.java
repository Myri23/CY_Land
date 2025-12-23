package com.music.actor.supervision;

import com.music.actor.core.ActorRef;

import java.util.function.Function;

/**
 * Stratégie de supervision "All-For-One".
 * 
 * Quand un enfant échoue, TOUS les enfants sont affectés par la directive.
 * Utile quand les enfants sont interdépendants et doivent être
 * redémarrés ensemble pour maintenir un état cohérent.
 * 
 * Exemple : un groupe d'acteurs représentant une transaction distribuée.
 */
public class AllForOneStrategy implements SupervisorStrategy {
    
    private final int maxRestarts;
    private final long withinTimeRangeMs;
    private final Function<Throwable, SupervisorDirective> decider;
    
    /**
     * Crée une stratégie avec une fonction de décision personnalisée.
     */
    public AllForOneStrategy(int maxRestarts, long withinTimeRangeMs,
                              Function<Throwable, SupervisorDirective> decider) {
        this.maxRestarts = maxRestarts;
        this.withinTimeRangeMs = withinTimeRangeMs;
        this.decider = decider;
    }
    
    /**
     * Crée une stratégie avec les paramètres par défaut.
     */
    public AllForOneStrategy() {
        this(10, 60_000, OneForOneStrategy::defaultDecider);
    }
    
    /**
     * Crée une stratégie avec limites personnalisées.
     */
    public AllForOneStrategy(int maxRestarts, long withinTimeRangeMs) {
        this(maxRestarts, withinTimeRangeMs, OneForOneStrategy::defaultDecider);
    }
    
    @Override
    public SupervisorDirective decide(ActorRef child, Throwable cause, int failureCount) {
        if (failureCount >= maxRestarts) {
            System.err.printf("[SUPERVISOR] Actor group exceeded max restarts (%d), stopping all%n", 
                    maxRestarts);
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
        return true;
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
        
        public AllForOneStrategy build() {
            return new AllForOneStrategy(maxRestarts, withinTimeRangeMs, decider);
        }
    }
}
