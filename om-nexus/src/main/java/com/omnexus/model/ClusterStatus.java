package com.omnexus.model;

import lombok.Data;

import java.util.List;

@Data
public class ClusterStatus {
    private String clusterId;
    private String status;
    private int totalNodes;
    private int runningNodes;
    private int stoppedNodes;
    private boolean mongosRunning;
    private List<NodeStatus> nodesStatuses;
    private String lastUpdate;

    // Additional monitoring fields
    private int configServers;
    private int shardServers;
    private double healthPercentage;
    private String uptime;
    private boolean isHealthy;

    // Replica set status
    private int activeReplicaSets;
    private int totalReplicaSets;

    // Shard distribution
    private int totalShards;
    private int activeShards;

    public boolean isHealthy() {
        return "running".equals(status) && mongosRunning && runningNodes == totalNodes;
    }

    public double getHealthPercentage() {
        return totalNodes > 0 ? (double) runningNodes / totalNodes * 100 : 0;
    }

}
