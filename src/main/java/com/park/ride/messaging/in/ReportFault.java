package com.park.ride.messaging.in;

import com.park.actor.core.Message;

public record ReportFault(String rideId, String faultType, String description) implements Message {}
