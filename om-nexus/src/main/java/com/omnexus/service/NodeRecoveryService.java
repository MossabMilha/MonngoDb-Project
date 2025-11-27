package com.omnexus.service;

import com.omnexus.model.ClusterConfig;
import com.omnexus.event.NodeFailureEvent;
import com.omnexus.model.NodeInfo;
import com.omnexus.util.ProcessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class NodeRecoveryService {
    private final ConfigServerService configServerService;
    private final BackupProgressService backupProgressService;
    private final HealthMonitorService healthMonitorService;

    public NodeRecoveryService(ConfigServerService configServerService,BackupProgressService backupProgressService, HealthMonitorService healthMonitorService) {
        this.configServerService = configServerService;
        this.backupProgressService = backupProgressService;
        this.healthMonitorService = healthMonitorService;
    }

    @Async("recoveryExecutor")
    @EventListener
    public void handleNodeFailure(NodeFailureEvent event){
        log.warn("Node failure detected: {} in cluster {}", event.getNodeId(), event.getClusterId());
        CompletableFuture.runAsync(()->{
            try{
                recoverNode(event.getClusterId(),event.getNodeId(),event.isAutoRestart());

            } catch (Exception e) {
                log.error("Failed to recover node {} in cluster {}: {}",event.getNodeId(), event.getClusterId(), e.getMessage());
            }
        });
    }
    public Map<String,Object> recoverNode(String clusterId,String nodeId,boolean autoRestart){
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if(config == null){
            return Map.of("error","Cluster not found: " + clusterId);
        }
        NodeInfo node = config.getNodes().stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);
        if(node == null){
            return Map.of("error", "Node not found: " + nodeId);
        }
        List<String> recoverySteps = new ArrayList<>();
        boolean success = true;
        try{
            // stop any zombie processes
            if(ProcessManager.isProcessRunning(nodeId)){
                ProcessManager.stopProcess(nodeId);
                recoverySteps.add("Stopped zombie process on port " + node.getPort());
                Thread.sleep(2000);
            }

            // Kill any process on the port as backup
            ProcessManager.killProcessesOnPort(node.getPort());
            recoverySteps.add("cleaned up port "+node.getPort());
            Thread.sleep(1000);

            if(autoRestart){
                boolean started;
                if ("mongos".equals(node.getType())) {
                    // For mongos, build the config replica set connection string
                    String configRsName = config.getNodes().stream()
                            .filter(n -> "config".equals(n.getType()))
                            .map(NodeInfo::getReplicaSet)
                            .findFirst()
                            .orElse("configReplSet");

                    String configHosts = config.getNodes().stream()
                            .filter(n -> "config".equals(n.getType()))
                            .map(n -> "localhost:" + n.getPort())
                            .reduce((a, b) -> a + "," + b)
                            .orElse("");

                    String configDbString = configRsName + "/" + configHosts;
                    started = ProcessManager.startMongosProcess(
                            node.getNodeId(),
                            node.getPort(),
                            configDbString
                    );
                } else {
                    started = ProcessManager.startMongodProcess(
                            node.getNodeId(),
                            node.getType(),
                            node.getPort(),
                            node.getDataPath(),
                            node.getReplicaSet()
                    );
                }
                if (started){
                    recoverySteps.add("Restarted node "+nodeId);
                    Thread.sleep(5000);
                }else{
                    success = false;
                    recoverySteps.add("Failed to restart node "+nodeId);
                }
            }

            // Check both our tracked process and if something is listening on the port
            boolean processAlive = ProcessManager.isProcessRunning(nodeId);
            boolean portInUse = ProcessManager.isProcessRunningOnPort(node.getPort());

            if(processAlive || portInUse){
                recoverySteps.add("Node health verified - process running on port " + node.getPort());
            } else {
                success = false;
                recoverySteps.add("Node still not running after restart on port " + node.getPort());
            }
        } catch (Exception e) {
            success = false;
            recoverySteps.add("Recovery failed: " + e.getMessage());
            log.error("Node recovery failed for {} in cluster {}: {}", nodeId, clusterId, e.getMessage());
        }
        return Map.of(
                "clusterId", clusterId,
                "nodeId", nodeId,
                "recovered", success,
                "steps", recoverySteps
        );
    }
}
