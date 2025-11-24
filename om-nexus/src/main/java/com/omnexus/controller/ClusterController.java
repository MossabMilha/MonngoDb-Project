package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.service.ClusterService;
import com.omnexus.service.ConfigServerService;
import com.omnexus.util.ProcessManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cluster")
public class ClusterController {
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private ConfigServerService configurationService;
    
    @PostMapping("/create")
    public ClusterConfig createCluster(@RequestParam String clusterId, @RequestParam int shards, @RequestParam int configServers) {
        ClusterConfig config = clusterService.createCluster(clusterId, shards, configServers);
        
        // Save to file
        configurationService.saveClusterConfig(config);
        
        return config;
    }
    @PostMapping("/{clusterId}/start")
    public String startCluster(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return "Cluster configuration not found: " + clusterId;
        }
        
        boolean success = clusterService.startCluster(config);
        
        // Save updated config
        configurationService.saveClusterConfig(config);
        
        return success ? "Cluster started successfully" : "Failed to start cluster";
    }
    @PostMapping("/{clusterId}/initialize")
    public String initializeCluster(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return "Cluster configuration not found: " + clusterId;
        }
        
        boolean success = clusterService.initializeCluster(config);
        
        // Save updated config
        configurationService.saveClusterConfig(config);
        
        return success ? "Cluster initialized successfully" : "Failed to initialize cluster";
    }
    @PostMapping("/{clusterId}/stop")
    public String stopCluster(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config == null) {
            return "Cluster configuration not found: " + clusterId;
        }
        
        boolean success = clusterService.stopCluster(config);
        
        // Save updated config
        configurationService.saveClusterConfig(config);
        
        return success ? "Cluster stopped successfully" : "Failed to stop cluster";
    }
    @GetMapping("/{clusterId}")
    public ClusterConfig getClusterConfig(@PathVariable String clusterId) {
        return configurationService.loadClusterConfig(clusterId);
    }
    @DeleteMapping("/{clusterId}")
    public String deleteCluster(@PathVariable String clusterId) {
        // Stop cluster first
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);
        if (config != null) {
            clusterService.stopCluster(config);
        }
        
        boolean deleted = configurationService.deleteClusterConfig(clusterId);
        return deleted ? "Cluster deleted successfully" : "Failed to delete cluster";
    }
    @PostMapping("/cleanup")
    public String cleanupPorts() {
        ProcessManager.killProcessesOnPortRange(28000, 28010);
        return "Cleanup processes on port range 28000-28010 and 27999";
    }
}
