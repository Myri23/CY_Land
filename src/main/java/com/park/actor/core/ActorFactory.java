package com.park.actor.core;

@FunctionalInterface
public interface ActorFactory {
    Actor create(String id);
}
