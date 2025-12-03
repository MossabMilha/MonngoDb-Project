package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClusterService {

    public ClusterConfig createCluster(String clusterId, int shards, int configServers, int replicasPerShard) {
        ClusterConfig config = new ClusterConfig();
        config.setClusterId(clusterId);
        config.setNumberOfShards(shards);
        config.setNumberOfConfigServers(configServers);
        config.setReplicaSetSize(replicasPerShard);

        List<NodeInfo> nodes = new ArrayList<>();
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

        // Create Shard nodes with replicas
        // Each shard has multiple replica nodes (e.g., shard1 has shard-1-0, shard-1-1, shard-1-2)
        for (int i = 1; i <= shards; i++) {
            String replicaSetName = "shard" + i;
            for (int r = 0; r < replicasPerShard; r++) {
                String nodeId = "shard-" + i + "-" + r;
                NodeInfo shardNode = new NodeInfo(
                        nodeId,
                        "shard",
                        currentPort++,
                        config.getBaseDataPath() + File.separator + "shard" + File.separator + nodeId
                );
                shardNode.setReplicaSet(replicaSetName);
                nodes.add(shardNode);
            }
        }

        config.setNodes(nodes);
        return config;
    }

    public boolean startCluster(ClusterConfig config){
        for(NodeInfo node : config.getNodes()){
            // Skip mongos nodes during start - they're handled separately
            if ("mongos".equals(node.getType())) {
                continue;
            }

            createDataDirectory(node.getDataPath());
            boolean started = ProcessManager.startMongodProcess(
                    node.getNodeId(),
                    node.getType(),
                    node.getPort(),
                    node.getDataPath(),
                    node.getReplicaSet()
            );
            node.setStatus(started ? "running" : "error");
        }
        return true;
    }

    private void createDataDirectory(String path){
        File directory = new File(path);
        if(!directory.exists()){
            directory.mkdirs();
            System.out.println("Created data directory: " + path);
        } else {
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
            if (!"mongos".equals(node.getType()) && ProcessManager.isProcessRunning(node.getNodeId())) {
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

            // Check if cluster is already initialized
            if (isClusterInitialized(config)) {
                System.out.println("Cluster is already initialized. Skipping initialization.");
                return true;
            }

            // Check if processes need to be started
            boolean needsStart = config.getNodes().stream()
                    .filter(node -> !"mongos".equals(node.getType()))
                    .anyMatch(node -> "stopped".equals(node.getStatus()) ||
                            !ProcessManager.isProcessRunning(node.getNodeId()));

            if (needsStart) {
                System.out.println("Starting cluster processes...");
                startCluster(config);
                System.out.println("Waiting for processes to stabilize...");
                Thread.sleep(8000); // Increased wait time
            }

            List<NodeInfo> configNodes = config.getNodes().stream()
                    .filter(node -> "config".equals(node.getType()))
                    .toList();

            List<NodeInfo> shardNodes = config.getNodes().stream()
                    .filter(node -> "shard".equals(node.getType()))
                    .toList();

            System.out.println("Found " + configNodes.size() + " config servers and " + shardNodes.size() + " shards");

            // 1. Initialize config server replica set (only if not already initialized)
            System.out.println("Step 1: Initializing config server replica set...");
            String[] configMembers = new String[configNodes.size()];
            for (int i = 0; i < configNodes.size(); i++) {
                configMembers[i] = "localhost:" + configNodes.get(i).getPort();
            }

            int firstConfigPort = configNodes.get(0).getPort();

            // Check if config replica set is already initialized
            if (!MongoConnectionUtil.isReplicaSetInitialized("localhost", firstConfigPort)) {
                boolean configInitialized = MongoConnectionUtil.initializeReplicateSet(
                        "localhost", firstConfigPort, "configReplSet", configMembers
                );

                if (!configInitialized) {
                    System.err.println("Failed to initialize config replica set");
                    return false;
                }

                System.out.println("Waiting for config replica set to be ready...");
                Thread.sleep(10000);
            } else {
                System.out.println("Config replica set already initialized");
            }

            // 2. Start mongos router (only if not already running)
            System.out.println("Step 2: Starting mongos router on port 27999...");

            if (!ProcessManager.isProcessRunning("mongos")) {
                String configDbString = "configReplSet/" + String.join(",", configMembers);
                boolean mongosStarted = ProcessManager.startMongosProcess("mongos", 27999, configDbString);

                if (!mongosStarted) {
                    System.err.println("Failed to start mongos router");
                    return false;
                }

                System.out.println("Waiting for mongos to start...");
                Thread.sleep(8000); // Increased wait time

                // Add mongos node to ClusterConfig if not already present
                boolean hasMongos = config.getNodes().stream()
                        .anyMatch(node -> "mongos".equals(node.getType()));

                if (!hasMongos) {
                    NodeInfo mongosNode = new NodeInfo("mongos", "mongos", 27999, "");
                    mongosNode.setStatus("running");
                    config.getNodes().add(mongosNode);
                }
            } else {
                System.out.println("Mongos already running");
            }

            // 3. Initialize shard replica sets and add to cluster
            System.out.println("Step 3: Initializing shard replica sets and adding to cluster...");

            // Group shard nodes by replica set name
            java.util.Map<String, List<NodeInfo>> shardsByReplicaSet = new java.util.HashMap<>();
            for (NodeInfo shardNode : shardNodes) {
                String replicaSetName = shardNode.getReplicaSet();
                shardsByReplicaSet.computeIfAbsent(replicaSetName, k -> new ArrayList<>()).add(shardNode);
            }

            // Initialize each replica set with all its members
            for (java.util.Map.Entry<String, List<NodeInfo>> entry : shardsByReplicaSet.entrySet()) {
                String shardName = entry.getKey();
                List<NodeInfo> replicaNodes = entry.getValue();

                // Build array of all members for this replica set
                String[] shardMembers = replicaNodes.stream()
                        .map(node -> "localhost:" + node.getPort())
                        .toArray(String[]::new);

                // Use the first node's port for initial connection
                int firstPort = replicaNodes.get(0).getPort();

                System.out.println("Shard " + shardName + " has " + replicaNodes.size() + " replica members: " + String.join(", ", shardMembers));

                // Check if shard replica set is already initialized
                if (!MongoConnectionUtil.isReplicaSetInitialized("localhost", firstPort)) {
                    System.out.println("Initializing shard replica set: " + shardName);
                    boolean shardInitialized = MongoConnectionUtil.initializeReplicateSet(
                            "localhost", firstPort, shardName, shardMembers
                    );

                    if (!shardInitialized) {
                        System.err.println("Failed to initialize shard: " + shardName);
                        continue;
                    }

                    // Wait longer for replica set with multiple members to elect primary
                    Thread.sleep(replicaNodes.size() > 1 ? 8000 : 3000);
                } else {
                    System.out.println("Shard " + shardName + " already initialized");
                }

                // Check if shard is already added to cluster
                if (!MongoConnectionUtil.isShardInCluster("localhost", 27999, shardName)) {
                    System.out.println("Adding shard to cluster: " + shardName);
                    // Build connection string with all replica members
                    String connectionString = shardName + "/" + String.join(",", shardMembers);
                    boolean shardAdded = MongoConnectionUtil.addShardToCluster(
                            "localhost", 27999, connectionString
                    );

                    if (!shardAdded) {
                        System.err.println("Failed to add shard to cluster: " + shardName);
                    } else {
                        Thread.sleep(2000); // Wait between shard additions
                    }
                } else {
                    System.out.println("Shard " + shardName + " already in cluster");
                }
            }

            System.out.println("=== Cluster Initialization Complete ===");
            return true;

        } catch (Exception e){
            System.err.println("Failed to initialize cluster: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if cluster is already fully initialized
     */
    private boolean isClusterInitialized(ClusterConfig config) {
        try {
            // Check if mongos is running
            if (!ProcessManager.isProcessRunning("mongos")) {
                return false;
            }

            // Check if we can connect to mongos
            boolean mongosConnected = MongoConnectionUtil.canConnect("localhost", 27999);
            if (!mongosConnected) {
                return false;
            }

            // Check if all shards are in the cluster
            List<NodeInfo> shardNodes = config.getNodes().stream()
                    .filter(node -> "shard".equals(node.getType()))
                    .toList();

            for (NodeInfo shardNode : shardNodes) {
                if (!MongoConnectionUtil.isShardInCluster("localhost", 27999, shardNode.getReplicaSet())) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteClusterData(ClusterConfig config) {
        System.out.println("=== Deleting Cluster Data ===");
        boolean allDeleted = true;

        for (NodeInfo node : config.getNodes()) {
            if (!"mongos".equals(node.getType()) && !node.getDataPath().isEmpty()) {
                try {
                    File dataDir = new File(node.getDataPath());
                    if (dataDir.exists()) {
                        System.out.println("Deleting data directory: " + dataDir.getAbsolutePath());
                        boolean deleted = deleteDirectory(dataDir);
                        if (!deleted) {
                            System.err.println("Failed to delete: " + dataDir.getAbsolutePath());
                            allDeleted = false;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error deleting data for node " + node.getNodeId() + ": " + e.getMessage());
                    allDeleted = false;
                }
            }
        }
        
        return allDeleted;
    }

    private boolean deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return directory.delete();
    }
}
