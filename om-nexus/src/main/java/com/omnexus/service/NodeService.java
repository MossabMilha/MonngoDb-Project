package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.model.NodeStatus;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Service
public class NodeService {
    public List<NodeStatus> getNodeStatuses(ClusterConfig config) {
        List<NodeStatus> nodeStatuses = new ArrayList<>();
        for(NodeInfo node : config.getNodes()){
            NodeStatus status = new NodeStatus();
            status.setNodeId(node.getNodeId());
            status.setType(node.getType());
            status.setPort(node.getPort());
            status.setHost("localhost");
            status.setReplicaSet(node.getReplicaSet());
            status.setStatus(ProcessManager.isProcessRunning(node.getNodeId()) ? "running" : "stopped" );
            status.setHealthy(ProcessManager.isProcessRunning(node.getNodeId()));
            status.setLastPing(LocalDateTime.now().toString());
            status.setUptime(0L); // TODO: Calculate actual uptime

            nodeStatuses.add(status);
        }
        return nodeStatuses;
    }
    public NodeStatus getNodeStatus(String nodeId, ClusterConfig config) {
        return getNodeStatuses(config).stream()
                .filter(status -> nodeId.equals(status.getNodeId()))
                .findFirst()
                .orElse(null);
    }
    public boolean startNode(String nodeId, ClusterConfig config) {
        NodeInfo node = config.getNodes().stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);

        if (node != null) {
            return ProcessManager.startMongodProcess(
                    node.getNodeId(),
                    node.getType(),
                    node.getPort(),
                    node.getDataPath(),
                    node.getReplicaSet()
            );
        }
        return false;
    }
    public boolean stopNode(String nodeId) {
        return ProcessManager.stopProcess(nodeId);
    }
}
