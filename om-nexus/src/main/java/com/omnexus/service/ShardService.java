package com.omnexus.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.model.ShardInfo;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShardService {
    private final ConfigServerService configServerService;

    @Autowired
    public ShardService(ConfigServerService configServerService,ClusterService clusterService){
        this.configServerService = configServerService;
    }
    public List<ShardInfo> getShardStatus(ClusterConfig config) {
        List<ShardInfo> shardInfos = new ArrayList<>();

        for (NodeInfo node : config.getNodes()) {
            if (!"shard".equals(node.getType())) continue;

            ShardInfo shardInfo = new ShardInfo();
            shardInfo.setShardId(node.getNodeId());
            shardInfo.setReplicaSet(node.getReplicaSet());
            shardInfo.setHost("localhost");
            shardInfo.setPort(node.getPort());
            shardInfo.setPrimary(true);

            // Try to connect to determine actual status
            try (MongoClient client = MongoConnectionUtil.createClient("localhost", node.getPort())) {
                MongoDatabase adminDb = client.getDatabase("admin");
                Document shardStats = adminDb.runCommand(new Document("serverStatus", 1));

                // If we can run a command, the shard is running
                shardInfo.setStatus("running");

                Document shardingMetrics = shardStats.get("sharding", Document.class);
                if (shardingMetrics != null) {
                    shardInfo.setDataSize(shardingMetrics.get("dataSize", 0L));
                    shardInfo.setChunkCount(shardingMetrics.get("chunks", 0));
                } else {
                    shardInfo.setDataSize(0L);
                    shardInfo.setChunkCount(0);
                }
            } catch (Exception e) {
                // If connection fails, check process manager as fallback
                shardInfo.setStatus(ProcessManager.isProcessRunning(node.getNodeId()) ? "running" : "stopped");
                shardInfo.setDataSize(0L);
                shardInfo.setChunkCount(0);
            }

            shardInfos.add(shardInfo);
        }

        return shardInfos;
    }
    public boolean addShardToCluster(ClusterConfig config, String shardConnectionString) {
        int mongosPort = getMongosPort(config);
        if (mongosPort == -1) {
            System.out.println("No mongos found in cluster config");
            return false;
        }
        return MongoConnectionUtil.addShardToCluster("localhost", mongosPort, shardConnectionString);
    }

    private int getMongosPort(ClusterConfig config) {
        for (NodeInfo node : config.getNodes()) {
            if ("mongos".equals(node.getType())) {
                return node.getPort();
            }
        }
        return -1;
    }

    public boolean removeShardFromCluster(ClusterConfig config, String shardId) {
        int mongosPort = getMongosPort(config);
        if (mongosPort == -1) {
            System.out.println("No mongos found in cluster config");
            return false;
        }
        try {
            try (MongoClient client = MongoConnectionUtil.createClient("localhost", mongosPort)) {
                MongoDatabase adminDb = client.getDatabase("admin");

                Document cmd = new Document("removeShard", shardId);
                Document result = adminDb.runCommand(cmd);
                String state = result.getString("state");
                System.out.println("Remove Shard Response: " + result.toJson());

                if("completed".equalsIgnoreCase(state)){
                    System.out.println("Shard " + shardId + " successfully removed.");
                    
                    // Remove from local config after successful MongoDB removal
                    config.getNodes().removeIf(node -> 
                        shardId.equals(node.getNodeId()) && "shard".equals(node.getType()));
                    
                    return true;
                }
                if ("started".equalsIgnoreCase(state) || "ongoing".equalsIgnoreCase(state)) {
                    System.out.println("Shard " + shardId + " is being drained.");
                    return false;
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Failed to remove shard " + shardId + ": " + e.getMessage());
            return false;
        }
    }
    public String buildShardConnectionString(String shardId, ClusterConfig config) {
        NodeInfo shardNode = config.getNodes().stream()
                .filter(n -> n.getNodeId().equals(shardId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Shard not found: " + shardId));

        return shardNode.getReplicaSet() + "/localhost:" + shardNode.getPort();
    }
    public boolean splitChunks(String clusterId,String databaseName,String collectionName,String shardKey,List<Object> splitValues){
        try{
            ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);

            try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
                MongoDatabase adminDb = client.getDatabase("admin");

                for (Object splitValue : splitValues) {
                    Document middle = new Document(shardKey,splitValue);
                    Document cmd = new Document("split",databaseName+"."+collectionName).append("middle",middle);
                    System.out.println("Running split command: " + cmd.toJson());
                    Document response =adminDb.runCommand(cmd);
                    System.out.println("Split response: " + response.toJson());
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Move chunks to distribute them evenly across shards.
     * Uses UUID-based lookup for MongoDB 8.0 compatibility.
     */
    public boolean moveChunksToShards(String clusterId, String databaseName, String collectionName) {
        try {
            ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);

            try (MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig, "mongos")) {
                MongoDatabase configDb = client.getDatabase("config");
                MongoDatabase adminDb = client.getDatabase("admin");

                // Get all available shards
                List<String> shardNames = getAvailableShards(clusterConfig);
                if (shardNames.size() < 2) {
                    System.out.println("Need at least 2 shards to distribute chunks");
                    return false;
                }

                // Get chunks for this collection (MongoDB 8.0 compatible)
                List<Document> chunks = getChunksForCollection(configDb, databaseName, collectionName);
                System.out.println("Found " + chunks.size() + " chunks to distribute");

                if (chunks.isEmpty()) {
                    System.out.println("No chunks found for " + databaseName + "." + collectionName);
                    return false;
                }

                // Distribute chunks evenly across shards using round-robin
                int shardIndex = 0;
                for (Document chunk : chunks) {
                    String currentShard = chunk.getString("shard");
                    String targetShard = shardNames.get(shardIndex % shardNames.size());

                    // Only move if it's on a different shard
                    if (!targetShard.equals(currentShard)) {
                        Document min = (Document) chunk.get("min");
                        if (min != null && !min.isEmpty()) {
                            String shardKey = min.keySet().iterator().next();
                            Object shardKeyValue = min.get(shardKey);

                            // Skip MinKey values for movement
                            if (!isMinKey(shardKeyValue)) {
                                try {
                                    Document moveCmd = new Document("moveChunk", databaseName + "." + collectionName)
                                            .append("find", new Document(shardKey, shardKeyValue))
                                            .append("to", targetShard);
                                    System.out.println("Moving chunk with " + shardKey + "=" + shardKeyValue + " to " + targetShard);
                                    adminDb.runCommand(moveCmd);
                                } catch (Exception e) {
                                    System.out.println("Failed to move chunk: " + e.getMessage());
                                }
                            }
                        }
                    }
                    shardIndex++;
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get chunks for a collection - handles both namespace and UUID-based storage.
     */
    private List<Document> getChunksForCollection(MongoDatabase configDb, String databaseName, String collectionName) {
        String namespace = databaseName + "." + collectionName;
        MongoCollection<Document> chunksCollection = configDb.getCollection("chunks");

        // Try namespace-based lookup first
        List<Document> chunks = chunksCollection.find(Filters.eq("ns", namespace)).into(new ArrayList<>());

        if (!chunks.isEmpty()) {
            return chunks;
        }

        // Try UUID-based lookup for MongoDB 5.0+
        try {
            MongoCollection<Document> collectionsConfig = configDb.getCollection("collections");
            Document collEntry = collectionsConfig.find(Filters.eq("_id", namespace)).first();

            if (collEntry != null) {
                Object uuid = collEntry.get("uuid");
                if (uuid != null) {
                    chunks = chunksCollection.find(Filters.eq("uuid", uuid)).into(new ArrayList<>());
                }
            }
        } catch (Exception e) {
            System.out.println("Error looking up chunks by UUID: " + e.getMessage());
        }

        return chunks;
    }

    private boolean isMinKey(Object value) {
        if (value == null) return false;
        String className = value.getClass().getName();
        return className.contains("MinKey");
    }

    /**
     * Get shard names as registered in MongoDB (uses replicaSet name, not nodeId).
     * MongoDB registers shards by their replica set name, e.g., "shard1" not "shard-1".
     */
    private List<String> getAvailableShards(ClusterConfig clusterConfig) {
        List<String> shardNames = new ArrayList<>();
        for (NodeInfo node : clusterConfig.getNodes()) {
            if ("shard".equals(node.getType())) {
                // Use replicaSet name which is how MongoDB identifies the shard
                shardNames.add(node.getReplicaSet());
            }
        }
        return shardNames;
    }

    public boolean rebalanceShards(String clusterId) {
        try {
            ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
            try (MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig, "mongos")) {
                MongoDatabase adminDb = client.getDatabase("admin");
                Document cmd = new Document("balancerStart", 1);
                adminDb.runCommand(cmd);
                System.out.println("Balancer started for cluster: " + clusterId);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Split and distribute chunks for a collection based on split points.
     * This creates multiple chunks and distributes them across shards.
     */
    public boolean splitAndDistribute(String clusterId, String databaseName, String collectionName,
                                       String shardKey, List<Object> splitPoints) {
        try {
            // First, split the chunks at the given points
            if (splitPoints != null && !splitPoints.isEmpty()) {
                boolean splitSuccess = splitChunks(clusterId, databaseName, collectionName, shardKey, splitPoints);
                if (!splitSuccess) {
                    System.out.println("Warning: Some splits may have failed");
                }
            }

            // Then distribute the chunks across shards
            return moveChunksToShards(clusterId, databaseName, collectionName);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean createAndAddNewShard(String clusterId, String shardId, ClusterConfig config) {
        try {
            System.out.println("=== Creating new shard: " + shardId + " for cluster: " + clusterId + " ===");

            // Check if shard already exists in config
            boolean shardExists = config.getNodes().stream()
                    .anyMatch(n -> n.getNodeId().equals(shardId) && "shard".equals(n.getType()));

            if (shardExists) {
                System.out.println("ERROR: Shard " + shardId + " already exists in cluster config");
                return false;
            }

            // Find next available port
            int nextPort = config.getNodes().stream()
                    .mapToInt(NodeInfo::getPort)
                    .max()
                    .orElse(28000) + 1;

            System.out.println("Using port: " + nextPort);

            // Create replica set name (use simple name without hyphens for MongoDB compatibility)
            String replicaSetName = shardId.replace("-", "") + "Rs";
            System.out.println("Replica set name: " + replicaSetName);

            // Create new shard node
            NodeInfo newShard = new NodeInfo(
                    shardId,
                    "shard",
                    nextPort,
                    config.getBaseDataPath() + File.separator + "shard" + File.separator + shardId
            );
            newShard.setReplicaSet(replicaSetName);
            newShard.setStatus("stopped");

            System.out.println("Data path: " + newShard.getDataPath());

            // Add to config
            config.getNodes().add(newShard);

            // Start the new shard process
            System.out.println("Starting mongod process...");
            boolean started = ProcessManager.startMongodProcess(
                    newShard.getNodeId(),
                    newShard.getType(),
                    newShard.getPort(),
                    newShard.getDataPath(),
                    newShard.getReplicaSet()
            );

            if (!started) {
                System.out.println("ERROR: Failed to start mongod process for " + shardId);
                // Remove from config if start failed
                config.getNodes().removeIf(n -> n.getNodeId().equals(shardId));
                return false;
            }
            System.out.println("Mongod process started successfully");

            // Initialize replica set for new shard
            System.out.println("Waiting for mongod to be ready...");
            Thread.sleep(3000);

            System.out.println("Initializing replica set...");
            boolean rsInit = MongoConnectionUtil.initializeReplicateSet(
                    "localhost", nextPort, replicaSetName,
                    new String[]{"localhost:" + nextPort}
            );

            if (!rsInit) {
                System.out.println("ERROR: Failed to initialize replica set for " + shardId);
                return false;
            }
            System.out.println("Replica set initialized successfully");

            // Add to MongoDB cluster
            System.out.println("Adding shard to cluster...");
            Thread.sleep(2000);
            String shardConnectionString = replicaSetName + "/localhost:" + nextPort;
            System.out.println("Shard connection string: " + shardConnectionString);

            boolean added = addShardToCluster(config, shardConnectionString);

            if (added) {
                newShard.setStatus("running");
                System.out.println("SUCCESS: Shard " + shardId + " added to cluster");
            } else {
                System.out.println("ERROR: Failed to add shard to cluster");
            }

            return added;

        } catch (Exception e) {
            System.out.println("ERROR: Exception while creating shard: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get a specific shard by ID
     */
    public ShardInfo getShardById(ClusterConfig config, String shardId) {
        for (NodeInfo node : config.getNodes()) {
            if ("shard".equals(node.getType()) && node.getNodeId().equals(shardId)) {
                ShardInfo shardInfo = new ShardInfo();
                shardInfo.setShardId(node.getNodeId());
                shardInfo.setReplicaSet(node.getReplicaSet());
                shardInfo.setHost("localhost");
                shardInfo.setPort(node.getPort());
                shardInfo.setStatus(node.getStatus());
                return shardInfo;
            }
        }
        return null;
    }
}
