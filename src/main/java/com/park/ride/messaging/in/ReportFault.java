package com.park.ride.messaging.in;

import com.park.actor.core.Message;

/**
 * Message pour signaler une panne sur une attraction.
 */
public record ReportFault(String rideId, String faultType, String description) implements Message {
}
