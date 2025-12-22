package com.park.ride.messaging.out;

import com.park.actor.core.Message;

import java.util.List;

/**
 * Evenement emis lorsqu'un cycle d'attraction se termine.
 */
public record CycleFinished(String rideId, List<String> passengers) implements Message {
}
