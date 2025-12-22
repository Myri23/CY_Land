package com.park.ride.messaging.out;

import com.park.actor.core.Message;

/**
 * Evenement emis lorsque la file d'attente d'une attraction change.
 */
public record QueueUpdated(String rideId, int size) implements Message {
}
