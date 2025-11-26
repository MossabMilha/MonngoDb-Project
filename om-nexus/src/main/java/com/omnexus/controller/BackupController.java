package com.omnexus.controller;

import com.omnexus.service.BackupService;
import com.omnexus.service.ConfigServerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/backup")
public class BackupController {
    private final BackupService backupService;
    private final ConfigServerService configServerService;

    public BackupController(BackupService backupService, ConfigServerService configServerService) {
        this.backupService = backupService;
        this.configServerService = configServerService;
    }

    @PostMapping("/{clusterId}")
    public Map<String, Object> createBackup(@PathVariable String clusterId, @RequestBody(required = false) Map<String,Object> body){
        boolean compress = body != null && Boolean.TRUE.equals(body.get("compress"));
        return backupService.backupCluster(clusterId, compress);
    }

    @GetMapping("/{clusterId}")
    public Map<String,Object> listBackup (@PathVariable String clusterId){
        List<String> backups = backupService.listBackups(clusterId);
        return Map.of("clusterId", clusterId, "backups", backups);
    }

    @PostMapping("/{clusterId}/restore")
    public Map<String,Object> restoreShard(@PathVariable String clusterId, @RequestBody(required = false) Map<String,Object> body){
        if (body == null || body.get("timestamp") == null || body.get("shard") == null) {
            return Map.of(
                "success", false,
                "error", "Request body required with 'timestamp' and 'shard' fields",
                "example", Map.of("timestamp", "2025-01-01T00-00-00Z", "shard", "shard1", "drop", true)
            );
        }

        String timestamp = (String) body.get("timestamp");
        String shard = (String) body.get("shard");
        boolean drop = Boolean.TRUE.equals(body.get("drop"));

        Map<String, Object> restoreResult = backupService.restoreShard(clusterId, timestamp, shard, drop);

        Map<String, Object> response = new HashMap<>(restoreResult);
        response.put("clusterId", clusterId);
        response.put("timestamp", timestamp);
        response.put("shard", shard);

        return response;
    }

    // Reset cluster config to default (removes failed shard entries)
    @PostMapping("/{clusterId}/reset-config")
    public Map<String, Object> resetClusterConfig(
            @PathVariable String clusterId,
            @RequestParam(defaultValue = "2") int numberOfShards) {

        return configServerService.resetClusterConfig(clusterId, numberOfShards);
    }

    // Get current cluster config
    @GetMapping("/{clusterId}/config")
    public Object getClusterConfig(@PathVariable String clusterId) {
        var config = configServerService.loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of("success", false, "error", "Cluster not found: " + clusterId);
        }
        return config;
    }
}
