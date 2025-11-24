package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.ClusterStatus;
import com.omnexus.model.NodeStatus;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonitoringService {
    private final NodeService nodeService;

    public MonitoringService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
    public ClusterStatus getClusterStatus(ClusterConfig config) {
        ClusterStatus status = new ClusterStatus();
        status.setClusterId(config.getClusterId());

        // Get all node statuses
        List<NodeStatus> nodeStatuses = nodeService.getNodeStatuses(config);
        status.setNodesStatuses(nodeStatuses);

        // Count running and stopped nodes
        int runningNodes = 0;
        int stoppedNodes = 0;
        int totalNodes = config.getNodes().size();
        int configServers = 0;
        int shardServers = 0;

        for (NodeStatus nodeStatus : nodeStatuses) {
            if ("running".equals(nodeStatus.getStatus())) {
                runningNodes++;
            } else {
                stoppedNodes++;
            }

            // Count node types
            if ("config".equals(nodeStatus.getType())) {
                configServers++;
            } else if ("shard".equals(nodeStatus.getType())) {
                shardServers++;
            }
        }

        // Basic counts
        status.setTotalNodes(totalNodes);
        status.setRunningNodes(runningNodes);
        status.setStoppedNodes(stoppedNodes);
        status.setConfigServers(configServers);
        status.setShardServers(shardServers);

        // Mongos status
        status.setMongosRunning(ProcessManager.isProcessRunning("mongos"));

        // Replica set counts (simplified for now)
        status.setTotalReplicaSets(2); // configReplSet + number of shards
        status.setActiveReplicaSets(runningNodes > 0 ? 2 : 0);

        // Shard counts
        status.setTotalShards(shardServers);
        status.setActiveShards((int) nodeStatuses.stream().filter(n -> "shard".equals(n.getType()) && "running".equals(n.getStatus())).count());

        // Health calculations
        status.setHealthPercentage(status.getHealthPercentage());
        status.setHealthy(status.isHealthy());

        // Timestamps
        status.setLastUpdate(LocalDateTime.now().toString());

        // Determine overall cluster status
        if (runningNodes == totalNodes && status.isMongosRunning()) {
            status.setStatus("running");
        } else if (runningNodes == 0) {
            status.setStatus("stopped");
        } else {
            status.setStatus("partial");
        }

        return status;
    }
    public Map<String,Object> getClusterMetrics(ClusterConfig config){
        Map<String,Object> metrics = new HashMap<>();

        // Basic cluster metrics
        ClusterStatus clusterStatus = getClusterStatus(config);
        metrics.put("clusterId", config.getClusterId());
        metrics.put("totalNodes", clusterStatus.getTotalNodes());
        metrics.put("runningNodes", clusterStatus.getRunningNodes());
        metrics.put("stoppedNodes", clusterStatus.getStoppedNodes());
        metrics.put("mongosRunning", clusterStatus.isMongosRunning());
        metrics.put("overallStatus", clusterStatus.getStatus());

        // Node type breakdown
        Map<String,Integer> nodeTypes = new HashMap<>();
        int configNodes = 0;
        int shardNodes = 0;

        for(var node : config.getNodes()){
            if("config".equals(node.getType())){
                configNodes++;
            } else if ("shard".equals(node.getType())) {
                shardNodes++;
            }
        }
        nodeTypes.put("configServers", configNodes);
        nodeTypes.put("shardServers", shardNodes);
        metrics.put("nodeTypes", nodeTypes);

        // Health metrics
        Map<String,Object> healthMetrics = new HashMap<>();
        healthMetrics.put("healthyNodes", clusterStatus.getRunningNodes());
        healthMetrics.put("unhealthyNodes", clusterStatus.getStoppedNodes());
        healthMetrics.put("healthPercentage",clusterStatus.getTotalNodes() > 0 ? ((double) clusterStatus.getRunningNodes() /clusterStatus.getTotalNodes()*100):0);
        metrics.put("health",healthMetrics);

        metrics.put("timestamp", LocalDateTime.now().toString());

        return metrics;
    }
    public Map<String, Object> getRealtimeClusterStatus(ClusterConfig config) {
        Map<String, Object> realTimeStatus = new HashMap<>();
        ClusterStatus clusterStatus = getClusterStatus(config);

        // Real-time cluster overview
        realTimeStatus.put("clusterId", config.getClusterId());
        realTimeStatus.put("timestamp", LocalDateTime.now().toString());
        realTimeStatus.put("overallStatus", clusterStatus.getStatus());
        realTimeStatus.put("isHealthy", clusterStatus.isHealthy());

        // Node status breakdown
        Map<String, Object> nodeBreakdown = new HashMap<>();
        nodeBreakdown.put("total", clusterStatus.getTotalNodes());
        nodeBreakdown.put("running", clusterStatus.getRunningNodes());
        nodeBreakdown.put("stopped", clusterStatus.getStoppedNodes());
        nodeBreakdown.put("healthPercentage", clusterStatus.getHealthPercentage());
        realTimeStatus.put("nodes", nodeBreakdown);

        // Service status
        Map<String, Object> services = new HashMap<>();
        services.put("mongos", clusterStatus.isMongosRunning());
        services.put("configServers", clusterStatus.getConfigServers());
        services.put("shardServers", clusterStatus.getShardServers());
        realTimeStatus.put("services", services);

        // Replica set status
        Map<String, Object> replicaSets = new HashMap<>();
        replicaSets.put("total", clusterStatus.getTotalReplicaSets());
        replicaSets.put("active", clusterStatus.getActiveReplicaSets());
        realTimeStatus.put("replicaSets", replicaSets);

        // Shard status
        Map<String, Object> shards = new HashMap<>();
        shards.put("total", clusterStatus.getTotalShards());
        shards.put("active", clusterStatus.getActiveShards());
        realTimeStatus.put("shards", shards);

        return realTimeStatus;
    }
    public Map<String, Object> getDetailedHealthCheck(ClusterConfig config) {
        Map<String, Object> detailedHealth = new HashMap<>();
        List<NodeStatus> nodeStatuses = nodeService.getNodeStatuses(config);

        // Overall health summary
        detailedHealth.put("clusterId", config.getClusterId());
        detailedHealth.put("timestamp", LocalDateTime.now().toString());

        // Node health details
        List<Map<String, Object>> nodeHealthDetails = new ArrayList<>();
        for (NodeStatus nodeStatus : nodeStatuses) {
            Map<String, Object> nodeHealth = new HashMap<>();
            nodeHealth.put("nodeId", nodeStatus.getNodeId());
            nodeHealth.put("type", nodeStatus.getType());
            nodeHealth.put("port", nodeStatus.getPort());
            nodeHealth.put("status", nodeStatus.getStatus());
            nodeHealth.put("isHealthy", nodeStatus.isHealthy());
            nodeHealth.put("replicaSet", nodeStatus.getReplicaSet());
            nodeHealth.put("lastPing", nodeStatus.getLastPing());
            nodeHealth.put("uptime", nodeStatus.getUptime());

            nodeHealthDetails.add(nodeHealth);
        }
        detailedHealth.put("nodeDetails", nodeHealthDetails);

        // Health statistics
        Map<String, Object> healthStats = new HashMap<>();
        long healthyNodes = nodeStatuses.stream().filter(NodeStatus::isHealthy).count();
        long unhealthyNodes = nodeStatuses.size() - healthyNodes;

        healthStats.put("healthyNodes", healthyNodes);
        healthStats.put("unhealthyNodes", unhealthyNodes);
        healthStats.put("totalNodes", nodeStatuses.size());
        healthStats.put("healthPercentage", !nodeStatuses.isEmpty() ? 
            ((double) healthyNodes / nodeStatuses.size() * 100) : 0);
        detailedHealth.put("healthStats", healthStats);

        // Critical services status
        Map<String, Object> criticalServices = new HashMap<>();
        criticalServices.put("mongosRunning", ProcessManager.isProcessRunning("mongos"));
        criticalServices.put("configServersHealthy", nodeStatuses.stream()
                .filter(n -> "config".equals(n.getType()))
                .allMatch(NodeStatus::isHealthy));
        criticalServices.put("shardsHealthy", nodeStatuses.stream()
                .filter(n -> "shard".equals(n.getType()))
                .allMatch(NodeStatus::isHealthy));

        detailedHealth.put("criticalServices", criticalServices);
        return detailedHealth;
    }
    public NodeStatus getIndividualNodeStatus(String nodeId, ClusterConfig config) {
        return nodeService.getNodeStatus(nodeId, config);
    }
}
