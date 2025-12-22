package com.park.ride.actor;

/**
 * Attraction Montagnes Russes.
 * Capacite: 20 passagers, Duree cycle: 60 secondes.
 */
public class RollerCoaster extends RideActor {
    
    public static final String RIDE_ID = "RC";
    
    public RollerCoaster() {
        super(RIDE_ID, 20, 60);
    }
}
