package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.util.FileManager;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClusterService {
    public ClusterConfig createCluster(String clusterId, int shards, int configServers) {
        ClusterConfig config = new ClusterConfig();
        config.setClusterId(clusterId);
        config.setNumberOfShards(shards);
        config.setNumberOfConfigServers(configServers);

        List<NodeInfo> nodes = new ArrayList<>();
        // Start from port 28000 to avoid conflict with default MongoDB on 27017
        int currentPort = 28000;

        // Create config servers
        for (int i = 1; i <= configServers; i++) {
            NodeInfo configNode = new NodeInfo(
                    "config-" + i,
                    "config",
                    currentPort++,
                    config.getBaseDataPath() + File.separator + "config" + File.separator + "configsvr" + i
            );
            configNode.setReplicaSet("configReplSet");
            nodes.add(configNode);
        }

        // Create Shard nodes
        for(int i = 1; i <= shards; i++){
            NodeInfo shardNode = new NodeInfo(
                    "shard-" + i,
                    "shard",
                    currentPort++,
                    config.getBaseDataPath() + File.separator + "shard" + File.separator + "shard" + i
            );
            shardNode.setReplicaSet("shard" + i);
            nodes.add(shardNode);
        }
        
        config.setNodes(nodes);
        return config;
    }
    public boolean startCluster(ClusterConfig config){
        for(NodeInfo node:config.getNodes()){
            createDataDirectory(node.getDataPath());
            boolean started = ProcessManager.startMongodProcess(
                    node.getNodeId(),
                    node.getType(),
                    node.getPort(),
                    node.getDataPath(),
                    node.getReplicaSet()
            );
            node.setStatus(started?"running":"error");
        }
        return true;
    }
    private void createDataDirectory(String path){
        File directory = new File(path);
        if(!directory.exists()){
            directory.mkdirs();
            System.out.println("Created data directory: " + path);
        } else {
            // Clean up any lock files from previous failed attempts
            File lockFile = new File(directory, "mongod.lock");
            if (lockFile.exists()) {
                System.out.println("Removing stale lock file: " + lockFile.getAbsolutePath());
                lockFile.delete();
            }
        }
    }

    public boolean stopCluster(ClusterConfig config) {
        System.out.println("=== Stopping Cluster ===");
        boolean allStopped = true;

        // Stop mongos first
        if (ProcessManager.isProcessRunning("mongos")) {
            System.out.println("Stopping mongos router...");
            ProcessManager.stopProcess("mongos");
        }

        // Stop all nodes
        for (NodeInfo node : config.getNodes()) {
            if (ProcessManager.isProcessRunning(node.getNodeId())) {
                System.out.println("Stopping node: " + node.getNodeId());
                boolean stopped = ProcessManager.stopProcess(node.getNodeId());
                if (stopped) {
                    node.setStatus("stopped");
                } else {
                    allStopped = false;
                    System.err.println("Failed to stop node: " + node.getNodeId());
                }
            }
        }

        System.out.println("=== Cluster Stop Complete ===");
        return allStopped;
    }
    public boolean initializeCluster(ClusterConfig config){
        try{
            System.out.println("=== Starting Cluster Initialization ===");

            // Check if processes need to be started
            boolean needsStart = config.getNodes().stream()
                    .anyMatch(node -> "stopped".equals(node.getStatus()) ||
                                     !ProcessManager.isProcessRunning(node.getNodeId()));

            if (needsStart) {
                System.out.println("Starting cluster processes...");
                startCluster(config);
                System.out.println("Waiting for processes to stabilize...");
                Thread.sleep(5000);
            }

            // Get the actual ports from config nodes
            List<NodeInfo> configNodes = config.getNodes().stream()
                    .filter(node -> "config".equals(node.getType()))
                    .toList();

            List<NodeInfo> shardNodes = config.getNodes().stream()
                    .filter(node -> "shard".equals(node.getType()))
                    .toList();

            System.out.println("Found " + configNodes.size() + " config servers and " + shardNodes.size() + " shards");

            // 1. Initialize config server replica set
            System.out.println("Step 1: Initializing config server replica set...");
            String[] configMembers = new String[configNodes.size()];
            for (int i = 0; i < configNodes.size(); i++) {
                configMembers[i] = "localhost:" + configNodes.get(i).getPort();
            }
            int firstConfigPort = configNodes.get(0).getPort();
            boolean configInitialized = MongoConnectionUtil.initializeReplicateSet("localhost", firstConfigPort, "configReplSet", configMembers);

            if (!configInitialized) {
                System.err.println("Failed to initialize config replica set");
                return false;
            }

            // Wait for config RS to be ready
            System.out.println("Waiting for config replica set to be ready...");
            Thread.sleep(10000);

            // 2. Start mongos router (use port 27999 to avoid conflicts)
            System.out.println("Step 2: Starting mongos router on port 27999...");
            String configDbString = "configReplSet/" + String.join(",", configMembers);
            boolean mongosStarted = ProcessManager.startMongosProcess("mongos", 27999, configDbString);

            if (!mongosStarted) {
                System.err.println("Failed to start mongos router");
                return false;
            }

            // Wait for mongos to start
            System.out.println("Waiting for mongos to start...");
            Thread.sleep(5000);

            // 3. Initialize shard replica sets and add to cluster
            System.out.println("Step 3: Initializing shard replica sets and adding to cluster...");
            for (int i = 0; i < shardNodes.size(); i++) {
                NodeInfo shardNode = shardNodes.get(i);
                String shardName = shardNode.getReplicaSet();
                String[] shardMembers = {"localhost:" + shardNode.getPort()};

                System.out.println("Initializing shard: " + shardName);
                // Initialize shard replica set
                boolean shardInitialized = MongoConnectionUtil.initializeReplicateSet("localhost", shardNode.getPort(), shardName, shardMembers);

                if (!shardInitialized) {
                    System.err.println("Failed to initialize shard: " + shardName);
                    continue;
                }

                // Wait a bit before adding to cluster
                Thread.sleep(3000);

                System.out.println("Adding shard to cluster: " + shardName);
                // Add shard to cluster
                boolean shardAdded = MongoConnectionUtil.addShardToCluster("localhost", 27999, shardName + "/localhost:" + shardNode.getPort());

                if (!shardAdded) {
                    System.err.println("Failed to add shard to cluster: " + shardName);
                }
            }

            System.out.println("=== Cluster Initialization Complete ===");
            return true;

        }catch (Exception e){
            System.err.println("Failed to initialize cluster: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
