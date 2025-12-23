package com.music.actor.runtime;

import com.music.actor.core.*;
import com.music.actor.logging.ActorLogger;
import com.music.actor.supervision.SupervisorDirective;
import com.music.actor.supervision.SupervisorStrategy;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implémentation locale d'une référence d'acteur.
 * 
 * Chaque acteur possède :
 * - Sa propre mailbox (BlockingQueue)
 * - Son propre thread de traitement (Virtual Thread)
 * - Un mécanisme d'ask pour les requêtes synchrones
 */
public class LocalActorRef implements ActorRef, ActorContext {
    
    private final String id;
    private final String path;
    private final Actor actor;
    private final BlockingQueue<Envelope> mailbox;
    private final ActorSystemImpl system;
    private final ActorLogger logger;
    private final SupervisorStrategy supervisorStrategy;
    
    // Hiérarchie
    private final LocalActorRef parent;
    private final Map<String, LocalActorRef> children;
    
    // État
    private volatile ActorState state;
    private volatile boolean blocked;
    private final Map<String, Object> snapshots;
    
    // Ask pattern
    private final Map<String, CompletableFuture<Object>> pendingAsks;
    
    // Supervision
    private final Map<String, Integer> failureCounts;
    private final Map<String, Long> failureTimestamps;
    
    // Sender courant
    private volatile ActorRef currentSender;
    
    // Schedulers
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private final ScheduledExecutorService scheduler;
    
