package com.park.ride.messaging.in;

import com.park.actor.core.Message;

/**
 * Message interne pour signaler la fin d'un cycle.
 */
public record CycleTick() implements Message {
}
