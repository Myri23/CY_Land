package com.music.actor.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Implémentation par défaut du logger d'acteurs.
 * 
 * Écrit les logs dans des fichiers JSON structurés par jour et par acteur.
 * Utilise une queue asynchrone pour ne pas bloquer les acteurs.
 */
@Component
public class DefaultActorLogger implements ActorLogger {
    
    private final String serviceName;
    private final Path logDirectory;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<LogEntry> logQueue;
    private final Thread logWriterThread;
    private volatile boolean running = true;
    
    public DefaultActorLogger(
            @Value("${spring.application.name:unknown}") String serviceName,
            @Value("${actor.logging.directory:logs/actors}") String logDir) {
        
        this.serviceName = serviceName;
        this.logDirectory = Paths.get(logDir);
        this.logQueue = new LinkedBlockingQueue<>(10000);
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Créer le répertoire de logs
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            System.err.println("[ACTOR-LOGGER] Failed to create log directory: " + e.getMessage());
        }
        
        // Thread dédié pour l'écriture des logs (non-bloquant)
        this.logWriterThread = new Thread(this::processLogQueue, "actor-log-writer");
        this.logWriterThread.setDaemon(true);
        this.logWriterThread.start();
        
        System.out.printf("[ACTOR-LOGGER] Initialized for service '%s', logs in '%s'%n", 
                serviceName, logDirectory);
    }
    
    @Override
    public void logActorCreated(String actorId, String actorPath, String parentId) {
        enqueue(LogEntry(LogLevel.INFO, actorId, actorPath, "ACTOR_CREATED",
                String.format("Actor created with parent: %s", parentId),
                null, null, null, null, null));
    }
    
    @Override
    public void logActorStopped(String actorId, String reason) {
        enqueue(LogEntry(LogLevel.INFO, actorId, null, "ACTOR_STOPPED",
                String.format("Actor stopped: %s", reason),
                null, null, null, null, null));
    }
    
    @Override
    public void logMessageReceived(String actorId, String messageType, String messageId) {
        enqueue(LogEntry(LogLevel.DEBUG, actorId, null, "MESSAGE_RECEIVED",
                "Message received", messageId, messageType, null, null, null));
    }
    
    @Override
    public void logMessageProcessed(String actorId, String messageType, String messageId, long durationMs) {
        enqueue(LogEntry(LogLevel.DEBUG, actorId, null, "MESSAGE_PROCESSED",
                String.format("Message processed in %dms", durationMs),
                messageId, messageType, durationMs, null, null));
    }
    
    @Override
    public void logMessageSent(String fromActorId, String toActorId, String messageType, String messageId) {
        enqueue(LogEntry(LogLevel.DEBUG, fromActorId, null, "MESSAGE_SENT",
                String.format("Message sent to %s", toActorId),
                messageId, messageType, null, null, null));
    }
    
    @Override
    public void logActorError(String actorId, String messageType, Throwable error) {
        enqueue(LogEntry(LogLevel.ERROR, actorId, null, "ACTOR_ERROR",
                error.getMessage(), null, messageType, null,
                error.getClass().getSimpleName(), error.getMessage()));
    }
    
    @Override
    public void logActorRestarted(String actorId, int restartCount, String reason) {
        enqueue(LogEntry(LogLevel.WARN, actorId, null, "ACTOR_RESTARTED",
                String.format("Actor restarted (attempt %d): %s", restartCount, reason),
                null, null, null, null, null));
    }
    
    @Override
    public void logActorBlocked(String actorId) {
        enqueue(LogEntry(LogLevel.WARN, actorId, null, "ACTOR_BLOCKED",
                "Actor blocked", null, null, null, null, null));
    }
    
    @Override
    public void logActorUnblocked(String actorId) {
        enqueue(LogEntry(LogLevel.INFO, actorId, null, "ACTOR_UNBLOCKED",
                "Actor unblocked", null, null, null, null, null));
    }
    
    @Override
    public void logStateChange(String actorId, String fromState, String toState) {
        enqueue(LogEntry(LogLevel.DEBUG, actorId, null, "STATE_CHANGE",
                String.format("State changed: %s -> %s", fromState, toState),
                null, null, null, null, null));
    }
    
    @Override
    public void logRemoteCommunication(String fromService, String toService, String actorId, String messageType) {
        enqueue(LogEntry(LogLevel.INFO, actorId, null, "REMOTE_COMMUNICATION",
                String.format("Remote call: %s -> %s", fromService, toService),
                null, messageType, null, null, null));
    }
    
    @Override
    public void log(LogLevel level, String actorId, String message, Object... args) {
        String formattedMessage = args.length > 0 ? String.format(message, args) : message;
        enqueue(LogEntry(level, actorId, null, "CUSTOM",
                formattedMessage, null, null, null, null, null));
    }
    
    private LogEntry LogEntry(LogLevel level, String actorId, String actorPath, String eventType,
                              String message, String messageId, String messageType,
                              Long durationMs, String errorClass, String errorMessage) {
        return new LogEntry(
                Instant.now(), level, actorId, actorPath, eventType, message,
                messageId, messageType, durationMs, errorClass, errorMessage, serviceName
        );
    }
    
    private void enqueue(LogEntry entry) {
        if (!logQueue.offer(entry)) {
            // Queue pleine, log sur stderr
            System.err.printf("[ACTOR-LOGGER] Queue full, dropping log: %s%n", entry.message());
        }
    }
    
    private void processLogQueue() {
        while (running || !logQueue.isEmpty()) {
            try {
                LogEntry entry = logQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (entry != null) {
                    writeLog(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void writeLog(LogEntry entry) {
        // Fichier par jour
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path logFile = logDirectory.resolve(String.format("actors-%s.jsonl", date));
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
            String json = objectMapper.writeValueAsString(entry);
            writer.println(json);
            
            // Aussi sur console pour le debug
            printToConsole(entry);
        } catch (IOException e) {
            System.err.printf("[ACTOR-LOGGER] Failed to write log: %s%n", e.getMessage());
        }
    }
    
    private void printToConsole(LogEntry entry) {
        String prefix = switch (entry.level()) {
            case ERROR -> "\u001B[31m[ERROR]\u001B[0m";
            case WARN -> "\u001B[33m[WARN]\u001B[0m";
            case INFO -> "\u001B[32m[INFO]\u001B[0m";
            case DEBUG -> "\u001B[36m[DEBUG]\u001B[0m";
            case TRACE -> "\u001B[37m[TRACE]\u001B[0m";
        };
        
        System.out.printf("%s [%s] [%s] %s%n",
                prefix, entry.actorId(), entry.eventType(), entry.message());
    }
    
    public void shutdown() {
        running = false;
        try {
            logWriterThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
