package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.ShardInfo;
import com.omnexus.service.ConfigServerService;
import com.omnexus.service.ShardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cluster/{clusterId}/shards")
public class ShardController {

    @Autowired
    private ShardService shardService;

    @Autowired
    private ConfigServerService configurationService;

    @GetMapping
    public Object getAllShards(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                    "success", false,
                    "message", "Cluster configuration not found: " + clusterId
            );
        }
        return shardService.getShardStatus(config);
    }

    @GetMapping("/{shardId}")
    public Object getShardById(@PathVariable String clusterId, @PathVariable String shardId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                    "success", false,
                    "message", "Cluster configuration not found: " + clusterId
            );
        }
        ShardInfo shard = shardService.getShardById(config, shardId);
        if (shard == null) {
            return Map.of(
                    "success", false,
                    "message", "Shard not found: " + shardId
            );
        }
        return shard;
    }

    // Add a shard using only shardId from the cluster config
    @PostMapping("/add")
    public Map<String, Object> addShard(@PathVariable String clusterId, @RequestParam String shardId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                    "success", false,
                    "message", "Cluster configuration not found: " + clusterId
            );
        }

        // Create new shard node dynamically
        boolean success = shardService.createAndAddNewShard(clusterId, shardId, config);

        // Save updated config with new shard
        configurationService.saveClusterConfig(config);

        return Map.of(
                "success", success,
                "message", success ? "New shard created and added successfully" : "Failed to create/add shard"
        );
    }

    @DeleteMapping("/{shardId}")
    public Map<String, Object> removeShard(@PathVariable String clusterId, @PathVariable String shardId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                    "success", false,
                    "message", "Cluster configuration not found: " + clusterId
            );
        }

        boolean success = shardService.removeShardFromCluster(config, shardId);

        configurationService.saveClusterConfig(config);

        return Map.of(
                "success", success,
                "message", success ? "Shard removed successfully" : "Failed to remove shard"
        );
    }

    // Rebalance - start the MongoDB balancer
    @PostMapping("/rebalance")
    public Map<String, Object> rebalanceShards(@PathVariable String clusterId) {
        boolean success = shardService.rebalanceShards(clusterId);
        return Map.of(
                "success", success,
                "message", success ? "Balancer started successfully" : "Failed to start balancer"
        );
    }

    // Split chunks at specified points and distribute across shards
    @PostMapping("/distribute")
    public Map<String, Object> splitAndDistribute(
            @PathVariable String clusterId,
            @RequestParam String databaseName,
            @RequestParam String collectionName,
            @RequestParam String shardKey,
            @RequestParam(required = false) List<String> splitPoints) {

        // Convert string split points to appropriate types
        List<Object> splitObjects = null;
        if (splitPoints != null && !splitPoints.isEmpty()) {
            splitObjects = new java.util.ArrayList<>();
            for (String value : splitPoints) {
                try {
                    splitObjects.add(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    splitObjects.add(value);
                }
            }
        }

        boolean success = shardService.splitAndDistribute(clusterId, databaseName, collectionName, shardKey, splitObjects);
        return Map.of(
                "success", success,
                "message", success ? "Chunks split and distributed successfully" : "Failed to split/distribute chunks"
        );
    }

    // Move chunks to distribute evenly
    @PostMapping("/moveChunks")
    public Map<String, Object> moveChunks(
            @PathVariable String clusterId,
            @RequestParam String databaseName,
            @RequestParam String collectionName) {

        boolean success = shardService.moveChunksToShards(clusterId, databaseName, collectionName);
        return Map.of(
                "success", success,
                "message", success ? "Chunks moved successfully" : "Failed to move chunks"
        );
    }
}
