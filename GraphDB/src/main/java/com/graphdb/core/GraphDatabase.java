package com.graphdb.core;

import com.graphdb.modal.Node;
import com.graphdb.modal.Relationship;
import com.graphdb.util.Serializer;
import com.graphdb.util.ValidationUtil;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GraphDatabase implements DatabaseOperations{
    private static final Logger LOGGER = Logger.getLogger(GraphDatabase.class.getName());
    private final ReadWriteLock rwLock;
    private volatile boolean isRunning;

    private final ConcurrentMap<String, Node> memoryStore;
    private final Map<String,List<Node>> adjacencyList;
    private final List<Relationship> relationships;
    private final ExecutorService executorService;
    private final Serializer serializer;

    private static final String BASE_PATH = "src/main/resources/graph_database/";
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();


    public GraphDatabase(){

        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        this.memoryStore = new ConcurrentHashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
        this.adjacencyList = new ConcurrentHashMap<>();
        this.relationships = new ArrayList<>();
        this.serializer = new Serializer();
        this.isRunning = true;
    }


    @Override
    public CompletableFuture<String> createNode(Map<String, String> document) {
        return CompletableFuture.supplyAsync(()->{
            try {
                rwLock.writeLock().lock();
                if (!ValidationUtil.validateDocument(document)) {
                    return "INVALID_COMMAND";
                }
                String id = document.get("_id");
//                String label = document.get("label").substring(1,document.get("label").length()-1);
                if (memoryStore.containsKey(id)) {
                    return "ID_CONFLICT";
                }
                Node node = new Node();
                document.forEach(node::put);

                adjacencyList.put(id,new ArrayList<>());
                memoryStore.put(id,node);

//                serializer.serialize(node,BASE_PATH +"/nodes" + "/" + label +"/"+  id + ".bin");
                return "SUCCESS";
            } finally {
                rwLock.writeLock().unlock();
            }
        },executorService);
    }

    @Override
    public CompletableFuture<String> createRelationship(Map<String, String> query) {
        return CompletableFuture.supplyAsync(()->{
            try{
                rwLock.writeLock().lock();
                if (!ValidationUtil.validateDocument(query)) {
                    return "INVALID_COMMAND";
                }
                String fromId = query.get("source");
                String toId = query.get("target");
                Node to = memoryStore.get(toId);
                Node from = memoryStore.get(fromId);

                // creation of relation and adding
                Relationship relation = new Relationship();
                query.forEach(relation::put);
                relationships.add(relation);

                //validation
                if(adjacencyList.get(fromId).contains(to)) return "INVALID_COMMAND";

                // adding relation into adjacency list
                adjacencyList.get(fromId).add(to);
                if(query.get("edge").substring(1,query.get("edge").length()-1).equals("UNDIRECTED"))
                    adjacencyList.get(toId).add(from);

                return "SUCCESS";
            }finally {
                rwLock.writeLock().unlock();
            }
        },executorService);
    }

    @Override
    public CompletableFuture<List<String>> findNodes(Map<String, String> criteria) {
        return CompletableFuture.supplyAsync(()->{
            try{
                rwLock.writeLock().lock();

                return memoryStore.entrySet().stream().filter(entry->{
                    Node node = entry.getValue();
                    Map<String,String> nodeAttributes = node.getData();

                    return criteria.entrySet().stream()
                            .allMatch(criterion->
                                nodeAttributes.containsKey(criterion.getKey())
                                && criterion.getValue().equals(nodeAttributes.get(criterion.getKey())));
                }).map(entry->{
                    Node node = entry.getValue();
                    Map<String,String> nodeAttributes = node.getData();
                    String id = entry.getKey();
                    String label = nodeAttributes.get("label");
                    return label.substring(1,label.length()-1)+"->"+id;
                }).collect(Collectors.toList());

            }finally {
                rwLock.writeLock().unlock();
            }
        },executorService);
    }

    @Override
    public CompletableFuture<String> deleteRelationship(Map<String, String> criteria) {
        return CompletableFuture.supplyAsync(()->{
            try{
                rwLock.writeLock().lock();
                List<Relationship> relationsToBeDeleted = relationships.parallelStream().filter(relation->{
                    Map<String,String> relationAttributes = relation.getRelation();

                    return criteria.entrySet().stream().allMatch(criterion->
                            relationAttributes.containsKey(criterion.getKey())
                            && criterion.getValue().equals(relationAttributes.get(criterion.getKey())));
                }).toList();

                for(Relationship relation: relationsToBeDeleted){
                    relationships.remove(relation);
                    Map<String,String> relationAttributes = relation.getRelation();
                    String fromID = relationAttributes.get("source");
                    String toID = relationAttributes.get("target");
                    Node node = memoryStore.get(toID);
                    adjacencyList.get(fromID).remove(node);
                }
                return relationsToBeDeleted.size() +" Relationship(s) deleted";

            }finally {
                rwLock.writeLock().unlock();
            }
        },executorService);
    }


    @Override
    public CompletableFuture<Integer> findCommonRelations(Map<String, String> criteria) {
        return null;
    }

    @Override
    public CompletableFuture<String> findRelatedNodes(Map<String, String> criteria) {
        return CompletableFuture.supplyAsync(()->{
            try{
                rwLock.writeLock().lock();
                if (!ValidationUtil.validateDocument(criteria)) {
                    return "INVALID_COMMAND";
                }
                Optional<Node> node = memoryStore.values().stream().filter(v -> {
                    Map<String, String> nodeAttributes = v.getData();

                    return criteria.entrySet().stream()
                            .filter(criterion -> !criterion.getKey().equals("depth"))
                            .allMatch(criterion -> nodeAttributes.containsKey(criterion.getKey())
                                    && criterion.getValue().equals(nodeAttributes.get(criterion.getKey())));
                }).findFirst();

                //If no node matches the criteria
                if(node.isEmpty()) return "NO_NODES_AVAILABLE";
                int parsedDepth = Integer.parseInt(criteria.get("depth"));

                //Applying BFS to find nodes up to a certain depth;
                List<String> result = new ArrayList<>();
                Queue<Node> queue = new LinkedList<>();
                Set<Node> visited = new HashSet<>();
                queue.add(node.get());
                int currentDepth = 0;
                while(!queue.isEmpty() && currentDepth <= parsedDepth){
                    int size = queue.size();
                    while(size-- >0){
                        Node nodeT = queue.poll();
                        String nodeID = nodeT.getData().get("_id");
                        if(!nodeT.equals(node.get()))
                            result.add(nodeT.get("label").substring(1,nodeT.get("label").length()-1)+"->"+nodeT.get("_id"));
                        for(Node adjacentNode : adjacencyList.get(nodeID)){
                            if(!visited.contains(adjacentNode)){
                                queue.add(adjacentNode);
                                visited.add(adjacentNode);
                            }
                        }
                    }
                    currentDepth++;
                }
                return result.isEmpty()? "NO_NODES_AVAILABLE":String.join("::",result);
            }finally {
                rwLock.writeLock().unlock();
            }
        },executorService);
    }

    @Override
    public CompletableFuture<String> findMinHops(Map<String, String> criteria) {
        return CompletableFuture.supplyAsync(()->{
            try{
                rwLock.writeLock().lock();
                int hops =0;

                int sourceID = Integer.parseInt(criteria.get("source"));
                int targetID = Integer.parseInt(criteria.get("target"));

                if (sourceID ==targetID) return "0 Hop(s) required";

                Queue<Integer> queue = new LinkedList<>();
                Set<Integer> visited = new HashSet<>();
                queue.add(sourceID);
                visited.add(sourceID);
                while(!queue.isEmpty()) {
                    int size = queue.size();
                    hops++;
                    while (size-- > 0) {
                        int nodeID = queue.poll();
                        List<Node> neighbours = adjacencyList.get(Integer.toString(nodeID));
                        if (neighbours == null) continue;
                        for (Node neighbor : neighbours) {
                            int neighborID = Integer.parseInt(neighbor.getData().get("_id"));
                            if (neighborID == targetID) {
                                return hops +" Hop(s) Required"; // Return minimum hops if target is reached
                            }

                            // Add unvisited neighbors to the queue
                            if (!visited.contains(neighborID)) {
                                visited.add(neighborID);
                                queue.add(neighborID);
                            }
                        }

                    }
                }
                return "-1 Hop(s) required";

            }finally {
                rwLock.writeLock().unlock();
            }
        },executorService);
    }

    @Override
    public void stop() {
        try{
            rwLock.writeLock().lock();
            this.isRunning = false;
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

}
