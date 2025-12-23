package com.park.ride.messaging.out;

import com.park.actor.core.Message;
import java.util.List;

public record CycleFinished(String rideId, List<String> passengers) implements Message {}