    public LocalActorRef(String id, Actor actor, ActorSystemImpl system, 
                         LocalActorRef parent, SupervisorStrategy strategy) {
        this.id = id;
        this.path = (parent != null ? parent.path() : "") + "/" + id;
        this.actor = actor;
        this.system = system;
        this.logger = system.getLogger();
        this.parent = parent;
        this.supervisorStrategy = strategy;
        
        this.mailbox = new LinkedBlockingQueue<>();
        this.children = new ConcurrentHashMap<>();
        this.state = ActorState.CREATED;
        this.blocked = false;
        this.snapshots = new ConcurrentHashMap<>();
        this.pendingAsks = new ConcurrentHashMap<>();
        this.failureCounts = new ConcurrentHashMap<>();
        this.failureTimestamps = new ConcurrentHashMap<>();
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "actor-scheduler-" + id);
            t.setDaemon(true);
            return t;
        });
    }
    
    // ==================== ActorRef ====================
    
    @Override
    public String id() {
        return id;
    }
    
    @Override
    public String path() {
        return path;
    }
    
    @Override
    public void tell(Message message) {
        tell(message, null);
    }
    
    public void tell(Message message, ActorRef sender) {
        if (state == ActorState.STOPPED || state == ActorState.FAILED) {
            logger.log(ActorLogger.LogLevel.WARN, id,
                    "Message dropped: actor is %s", state);
            return;
        }
        
        mailbox.offer(new Envelope(message, sender));
        
        if (sender != null) {
            logger.logMessageSent(sender.id(), id, message.getClass().getSimpleName(), message.messageId());
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> ask(Message message, Duration timeout) {
        String askId = UUID.randomUUID().toString();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingAsks.put(askId, future);
        
        // Enveloppe spéciale pour l'ask
        AskMessage askMessage = new AskMessage(message, askId, this);
        mailbox.offer(new Envelope(askMessage, this));
        
        // Timeout
        scheduler.schedule(() -> {
            CompletableFuture<Object> pending = pendingAsks.remove(askId);
            if (pending != null && !pending.isDone()) {
                pending.completeExceptionally(
                        new TimeoutException("Ask timed out after " + timeout.toMillis() + "ms"));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        
        return (CompletableFuture<T>) future;
    }
    
    @Override
    public boolean isLocal() {
        return true;
    }
    
    // ==================== ActorContext ====================
    
    @Override
    public ActorRef self() {
        return this;
    }
    
    @Override
    public Optional<ActorRef> sender() {
        return Optional.ofNullable(currentSender);
    }
    
    @Override
    public Optional<ActorRef> parent() {
        return Optional.ofNullable(parent);
    }
    
    @Override
    public List<ActorRef> children() {
        return new ArrayList<>(children.values());
    }
    
    @Override
    public ActorRef lookup(String actorId) {
        return system.findActor(actorId).orElse(null);
    }
    
    @Override
    public ActorRef lookupRemote(String serviceName, String actorId) {
        return system.remoteActorOf(serviceName, actorId);
    }
    
    @Override
    public ActorRef createChild(String childId, ActorFactory factory) {
        String fullId = id + "-" + childId;
        Actor childActor = factory.create(fullId);
        LocalActorRef childRef = new LocalActorRef(fullId, childActor, system, this, supervisorStrategy);
        children.put(childId, childRef);
        system.registerActor(childRef);
        childRef.start();
        
        logger.logActorCreated(fullId, childRef.path(), id);
        return childRef;
    }
    
    @Override
    public void stopChild(ActorRef child) {
        if (child instanceof LocalActorRef localChild) {
            children.remove(localChild.id().replace(id + "-", ""));
            localChild.stop();
        }
    }
    
    @Override
    public void snapshot(Object state) {
        snapshots.put("current", state);
        logger.log(ActorLogger.LogLevel.DEBUG, id, "Snapshot saved: %s", state.getClass().getSimpleName());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T restore(Class<T> type) {
        Object restored = snapshots.get("current");
        if (restored != null && type.isInstance(restored)) {
            logger.log(ActorLogger.LogLevel.DEBUG, id, "State restored: %s", type.getSimpleName());
            return (T) restored;
        }
        return null;
    }
    
    @Override
    public void scheduleOnce(Message message, long delayMs) {
        scheduler.schedule(() -> tell(message), delayMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public String schedulePeriodic(Message message, long initialDelayMs, long periodMs) {
        String schedulerId = UUID.randomUUID().toString();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> tell(message),
                initialDelayMs, periodMs, TimeUnit.MILLISECONDS
        );
        scheduledTasks.put(schedulerId, future);
        return schedulerId;
    }
    
    @Override
    public void cancelSchedule(String schedulerId) {
        ScheduledFuture<?> future = scheduledTasks.remove(schedulerId);
        if (future != null) {
            future.cancel(false);
        }
    }
    
    @Override
    public ActorSystem system() {
        return system;
    }
    
    // ==================== Lifecycle ====================
    
    public void start() {
        state = ActorState.STARTING;
        try {
            actor.preStart(this);
            state = ActorState.RUNNING;
            logger.logStateChange(id, "CREATED", "RUNNING");
            
            // Démarrer le thread de traitement
            system.getExecutor().submit(this::processLoop);
        } catch (Exception e) {
            logger.logActorError(id, "preStart", e);
            handleFailure(null, e);
        }
    }
    
    public void stop() {
        if (state == ActorState.STOPPED) return;
        
        state = ActorState.STOPPING;
        logger.logStateChange(id, "RUNNING", "STOPPING");
        
        // Arrêter les enfants
        children.values().forEach(LocalActorRef::stop);
        children.clear();
        
        // Annuler les schedulers
        scheduledTasks.values().forEach(f -> f.cancel(true));
        scheduledTasks.clear();
        scheduler.shutdownNow();
        
        try {
            actor.postStop(this);
        } catch (Exception e) {
            logger.logActorError(id, "postStop", e);
        }
        
        state = ActorState.STOPPED;
        logger.logActorStopped(id, "Normal shutdown");
    }
    
    public void block() {
        blocked = true;
        logger.logActorBlocked(id);
    }
    
    public void unblock() {
        blocked = false;
        logger.logActorUnblocked(id);
    }
    
    public boolean isBlocked() {
        return blocked;
    }
    
    public int mailboxSize() {
        return mailbox.size();
    }
    
    public ActorState getState() {
        return state;
    }
    
    // ==================== Processing ====================
    
    private void processLoop() {
        Thread.currentThread().setName("actor-" + id);
        
        while (state == ActorState.RUNNING || state == ActorState.BLOCKED) {
            try {
                Envelope envelope = mailbox.poll(100, TimeUnit.MILLISECONDS);
                if (envelope == null) continue;
                
                if (blocked) {
                    // Remettre le message dans la queue
                    mailbox.offer(envelope);
                    Thread.sleep(50);
                    continue;
                }
                
                processMessage(envelope);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void processMessage(Envelope envelope) {
        Message message = envelope.message();
        currentSender = envelope.sender();
        
        logger.logMessageReceived(id, message.getClass().getSimpleName(), message.messageId());
        long startTime = System.currentTimeMillis();
        
        try {
            // Gérer les messages spéciaux
            if (message instanceof AskMessage askMsg) {
                handleAskMessage(askMsg);
            } else if (message instanceof AskResponse response) {
                handleAskResponse(response);
            } else {
                actor.onReceive(message, this);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.logMessageProcessed(id, message.getClass().getSimpleName(), message.messageId(), duration);
            
        } catch (Exception e) {
            logger.logActorError(id, message.getClass().getSimpleName(), e);
            handleFailure(message, e);
        } finally {
            currentSender = null;
        }
    }
    
    private void handleAskMessage(AskMessage askMsg) {
        try {
            // Créer un contexte spécial pour capturer la réponse
            AskContext askContext = new AskContext(this, askMsg.askId(), askMsg.replyTo());
            actor.onReceive(askMsg.originalMessage(), askContext);
        } catch (Exception e) {
            // Envoyer l'erreur comme réponse
            askMsg.replyTo().tell(new AskResponse(askMsg.askId(), null, e));
        }
    }
    
    private void handleAskResponse(AskResponse response) {
        CompletableFuture<Object> future = pendingAsks.remove(response.askId());
        if (future != null) {
            if (response.error() != null) {
                future.completeExceptionally(response.error());
            } else {
                future.complete(response.result());
            }
        }
    }
    
    private void handleFailure(Message message, Throwable cause) {
        String childId = id;
        int failureCount = failureCounts.merge(childId, 1, Integer::sum);
        
        // Nettoyer les vieux échecs
        long now = System.currentTimeMillis();
        Long lastFailure = failureTimestamps.get(childId);
        if (lastFailure != null && (now - lastFailure) > supervisorStrategy.withinTimeRangeMs()) {
            failureCounts.put(childId, 1);
            failureCount = 1;
        }
        failureTimestamps.put(childId, now);
        
        SupervisorDirective directive = supervisorStrategy.decide(this, cause, failureCount);
        
        switch (directive) {
            case RESUME -> {
                logger.log(ActorLogger.LogLevel.WARN, id, "Resuming after error: %s", cause.getMessage());
            }
            case RESTART -> {
                logger.logActorRestarted(id, failureCount, cause.getMessage());
                restart(cause, message);
            }
            case STOP -> {
                logger.log(ActorLogger.LogLevel.ERROR, id, "Stopping due to error: %s", cause.getMessage());
                stop();
            }
            case ESCALATE -> {
                if (parent != null) {
                    parent.handleChildFailure(this, cause, failureCount);
                } else {
                    logger.log(ActorLogger.LogLevel.ERROR, id, 
                            "Cannot escalate (no parent), stopping: %s", cause.getMessage());
                    stop();
                }
            }
        }
    }
    
    void handleChildFailure(LocalActorRef child, Throwable cause, int failureCount) {
        SupervisorDirective directive = supervisorStrategy.decide(child, cause, failureCount);
        
        if (supervisorStrategy.isAllForOne() && directive == SupervisorDirective.RESTART) {
            // Redémarrer tous les enfants
            children.values().forEach(c -> c.restart(cause, null));
        } else {
            // Appliquer la directive seulement à l'enfant concerné
            switch (directive) {
                case RESTART -> child.restart(cause, null);
                case STOP -> stopChild(child);
                case ESCALATE -> {
                    if (parent != null) {
                        parent.handleChildFailure(this, cause, failureCount);
                    }
                }
                default -> {} // RESUME: ne rien faire
            }
        }
    }
    
    private void restart(Throwable cause, Message message) {
        state = ActorState.RESTARTING;
        
        try {
            actor.preRestart(cause, message, this);
            // Note: on ne recrée pas l'acteur, on réinitialise juste son état
            actor.postRestart(cause, this);
            state = ActorState.RUNNING;
        } catch (Exception e) {
            logger.logActorError(id, "restart", e);
            state = ActorState.FAILED;
        }
    }
    
    // ==================== Inner Classes ====================
    
    private record Envelope(Message message, ActorRef sender) {}
    
    public record AskMessage(Message originalMessage, String askId, ActorRef replyTo) implements Message {}
    
    public record AskResponse(String askId, Object result, Throwable error) implements Message {}
    
    /**
     * Contexte spécial pour les requêtes ask.
     */
    private static class AskContext implements ActorContext {
        private final LocalActorRef delegate;
        private final String askId;
        private final ActorRef replyTo;
        
        AskContext(LocalActorRef delegate, String askId, ActorRef replyTo) {
            this.delegate = delegate;
            this.askId = askId;
            this.replyTo = replyTo;
        }
        
        /**
         * Envoie la réponse à la requête ask.
         */
        public void reply(Object response) {
            replyTo.tell(new AskResponse(askId, response, null));
        }
        
        // Délégation des autres méthodes
        @Override public String id() { return delegate.id(); }
        @Override public String path() { return delegate.path(); }
        @Override public ActorRef self() { return delegate.self(); }
        @Override public Optional<ActorRef> sender() { return Optional.of(replyTo); }
        @Override public Optional<ActorRef> parent() { return delegate.parent(); }
        @Override public List<ActorRef> children() { return delegate.children(); }
        @Override public ActorRef lookup(String actorId) { return delegate.lookup(actorId); }
        @Override public ActorRef lookupRemote(String s, String a) { return delegate.lookupRemote(s, a); }
        @Override public ActorRef createChild(String id, ActorFactory f) { return delegate.createChild(id, f); }
        @Override public void stopChild(ActorRef c) { delegate.stopChild(c); }
        @Override public void snapshot(Object s) { delegate.snapshot(s); }
        @Override public <T> T restore(Class<T> t) { return delegate.restore(t); }
        @Override public void scheduleOnce(Message m, long d) { delegate.scheduleOnce(m, d); }
        @Override public String schedulePeriodic(Message m, long i, long p) { return delegate.schedulePeriodic(m, i, p); }
        @Override public void cancelSchedule(String s) { delegate.cancelSchedule(s); }
        @Override public ActorSystem system() { return delegate.system(); }
    }
}
