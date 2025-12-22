package com.park.ride.actor;

/**
 * Attraction Grande Roue.
 * Capacite: 12 passagers, Duree cycle: 120 secondes.
 */
public class GrandeRoue extends RideActor {
    
    public static final String RIDE_ID = "GR";
    
    public GrandeRoue() {
        super(RIDE_ID, 12, 120);
    }
}
