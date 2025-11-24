package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.service.ClusterService;
import com.omnexus.util.ProcessManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cluster")
public class ClusterController {
    @Autowired
    private ClusterService clusterService;

    @PostMapping("/create")
    public ClusterConfig createCluster(@RequestParam String clusterId,@RequestParam int shards,@RequestParam int configServers){
        return clusterService.createCluster(clusterId, shards, configServers);
    }
    @PostMapping("/start")
    public String startCluster(@RequestBody ClusterConfig config){
        boolean success = clusterService.startCluster(config);
        return success ? "Cluster started successfully" : "Failed to start cluster";
    }
    @PostMapping("/initialize")
    public String initializeCluster(@RequestBody ClusterConfig config){
        boolean success = clusterService.initializeCluster(config);
        return  success ? "Cluster initialized Successfully":"Failed to initialize cluster";
    }
    @PostMapping("/stop")
    public String stopCluster(@RequestBody ClusterConfig config){
        boolean success = clusterService.stopCluster(config);
        return success ? "Cluster stopped successfully" : "Failed to stop cluster";
    }
    @PostMapping("/cleanup")
    public String cleanupPorts(){
        ProcessManager.killProcessesOnPortRange(28000,28010);
        return "Cleanup processes on port range 28000-28010 and 27999";
    }
}
