package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.model.NodeStatus;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<NodeInfo> getAllNodes(ClusterConfig config) {
        return config.getNodes();
    }
    public NodeInfo getNodeInfo(String nodeId,ClusterConfig config) {
        return config.getNodes().stream()
                .filter(node -> nodeId.equals(node.getNodeId()))
                .findFirst()
                .orElse(null);
    }
    public Map<String,Object> restartNode(String nodeId,ClusterConfig config) {
        Map<String,Object> result = new HashMap<>();
        try{
            // First stop the node
            boolean stopped = stopNode(nodeId);
            if(!stopped){
                result.put("success",false);
                result.put("message","Failed to stop node " + nodeId + " for restart");
                result.put("nodeId",nodeId);
                return result;
            }
            // Wait a moment for cleanup
            Thread.sleep(2000);
            // Start it Again
            boolean started = startNode(nodeId,config);

            result.put("success",started);
            result.put("message",started ? ("Node "+nodeId+" restarted successfully") : ("Failed to restart node " + nodeId));
            result.put("nodeId",nodeId);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            result.put("success",false);
            result.put("message","Restart interrupted for node " + nodeId);
            result.put("nodeId",nodeId);
        }

        return result;

    }
    public Map<String,Object> removeNodeFromCluster(String nodeId,ClusterConfig config){
        Map<String,Object> result = new HashMap<>();

        // Find The node
        NodeInfo nodeToRemove = getNodeInfo(nodeId,config);
        if(nodeToRemove == null){
            result.put("success",false);
            result.put("message","Node " + nodeId + " not found in cluster");
            result.put("nodeId",nodeId);
            return result;
        }
        try{
            // Stop The Node first if it's running
            if(ProcessManager.isProcessRunning(nodeId)){
                boolean stopped = stopNode(nodeId);
                if(!stopped){
                    result.put("success",false);
                    result.put("message","Failed to stop node "+nodeId+" before removal");
                    result.put("nodeId",nodeId);
                    return result;
                }
            }

            // Remove From Config (this would need to be persisted in a real implementation)
            boolean removed = config.getNodes().removeIf(node -> nodeId.equals(node.getNodeId()));
            result.put("success",removed);
            result.put("message",removed ? ("Node " + nodeId + " removed from cluster successfully") : ("Failed to remove node " + nodeId + " from cluster"));
            result.put("nodeId",nodeId);
            result.put("nodeType",nodeToRemove.getType());
        } catch (Exception e) {
            result.put("success",false);
            result.put("message","Error removing node "+nodeId+": "+e.getMessage());
            result.put("nodeId",nodeId);
        }
        return result;
    }


}