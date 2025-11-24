package com.omnexus.controller;

import com.omnexus.service.DatabaseService;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases")
public class DatabaseController {
    private final DatabaseService databaseService;
    public DatabaseController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    // enable sharding on database
    @PostMapping("/{clusterId}/enable")
    public Map<String,Object> enableSharding(@PathVariable String clusterId, @RequestParam String databaseName){
        boolean success = databaseService.enableSharding(clusterId,databaseName);
        return Map.of(
                "success",success,
                "message",success ? ("Sharding enabled for database: " + databaseName) : ("Failed to enable sharding")
            );
    }
    //Create a Shared collection
    @PostMapping("/{clusterId}/collection/create")
    public Map<String,Object> createSharedCollection(@PathVariable String clusterId, @RequestParam String databaseName, @RequestParam String collectionName,@RequestParam String shardKey){
        boolean success = databaseService.createShardedCollection(clusterId,databaseName,collectionName,shardKey);
        return Map.of(
                "success", success,
                "message", success ? "Sharded collection created: " + databaseName + "." + collectionName : "Failed to create sharded collection"
        );
    }
    // Insert a document
    @PostMapping("/{clusterId}/collection/{collectionName}/insert")
    public Map<String,Object> insertDocument(@PathVariable String clusterId,@RequestParam String databaseName,@PathVariable String collectionName,@RequestBody Map<String,Object> documentData){
        Document document = new Document(documentData);
        boolean success = databaseService.insertDocument(clusterId,databaseName,collectionName,document);
        return Map.of(
                "success", success,
                "message", success ? "Document inserted successfully" : "Failed to insert document"
        );
    }
    //List all database
    @GetMapping("/{clusterId}/list")
    public List<String> listDatabases(@PathVariable String clusterId){
        return databaseService.listDatabases(clusterId);
    }

    // Get collection stats
    @GetMapping("/{clusterId}/collection/{collectionName}/stats")
    public Document getCollectionStats(@PathVariable String clusterId,@RequestParam String databaseName,@PathVariable String collectionName){
        return databaseService.getCollectionStats(clusterId,databaseName,collectionName);
    }

    // Get shard distribution per database
    @GetMapping("/{clusterId}/distrubution")
    public Map<String,Object> getShardDistribution(@PathVariable String clusterId,@RequestParam String databaseName){
        return databaseService.getShardDistribution(clusterId,databaseName);
    }
    // Get shard distribution per collection
    @GetMapping("/{clusterId}/collection/{collectionName}/distribution")
    public Map<String,Object> getShardDistributionPerCollection(@PathVariable String clusterId,@RequestParam String databaseName,@PathVariable String collectionName){
        return databaseService.getShardDistributionPerCollection(clusterId,databaseName,collectionName);
    }
    //Move chunk To Another shard
    @PostMapping("/{clusterId}/collection/{collectionName}/moveChunk")
    public Map<String,Object> moveChunk(@PathVariable String clusterId,@RequestParam String databaseName,@PathVariable String collectionName,@RequestParam String shardKey,@RequestParam Object shardKeyValue,@RequestParam String toShard){
        boolean success = databaseService.moveChunk(clusterId,databaseName,collectionName,shardKey,shardKeyValue,toShard);
        return Map.of(
                "success", success,
                "message", success ? "Chunk moved successfully" : "Failed to move chunk"
        );
    }

}
