package com.graphdb.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DatabaseOperations {
    CompletableFuture<String> createNode(Map<String, String> document);
    CompletableFuture<String> createRelationship(Map<String, String> document);
    CompletableFuture<List<String>> findNodes(Map<String, String> criteria);
    CompletableFuture<String> deleteRelationship(Map<String, String> criteria);
    CompletableFuture<Integer> findCommonRelations(Map<String, String> criteria);
    CompletableFuture<String> findRelatedNodes(Map<String, String> criteria);
    CompletableFuture<String> findMinHops(Map<String, String> criteria);
    void stop();
}
