package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.model.NodeStatus;
import com.omnexus.model.ShardInfo;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShardService {
    public List<ShardInfo> getShardStatus(ClusterConfig config) {
        List<ShardInfo> shardInfos = new ArrayList<>();

        for (NodeInfo node : config.getNodes()) {
            if ("shard".equals(node.getType())) {
                ShardInfo shardInfo = new ShardInfo();
                shardInfo.setShardId(node.getNodeId());
                shardInfo.setReplicaSet(node.getReplicaSet());
                shardInfo.setHost("localhost");
                shardInfo.setPort(node.getPort());
                shardInfo.setStatus(ProcessManager.isProcessRunning(node.getNodeId()) ? "running" : "stopped");
                shardInfo.setPrimary(true); // Single node shard is always primary
                shardInfo.setDataSize(0L); // TODO: Get actual data size
                shardInfo.setChunkCount(0); // TODO: Get actual chunk count

                shardInfos.add(shardInfo);
            }
        }

        return shardInfos;
    }

    public boolean addShardToCluster(String shardConnectionString) {
        return MongoConnectionUtil.addShardToCluster("localhost", 27999, shardConnectionString);
    }

    public boolean removeShardFromCluster(String shardId) {
        // TODO: Implement shard removal logic
        return false;
    }
}
