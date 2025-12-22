package com.park;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du micro-service Gate.
 * 
 * <p>Ce service gère le contrôle d'accès aux portes d'un parc d'attractions
 * en utilisant un modèle d'acteurs pour la gestion concurrente.
 */
@SpringBootApplication
public class GateServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GateServiceApplication.class, args);
    }
}
