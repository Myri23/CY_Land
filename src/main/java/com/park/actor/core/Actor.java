package com.park.actor.core;

public interface Actor {
    void onReceive(Message message, ActorContext context);
}
