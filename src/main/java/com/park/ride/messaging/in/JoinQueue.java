package com.park.ride.messaging.in;

import com.park.actor.core.Message;

public record JoinQueue(String ticketId, String rideId) implements Message {}
