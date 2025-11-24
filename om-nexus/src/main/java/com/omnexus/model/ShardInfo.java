package com.omnexus.model;

import lombok.Data;

@Data
public class ShardInfo {
    private String shardId;
    private String replicaSet;
    private String host;
    private int port;
    private String status; // "running", "stopped", "error"
    private long dataSize;
    private int chunkCount;
    private boolean isPrimary;
}
