package com.omnexus.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import javax.print.DocFlavor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class BackupProgressService {
    private final Map<String,BackupProgress> activeBackups = new ConcurrentHashMap<>();

    @Data
    public static class BackupProgress {
        private String clusterId;
        private String status;// "starting", "dumping", "compressing", "completed", "failed"
        private int totalStep = 4;
        private int currentStep = 0;
        private String currentOperation;
        private long startTime;
        private long estimatedEndTime;
        private List<String> completedDatabases = new ArrayList<>();
    }
    public void startBackupProgress(String clusterId){
        BackupProgress progress = new BackupProgress();
        progress.setClusterId(clusterId);
        progress.setStatus("starting");
        progress.setStartTime(System.currentTimeMillis());
        progress.setCurrentOperation("Initializing backup...");
        activeBackups.put(clusterId,progress);
    }
    public void updateProgress(String clusterId,String operation,int step){
        BackupProgress progress = activeBackups.get(clusterId);
        if(progress!=null){
            progress.setCurrentStep(step);
            progress.setStatus(operation);
            progress.setCurrentOperation("in_progress");
        }
    }
    public void completeBackup(String clusterId,boolean success){
        BackupProgress progress = activeBackups.get(clusterId);
        if(progress!=null){
            progress.setStatus(success?"completed":"failed");
            progress.setCurrentStep(progress.getTotalStep());

            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES).execute(()->activeBackups.remove(clusterId));
        }
    }

    public Map<String,Object> getBackupProgress(String clusterId){
        BackupProgress progress = activeBackups.get(clusterId);
        if(progress==null){
            return Map.of("status","no_backup_running");
        }
        return Map.of(
                "clusterId",clusterId,
                "status",progress.getStatus(),
                "progress",(progress.getCurrentStep() *100)/progress.getTotalStep(),
                "currentOperation", progress.getCurrentOperation(),
                "startTime", progress.getStartTime(),
                "completedDatabases", progress.getCompletedDatabases()
        );
    }
}
