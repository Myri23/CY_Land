package com.music.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservice de contrôle d'accès au parc.
 * 
 * Gère les portes d'entrée et la validation des tickets.
 * Utilise le framework d'acteurs pour le traitement asynchrone.
 */
@SpringBootApplication(scanBasePackages = {"com.music.gate", "com.music.actor"})
@EnableDiscoveryClient
public class GateServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GateServiceApplication.class, args);
    }
}
