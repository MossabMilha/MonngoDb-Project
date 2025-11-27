package com.omnexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnexus.model.ClusterConfig;
import com.omnexus.model.NodeInfo;
import com.omnexus.util.ProcessManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;


@Service
public class BackupService {
    private static final String BACKUP_ROOT = "backup";
    private final ConfigServerService configServerService;
    private final ClusterService clusterService;
    private final BackupProgressService backupProgressService;
    private final ObjectMapper mapper = new ObjectMapper();


    public BackupService(ConfigServerService configServerService, ClusterService clusterService,BackupProgressService backupProgressService) {
        this.configServerService = configServerService;
        this.clusterService = clusterService;
        this.backupProgressService = backupProgressService;
        ensureBackupRoot();
    }
    private void ensureBackupRoot(){
        try{
            Files.createDirectories(Paths.get("backup"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private String nowTimestamp(){return DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":","-");}


    public Map<String,Object> backupCluster(String clusterId,boolean compress){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null) {
            return  Map.of("error","cluster not found: " + clusterId);
        }

        // Ensure cluster is running before backup
        System.out.println("=== Starting Full Cluster Backup ===");
        boolean clusterReady = ensureClusterRunning(clusterConfig);
        if (!clusterReady) {
            return Map.of("error", "Failed to start cluster for backup");
        }

        String timestamp = nowTimestamp();
        Path clusterBackupPath = Paths.get(BACKUP_ROOT,clusterId,timestamp);
        Path dumpPath;
        try{
            Files.createDirectories(clusterBackupPath);
            dumpPath = clusterBackupPath.resolve("dump");
            Files.createDirectory(dumpPath);
        } catch (Exception e) {
            return Map.of("error","failed to create backup directory: " + e.getMessage());
        }
        List<Map<String,Object>> artifacts = new ArrayList<>();
        boolean allSuccess = true;

        // Find mongos port for backing up user databases
        var mongosNode = clusterConfig.getNodes().stream()
                .filter(n -> "mongos".equals(n.getType()))
                .findFirst();

        int mongosPort = mongosNode.map(NodeInfo::getPort).orElse(27999);
        System.out.println("Using mongos on port " + mongosPort + " for backup");

        // 1. Backup ALL databases through mongos (this gets user databases properly)
        try {
            System.out.println("Backing up all databases through mongos...");
            boolean ok = ProcessManager.runMongoDump("localhost", mongosPort, null, dumpPath.toString(), false, false);
            artifacts.add(Map.of("type", "mongos-dump", "port", mongosPort, "success", ok));
            allSuccess &= ok;
            if (ok) {
                System.out.println("Successfully backed up all databases through mongos");
            } else {
                System.err.println("Failed to backup through mongos");
            }
        } catch (Exception e) {
            artifacts.add(Map.of("type", "mongos-dump", "success", false, "error", e.getMessage()));
            allSuccess = false;
        }

        // 2. Also backup config database from config server (for cluster metadata)
        try{
            var configNodes = clusterConfig.getNodes().stream().filter(n->"config".equals(n.getType())).toList();
            if(!configNodes.isEmpty()){
                int port = configNodes.get(0).getPort();
                System.out.println("Backing up config database from config server (port " + port + ")...");
                boolean ok = ProcessManager.runMongoDump("localhost", port, "config", dumpPath.toString(), false, false);
                artifacts.add(Map.of("type","config","port", port, "success",ok));
                // Don't fail the whole backup if config backup fails - we already have it from mongos
            }
        } catch (Exception e) {
            artifacts.add(Map.of("type","config","success",false,"error",e.getMessage()));
        }

        // List what was backed up BEFORE compression (since compression deletes the folder)
        List<String> backedUpDatabases = listDatabasesInDump(dumpPath);
        System.out.println("Databases backed up: " + backedUpDatabases);

        // Compress the entire dump directory if requested
        if(compress && allSuccess){
            try{
                boolean compressed = ProcessManager.compressDirectory(dumpPath.toString());
                // Note: compressDirectory already deletes the folder after zipping
                if (compressed) {
                    artifacts.add(Map.of("type", "compression", "success", true));
                } else {
                    artifacts.add(Map.of("type", "compression", "success", false, "error", "compression failed"));
                }
            } catch (Exception e) {
                artifacts.add(Map.of("type","compression","success",false,"error",e.getMessage()));
            }
        }

        //Save metadata
        Map<String,Object> meta = new HashMap<>();
        meta.put("clusterId", clusterId);
        meta.put("timestamp", timestamp);
        meta.put("compressed", compress && allSuccess);
        meta.put("dumpPath", dumpPath.toString());
        meta.put("artifacts", artifacts);
        meta.put("databases", backedUpDatabases);
        meta.put("success", allSuccess);

        try{
            mapper.writerWithDefaultPrettyPrinter().writeValue(clusterBackupPath.resolve("metadata.json").toFile(),meta);
        }catch (IOException e){
            artifacts.add(Map.of("type","metadata","success",false,"error",e.getMessage()));
        }

        System.out.println("=== Backup Complete ===");
        return meta;
    }

    // Restore a shard from a given Backup path
    public Map<String,Object> restoreShard(String clusterId,String timestamp,String shardName,boolean dropBeforeRestore){
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) return Map.of("error","cluster not found: " + clusterId);

        // Use the main backup path, not shard-specific path
        Path backupPath = Paths.get(BACKUP_ROOT, clusterId, timestamp);
        if(!Files.exists(backupPath)){
            return Map.of("error", "backup not found at " + backupPath.toString());
        }

        // Check if backup is compressed and decompress if needed
        Path dumpPath = backupPath.resolve("dump");
        if (!Files.exists(dumpPath)) {
            Map<String, Object> decompressResult = decompressBackupIfNeeded(backupPath);
            if (decompressResult != null) {
                return decompressResult; // Return error
            }
        }

        // Find actual dump content
        Path actualDumpPath = findActualDumpPath(dumpPath);

        // Find shard node in config
        var nodeOpt = config.getNodes().stream()
                .filter(n -> shardName.equals(n.getReplicaSet()))
                .findFirst();
        if(nodeOpt.isEmpty()) return Map.of("error", "shard not found in config: " + shardName);

        var node = nodeOpt.get();

        // Restore all databases to this specific shard
        boolean allSuccess = true;
        List<String> restoredDatabases = new ArrayList<>();

        try {
            List<String> databases = listDatabasesInDump(actualDumpPath);
            for (String dbName : databases) {
                if (!dbName.equals("config") && !dbName.equals("admin") && !dbName.equals("local")) {
                    Path dbPath = actualDumpPath.resolve(dbName);
                    if (Files.exists(dbPath)) {
                        boolean ok = ProcessManager.runMongoRestore("localhost", node.getPort(),
                                dbPath.toString(), dropBeforeRestore, false);
                        if (ok) {
                            restoredDatabases.add(dbName);
                        }
                        allSuccess &= ok;
                    }
                }
            }
        } catch (Exception e) {
            return Map.of("error", "restore failed: " + e.getMessage());
        }

        return Map.of(
                "shard", shardName,
                "path", actualDumpPath.toString(),
                "restored", allSuccess,
                "restoredDatabases", restoredDatabases
        );
    }

    // List backups for cluster
    public List<String> listBackups(String clusterId){
        Path clusterPath = Paths.get(BACKUP_ROOT,clusterId);
        if(!Files.exists(clusterPath)) return Collections.emptyList();
        try{
            List<String> list = new ArrayList<>();
            try(DirectoryStream<Path> stream = Files.newDirectoryStream(clusterPath)){
                for (Path p :stream){
                    if(Files.isDirectory(p)) list.add(p.getFileName().toString());
                }
            }
            list.sort(Comparator.reverseOrder());
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // Restore entire cluster from backup
    public Map<String,Object> restoreCluster(String clusterId, String timestamp, boolean dropBeforeRestore){
        System.out.println("=== Starting Full Cluster Restore ===");
        System.out.println("Cluster ID: " + clusterId + ", Timestamp: " + timestamp);

        Path backupPath = Paths.get(BACKUP_ROOT, clusterId, timestamp);
        if (!Files.exists(backupPath)) {
            return Map.of("error", "backup not found at " + backupPath.toString());
        }

        // Load or create cluster config
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) {
            System.out.println("Cluster config not found, attempting to recreate from backup metadata...");
            config = recreateClusterConfigFromBackup(clusterId, backupPath);
            if (config == null) {
                return Map.of("error", "Could not recreate cluster configuration from backup");
            }
        }

        // Check if backup is compressed and decompress if needed
        Path dumpPath = backupPath.resolve("dump");
        if (!Files.exists(dumpPath)) {
            Map<String, Object> decompressResult = decompressBackupIfNeeded(backupPath);
            if (decompressResult != null) {
                return decompressResult; // Return error
            }
        }

        // Find actual dump content - may be nested due to previous mongodump bug
        Path actualDumpPath = findActualDumpPath(dumpPath);
        System.out.println("Using dump path: " + actualDumpPath.toString());

        // List all databases found in dump
        List<String> foundDatabases = listDatabasesInDump(actualDumpPath);
        System.out.println("Found databases in backup: " + foundDatabases);

        // Ensure cluster is running before restore
        System.out.println("Ensuring cluster is running...");
        boolean clusterReady = ensureClusterRunning(config);
        if (!clusterReady) {
            return Map.of("error", "Failed to start cluster for restore. Please ensure MongoDB processes can start.");
        }

        List<Map<String,Object>> restoreResults = new ArrayList<>();
        boolean allSuccess = true;

        // Find mongos port for restoring user databases
        var mongosNode = config.getNodes().stream()
                .filter(n -> "mongos".equals(n.getType()))
                .findFirst();

        int mongosPort = mongosNode.map(NodeInfo::getPort).orElse(27999);
        System.out.println("Using mongos on port " + mongosPort + " for restore");

        // Find user databases (exclude system databases)
        List<String> userDatabases = foundDatabases.stream()
                .filter(db -> !db.equals("config") && !db.equals("admin") && !db.equals("local"))
                .toList();

        System.out.println("User databases to restore: " + userDatabases);

        // 1. Restore user databases through mongos (this ensures proper routing)
        for (String dbName : userDatabases) {
            Path dbPath = actualDumpPath.resolve(dbName);
            if (Files.exists(dbPath)) {
                try {
                    System.out.println("Restoring database '" + dbName + "' through mongos (port " + mongosPort + ")");
                    boolean ok = ProcessManager.runMongoRestore("localhost", mongosPort, dbPath.toString(), dropBeforeRestore, false);
                    restoreResults.add(Map.of("type", "database", "database", dbName, "port", mongosPort, "success", ok));
                    allSuccess &= ok;
                    if (!ok) {
                        System.err.println("Failed to restore database " + dbName);
                    } else {
                        System.out.println("Successfully restored database " + dbName);
                    }
                } catch (Exception e) {
                    restoreResults.add(Map.of("type", "database", "database", dbName, "success", false, "error", e.getMessage()));
                    allSuccess = false;
                }
            }
        }

        // 2. Also restore admin database through mongos if exists
        Path adminPath = actualDumpPath.resolve("admin");
        if (Files.exists(adminPath)) {
            try {
                System.out.println("Restoring admin database through mongos");
                boolean ok = ProcessManager.runMongoRestore("localhost", mongosPort, adminPath.toString(), dropBeforeRestore, false);
                restoreResults.add(Map.of("type", "admin", "port", mongosPort, "success", ok));
            } catch (Exception e) {
                restoreResults.add(Map.of("type", "admin", "success", false, "error", e.getMessage()));
            }
        }

        // Save updated config
        configServerService.saveClusterConfig(config);

        System.out.println("=== Cluster Restore Complete ===");
        System.out.println("Success: " + allSuccess);

        Map<String, Object> result = new HashMap<>();
        result.put("clusterId", clusterId);
        result.put("timestamp", timestamp);
        result.put("backupPath", backupPath.toString());
        result.put("actualDumpPath", actualDumpPath.toString());
        result.put("databasesRestored", userDatabases);
        result.put("restoreResults", restoreResults);
        result.put("success", allSuccess);
        result.put("message", allSuccess ? "Cluster restored successfully" : "Some restore operations failed");

        return result;
    }
    @SuppressWarnings("unchecked")
    private ClusterConfig recreateClusterConfigFromBackup(String clusterId, Path backupPath) {
        try {
            Path metadataPath = backupPath.resolve("metadata.json");
            if (!Files.exists(metadataPath)) {
                System.err.println("No metadata.json found in backup");
                return null;
            }

            Map<String, Object> metadata = mapper.readValue(metadataPath.toFile(), Map.class);
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) metadata.get("artifacts");

            int shardCount = (int) artifacts.stream().filter(a -> "shard".equals(a.get("type"))).count();
            int configCount = (int) artifacts.stream().filter(a -> "config".equals(a.get("type"))).count();

            ClusterConfig newConfig = new ClusterConfig();
            newConfig.setClusterId(clusterId);
            newConfig.setNumberOfShards(shardCount > 0 ? shardCount : 2);
            newConfig.setNumberOfConfigServers(configCount > 0 ? configCount : 1);

            List<NodeInfo> nodes = new ArrayList<>();
            int currentPort = 28000;

            for (int i = 1; i <= newConfig.getNumberOfConfigServers(); i++) {
                NodeInfo configNode = new NodeInfo(
                        "config-" + i,
                        "config",
                        currentPort++,
                        newConfig.getBaseDataPath() + File.separator + "config" + File.separator + "configsvr" + i
                );
                configNode.setReplicaSet("configReplSet");
                nodes.add(configNode);
            }

            for (int i = 1; i <= newConfig.getNumberOfShards(); i++) {
                NodeInfo shardNode = new NodeInfo(
                        "shard-" + i,
                        "shard",
                        currentPort++,
                        newConfig.getBaseDataPath() + File.separator + "shard" + File.separator + "shard" + i
                );
                shardNode.setReplicaSet("shard" + i);
                nodes.add(shardNode);
            }

            newConfig.setNodes(nodes);

            boolean configSaved = configServerService.saveClusterConfig(newConfig);
            if (!configSaved) {
                System.err.println("Failed to save recreated cluster config");
                return null;
            }

            System.out.println("Recreated cluster config with " + shardCount + " shards and " + configCount + " config servers");
            return newConfig;

        } catch (Exception e) {
            System.err.println("Error recreating cluster config: " + e.getMessage());
            return null;
        }
    }
    private Map<String, Object> decompressBackupIfNeeded(Path backupPath) {
        Path compressedZipPath = backupPath.resolve("dump.zip");
        Path compressedTarPath = backupPath.resolve("dump.tar.gz");

        if (Files.exists(compressedZipPath)) {
            try {
                System.out.println("Decompressing ZIP backup...");
                boolean decompressed = decompressZipFile(compressedZipPath.toString(), backupPath.toString());
                if (!decompressed) {
                    return Map.of("error", "failed to decompress ZIP backup");
                }
            } catch (Exception e) {
                return Map.of("error", "ZIP decompression failed: " + e.getMessage());
            }
        } else if (Files.exists(compressedTarPath)) {
            try {
                System.out.println("Decompressing TAR.GZ backup...");
                boolean decompressed = ProcessManager.decompressFile(compressedTarPath.toString(), backupPath.toString());
                if (!decompressed) {
                    return Map.of("error", "failed to decompress TAR.GZ backup");
                }
            } catch (Exception e) {
                return Map.of("error", "TAR.GZ decompression failed: " + e.getMessage());
            }
        } else {
            return Map.of("error", "no dump directory or compressed backup found");
        }
        return null; // Success
    }
    private boolean ensureClusterRunning(ClusterConfig config) {
        try {
            // Check if all nodes are running
            boolean allRunning = config.getNodes().stream()
                    .filter(n -> !"mongos".equals(n.getType()))
                    .allMatch(n -> ProcessManager.isProcessRunning(n.getNodeId()));

            if (!allRunning) {
                System.out.println("Starting cluster processes...");
                clusterService.startCluster(config);
                Thread.sleep(5000); // Wait for processes to start
            }

            // Initialize if needed
            System.out.println("Initializing cluster if needed...");
            boolean initialized = clusterService.initializeCluster(config);

            if (!initialized) {
                System.err.println("Cluster initialization returned false, but continuing with restore...");
            }

            // Wait a bit more for stability
            Thread.sleep(3000);

            return true;
        } catch (Exception e) {
            System.err.println("Error ensuring cluster is running: " + e.getMessage());
            return false;
        }
    }
    private List<String> listDatabasesInDump(Path dumpPath) {
        List<String> databases = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dumpPath)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    databases.add(p.getFileName().toString());
                }
            }
        } catch (Exception e) {
            System.err.println("Error listing databases in dump: " + e.getMessage());
        }
        return databases;
    }
    private Path findActualDumpPath(Path dumpPath) {
        // First check if dumpPath directly contains database folders
        if (containsDatabaseFolders(dumpPath)) {
            return dumpPath;
        }

        // Otherwise, search recursively for the actual dump content
        try {
            Path result = findDumpContentRecursively(dumpPath, 0);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error searching for dump content: " + e.getMessage());
        }

        // Fallback to original path
        return dumpPath;
    }
    private boolean containsDatabaseFolders(Path path) {
        // Check for common MongoDB database directories
        return Files.exists(path.resolve("config")) ||
               Files.exists(path.resolve("admin")) ||
               Files.exists(path.resolve("local"));
    }
    private Path findDumpContentRecursively(Path path, int depth) throws IOException {
        if (depth > 10) return null; // Prevent infinite recursion

        if (containsDatabaseFolders(path)) {
            return path;
        }

        // Check subdirectories
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path subPath : stream) {
                if (Files.isDirectory(subPath)) {
                    Path result = findDumpContentRecursively(subPath, depth + 1);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        return null;
    }
    private boolean decompressZipFile(String zipFilePath, String outputDir) {
        try {
            Path zipPath = Paths.get(zipFilePath);
            Path outputPath = Paths.get(outputDir);
            
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    Files.newInputStream(zipPath))) {
                
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = outputPath.resolve(entry.getName());
                    
                    // Ensure parent directories exist
                    Files.createDirectories(entryPath.getParent());
                    
                    if (!entry.isDirectory()) {
                        Files.copy(zis, entryPath);
                    }
                    zis.closeEntry();
                }
            }
            
            System.out.println("Successfully decompressed ZIP file: " + zipFilePath);
            return true;
            
        } catch (Exception e) {
            System.err.println("Failed to decompress ZIP file: " + e.getMessage());
            return false;
        }
    }

    @Async("backupExecutor")
    public CompletableFuture<Map<String,Object>> backupClusterAsync(String clusterId,boolean compress){
        backupProgressService.startBackupProgress(clusterId);
        try{
            backupProgressService.updateProgress(clusterId,"Starting cluster backup...",1);
            Map<String,Object> result = backupCluster(clusterId,compress);
            backupProgressService.completeBackup(clusterId,(Boolean) result.get("success"));
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            backupProgressService.completeBackup(clusterId,false);
            return CompletableFuture.completedFuture(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
