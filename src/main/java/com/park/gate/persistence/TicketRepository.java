package com.park.gate.persistence;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Repository en mémoire pour les tickets.
 * Simule une base de données pour simplifier le projet.
 */
@Repository
public class TicketRepository {
    
    private final Map<String, TicketEntity> tickets = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    public List<TicketEntity> findAll() {
        return new ArrayList<>(tickets.values());
    }
    
    public Optional<TicketEntity> findByTicketId(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }
    
    public boolean existsByTicketId(String ticketId) {
        return tickets.containsKey(ticketId);
    }
    
    public TicketEntity save(TicketEntity ticket) {
        if (ticket.getId() == null) {
            ticket.setId(idGenerator.getAndIncrement());
        }
        tickets.put(ticket.getTicketId(), ticket);
        return ticket;
    }
    
    public void deleteByTicketId(String ticketId) {
        tickets.remove(ticketId);
    }
    
    public List<TicketEntity> findValidToday() {
        LocalDate today = LocalDate.now();
        return tickets.values().stream()
                .filter(t -> !t.isUsed())
                .filter(t -> t.getDateValidity() != null && !t.getDateValidity().isBefore(today))
                .collect(Collectors.toList());
    }
    
    public long count() {
        return tickets.size();
    }
}
