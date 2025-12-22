package com.park.actor.runtime;

import com.park.actor.core.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Implémentation locale d'un acteur avec sa mailbox.
 * Chaque acteur possède sa propre file de messages et son thread de traitement.
 */
public class LocalActorRef implements ActorRef, ActorContext {
    
    private final String id;
    private final Actor actor;
    private final BlockingQueue<Message> mailbox;
    private final ActorRuntime runtime;
    
    public LocalActorRef(String id, Actor actor, ActorRuntime runtime) {
        this.id = id;
        this.actor = actor;
        this.mailbox = new LinkedBlockingQueue<>();
        this.runtime = runtime;
    }
    
    // ========== ActorRef ==========
    
    @Override
    public String id() {
        return id;
    }
    
    @Override
    public void tell(Message message) {
        mailbox.offer(message);
    }
    
    // ========== ActorContext ==========
    
    @Override
    public ActorRef self() {
        return this;
    }
    
    @Override
    public ActorRef lookup(String actorId) {
        return runtime.find(actorId);
    }
    
    @Override
    public void snapshot(Object state) {
        // TODO: Implémenter la persistance (JPA, Redis, etc.)
        // Pour l'instant, log uniquement
        System.out.printf("[SNAPSHOT] Actor %s: %s%n", id, state);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T restore(Class<T> type) {
        // TODO: Implémenter la restauration
        return null;
    }
    
    // ========== Boucle de traitement ==========
    
    /**
     * Boucle principale de traitement des messages.
     * Appelée par le runtime dans un thread dédié.
     */
    void processLoop() {
        Thread.currentThread().setName("actor-" + id);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Message message = mailbox.take(); // bloquant
                try {
                    actor.onReceive(message, this);
                } catch (Exception e) {
                    System.err.printf("[ERROR] Actor %s failed to process %s: %s%n", 
                            id, message.getClass().getSimpleName(), e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
