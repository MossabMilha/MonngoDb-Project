package com.omnexus.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.model.NodeStatus;
import com.omnexus.model.ShardInfo;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShardService {
    public List<ShardInfo> getShardStatus(ClusterConfig config) {
        List<ShardInfo> shardInfos = new ArrayList<>();

        for (NodeInfo node : config.getNodes()) {
            if (!"shard".equals(node.getType())) continue;

            ShardInfo shardInfo = new ShardInfo();
            shardInfo.setShardId(node.getNodeId());
            shardInfo.setReplicaSet(node.getReplicaSet());
            shardInfo.setHost("localhost");
            shardInfo.setPort(node.getPort());
            shardInfo.setStatus(ProcessManager.isProcessRunning(node.getNodeId()) ? "running" : "stopped");

            // Single-node shards always act as primary
            shardInfo.setPrimary(true);

            try (MongoClient client = MongoConnectionUtil.createClient("localhost", node.getPort())) {

                MongoDatabase adminDb = client.getDatabase("admin");
                Document shardStats = adminDb.runCommand(new Document("serverStatus", 1));

                Document shardingMetrics = shardStats.get("sharding", Document.class);

                if (shardingMetrics != null) {
                    shardInfo.setDataSize(shardingMetrics.get("dataSize", 0L));
                    shardInfo.setChunkCount(shardingMetrics.get("chunks", 0));
                } else {
                    shardInfo.setDataSize(0L);
                    shardInfo.setChunkCount(0);
                }

            } catch (Exception e) {
                shardInfo.setStatus("error");
                shardInfo.setDataSize(0L);
                shardInfo.setChunkCount(0);
            }

            shardInfos.add(shardInfo);
        }

        return shardInfos;
    }
    public boolean addShardToCluster(String shardConnectionString) {
        return MongoConnectionUtil.addShardToCluster("localhost", 27999, shardConnectionString);
    }
    public boolean removeShardFromCluster(String shardId) {
        try{
            try(MongoClient client = MongoConnectionUtil.createClient("localhost",27999)){
                MongoDatabase adminDb = client.getDatabase("admin");

                Document cmd = new Document("removeShard",shardId);
                Document result = adminDb.runCommand(cmd);
                String state = result.getString("state");
                System.out.println("Remove Shard Response: " + result.toJson());

                if("completed".equalsIgnoreCase(state)){
                    System.out.println("Shard " + shardId + " successfully removed.");
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
}
