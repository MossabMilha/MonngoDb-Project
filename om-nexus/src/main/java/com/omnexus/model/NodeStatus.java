package com.omnexus.model;

import lombok.Data;

@Data
public class NodeStatus {
    private String nodeId;
    private String type; // "config", "shard", "mongos"
    private int port;
    private String status; // "running", "stopped", "error"
    private String host;
    private String replicaSet;
    private long uptime;
    private String lastPing;
    private boolean isHealthy;
}
