package com.omnexus.util;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;




public class MongoConnectionUtil {
    public static boolean initializeReplicateSet(String host,int port,String replicaSetName,String[] members){
        try(MongoClient client = MongoClients.create("mongodb://" + host + ":" + port)){
            MongoDatabase admin = client.getDatabase("admin");
            // Check if already initialized
            try{
                Document status = admin.runCommand(new Document("replSetGetStatus",1));
                System.out.println("Replica set " + replicaSetName + " already initialized");
                return true;
            }catch (Exception e){
                // Not initialized, proceed with initialization
            }


            Document config = new Document("_id",replicaSetName);
            Document[] memberDocuments = new Document[members.length];
            for (int i = 0; i < members.length; i++) {
                memberDocuments[i] = new Document("_id",i).append("host",members[i]);
            }
            config.append("members", java.util.Arrays.asList(memberDocuments));
            admin.runCommand(new Document("replSetInitiate",config));
            return true;
        } catch (Exception e) {
            System.out.println("Failed to initialize replica set: " + e.getMessage());
            return false;
        }
    }
    public static boolean addShardToCluster(String mongoHost,int mongosPort,String shardReplSet){
        try(MongoClient client = MongoClients.create("mongodb://" + mongoHost + ":" + mongosPort)){
            MongoDatabase admin = client.getDatabase("admin");
            admin.runCommand(new Document("addShard",shardReplSet));
            return true;
        }catch (Exception e){
            System.out.println("Failed to add shard: " + e.getMessage());
            return false;
        }

    }
}
