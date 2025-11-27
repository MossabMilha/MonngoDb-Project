package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.service.ConfigServerService;
import com.omnexus.service.HealthMonitorService;
import com.omnexus.service.NodeRecoveryService;
import com.omnexus.util.ProcessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/failure")
@Slf4j
public class FailureController {

    private final HealthMonitorService healthMonitorService;
    private final NodeRecoveryService nodeRecoveryService;
    private final ConfigServerService configServerService;

    public FailureController(HealthMonitorService healthMonitorService,
                             NodeRecoveryService nodeRecoveryService,
                             ConfigServerService configServerService) {
        this.healthMonitorService = healthMonitorService;
        this.nodeRecoveryService = nodeRecoveryService;
        this.configServerService = configServerService;
    }

    @GetMapping("/health/{clusterId}")
    public ResponseEntity<Map<String, Object>> getClusterHealth(@PathVariable String clusterId) {
        Map<String, Object> health = healthMonitorService.checkClusterHealth(clusterId);
        return ResponseEntity.ok(health);
    }

    @GetMapping("/health/{clusterId}/nodes")
    public ResponseEntity<Map<String, HealthMonitorService.NodeHealth>> getNodeHealthStatus(@PathVariable String clusterId) {
        Map<String, HealthMonitorService.NodeHealth> nodeHealth = healthMonitorService.getClusterHealthStatus(clusterId);
        return ResponseEntity.ok(nodeHealth);
    }

    @PostMapping("/recover/{clusterId}/{nodeId}")
    public ResponseEntity<Map<String, Object>> recoverNode(
            @PathVariable String clusterId,
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "true") boolean autoRestart) {

        Map<String, Object> result = nodeRecoveryService.recoverNode(clusterId, nodeId, autoRestart);

        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/recover/{clusterId}/all")
    public ResponseEntity<Map<String, Object>> recoverAllNodes(@PathVariable String clusterId) {
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cluster not found: " + clusterId));
        }

        List<Map<String, Object>> recoveryResults = new ArrayList<>();
        boolean allSuccess = true;

        for (NodeInfo node : config.getNodes()) {
            if (!"mongos".equals(node.getType())) { // Skip mongos for now
                Map<String, Object> result = nodeRecoveryService.recoverNode(clusterId, node.getNodeId(), true);
                recoveryResults.add(result);

                if (result.containsKey("error") || !Boolean.TRUE.equals(result.get("recovered"))) {
                    allSuccess = false;
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "clusterId", clusterId,
                "allRecovered", allSuccess,
                "results", recoveryResults
        ));
    }

    @PostMapping("/restart/{clusterId}/{nodeId}")
    public ResponseEntity<Map<String, Object>> restartNode(
            @PathVariable String clusterId,
            @PathVariable String nodeId) {

        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cluster not found: " + clusterId));
        }

        NodeInfo node = config.getNodes().stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);

        if (node == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Node not found: " + nodeId));
        }

        try {
            // Stop the node
            ProcessManager.stopProcess(nodeId);
            Thread.sleep(2000);

            boolean started;
            // Start the node - use appropriate method based on node type
            if ("mongos".equals(node.getType())) {
                // For mongos, we need the config replica set connection string
                // Get config replica set name from config nodes (default: configReplSet)
                String configRsName = config.getNodes().stream()
                        .filter(n -> "config".equals(n.getType()))
                        .map(NodeInfo::getReplicaSet)
                        .findFirst()
                        .orElse("configReplSet");

                String configHosts = config.getNodes().stream()
                        .filter(n -> "config".equals(n.getType()))
                        .map(n -> "localhost:" + n.getPort())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");

                String configDbString = configRsName + "/" + configHosts;
                started = ProcessManager.startMongosProcess(
                        node.getNodeId(),
                        node.getPort(),
                        configDbString
                );
            } else {
                started = ProcessManager.startMongodProcess(
                        node.getNodeId(),
                        node.getType(),
                        node.getPort(),
                        node.getDataPath(),
                        node.getReplicaSet()
                );
            }

            return ResponseEntity.ok(Map.of(
                    "clusterId", clusterId,
                    "nodeId", nodeId,
                    "restarted", started,
                    "message", started ? "Node restarted successfully" : "Failed to restart node"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Restart failed: " + e.getMessage()));
        }
    }
}
