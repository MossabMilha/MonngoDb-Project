package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.service.ClusterService;
import com.omnexus.service.ConfigServerService;
import com.omnexus.util.ProcessManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cluster")
public class ClusterController {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ConfigServerService configurationService;

    @PostMapping("/create")
    public ResponseEntity<?> createCluster(
            @RequestParam String clusterId,
            @RequestParam int shards,
            @RequestParam int configServers) {

        try {
            // Check if cluster already exists
            ClusterConfig existing = configurationService.loadClusterConfig(clusterId);
            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Cluster already exists: " + clusterId));
            }

            ClusterConfig config = clusterService.createCluster(clusterId, shards, configServers);
            configurationService.saveClusterConfig(config);

            return ResponseEntity.ok(config);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create cluster: " + e.getMessage()));
        }
    }

    @PostMapping("/{clusterId}/start")
    public ResponseEntity<?> startCluster(@PathVariable String clusterId) {
        try {
            ClusterConfig config = configurationService.loadClusterConfig(clusterId);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Cluster configuration not found: " + clusterId));
            }

            boolean success = clusterService.startCluster(config);
            configurationService.saveClusterConfig(config);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "message", "Cluster started successfully",
                        "config", config
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to start cluster"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error starting cluster: " + e.getMessage()));
        }
    }
    @PostMapping("/{clusterId}/initialize")
    public ResponseEntity<?> initializeCluster(@PathVariable String clusterId) {
        try {
            ClusterConfig config = configurationService.loadClusterConfig(clusterId);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Cluster configuration not found: " + clusterId));
            }

            boolean success = clusterService.initializeCluster(config);
            configurationService.saveClusterConfig(config);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "message", "Cluster initialized successfully",
                        "config", config
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to initialize cluster"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error initializing cluster: " + e.getMessage()));
        }
    }
    @PostMapping("/{clusterId}/stop")
    public ResponseEntity<?> stopCluster(@PathVariable String clusterId) {
        try {
            ClusterConfig config = configurationService.loadClusterConfig(clusterId);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Cluster configuration not found: " + clusterId));
            }

            boolean success = clusterService.stopCluster(config);
            configurationService.saveClusterConfig(config);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "message", "Cluster stopped successfully",
                        "config", config
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to stop cluster"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error stopping cluster: " + e.getMessage()));
        }
    }
    @GetMapping("/{clusterId}")
    public ResponseEntity<?> getClusterConfig(@PathVariable String clusterId) {
        ClusterConfig config = configurationService.loadClusterConfig(clusterId);

        if (config == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Cluster not found: " + clusterId));
        }

        return ResponseEntity.ok(config);
    }
    @GetMapping("/{clusterId}/status")
    public ResponseEntity<?> getClusterStatus(@PathVariable String clusterId) {
        try {
            ClusterConfig config = configurationService.loadClusterConfig(clusterId);

            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Cluster not found: " + clusterId));
            }

            Map<String, Object> status = new HashMap<>();
            status.put("clusterId", clusterId);
            status.put("nodes", config.getNodes());

            // Check running processes
            Map<String, Boolean> processStatus = new HashMap<>();
            config.getNodes().forEach(node -> {
                processStatus.put(node.getNodeId(),
                        ProcessManager.isProcessRunning(node.getNodeId()));
            });
            status.put("processStatus", processStatus);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error getting cluster status: " + e.getMessage()));
        }
    }
    @DeleteMapping("/{clusterId}")
    public ResponseEntity<?> deleteCluster(@PathVariable String clusterId) {
        try {
            ClusterConfig config = configurationService.loadClusterConfig(clusterId);

            if (config != null) {
                clusterService.stopCluster(config);
                clusterService.deleteClusterData(config);
            }

            boolean deleted = configurationService.deleteClusterConfig(clusterId);

            if (deleted || config == null) {
                return ResponseEntity.ok(Map.of(
                        "message", "Cluster deleted successfully"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to delete cluster"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error deleting cluster: " + e.getMessage()));
        }
    }
    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanupPorts() {
        try {
            ProcessManager.killProcessesOnPortRange(28000, 28010);
            ProcessManager.killProcessesOnPort(27999); // Also cleanup mongos port

            return ResponseEntity.ok(Map.of(
                    "message", "Cleaned up processes on port range 28000-28010 and 27999"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error during cleanup: " + e.getMessage()));
        }
    }
}