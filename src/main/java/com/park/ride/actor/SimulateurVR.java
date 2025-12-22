package com.park.ride.actor;

/**
 * Attraction Simulateur VR.
 * Capacite: 8 passagers, Duree cycle: 180 secondes.
 */
public class SimulateurVR extends RideActor {
    
    public static final String RIDE_ID = "VR";
    
    public SimulateurVR() {
        super(RIDE_ID, 8, 180);
    }
}
