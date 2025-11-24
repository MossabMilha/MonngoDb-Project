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
    public List<ShardInfo> getAllShards(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return shardService.getShardStatus(config);
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

        // Get shard node from cluster config
        String shardConnectionString = shardService.buildShardConnectionString(shardId, config);

        boolean success = shardService.addShardToCluster(shardConnectionString);

        // Save updated config
        configurationService.saveClusterConfig(config);

        return Map.of(
                "success", success,
                "message", success ? "Shard added successfully" : "Failed to add shard"
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

        boolean success = shardService.removeShardFromCluster(shardId);

        configurationService.saveClusterConfig(config);

        return Map.of(
                "success", success,
                "message", success ? "Shard removed successfully" : "Failed to remove shard"
        );
    }

    @PostMapping("/{shardId}/rebalance")
    public Map<String, Object> rebalanceShard(@PathVariable String clusterId, @PathVariable String shardId) {
        // TODO: Implement actual rebalance logic
        return Map.of(
                "success", true,
                "message", "Rebalance triggered for shard " + shardId
        );
    }
}
