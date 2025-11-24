package com.omnexus.util;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MongoConnectionUtil {

    public static boolean initializeReplicateSet(String host, int port, String replicaSetName, String[] members){
        try(MongoClient client = MongoClients.create("mongodb://" + host + ":" + port + "/?directConnection=true")){
            MongoDatabase admin = client.getDatabase("admin");

            // Check if already initialized
            try{
                Document status = admin.runCommand(new Document("replSetGetStatus", 1));
                System.out.println("Replica set " + replicaSetName + " already initialized");
                return true;
            } catch (MongoCommandException e){
                // Error code 94 means not yet initialized
                if (e.getErrorCode() != 94) {
                    System.err.println("Error checking replica set status: " + e.getMessage());
                    return false;
                }
                // Not initialized, proceed with initialization
            }

            Document config = new Document("_id", replicaSetName);
            Document[] memberDocuments = new Document[members.length];
            for (int i = 0; i < members.length; i++) {
                memberDocuments[i] = new Document("_id", i).append("host", members[i]);
            }
            config.append("members", java.util.Arrays.asList(memberDocuments));

            Document result = admin.runCommand(new Document("replSetInitiate", config));
            System.out.println("Initialized replica set: " + replicaSetName);
            return true;

        } catch (Exception e) {
            System.err.println("Failed to initialize replica set: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addShardToCluster(String mongoHost, int mongosPort, String shardReplSet){
        try(MongoClient client = MongoClients.create("mongodb://" + mongoHost + ":" + mongosPort +
                "/?serverSelectionTimeoutMS=10000")){
            MongoDatabase admin = client.getDatabase("admin");

            Document result = admin.runCommand(new Document("addShard", shardReplSet));

            if (getOkValue(result) == 1) {
                System.out.println("Successfully added shard: " + shardReplSet);
                return true;
            } else {
                System.err.println("Failed to add shard, result: " + result.toJson());
                return false;
            }

        } catch (Exception e){
            System.err.println("Failed to add shard: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a replica set is already initialized
     */
    public static boolean isReplicaSetInitialized(String host, int port) {
        MongoClient client = null;
        try {
            client = MongoClients.create("mongodb://" + host + ":" + port +
                    "/?directConnection=true&serverSelectionTimeoutMS=5000&connectTimeoutMS=5000");
            MongoDatabase adminDb = client.getDatabase("admin");

            Document result = adminDb.runCommand(new Document("replSetGetStatus", 1));

            // If we get here without exception, replica set is initialized
            return getOkValue(result) == 1;

        } catch (MongoCommandException e) {
            // Error code 94 means "NotYetInitialized"
            if (e.getErrorCode() == 94) {
                return false;
            }
            // Other errors might mean it's initialized but we can't connect properly
            System.err.println("Error checking replica set status: " + e.getMessage());
            return false;

        } catch (Exception e) {
            System.err.println("Error connecting to check replica set: " + e.getMessage());
            return false;

        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            }
        }
    }

    /**
     * Check if can connect to a MongoDB instance
     */
    public static boolean canConnect(String host, int port) {
        MongoClient client = null;
        try {
            client = MongoClients.create("mongodb://" + host + ":" + port +
                    "/?serverSelectionTimeoutMS=5000&connectTimeoutMS=5000");

            // Try to run a simple command
            Document result = client.getDatabase("admin").runCommand(new Document("ping", 1));
            return getOkValue(result) == 1;

        } catch (Exception e) {
            System.err.println("Cannot connect to " + host + ":" + port + " - " + e.getMessage());
            return false;

        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            }
        }
    }

    /**
     * Check if a shard is already added to the cluster
     */
    public static boolean isShardInCluster(String mongosHost, int mongosPort, String shardName) {
        MongoClient client = null;
        try {
            client = MongoClients.create("mongodb://" + mongosHost + ":" + mongosPort +
                    "/?serverSelectionTimeoutMS=10000&connectTimeoutMS=10000");

            MongoDatabase configDb = client.getDatabase("config");
            MongoCollection<Document> shardsCollection = configDb.getCollection("shards");

            Document shard = shardsCollection.find(new Document("_id", shardName)).first();

            if (shard != null) {
                System.out.println("Shard " + shardName + " found in cluster");
                return true;
            } else {
                System.out.println("Shard " + shardName + " not found in cluster");
                return false;
            }

        } catch (Exception e) {
            System.err.println("Error checking if shard is in cluster: " + e.getMessage());
            return false;

        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            }
        }
    }

    public static MongoClient createClient(String host, int port){
        String uri = "mongodb://" + host + ":" + port;
        return MongoClients.create(uri);
    }

    public static MongoClient createClient(String connectionString) {
        return MongoClients.create(connectionString);
    }
    public static MongoClient createClientForShard(String shardHost) {
        // shardHost is like "shard1/localhost:28003"
        String hostOnly = shardHost.contains("/") ? shardHost.split("/")[1] : shardHost;
        String connectionString = "mongodb://" + hostOnly;
        return MongoClients.create(connectionString);
    }

    public static MongoClient createClientForNodeId(ClusterConfig config, String nodeId) {
        return config.getNodes().stream()
                .filter(n -> n.getNodeId().equals(nodeId))
                .findFirst()
                .map(n -> MongoClients.create("mongodb://localhost:" + n.getPort()))
                .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));
    }

    /**
     * Helper method to safely get the "ok" value from a MongoDB response document.
     * Handles both Integer and Double types that MongoDB might return.
     */
    private static int getOkValue(Document result) {
        Object ok = result.get("ok");
        if (ok instanceof Integer) {
            return (Integer) ok;
        } else if (ok instanceof Double) {
            return ((Double) ok).intValue();
        } else if (ok instanceof Number) {
            return ((Number) ok).intValue();
        }
        return 0;
    }

    public static MongoDatabase getDatabase(ClusterConfig clusterConfig, String dbName) {
        Optional<NodeInfo> nodeOpt = clusterConfig.getNodes().stream()
                .filter(n -> n.getStatus().equals("RUNNING")) // pick a running node
                .findFirst();

        if (nodeOpt.isEmpty()) {
            throw new RuntimeException("No running nodes found in cluster: " + clusterConfig.getClusterId());
        }

        NodeInfo node = nodeOpt.get();
        MongoClient client = createClient("localhost", node.getPort());
        return client.getDatabase(dbName);
    }
}