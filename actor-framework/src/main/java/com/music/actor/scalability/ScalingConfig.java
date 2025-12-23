package com.music.actor.scalability;

/**
 * Configuration pour l'auto-scaling d'un pool d'acteurs.
 */
public record ScalingConfig(
        /**
         * Nombre minimum d'acteurs (toujours maintenus).
         */
        int minInstances,
        
        /**
         * Nombre maximum d'acteurs (limite de scale-up).
         */
        int maxInstances,
        
        /**
         * Seuil de charge (%) pour déclencher un scale-up.
         * Ex: 80 = scale-up si utilisation > 80%
         */
        int scaleUpThresholdPercent,
        
        /**
         * Seuil de charge (%) pour déclencher un scale-down.
         * Ex: 20 = scale-down si utilisation < 20%
         */
        int scaleDownThresholdPercent,
        
        /**
         * Délai (ms) avant de prendre une décision de scaling.
         * Évite les oscillations dues à des pics temporaires.
         */
        long cooldownPeriodMs,
        
        /**
         * Intervalle (ms) entre les vérifications de charge.
         */
        long checkIntervalMs,
        
        /**
         * Nombre de messages en attente par acteur pour considérer comme surchargé.
         */
        int messagesPerActorThreshold
) {
    /**
     * Configuration par défaut : 1-10 acteurs, scale-up à 80%, scale-down à 20%.
     */
    public static ScalingConfig defaultConfig() {
        return new ScalingConfig(1, 10, 80, 20, 30_000, 5_000, 100);
    }
    
    /**
     * Configuration pour haute disponibilité : minimum 3 acteurs.
     */
    public static ScalingConfig highAvailability() {
        return new ScalingConfig(3, 20, 70, 30, 20_000, 3_000, 50);
    }
    
    /**
     * Configuration économique : 1 seul acteur minimum.
     */
    public static ScalingConfig economical() {
        return new ScalingConfig(1, 5, 90, 10, 60_000, 10_000, 200);
    }
    
    /**
     * Builder pour configuration personnalisée.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int minInstances = 1;
        private int maxInstances = 10;
        private int scaleUpThresholdPercent = 80;
        private int scaleDownThresholdPercent = 20;
        private long cooldownPeriodMs = 30_000;
        private long checkIntervalMs = 5_000;
        private int messagesPerActorThreshold = 100;
        
        public Builder minInstances(int min) {
            this.minInstances = min;
            return this;
        }
        
        public Builder maxInstances(int max) {
            this.maxInstances = max;
            return this;
        }
        
        public Builder scaleUpThreshold(int percent) {
            this.scaleUpThresholdPercent = percent;
            return this;
        }
        
        public Builder scaleDownThreshold(int percent) {
            this.scaleDownThresholdPercent = percent;
            return this;
        }
        
        public Builder cooldownPeriod(long ms) {
            this.cooldownPeriodMs = ms;
            return this;
        }
        
        public Builder checkInterval(long ms) {
            this.checkIntervalMs = ms;
            return this;
        }
        
        public Builder messagesPerActorThreshold(int threshold) {
            this.messagesPerActorThreshold = threshold;
            return this;
        }
        
        public ScalingConfig build() {
            return new ScalingConfig(
                    minInstances, maxInstances,
                    scaleUpThresholdPercent, scaleDownThresholdPercent,
                    cooldownPeriodMs, checkIntervalMs,
                    messagesPerActorThreshold
            );
        }
    }
}
