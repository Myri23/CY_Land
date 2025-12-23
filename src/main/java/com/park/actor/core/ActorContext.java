package com.park.actor.core;

public interface ActorContext {
    String id();
    ActorRef self();
    ActorRef lookup(String actorId);
    void snapshot(Object state);
    <T> T restore(Class<T> type);
}
