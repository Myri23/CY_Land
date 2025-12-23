package com.park.ride.messaging.out;

import com.park.actor.core.Message;

public record QueueUpdated(String rideId, int size) implements Message {}
