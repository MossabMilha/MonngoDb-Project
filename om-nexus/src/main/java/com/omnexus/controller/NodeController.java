package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.model.NodeStatus;

import com.omnexus.service.ConfigServerService;
import com.omnexus.service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clusters/{clusterId}/nodes")
public class NodeController {
    
    @Autowired
    private NodeService nodeService;
    
    @Autowired
    private ConfigServerService configurationService;
    
    @GetMapping
    public List<NodeInfo> getAllNodes(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return nodeService.getAllNodes(config);
    }
    @GetMapping("/{nodeId}")
    public NodeInfo getNodeInfo(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return nodeService.getNodeInfo(nodeId, config);
    }
    @GetMapping("/{nodeId}/status")
    public NodeStatus getNodeStatus(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return nodeService.getNodeStatus(nodeId, config);
    }
    @PostMapping("/{nodeId}/start")
    public Map<String, Object> startNode(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                "success", false,
                "message", "Cluster configuration not found: " + clusterId,
                "nodeId", nodeId
            );
        }
        
        boolean success = nodeService.startNode(nodeId, config);
        
        // Save updated config
        configurationService.saveClusterConfig(config);
        
        return Map.of(
            "success", success,
            "message", success ? "Node " + nodeId + " started successfully" : "Failed to start node " + nodeId,
            "nodeId", nodeId
        );
    }
    @PostMapping("/{nodeId}/stop")
    public Map<String, Object> stopNode(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                "success", false,
                "message", "Cluster configuration not found: " + clusterId,
                "nodeId", nodeId
            );
        }
        
        boolean success = nodeService.stopNode(nodeId);
        
        // Save updated config
        configurationService.saveClusterConfig(config);
        
        return Map.of(
            "success", success,
            "message", success ? "Node " + nodeId + " stopped successfully" : "Failed to stop node " + nodeId,
            "nodeId", nodeId
        );
    }
    @PostMapping("/{nodeId}/restart")
    public Map<String, Object> restartNode(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                "success", false,
                "message", "Cluster configuration not found: " + clusterId,
                "nodeId", nodeId
            );
        }
        
        Map<String, Object> result = nodeService.restartNode(nodeId, config);
        
        // Save updated config
        configurationService.saveClusterConfig(config);
        
        return result;
    }
    @DeleteMapping("/{nodeId}")
    public Map<String, Object> removeNode(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of(
                "success", false,
                "message", "Cluster configuration not found: " + clusterId,
                "nodeId", nodeId
            );
        }
        
        Map<String, Object> result = nodeService.removeNodeFromCluster(nodeId, config);
        
        // Save updated config if removal was successful
        if ((Boolean) result.get("success")) {
            configurationService.saveClusterConfig(config);
        }
        
        return result;
    }
}
