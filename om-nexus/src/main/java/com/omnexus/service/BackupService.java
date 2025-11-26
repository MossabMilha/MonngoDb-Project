package com.omnexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnexus.model.ClusterConfig;
import com.omnexus.util.MongoConnectionUtil;
import com.omnexus.util.ProcessManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BackupService {
    private static final String BACKUP_ROOT = "backup";
    private final ConfigServerService configServerService;
    private final ObjectMapper mapper = new ObjectMapper();
    public BackupService(ConfigServerService configServerService) {
        this.configServerService = configServerService;
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

    // Create a Full cluster backup (config + shards)
    public Map<String,Object> backupCluster(String clusterId,boolean compress){
        ClusterConfig clusterConfig = configServerService.loadClusterConfig(clusterId);
        if(clusterConfig == null){
            return Map.of("error","cluster not found: " + clusterId);
        }
        String timestamp = nowTimestamp();
        Path clusterPath = Paths.get(BACKUP_ROOT,clusterId,timestamp);
        try{
            Files.createDirectories(clusterPath);
        } catch (Exception e) {
            return Map.of("error", "failed create backup dir: " + e.getMessage());
        }

        List<Map<String,Object>> artifacts = new ArrayList<>();
        // 1) Backup config DB (connect to mongos or config server)
        try{
            String configDumpPath = clusterPath.resolve("config-db").toString();
            Files.createDirectory(Paths.get(configDumpPath));

            // Use ProcessManager to run mongodump for the config server or mongos
            // Prefer dumping from a config server node
            var configNodes = clusterConfig.getNodes().stream().filter(n -> "config".equals(n.getType())).toList();
            if(!configNodes.isEmpty()){
                int port  = configNodes.get(0).getPort();
                boolean ok = ProcessManager.runMongoDump("localhost",port,"config",configDumpPath,compress,false);
                artifacts.add(Map.of("type","config","path",configDumpPath,"success",ok));
            }else{
                artifacts.add(Map.of("type","config","path","none","success",false,"reason","no-config-node"));
            }
        } catch (Exception e) {
            artifacts.add(Map.of("type","config","success",false,"error",e.getMessage()));
        }
        // 2) Backup each shard
        for(var shardNode : clusterConfig.getNodes().stream().filter(n ->"shard".equals(n.getType())).toList()){
            try{
                String shardName = shardNode.getReplicaSet();
                String shardDumpPath = clusterPath.resolve("shard-"+shardName).toString();
                Files.createDirectory(Paths.get(shardDumpPath));

                boolean ok = ProcessManager.runMongoDump("localhost", shardNode.getPort(), null, shardDumpPath, compress, MongoConnectionUtil.isReplicaSetInitialized("localhost", shardNode.getPort()));
                artifacts.add(Map.of("type","shard","shard",shardName,"path",shardDumpPath,"success",ok));
            } catch (Exception e) {
                artifacts.add(Map.of("type","shard","shard",shardNode.getReplicaSet(),"success",false,"error",e.getMessage()));
            }
        }

        // Save metadata
        Map<String,Object> meta = new HashMap<>();
        meta.put("clusterId",clusterId);
        meta.put("timestamp",timestamp);
        meta.put("artifacts",artifacts);
        Path metaFile = clusterPath.resolve("metadata.json");
        try{
            mapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);
        }catch (IOException e){
            artifacts.add(Map.of("type","meta","success",false,"error",e.getMessage()));
        }

        return Map.of("clusterId", clusterId, "timestamp", timestamp, "artifacts", artifacts);

    }

    // Restore a shard from a given Backup path
    public Map<String,Object> restoreShard(String clusterId,String timestamp,String shardName,boolean dropBeforeRestore){
        ClusterConfig config = configServerService.loadClusterConfig(clusterId);
        if (config == null) return Map.of("error","cluster not found: " + clusterId);

        Path shardPath = Paths.get(BACKUP_ROOT,clusterId,timestamp,"shard-"+shardName);
        if(!Files.exists(shardPath)){
            return Map.of("error", "backup not found at " + shardPath.toString());
        }
        // Find shard node in config
        var nodeOpt = config.getNodes().stream().filter(n->shardName.equals(n.getReplicaSet())).findFirst();
        if(nodeOpt.isEmpty()) return Map.of("error", "shard not found in config: " + shardName);

        var node = nodeOpt.get();

        // Ensure node is stopped before restore ? We'll perform on running node if mongorestore supports supports --drop
        boolean ok = ProcessManager.runMongoRestore("localhost",node.getPort(),shardPath.toString(),dropBeforeRestore,false);
        return Map.of("shard", shardName, "path", shardPath.toString(), "restored", ok);
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

}
