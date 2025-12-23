package com.music.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Serveur Eureka pour la découverte de services.
 * 
 * Les microservices (Gate Service, Ride Service) s'enregistrent ici
 * et peuvent se découvrir mutuellement pour la communication inter-services.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
        
        System.out.println("===========================================");
        System.out.println("  EUREKA SERVER STARTED");
        System.out.println("  Dashboard: http://localhost:8761");
        System.out.println("===========================================");
    }
}
