package com.park.actor.core;

public interface ActorRef {
    String id();
    void tell(Message message);
}
