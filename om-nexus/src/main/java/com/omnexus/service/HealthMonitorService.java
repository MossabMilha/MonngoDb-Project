package com.omnexus.service;


import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HealthMonitorService {
    
    private final ConfigServerService configServerService;
    private final Map<String, NodeHealth> nodeHealthCache = new ConcurrentHashMap<>();
    
    @Data
    @AllArgsConstructor
    public static class NodeHealth {
        private String nodeId;
        private String status; // "healthy", "unhealthy", "dead"
        private long lastCheckTime;
        private String errorMessage;
        private int consecutiveFailures;
    }
    
    public HealthMonitorService(ConfigServerService configServerService) {
        this.configServerService = configServerService;
    }
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void performHealthChecks() {
        List<String> clusterIds = configServerService.getAllClusterIds();
        
        for (String clusterId : clusterIds) {
            checkClusterHealth(clusterId);
        }
    }
    
    public Map<String, Object> checkClusterHealth(String clusterId) {
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of("error", "Cluster not found: " + clusterId);
        }
        
        List<NodeHealth> unhealthyNodes = new ArrayList<>();
        List<NodeHealth> healthyNodes = new ArrayList<>();
        
        for (NodeInfo node : config.getNodes()) {
            NodeHealth health = checkNodeHealth(clusterId, node);
            nodeHealthCache.put(clusterId + ":" + node.getNodeId(), health);
            
            if ("healthy".equals(health.getStatus())) {
                healthyNodes.add(health);
            } else {
                unhealthyNodes.add(health);
                log.warn("Unhealthy node detected: {} in cluster {}", node.getNodeId(), clusterId);
            }
        }
        
        return Map.of(
            "clusterId", clusterId,
            "healthyNodes", healthyNodes.size(),
            "unhealthyNodes", unhealthyNodes.size(),
            "details", Map.of(
                "healthy", healthyNodes,
                "unhealthy", unhealthyNodes
            )
        );
    }
    
    private NodeHealth checkNodeHealth(String clusterId, NodeInfo node) {
        String nodeKey = clusterId + ":" + node.getNodeId();
        NodeHealth previousHealth = nodeHealthCache.get(nodeKey);
        int consecutiveFailures = previousHealth != null ? previousHealth.getConsecutiveFailures() : 0;

        try {
            // Check if process is running (either tracked by us OR running on port)
            boolean trackedProcess = ProcessManager.isProcessRunning(node.getNodeId());
            boolean portInUse = ProcessManager.isProcessRunningOnPort(node.getPort());

            if (!trackedProcess && !portInUse) {
                consecutiveFailures++;
                return new NodeHealth(node.getNodeId(), "dead", System.currentTimeMillis(),
                                    "Process not running on port " + node.getPort(), consecutiveFailures);
            }

            // Check MongoDB connection using existing utility
            boolean canConnect = MongoConnectionUtil.canConnect("localhost", node.getPort());
            if (!canConnect) {
                consecutiveFailures++;
                return new NodeHealth(node.getNodeId(), "unhealthy", System.currentTimeMillis(),
                                    "Failed to connect to MongoDB on port " + node.getPort(), consecutiveFailures);
            }

            // Node is healthy
            return new NodeHealth(node.getNodeId(), "healthy", System.currentTimeMillis(), null, 0);

        } catch (Exception e) {
            consecutiveFailures++;
            return new NodeHealth(node.getNodeId(), "unhealthy", System.currentTimeMillis(),
                                e.getMessage(), consecutiveFailures);
        }
    }
    
    public Map<String, NodeHealth> getClusterHealthStatus(String clusterId) {
        return nodeHealthCache.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(clusterId + ":"))
                .collect(Collectors.toMap(
                    entry -> entry.getKey().substring(clusterId.length() + 1),
                    Map.Entry::getValue
                ));
    }
}
