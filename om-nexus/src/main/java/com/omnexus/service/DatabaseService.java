package com.omnexus.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
    @Autowired
    public DatabaseService(ConfigServerService configServerService,ClusterService clusterService){
        this.configServerService = configServerService;
        this.clusterService = clusterService;
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
    public boolean createShardedCollection(String clusterId,String databaseName,String collectionName,String shardKey){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            System.out.println("Cluster not found: " + clusterId);
            return false;
        }
        try(MongoClient client = MongoConnectionUtil.createClientForNodeId(clusterConfig,"mongos")){
            MongoDatabase admin = client.getDatabase("admin");
            // Enable sharding On the collection
            Document command = new Document("shardCollection",databaseName+"."+collectionName)
                    .append("key",new Document(shardKey,1));
            admin.runCommand(command);
            System.out.println("Sharded collection created: " + databaseName + "." + collectionName);
            return true;
        } catch (Exception e) {
            System.out.println("Sharded collection created: " + databaseName + "." + collectionName);
            return true;
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

            for (Document shard : shards) {
                String shardId = shard.getString("_id");
                String host = shard.getString("host");

                long chunkCount = chunksCollection.countDocuments(Filters.and(
                        Filters.eq("shard", shardId),
                        Filters.eq("ns", databaseName + "." + collectionName)
                ));

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
        MongoCollection<Document> collection = getCollection(clusterId,dbName,collectionName);
        long totalInserted = 0;
        List<Document> batch = new ArrayList<>();
        JsonFactory factory = new JsonFactory();
        try(InputStream inputStream = file.getInputStream(); JsonParser parser = factory.createParser(inputStream)){
            // Expecting JSON array at the root
            if(parser.nextToken() != JsonToken.START_ARRAY){
                throw new IllegalArgumentException("JSON file must contain an array of documents at the root.");
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Document doc = Document.parse(parser.readValueAsTree().toString());
                batch.add(doc);

                if (batch.size() >= batchSize) {
                    collection.insertMany(batch);
                    totalInserted += batch.size();
                    batch.clear();
                }
            }
            // Insert remaining documents
            if (!batch.isEmpty()) {
                collection.insertMany(batch);
                totalInserted += batch.size();
            }
            return totalInserted;
        }
    }
    // Utility method to get MongoCollection from clusterId/dbName/collectionName
    private MongoCollection<Document> getCollection(String clusterId, String dbName, String collectionName) {
        ClusterConfig config = configServerService.loadClusterConfig(clusterId); // get the ClusterConfig
        return MongoConnectionUtil.getDatabase(config, dbName).getCollection(collectionName);
    }
}
