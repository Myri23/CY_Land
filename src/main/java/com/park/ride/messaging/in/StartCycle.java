package com.park.ride.messaging.in;

import com.park.actor.core.Message;

/**
 * Message pour demarrer un cycle d'attraction.
 */
public record StartCycle() implements Message {
}
