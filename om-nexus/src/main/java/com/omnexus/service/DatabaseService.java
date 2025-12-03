package com.omnexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.omnexus.model.ClusterConfig;
import com.omnexus.util.MongoConnectionUtil;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class DatabaseService {


    private final ConfigServerService configServerService;
    private final ClusterService clusterService;
    private final ShardService shardService;
    @Autowired
    public DatabaseService(ConfigServerService configServerService,ClusterService clusterService,ShardService shardService){
        this.configServerService = configServerService;
        this.clusterService = clusterService;
        this.shardService = shardService;
    }
    // Enable sharding on a database
    public boolean enableSharding(String clusterId,String databaseName){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: " + clusterId);
            return false;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoDatabase admin = client.getDatabase("admin");
            Document command = new Document("enableSharding",databaseName);
            admin.runCommand(command);
            System.out.println("Sharding enabled for database: " + databaseName);
            return true;
        } catch (Exception e) {
            System.out.println("Failed to enable sharding: " + e.getMessage());
            return false;
        }
    }
    // Create a sharded collection with a shard key
    public boolean createShardedCollection(String clusterId,String databaseName,String collectionName,String shardKey,List<Object> splitValues /*optional pre-split points*/){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: " + clusterId);
            return false;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoDatabase db = client.getDatabase(databaseName);
            db.getCollection(collectionName).drop();
            System.out.println("Dropped existing collection: " + databaseName + "." + collectionName);

            MongoDatabase admin = client.getDatabase("admin");

            // Enable sharding on the database
            admin.runCommand(new Document("enableSharding", databaseName));
            // Enable sharding On the collection
            Document command = new Document("shardCollection",databaseName+"."+collectionName)
                    .append("key",new Document(shardKey,1));
            admin.runCommand(command);
            System.out.println("Sharded collection created: " + databaseName + "." + collectionName);

            //Pre-split chunks if splitValues are provided
            if(splitValues != null && !splitValues.isEmpty()){
                shardService.splitChunks(clusterId,databaseName,collectionName,shardKey,splitValues);
            }
            // move chunks to shards immediately
            shardService.moveChunksToShards(clusterId,databaseName,collectionName);
            // Start the balancer
            shardService.rebalanceShards(clusterId);

            return true;
        } catch (Exception e) {
            System.out.println("Failed to create sharded collection: " + e.getMessage());
            return false;
        }
    }
    // Insert a document into a collection
    public boolean insertDocument(String clusterId,String databaseName,String collectionName,Document document){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: " + clusterId);
            return false;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoDatabase database = client.getDatabase(databaseName);
            database.getCollection(collectionName).insertOne(document);
            System.out.println("Document inserted into: " + databaseName + "." + collectionName);
            return true;
        } catch (Exception e) {
            System.out.println("Failed to insert document: " + e.getMessage());
            return false;
        }
    }
    // List all databases in the cluster
    public List<String> listDatabases(String clusterId){
        List<String> dbNames = new ArrayList<>();
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: "+clusterId);
            return dbNames;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoIterable<String> databases = client.listDatabaseNames();
            databases.into(dbNames);
        } catch (Exception e) {
            System.out.println("Failed to list databases: " + e.getMessage());
        }
        return dbNames;
    }

    // List all databases with sharding status
    public List<Map<String, Object>> listDatabasesWithStatus(String clusterId){
        List<Map<String, Object>> result = new ArrayList<>();
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: "+clusterId);
            return result;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            // Get all database names
            List<String> dbNames = new ArrayList<>();
            client.listDatabaseNames().into(dbNames);

            // Get sharding status from config.databases
            MongoDatabase configDb = client.getDatabase("config");
            MongoCollection<Document> databasesCollection = configDb.getCollection("databases");
            MongoCollection<Document> collectionsConfig = configDb.getCollection("collections");

            for(String dbName : dbNames) {
                Map<String, Object> dbInfo = new HashMap<>();
                dbInfo.put("name", dbName);

                boolean hasSharding = false;
                String primaryShard = null;

                // Check if database has sharding enabled from config.databases
                Document dbEntry = databasesCollection.find(Filters.eq("_id", dbName)).first();
                if(dbEntry != null) {
                    // In MongoDB 5.0+, 'partitioned' field may not exist. Check for it safely.
                    Object partitionedObj = dbEntry.get("partitioned");
                    if (partitionedObj instanceof Boolean && (Boolean) partitionedObj) {
                        hasSharding = true;
                    }
                    primaryShard = dbEntry.getString("primary");
                }

                // Also check if database has any sharded collections in config.collections
                // This is more reliable for MongoDB 5.0+ where 'partitioned' may not be set
                if (!hasSharding) {
                    String prefix = dbName + ".";
                    long shardedCollCount = collectionsConfig.countDocuments(
                        Filters.and(
                            Filters.regex("_id", "^" + prefix),
                            Filters.exists("key")
                        )
                    );
                    if (shardedCollCount > 0) {
                        hasSharding = true;
                        System.out.println("Database " + dbName + " has " + shardedCollCount + " sharded collection(s)");
                    }
                }

                dbInfo.put("shardingEnabled", hasSharding);
                dbInfo.put("primaryShard", primaryShard);

                result.add(dbInfo);
            }
        } catch (Exception e) {
            System.out.println("Failed to list databases with status: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
    // Get collection Statistics
    public Document getCollectionStats(String clusterId,String databaseName,String collectionName){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: "+clusterId);
            return null;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoDatabase database = client.getDatabase(databaseName);
            Document statsCommand = new Document("collStats",collectionName);
            return database.runCommand(statsCommand);
        } catch (Exception e) {
            System.out.println("Failed to get collection stats: " + e.getMessage());
            return null;
        }
    }
    // List collections in a database with sharding status
    public Map<String, Object> listCollectionsWithStatus(String clusterId, String databaseName) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> collections = new HashMap<>();

        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if (clusterConfig == null) {
            System.out.println("Cluster not found: " + clusterId);
            return Map.of("error", "Cluster not found: " + clusterId);
        }

        try (MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig, "mongos")) {
            MongoDatabase database = client.getDatabase(databaseName);
            MongoDatabase configDb = client.getDatabase("config");

            // Get sharded collections from config.collections
            MongoCollection<Document> configCollections = configDb.getCollection("collections");
            List<Document> shardedCollections = configCollections.find().into(new ArrayList<>());

            // Create a set of sharded collection namespaces
            java.util.Set<String> shardedNamespaces = new java.util.HashSet<>();
            for (Document doc : shardedCollections) {
                String id = doc.getString("_id");
                if (id != null && id.startsWith(databaseName + ".")) {
                    shardedNamespaces.add(id);
                }
            }

            // List all collections in the database
            for (String collName : database.listCollectionNames()) {
                Map<String, Object> collInfo = new HashMap<>();
                String namespace = databaseName + "." + collName;
                boolean isSharded = shardedNamespaces.contains(namespace);
                collInfo.put("sharded", isSharded);
                collections.put(collName, collInfo);
            }

            result.put("database", databaseName);
            result.put("collections", collections);

        } catch (Exception e) {
            System.out.println("Failed to list collections: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }

        return result;
    }

    // Get detailed shard distribution for a database
    public Map<String,Object> getShardDistribution(String clusterId,String databaseName){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: "+clusterId);
            return Map.of("error","Cluster not found: "+clusterId);
        }
        Map<String,Object> distribution = new HashMap<>();

        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoDatabase configDb = client.getDatabase("config");
            // Get All Shards
            MongoCollection<Document> shardsCollection = configDb.getCollection("shards");
            MongoIterable<Document> shards = shardsCollection.find();
            for(Document shard : shards){
                String shardId = shard.getString("_id");
                String host = shard.getString("host");

                Map<String,Object> shardInfo  = new HashMap<>();
                shardInfo.put("host",host);

                //Count chunks on this shard
                MongoCollection<Document> chunksCollection = configDb.getCollection("chunks");
                long chunkCount = chunksCollection.countDocuments(Filters.eq("shard",shardId));
                shardInfo.put("chunkCount",chunkCount);

                try(MongoClient shardClient = MongoConnectionUtil.createClientForShard(host)){
                    MongoDatabase shardDb = shardClient.getDatabase(databaseName);
                    long totalSize = 0;
                    for (String colName : shardDb.listCollectionNames()) {
                        Document stats = shardDb.runCommand(new Document("collStats", colName));
                        totalSize += ((Number) stats.get("size")).longValue();
                    }
                    shardInfo.put("dataSize", totalSize);
                }
                distribution.put(shardId, shardInfo);
            }
        } catch (Exception e) {
            System.out.println("Failed to get shard distribution: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }
        return distribution;
    }
    // Move a chunk from one shard to another
    public boolean moveChunk(String clusterId,String databaseName,String collectionName,String shardKey,Object  shardKeyValue,String toShard){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if (clusterConfig == null) {
            System.out.println("Cluster not found: " + clusterId);
            return false;
        }
        try (MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig, "mongos")) {
            MongoDatabase admin = client.getDatabase("admin");

            // Build moveChunk command with generic Object
            Document moveChunkCommand = new Document("moveChunk", databaseName + "." + collectionName)
                    .append("find", new Document(shardKey, shardKeyValue))
                    .append("to", toShard);
            admin.runCommand(moveChunkCommand);

            System.out.println("Chunk moved to shard: " + toShard);
            return true;
        } catch (Exception e) {
            System.out.println("Failed to move chunk: " + e.getMessage());
            return false;
        }
    }
    // Get detailed shard distribution per collection
    public Map<String,Object> getShardDistributionPerCollection(String clusterId,String databaseName,String collectionName){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if (clusterConfig == null){
            System.out.println("Cluster not found: " + clusterId);
            return Map.of("error", "Cluster not found: " + clusterId);
        }
        Map<String, Object> distribution = new HashMap<>();

        try (MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig, "mongos")) {
            MongoDatabase configDb = client.getDatabase("config");

            MongoCollection<Document> chunksCollection = configDb.getCollection("chunks");
            MongoIterable<Document> shards = configDb.getCollection("shards").find();

            // Get all chunks for this collection (handles both UUID and namespace-based storage)
            String namespace = databaseName + "." + collectionName;
            List<Document> allChunks = getChunksForCollection(configDb, chunksCollection, namespace);

            for (Document shard : shards) {
                String shardId = shard.getString("_id");
                String host = shard.getString("host");

                // Count chunks for this shard from the loaded chunks
                long chunkCount = allChunks.stream()
                        .filter(chunk -> shardId.equals(chunk.getString("shard")))
                        .count();

                Map<String, Object> shardInfo = new HashMap<>();
                shardInfo.put("host", host);
                shardInfo.put("chunkCount", chunkCount);

                try (MongoClient shardClient = MongoConnectionUtil.createClientForShard(host)) {
                    MongoDatabase shardDb = shardClient.getDatabase(databaseName);
                    long totalSize = 0;
                    if (shardDb.listCollectionNames().into(new ArrayList<>()).contains(collectionName)) {
                        Document stats = shardDb.runCommand(new Document("collStats", collectionName));
                        Number sizeNum = (Number) stats.get("size");  // works for Integer or Long
                        totalSize = sizeNum.longValue();
                    }
                    shardInfo.put("dataSize", totalSize);
                }

                distribution.put(shardId, shardInfo);
            }
        } catch (Exception e) {
            System.out.println("Failed to get shard distribution: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }
        return distribution;
    }
    public long bulkInsertJson(String clusterId, String dbName, String collectionName, MultipartFile file, int batchSize) throws Exception {
        System.out.println("Bulk Insert Json started");

        MongoCollection<Document> collection = getCollection(clusterId, dbName, collectionName);
        long totalInserted = 0;
        List<Document> batch = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        try (InputStream inputStream = file.getInputStream()) {
            System.out.println("InputStream obtained from MultipartFile: " + file.getOriginalFilename());
            JsonNode root = mapper.readTree(inputStream);
            System.out.println("JSON root node type: " + root.getNodeType());

            if (!root.isArray()) {
                throw new IllegalArgumentException("JSON file must contain an array at the root.");
            }

            for (JsonNode node : root) {
                System.out.println("Parsing node: " + node.toString());
                Document doc = Document.parse(node.toString());
                batch.add(doc);

                if (batch.size() >= batchSize) {
                    System.out.println("Inserting batch of size: " + batch.size());
                    collection.insertMany(batch);
                    totalInserted += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                System.out.println("Inserting final batch of size: " + batch.size());
                collection.insertMany(batch);
                totalInserted += batch.size();
            }
        } catch (Exception e) {
            System.out.println("Exception during bulk insert: " + e.getMessage());
            e.printStackTrace();
            throw e; // rethrow so controller can catch it
        }

        System.out.println("Total documents inserted: " + totalInserted);
        return totalInserted;
    }
    // Utility method to get MongoCollection from clusterId/dbName/collectionName
    private MongoCollection<Document> getCollection(String clusterId, String dbName, String collectionName) {
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) {
            System.out.println("ClusterConfig is null for clusterId: " + clusterId);
            throw new RuntimeException("Cluster not found: " + clusterId);
        } else {
            System.out.println("ClusterConfig loaded: " + config);
        }

        MongoDatabase db;
        try {
            db = MongoConnectionUtil.getDatabase(config, dbName);
            if (db == null) {
                System.out.println("MongoDatabase is null for database: " + dbName);
                throw new RuntimeException("Database not found: " + dbName);
            } else {
                System.out.println("MongoDatabase obtained: " + db.getName());
            }
        } catch (Exception e) {
            System.out.println("Failed to get MongoDatabase: " + e.getMessage());
            throw e;
        }

        MongoCollection<Document> collection;
        try {
            collection = db.getCollection(collectionName);
            System.out.println("MongoCollection obtained: " + collection.getNamespace());
        } catch (Exception e) {
            System.out.println("Failed to get collection: " + e.getMessage());
            throw e;
        }

        return collection;
    }
    public List<Map<String, Object>> listDocumentsWithShard(String clusterId, String databaseName, String collectionName, String shardKey) {
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if (clusterConfig == null) {
            System.out.println("Cluster not found: " + clusterId);
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        try (MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig, "mongos")) {
            MongoDatabase configDatabase = client.getDatabase("config");
            MongoCollection<Document> chunksCollection = configDatabase.getCollection("chunks");

            MongoDatabase database = client.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);


            String namespace = databaseName + "." + collectionName;
            List<Document> chunksList = getChunksForCollection(configDatabase, chunksCollection, namespace);
            System.out.println("Found " + chunksList.size() + " chunks for collection " + collectionName);

            String primaryShard = getPrimaryShardForDatabase(configDatabase, databaseName);
            System.out.println("Primary shard for database " + databaseName + ": " + primaryShard);

            for (Document document : collection.find()) {
                Object shardKeyValueObj = document.get(shardKey);
                String shardId;

                if (chunksList.isEmpty()) {

                    shardId = primaryShard != null ? primaryShard : "unknown";
                } else if (shardKeyValueObj != null) {
                    shardId = findShardForValue(chunksList, shardKey, shardKeyValueObj);
                    if ("unknown".equals(shardId) && primaryShard != null) {
                        shardId = primaryShard;
                    }
                } else {
                    shardId = primaryShard != null ? primaryShard : "unknown";
                }

                Map<String, Object> documentInfo = new HashMap<>();
                documentInfo.put("document", convertDocumentForJson(document));
                documentInfo.put("shard", shardId);

                result.add(documentInfo);
            }
        } catch (Exception e) {
            System.out.println("Failed to list documents with shard info: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Get chunks for a collection, handling both MongoDB 5.0+ (UUID-based) and older (namespace-based) formats.
     * In MongoDB 8.0, chunks are stored with UUID reference instead of namespace string.
     */
    private List<Document> getChunksForCollection(MongoDatabase configDatabase, MongoCollection<Document> chunksCollection, String namespace) {
        // First, try to get chunks by namespace (older MongoDB versions)
        List<Document> chunksList = chunksCollection.find(Filters.eq("ns", namespace)).into(new ArrayList<>());

        if (!chunksList.isEmpty()) {
            return chunksList;
        }

        // For MongoDB 5.0+, chunks use UUID. We need to look up the collection's UUID first.
        try {
            MongoCollection<Document> collectionsConfig = configDatabase.getCollection("collections");
            Document collectionEntry = collectionsConfig.find(Filters.eq("_id", namespace)).first();

            if (collectionEntry != null) {
                Object uuid = collectionEntry.get("uuid");
                if (uuid != null) {
                    System.out.println("Looking up chunks by UUID for " + namespace + ": " + uuid);
                    chunksList = chunksCollection.find(Filters.eq("uuid", uuid)).into(new ArrayList<>());
                }
            }
        } catch (Exception e) {
            System.out.println("Error looking up chunks by UUID: " + e.getMessage());
        }

        // If still empty, the collection might have only one implicit chunk (all data on one shard)
        // In this case, we need to get the shard information from the collections config
        if (chunksList.isEmpty()) {
            System.out.println("No explicit chunks found for " + namespace + ", collection might be unsharded or has implicit single chunk");
        }

        return chunksList;
    }

    /**
     * Get the primary shard for a database from config.databases collection.
     */
    private String getPrimaryShardForDatabase(MongoDatabase configDatabase, String databaseName) {
        try {
            MongoCollection<Document> databasesCollection = configDatabase.getCollection("databases");
            Document dbEntry = databasesCollection.find(Filters.eq("_id", databaseName)).first();
            if (dbEntry != null) {
                return dbEntry.getString("primary");
            }
        } catch (Exception e) {
            System.out.println("Error getting primary shard for database " + databaseName + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Find which shard a value belongs to by checking chunk ranges.
     * A document belongs to a chunk where: min <= value < max
     */
    private String findShardForValue(List<Document> chunks, String shardKey, Object value) {
        for (Document chunk : chunks) {
            Document min = (Document) chunk.get("min");
            Document max = (Document) chunk.get("max");

            if (min == null || max == null) continue;

            Object minValue = min.get(shardKey);
            Object maxValue = max.get(shardKey);

            if (isValueInRange(value, minValue, maxValue)) {
                return chunk.getString("shard");
            }
        }
        return "unknown";
    }

    /**
     * Check if value is in range [minValue, maxValue)
     * Handles both numeric and string shard keys, and special MongoDB min/max key values
     */
    private boolean isValueInRange(Object value, Object minValue, Object maxValue) {
        // Handle MongoDB's special MinKey and MaxKey
        boolean minIsMinKey = isMinKey(minValue);
        boolean maxIsMaxKey = isMaxKey(maxValue);

        // If min is MinKey and max is MaxKey, all values are in range
        if (minIsMinKey && maxIsMaxKey) {
            return true;
        }

        // Check min boundary: value >= minValue (or minValue is MinKey)
        boolean aboveMin = minIsMinKey || compareValues(value, minValue) >= 0;

        // Check max boundary: value < maxValue (or maxValue is MaxKey)
        boolean belowMax = maxIsMaxKey || compareValues(value, maxValue) < 0;

        return aboveMin && belowMax;
    }

    private boolean isMinKey(Object value) {
        if (value == null) return false;
        String className = value.getClass().getName();
        return className.contains("MinKey") ||
               (value instanceof Document && ((Document)value).containsKey("$minKey"));
    }

    private boolean isMaxKey(Object value) {
        if (value == null) return false;
        String className = value.getClass().getName();
        return className.contains("MaxKey") ||
               (value instanceof Document && ((Document)value).containsKey("$maxKey"));
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        // Handle numeric comparison
        if (a instanceof Number && b instanceof Number) {
            double aDouble = ((Number) a).doubleValue();
            double bDouble = ((Number) b).doubleValue();
            return Double.compare(aDouble, bDouble);
        }

        // Handle string comparison
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }

        // Handle Comparable types
        if (a instanceof Comparable && a.getClass().equals(b.getClass())) {
            return ((Comparable<Object>) a).compareTo(b);
        }

        // Fallback to string comparison
        return a.toString().compareTo(b.toString());
    }

    /**
     * Convert a Document for JSON serialization, handling ObjectId properly
     */
    private Map<String, Object> convertDocumentForJson(Document document) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof org.bson.types.ObjectId) {
                result.put(entry.getKey(), value.toString());
            } else if (value instanceof Document) {
                result.put(entry.getKey(), convertDocumentForJson((Document) value));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }




}
