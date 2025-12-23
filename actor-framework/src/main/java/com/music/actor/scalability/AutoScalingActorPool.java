package com.music.actor.scalability;

import com.music.actor.core.ActorFactory;
import com.music.actor.core.ActorRef;
import com.music.actor.core.ActorSystem;
import com.music.actor.logging.ActorLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Pool d'acteurs avec auto-scaling basé sur la charge.
 * 
 * Fonctionnalités :
 * - Scale-up automatique quand la charge dépasse le seuil
 * - Scale-down automatique quand la charge est basse
 * - Distribution round-robin des messages
 * - Métriques de performance
 */
public class AutoScalingActorPool implements ActorPool {
    
    private final String poolName;
    private final ActorSystem actorSystem;
    private final ActorFactory actorFactory;
    private final ScalingConfig config;
    private final ActorLogger logger;
    
    private final List<ActorRef> actors;
    private final AtomicInteger roundRobinIndex;
    private final AtomicLong totalMessagesProcessed;
    private final AtomicLong lastScalingTime;
    
    private final ScheduledExecutorService scheduler;
    private final Function<ActorRef, Integer> pendingMessagesProvider;
    
    private volatile boolean running = true;
    
    public AutoScalingActorPool(
            String poolName,
            ActorSystem actorSystem,
            ActorFactory actorFactory,
            ScalingConfig config,
            ActorLogger logger,
            Function<ActorRef, Integer> pendingMessagesProvider) {
        
        this.poolName = poolName;
        this.actorSystem = actorSystem;
        this.actorFactory = actorFactory;
        this.config = config;
        this.logger = logger;
        this.pendingMessagesProvider = pendingMessagesProvider;
        
        this.actors = new CopyOnWriteArrayList<>();
        this.roundRobinIndex = new AtomicInteger(0);
        this.totalMessagesProcessed = new AtomicLong(0);
        this.lastScalingTime = new AtomicLong(System.currentTimeMillis());
        
        // Créer les acteurs initiaux
        for (int i = 0; i < config.minInstances(); i++) {
            createActor();
        }
        
        // Scheduler pour la vérification périodique de la charge
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "actor-pool-scaler-" + poolName);
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(
                this::checkAndScale,
                config.checkIntervalMs(),
                config.checkIntervalMs(),
                TimeUnit.MILLISECONDS
        );
        
        logger.log(ActorLogger.LogLevel.INFO, poolName,
                "Actor pool created with %d initial instances (min=%d, max=%d)",
                actors.size(), config.minInstances(), config.maxInstances());
    }
    
    @Override
    public String name() {
        return poolName;
    }
    
    @Override
    public ActorRef getActor() {
        if (actors.isEmpty()) {
            throw new IllegalStateException("No actors available in pool " + poolName);
        }
        
        // Distribution round-robin
        int index = Math.abs(roundRobinIndex.getAndIncrement() % actors.size());
        totalMessagesProcessed.incrementAndGet();
        return actors.get(index);
    }
    
    @Override
    public List<ActorRef> getAllActors() {
        return List.copyOf(actors);
    }
    
    @Override
    public int size() {
        return actors.size();
    }
    
    @Override
    public int minSize() {
        return config.minInstances();
    }
    
    @Override
    public int maxSize() {
        return config.maxInstances();
    }
    
    @Override
    public ActorRef scaleUp() {
        if (actors.size() >= config.maxInstances()) {
            logger.log(ActorLogger.LogLevel.WARN, poolName,
                    "Cannot scale up: already at max instances (%d)", config.maxInstances());
            return null;
        }
        
        ActorRef newActor = createActor();
        lastScalingTime.set(System.currentTimeMillis());
        
        logger.log(ActorLogger.LogLevel.INFO, poolName,
                "Scaled up: %d -> %d instances", actors.size() - 1, actors.size());
        
        return newActor;
    }
    
    @Override
    public void scaleDown() {
        if (actors.size() <= config.minInstances()) {
            logger.log(ActorLogger.LogLevel.WARN, poolName,
                    "Cannot scale down: already at min instances (%d)", config.minInstances());
            return;
        }
        
        // Retirer le dernier acteur
        ActorRef toRemove = actors.remove(actors.size() - 1);
        actorSystem.stop(toRemove);
        lastScalingTime.set(System.currentTimeMillis());
        
        logger.log(ActorLogger.LogLevel.INFO, poolName,
                "Scaled down: %d -> %d instances", actors.size() + 1, actors.size());
    }
    
    @Override
    public PoolMetrics getMetrics() {
        int totalPending = actors.stream()
                .mapToInt(pendingMessagesProvider::apply)
                .sum();
        
        double utilization = actors.isEmpty() ? 0 :
                (double) totalPending / (actors.size() * config.messagesPerActorThreshold()) * 100;
        
        return new PoolMetrics(
                poolName,
                actors.size(),
                config.minInstances(),
                config.maxInstances(),
                totalMessagesProcessed.get(),
                0, // TODO: calculer le temps moyen
                totalPending,
                Math.min(100, utilization)
        );
    }
    
    private ActorRef createActor() {
        String actorId = String.format("%s-worker-%d", poolName, actors.size());
        ActorRef actor = actorSystem.actorOf(actorId, actorFactory);
        actors.add(actor);
        return actor;
    }
    
    private void checkAndScale() {
        if (!running) return;
        
        // Vérifier le cooldown
        long timeSinceLastScaling = System.currentTimeMillis() - lastScalingTime.get();
        if (timeSinceLastScaling < config.cooldownPeriodMs()) {
            return;
        }
        
        PoolMetrics metrics = getMetrics();
        double utilization = metrics.utilizationPercent();
        
        if (utilization > config.scaleUpThresholdPercent() && actors.size() < config.maxInstances()) {
            logger.log(ActorLogger.LogLevel.INFO, poolName,
                    "High load detected (%.1f%%), scaling up", utilization);
            scaleUp();
        } else if (utilization < config.scaleDownThresholdPercent() && actors.size() > config.minInstances()) {
            logger.log(ActorLogger.LogLevel.INFO, poolName,
                    "Low load detected (%.1f%%), scaling down", utilization);
            scaleDown();
        }
    }
    
    public void shutdown() {
        running = false;
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        actors.forEach(actorSystem::stop);
        actors.clear();
        
        logger.log(ActorLogger.LogLevel.INFO, poolName, "Actor pool shut down");
    }
}
