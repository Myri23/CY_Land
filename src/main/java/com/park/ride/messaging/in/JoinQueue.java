package com.park.ride.messaging.in;

import com.park.actor.core.Message;

/**
 * Message pour rejoindre la file d'attente d'une attraction.
 */
public record JoinQueue(String ticketId, String rideId) implements Message {
}
