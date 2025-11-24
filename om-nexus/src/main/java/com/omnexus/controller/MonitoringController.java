package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.ClusterStatus;
import com.omnexus.model.NodeStatus;
import com.omnexus.service.ConfigServerService;
import com.omnexus.service.MonitoringService;
import com.omnexus.service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    
    @Autowired
    private MonitoringService monitoringService;
    
    @Autowired
    private NodeService nodeService;
    
    @Autowired
    private ConfigServerService configurationService;

    @GetMapping("/cluster/{clusterId}")
    public ClusterStatus getClusterHealth(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return monitoringService.getClusterStatus(config);
    }
    @GetMapping("/cluster/{clusterId}/nodes")
    public List<NodeStatus> getAllNodesStatuses(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return nodeService.getNodeStatuses(config);
    }
    @GetMapping("/cluster/{clusterId}/metrics")
    public Map<String, Object> getClusterMetrics(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return monitoringService.getClusterMetrics(config);
    }
    
    @GetMapping("/status/realtime/{clusterId}")
    public Map<String, Object> getRealtimeStatus(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return monitoringService.getRealtimeClusterStatus(config);
    }

    @GetMapping("/health/detailed/{clusterId}")
    public Map<String, Object> getDetailedHealth(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return monitoringService.getDetailedHealthCheck(config);
    }
    
    @GetMapping("/node/{clusterId}/{nodeId}/status")
    public NodeStatus getIndividualNodeStatus(@PathVariable String clusterId, @PathVariable String nodeId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            throw new RuntimeException("Cluster configuration not found: " + clusterId);
        }
        return monitoringService.getIndividualNodeStatus(nodeId, config);
    }
}
