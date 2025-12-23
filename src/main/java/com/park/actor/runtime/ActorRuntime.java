package com.park.actor.runtime;

import com.park.actor.core.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class ActorRuntime {
    
    private final ConcurrentMap<String, LocalActorRef> actors = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("actor-", 0).factory()
    );
    
    public ActorRef getOrCreate(String id, ActorFactory factory) {
        return actors.computeIfAbsent(id, key -> {
            Actor actor = factory.create(key);
            LocalActorRef ref = new LocalActorRef(key, actor, this);
            executor.submit(ref::processLoop);
            System.out.printf("[ACTOR] Created actor: %s%n", key);
            return ref;
        });
    }
    
    public ActorRef find(String id) {
        return actors.get(id);
    }
    
    public boolean exists(String id) {
        return actors.containsKey(id);
    }
    
    public int actorCount() {
        return actors.size();
    }
    
    @PreDestroy
    public void shutdown() {
        System.out.println("[ACTOR] Shutting down actor runtime...");
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[ACTOR] Timeout waiting for actors to stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        actors.clear();
        System.out.println("[ACTOR] Runtime stopped");
    }
}
