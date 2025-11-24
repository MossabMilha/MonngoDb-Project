package com.omnexus.controller;

import com.omnexus.model.ClusterConfig;
import com.omnexus.model.ClusterStatus;
import com.omnexus.model.NodeStatus;
import com.omnexus.service.MonitoringService;
import com.omnexus.service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    @Autowired
    private MonitoringService monitoringService;
    @Autowired
    private NodeService nodeService;

    @GetMapping("/cluster/{clusterId}")
    public ClusterStatus getClusterHealth(@PathVariable String clusterId, @RequestBody ClusterConfig config){
        return monitoringService.getClusterStatus(config);
    }

    @PostMapping("/nodes")
    public List<NodeStatus> getAllNodesStatuses(@RequestBody ClusterConfig config){
        return nodeService.getNodeStatuses(config);
    }
    @PostMapping("/metrics")
    public Map<String,Object> getClusterMetrics(@RequestBody ClusterConfig config){
        return monitoringService.getClusterMetrics(config);
    }

    @PostMapping("/status/realtime")
    public Map<String,Object> getRealtimeStatus(@RequestBody ClusterConfig config){
        return monitoringService.getRealTimeClusterStatus(config);
    }

    @PostMapping("/health/detailed")
    public Map<String,Object> getDetailedHealth(@RequestBody ClusterConfig config){
        return monitoringService.getDetailedHealthStatus(config);
    }

    @GetMapping("/node/{nodeId}/status")
    public NodeStatus getNodeStatus(@PathVariable String nodeId,@RequestBody ClusterConfig config){
        return nodeService.getNodeStatus(nodeId,config);
    }
}
