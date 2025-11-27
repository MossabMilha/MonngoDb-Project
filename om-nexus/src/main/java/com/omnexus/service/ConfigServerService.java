package com.omnexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnexus.model.ClusterConfig;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigServerService {
    private static final String CONFIG_DIR = "configs";
    private static final String CONFIG_FILE_SUFFIX = "-config.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ClusterConfig> configCache = new HashMap<>(); // In-memory cache for active configs

    public ConfigServerService() {
        // Ensure config directory exists
        createConfigDirectory();
    }
    public void createConfigDirectory(){
        try{
            Path configPath = Paths.get(CONFIG_DIR);
            if(!Files.exists(configPath)){
                Files.createDirectory(configPath);
                System.out.println("Created config directory at " + configPath.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create config directory: " + e.getMessage());
        }
    }
    public boolean saveClusterConfig(ClusterConfig config){
        try{
            String fileName = config.getClusterId() + CONFIG_FILE_SUFFIX;
            File configFile = new File(CONFIG_DIR,fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);

            // Update cache
            configCache.put(config.getClusterId(),config);
            System.out.println("Saved cluster config: " + configFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save cluster config: " + e.getMessage());
            return false;
        }
    }
    public ClusterConfig loadClusterConfig(String clusterId) {
        // Check cache first
        if (configCache.containsKey(clusterId)) {
            return configCache.get(clusterId);
        }

        try {
            String fileName = clusterId + CONFIG_FILE_SUFFIX;
            File configFile = new File(CONFIG_DIR, fileName);

            if (!configFile.exists()) {
                System.err.println("Config file not found: " + configFile.getAbsolutePath());
                return null;
            }

            ClusterConfig config = objectMapper.readValue(configFile, ClusterConfig.class);

            // Update cache
            configCache.put(clusterId, config);

            System.out.println("Loaded cluster config: " + configFile.getAbsolutePath());
            return config;

        } catch (IOException e) {
            System.err.println("Failed to load cluster config: " + e.getMessage());
            return null;
        }
    }
    public boolean deleteClusterConfig(String clusterId) {
        try {
            String fileName = clusterId + CONFIG_FILE_SUFFIX;
            File configFile = new File(CONFIG_DIR, fileName);

            boolean deleted = configFile.delete();

            if (deleted) {
                // Remove from cache
                configCache.remove(clusterId);
                System.out.println("Deleted cluster config: " + configFile.getAbsolutePath());
            }

            return deleted;

        } catch (Exception e) {
            System.err.println("Failed to delete cluster config: " + e.getMessage());
            return false;
        }
    }
    public boolean configExists(String clusterId) {
        String fileName = clusterId + CONFIG_FILE_SUFFIX;
        File configFile = new File(CONFIG_DIR, fileName);
        return configFile.exists();
    }
    public void updateConfigCache(ClusterConfig config) {
        configCache.put(config.getClusterId(), config);
    }

    /**
     * Reset cluster config to a clean state with specified number of shards.
     * Removes any failed/stopped shard entries and keeps only running ones.
     */
    public Map<String, Object> resetClusterConfig(String clusterId, int numberOfShards) {
        ClusterConfig config = loadClusterConfig(clusterId);
        if (config == null) {
            return Map.of("success", false, "error", "Cluster not found: " + clusterId);
        }

        // Remove the config from cache to force reload
        configCache.remove(clusterId);

        // Count current nodes by type
        long configNodes = config.getNodes().stream().filter(n -> "config".equals(n.getType())).count();
        long shardNodes = config.getNodes().stream().filter(n -> "shard".equals(n.getType())).count();
        long mongosNodes = config.getNodes().stream().filter(n -> "mongos".equals(n.getType())).count();

        // Remove failed/stopped shard entries (keep only running ones up to numberOfShards)
        var runningShards = config.getNodes().stream()
                .filter(n -> "shard".equals(n.getType()))
                .filter(n -> "running".equals(n.getStatus()))
                .limit(numberOfShards)
                .toList();

        var otherNodes = config.getNodes().stream()
                .filter(n -> !"shard".equals(n.getType()))
                .toList();

        // Rebuild nodes list
        config.getNodes().clear();
        config.getNodes().addAll(otherNodes);
        config.getNodes().addAll(runningShards);

        // Save the cleaned config
        boolean saved = saveClusterConfig(config);

        return Map.of(
                "success", saved,
                "clusterId", clusterId,
                "configNodes", configNodes,
                "shardsBefore", shardNodes,
                "shardsAfter", runningShards.size(),
                "mongosNodes", mongosNodes,
                "message", saved ? "Config reset successfully" : "Failed to save config"
        );
    }

    /**
     * Clear config cache to force reload from disk
     */
    public void clearCache(String clusterId) {
        configCache.remove(clusterId);
    }

    public List<String> getAllClusterIds() {
        List<String> clusterIds = new ArrayList<>();
        try {
            Path configPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(configPath)) {
                return clusterIds;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(configPath, "*" + CONFIG_FILE_SUFFIX)) {
                for (Path configFile : stream) {
                    String fileName = configFile.getFileName().toString();
                    // Remove the suffix to get cluster ID
                    String clusterId = fileName.replace(CONFIG_FILE_SUFFIX, "");
                    clusterIds.add(clusterId);
                }
            }

            System.out.println("Found " + clusterIds.size() + " cluster configurations");
            return clusterIds;

        } catch (IOException e) {
            System.err.println("Failed to list cluster configurations: " + e.getMessage());
            return clusterIds;
        }
    }
}
