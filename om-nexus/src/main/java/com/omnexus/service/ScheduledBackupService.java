package com.omnexus.service;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class ScheduledBackupService {
    private final BackupService backupService;
    private final Map<String, ScheduledFuture<?>> scheduledBackups = new ConcurrentHashMap<>();
    private final TaskScheduler taskScheduler;
    private final ConfigServerService configServerService;

    public ScheduledBackupService(ConfigServerService configServerService, BackupService backupProgressService, TaskScheduler taskScheduler) {
        this.backupService = backupProgressService;
        this.configServerService = configServerService;
        this.taskScheduler = taskScheduler;
    }

    @Async("backupExecutor")
    @Scheduled(cron = "0 0 2 * * ?")
    public void performScheduledBackups() {
        List<String> clusters = configServerService.getAllClusterIds();
        for(String clusterId : clusters){
            if(isBackupScheduled(clusterId)){
                CompletableFuture.runAsync(()->{
                    System.out.println("Starting scheduled backup for cluster " + clusterId);
                    backupService.backupCluster(clusterId,true);
                });
            }
        }
    }

    public Map<String,Object> scheduledBackup(String clusterId,Map<String,Object> config){
        String cronExpression = (String)config.getOrDefault("cron","0 0 2 * * ?");
        boolean enabled = (boolean) config.getOrDefault("enabled",true);
        if(enabled){
            ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(
                    ()->backupService.backupCluster(clusterId,true),
                    new CronTrigger(cronExpression)
            );
            scheduledBackups.put(clusterId,scheduledFuture);
        }
        return Map.of("success", true, "clusterId", clusterId, "scheduled", enabled);
    }
    private boolean isBackupScheduled(String clusterId){
        return scheduledBackups.containsKey(clusterId) && !scheduledBackups.get(clusterId).isCancelled();
    }
}

