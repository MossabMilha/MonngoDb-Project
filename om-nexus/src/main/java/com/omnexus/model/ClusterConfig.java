package com.omnexus.model;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class ClusterConfig {
    private String clusterId;
    private int numberOfShards;
    private int numberOfConfigServers;
    private int replicaSetSize;
    private String baseDataPath;
    private int basePort;
    private List<NodeInfo> nodes;

    public ClusterConfig() {
        this.nodes = new ArrayList<>();
        this.basePort = 28000; // Changed from 27017 to avoid conflict with default MongoDB
        this.baseDataPath = System.getProperty("user.dir")+ File.separator+"data";
    }
}
