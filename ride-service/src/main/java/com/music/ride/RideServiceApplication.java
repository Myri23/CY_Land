package com.music.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservice de gestion des attractions du parc.
 * 
 * Gère les files d'attente, les cycles des attractions et les pannes.
 * Communique avec le Gate Service via Eureka.
 */
@SpringBootApplication(scanBasePackages = {"com.music.ride", "com.music.actor"})
@EnableDiscoveryClient
public class RideServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}
