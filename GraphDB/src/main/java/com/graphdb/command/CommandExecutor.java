package com.graphdb.command;

import com.graphdb.core.DatabaseOperations;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandExecutor {

    private final DatabaseOperations database;
    private static final Pattern DOCUMENT_PATTERN =
            Pattern.compile("\\{([^}]+)}");

    public CommandExecutor(DatabaseOperations database) {
        this.database = database;
    }
    public CompletableFuture<String> executeCommand(String command) {
        String[] parts = command.trim().split("\\s+", 2);
        String operation = parts[0].toUpperCase();

        return switch (operation) {
            case "CREATE_NODE" -> executeCreateNode(parts[1]);
            case "CREATE_RELATIONSHIP" -> executeCreateRelationship(parts[1]);
            case "FIND_NODE" -> executeFindNode(parts[1])
                    .thenApply(results -> String.join(",", results));
            case "DELETE_RELATIONSHIP" -> executeDeleteRelationship(parts[1]);
            case "FIND_RELATED_NODES" -> executeFindRelatedNodes(parts[1]);
            case "FIND_MIN_HOPS" -> executeMinHops(parts[1]);


            case "STOP" -> {
                database.stop();
                yield CompletableFuture.completedFuture("ADIOS!");
            }
            default -> CompletableFuture.completedFuture("INVALID_COMMAND");
        };
    }

    private CompletableFuture<String> executeMinHops(String part) {
        return parseDocument(part)
                .map(database::findMinHops)
                .orElse(CompletableFuture.completedFuture("INVALID_COMMAND"));
    }

    private CompletableFuture<String> executeFindRelatedNodes(String part) {
        return parseDocument(part)
                .map(database::findRelatedNodes)
                .orElse(CompletableFuture.completedFuture("INVALID_COMMAND"));
    }

    private  CompletableFuture<String> executeDeleteRelationship(String part){
        return parseDocument(part)
                .map(database::deleteRelationship)
                .orElse(CompletableFuture.completedFuture("INVALID_COMMAND"));
    }
    private CompletableFuture<List<String>> executeFindNode(String part) {
        return parseDocument(part)
                .map(database::findNodes)
                .orElse(CompletableFuture.completedFuture(Collections.singletonList("INVALID_COMMAND")));
    }

    private CompletableFuture<String> executeCreateRelationship(String part) {
        return parseDocument(part)
                .map(database::createRelationship)
                .orElse(CompletableFuture.completedFuture("INVALID_COMMAND"));
    }

    private CompletableFuture<String> executeCreateNode(String part) {
        return parseDocument(part)
                .map(database::createNode)
                .orElse(CompletableFuture.completedFuture("INVALID_COMMAND"));
    }

    private Optional<Map<String, String>> parseDocument(String documentStr) {
        Matcher matcher = DOCUMENT_PATTERN.matcher(documentStr);
        if (!matcher.find()) return Optional.empty();

        Map<String, String> document = new HashMap<>();
        String[] pairs = matcher.group(1).split(",");

        // Check for invalid key-value pairs
        for (String pair : pairs) {
            String[] parts = pair.trim().split(":", 2);

            // If we don't have exactly two parts (key:value), return empty
            if (parts.length != 2 || parts[1].trim().isEmpty()) {
                return Optional.empty();
            }
            document.put(parts[0].trim().substring(1,parts[0].length()-1), parts[1].trim());
        }

        return Optional.of(document);
    }
}
