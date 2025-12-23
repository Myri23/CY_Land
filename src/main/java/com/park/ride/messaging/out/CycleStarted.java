package com.park.ride.messaging.out;

import com.park.actor.core.Message;
import java.util.List;

public record CycleStarted(String rideId, List<String> passengers) implements Message {}
