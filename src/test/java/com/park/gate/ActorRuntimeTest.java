package com.park.gate;

import com.park.actor.core.*;
import com.park.actor.runtime.ActorRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du système d'acteurs (runtime).
 */
class ActorRuntimeTest {
    
    private ActorRuntime runtime;
    
    @BeforeEach
    void setUp() {
        runtime = new ActorRuntime();
    }
    
    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }
    
    @Test
    @DisplayName("getOrCreate doit créer un acteur s'il n'existe pas")
    void shouldCreateActorIfNotExists() {
        // When
        ActorRef ref = runtime.getOrCreate("test-actor", id -> new NoOpActor());
        
        // Then
        assertThat(ref).isNotNull();
        assertThat(ref.id()).isEqualTo("test-actor");
        assertThat(runtime.exists("test-actor")).isTrue();
    }
    
    @Test
    @DisplayName("getOrCreate doit retourner le même acteur si déjà créé")
    void shouldReturnSameActorIfExists() {
        // Given
        ActorRef first = runtime.getOrCreate("same-actor", id -> new NoOpActor());
        
        // When
        ActorRef second = runtime.getOrCreate("same-actor", id -> new NoOpActor());
        
        // Then
        assertThat(first).isSameAs(second);
        assertThat(runtime.actorCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Un acteur doit recevoir les messages envoyés")
    void actorShouldReceiveMessages() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger(0);
        
        ActorRef ref = runtime.getOrCreate("counter", id -> new Actor() {
            @Override
            public void onReceive(Message message, ActorContext context) {
                counter.incrementAndGet();
                latch.countDown();
            }
        });
        
        // When
        ref.tell(new TestMessage());
        ref.tell(new TestMessage());
        ref.tell(new TestMessage());
        
        // Then
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Les messages doivent être traités dans l'ordre FIFO")
    void messagesShouldBeProcessedInOrder() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(3);
        StringBuilder order = new StringBuilder();
        
        ActorRef ref = runtime.getOrCreate("ordered", id -> new Actor() {
            @Override
            public void onReceive(Message message, ActorContext context) {
                if (message instanceof OrderedMessage om) {
                    order.append(om.order());
                }
                latch.countDown();
            }
        });
        
        // When
        ref.tell(new OrderedMessage(1));
        ref.tell(new OrderedMessage(2));
        ref.tell(new OrderedMessage(3));
        
        // Then
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(order.toString()).isEqualTo("123");
    }
    
    // === Test messages ===
    
    record TestMessage() implements Message {}
    record OrderedMessage(int order) implements Message {}
    
    static class NoOpActor implements Actor {
        @Override
        public void onReceive(Message message, ActorContext context) {
            // No-op
        }
    }
}
