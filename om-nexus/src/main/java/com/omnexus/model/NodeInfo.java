package com.omnexus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeInfo {
    private String nodeId;
    private String type; // "shard", "config", "mongos"
    private int port;
    private String status;// "running","stopped",error
    private String dataPath;
    private String replicaSet;
    public NodeInfo(String nodeId, String type, int port, String dataPath) {
        this.nodeId = nodeId;
        this.type = type;
        this.port = port;
        this.dataPath = dataPath;
        this.status = "stopped";
    }


}
