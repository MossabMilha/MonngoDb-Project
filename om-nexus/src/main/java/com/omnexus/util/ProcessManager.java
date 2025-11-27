package com.omnexus.util;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class ProcessManager {
    private static final Map<String,Process> runningProcesses = new ConcurrentHashMap<>();

    private static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void killProcessesOnPortRange(int startPort, int endPort) {
        try {
            System.out.println("=== Cleaning up processes on ports " + startPort + "-" + endPort + " ===");
            
            for (int port = startPort; port <= endPort; port++) {
                killProcessOnPort(port);
            }
            
            // Also kill mongos on port 27999
            killProcessOnPort(27999);
            
            // Clear our process map
            runningProcesses.clear();
            
            // Wait for processes to fully terminate
            Thread.sleep(2000);
            
            System.out.println("=== Port cleanup complete ===");
            
        } catch (Exception e) {
            System.err.println("Error during port cleanup: " + e.getMessage());
        }
    }

    private static void killProcessOnPort(int port) {
        try {
            // Find process using the port on Windows
            ProcessBuilder pb = new ProcessBuilder("netstat", "-ano");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(":" + port + " ") && line.contains("LISTENING")) {
                        // Extract PID from the line (last column)
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 4) {
                            String pid = parts[parts.length - 1];
                            System.out.println("Killing process " + pid + " on port " + port);
                            
                            // Kill the process
                            ProcessBuilder killPb = new ProcessBuilder("taskkill", "/F", "/PID", pid);
                            killPb.start().waitFor();
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignore errors for individual ports
            System.out.println("No process found on port " + port + " or failed to kill");
        }
    }

    public static boolean startMongodProcess(String nodeId, String type, int port, String dataPath, String replicaSet) {
        try {
            // Check if port is available, if not try to kill only that specific port
            if (!isPortAvailable(port)) {
                System.out.println("Port " + port + " is in use, attempting to free it...");
                killProcessOnPort(port);
                Thread.sleep(1000); // Wait for process to die

                if (!isPortAvailable(port)) {
                    System.err.println("ERROR: Port " + port + " is still in use for " + nodeId + " after cleanup");
                    return false;
                }
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            String mongodPath = "C:\\Program Files\\MongoDB\\Server\\8.2\\bin\\mongod.exe";

            // Check if mongod.exe exists
            File mongodFile = new File(mongodPath);
            if (!mongodFile.exists()) {
                System.err.println("ERROR: mongod.exe not found at: " + mongodPath);
                return false;
            }

            // Ensure data directory exists
            File dataDir = new File(dataPath);
            if (!dataDir.exists()) {
                System.out.println("Creating data directory: " + dataPath);
                dataDir.mkdirs();
            }

            // Remove stale lock files (both mongod.lock and WiredTiger.lock)
            File lockFile = new File(dataPath, "mongod.lock");
            if (lockFile.exists()) {
                System.out.println("Removing stale lock file: " + lockFile.getAbsolutePath());
                lockFile.delete();
            }
            File wiredTigerLock = new File(dataPath, "WiredTiger.lock");
            if (wiredTigerLock.exists()) {
                System.out.println("Removing stale WiredTiger lock file: " + wiredTigerLock.getAbsolutePath());
                wiredTigerLock.delete();
            }

            String logPath = dataPath + File.separator + "mongod.log";

            if("config".equals(type)) {
                processBuilder.command(
                        mongodPath,
                        "--configsvr",
                        "--replSet", replicaSet,
                        "--port", String.valueOf(port),
                        "--dbpath", dataPath,
                        "--bind_ip", "localhost",
                        "--logpath", logPath,
                        "--logappend"
                );
            } else if("shard".equals(type)) {
                processBuilder.command(
                        mongodPath,
                        "--shardsvr",
                        "--replSet", replicaSet,
                        "--port", String.valueOf(port),
                        "--dbpath", dataPath,
                        "--bind_ip", "localhost",
                        "--logpath", logPath,
                        "--logappend"
                );
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            runningProcesses.put(nodeId, process);

            startOutputReader(nodeId, process);
            Thread.sleep(3000);

            if (!process.isAlive()) {
                System.err.println("ERROR: Process " + nodeId + " died immediately after start");
                runningProcesses.remove(nodeId);
                return false;
            }

            System.out.println("Started " + type + " process: " + nodeId + " on port " + port);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to start mongod process " + nodeId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            System.err.println("Interrupted while starting mongod process " + nodeId);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void startOutputReader(String nodeId, Process process) {
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + nodeId + "] " + line);
                }
            } catch (IOException e) {
                // Process terminated, this is expected
            }
        });
        outputThread.setDaemon(true);
        outputThread.setName("OutputReader-" + nodeId);
        outputThread.start();
    }
    public static  boolean stopProcess(String nodeId){
        Process process = runningProcesses.get(nodeId);
        if(process != null){
            process.destroy();
            runningProcesses.remove(nodeId);
            return true;
        }
        return false;
    }
    public static boolean isProcessRunning(String nodeId){
        Process process = runningProcesses.get(nodeId);
        return process != null && process.isAlive();
    }

    /**
     * Check if a process is running on a specific port (regardless of our tracking map)
     */
    public static boolean isProcessRunningOnPort(int port) {
        return !isPortAvailable(port);
    }
    public static boolean startMongosProcess(String nodeId,int port,String configReplSet){
        try{
            ProcessBuilder processBuilder = new ProcessBuilder();
            String mongosPath = "C:\\Program Files\\MongoDB\\Server\\8.2\\bin\\mongos.exe";

            // Check if mongos.exe exists
            File mongosFile = new File(mongosPath);
            if (!mongosFile.exists()) {
                System.err.println("ERROR: mongos.exe not found at: " + mongosPath);
                return false;
            }

            processBuilder.command(
                    mongosPath,
                    "--port",String.valueOf(port),
                    "--configdb",configReplSet,
                    "--bind_ip", "localhost"
            );

            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            runningProcesses.put(nodeId,process);

            // Start a thread to read process output
            startOutputReader(nodeId, process);

            // Wait a bit to see if process starts successfully
            Thread.sleep(1000);

            if (!process.isAlive()) {
                System.err.println("ERROR: Process " + nodeId + " died immediately after start");
                runningProcesses.remove(nodeId);
                return false;
            }

            System.out.println("Started mongos process: " + nodeId + " on port " + port);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to start mongos process " + nodeId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    public static void killProcessesOnPort(int port) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows: Find and kill process on port
                ProcessBuilder findProcess = new ProcessBuilder(
                        "cmd.exe", "/c",
                        "for /f \"tokens=5\" %a in ('netstat -aon ^| findstr :" + port + "') do @echo %a"
                );
                Process process = findProcess.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String pid;
                while ((pid = reader.readLine()) != null) {
                    pid = pid.trim();
                    if (!pid.isEmpty() && pid.matches("\\d+")) {
                        System.out.println("Killing process " + pid + " on port " + port);
                        Runtime.getRuntime().exec("taskkill /F /PID " + pid);
                    }
                }

            } else {
                // Linux/Mac: Find and kill process on port
                ProcessBuilder findProcess = new ProcessBuilder(
                        "sh", "-c",
                        "lsof -ti:" + port
                );
                Process process = findProcess.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String pid;
                while ((pid = reader.readLine()) != null) {
                    pid = pid.trim();
                    if (!pid.isEmpty()) {
                        System.out.println("Killing process " + pid + " on port " + port);
                        Runtime.getRuntime().exec("kill -9 " + pid);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error killing process on port " + port + ": " + e.getMessage());
        }
    }



    private static String findMongoToolPath(String toolName) {
        String[] possiblePaths = {
            "C:\\Program Files\\MongoDB\\Server\\8.2\\bin\\" + toolName + ".exe",
            "C:\\Program Files\\MongoDB\\Server\\8.0\\bin\\" + toolName + ".exe", 
            "C:\\Program Files\\MongoDB\\Server\\7.0\\bin\\" + toolName + ".exe",
            "C:\\Program Files\\MongoDB\\Server\\6.0\\bin\\" + toolName + ".exe",
            "C:\\Program Files\\MongoDB\\Tools\\100\\bin\\" + toolName + ".exe",
            "C:\\mongodb\\bin\\" + toolName + ".exe",
            toolName + ".exe"
        };
        
        System.out.println("Searching for " + toolName + "...");
        for (String path : possiblePaths) {
            File file = new File(path);
            System.out.println("Checking: " + path + " - exists: " + file.exists());
            if (file.exists()) {
                System.out.println("Found " + toolName + " at: " + path);
                return path;
            }
        }
        
        System.err.println("Could not find " + toolName + " in any standard location");
        return toolName + ".exe";
    }

    public static boolean runMongoDump(String host,int port,String dbName,String outDir,boolean compress,boolean useOplog){
        List<String> cmd = new ArrayList<>();
        cmd.add(findMongoToolPath("mongodump"));
        cmd.add("--host");
        cmd.add(host+":"+port);
        if(dbName != null && !dbName.isBlank()){
            cmd.add("--db");
            cmd.add(dbName);
        }
        // Use absolute path to avoid nested directory issues
        File outDirFile = new File(outDir);
        String absoluteOutDir = outDirFile.getAbsolutePath();
        cmd.add("--out");
        cmd.add(absoluteOutDir);
        if(useOplog){
            cmd.add("--oplog");
        }
        // Use current working directory instead of outDir to avoid path issues
        int rc = runProcessAndWait(cmd, System.getProperty("user.dir"));
        if (rc != 0) return false;
        if (compress){
            return compressDirectory(outDir);
        }
        return true;
    }

    public static boolean runMongoRestore(String host,int port,String dumpDir,boolean dropBeforeRestore,boolean decompress){
        List<String> cmd = new ArrayList<>();
        cmd.add(findMongoToolPath("mongorestore"));
        cmd.add("--host");
        cmd.add(host+":"+port);
        if (dropBeforeRestore) cmd.add("--drop");

        // Extract database name from the directory path
        File dumpDirFile = new File(dumpDir);
        String dbName = dumpDirFile.getName();

        // Use absolute path
        String absoluteDumpDir = dumpDirFile.getAbsolutePath();

        // Add --db flag to specify which database to restore to
        // and --dir flag to specify the source directory
        cmd.add("--db");
        cmd.add(dbName);
        cmd.add("--dir");
        cmd.add(absoluteDumpDir);

        System.out.println("Running mongorestore: " + String.join(" ", cmd));

        // Use current working directory for process execution
        int rc = runProcessAndWait(cmd, System.getProperty("user.dir"));
        return rc == 0;
    }
    public static boolean compressDirectory(String directoryPath){
        File dir = new File(directoryPath);
        if(!dir.exists() || !dir.isDirectory()){
            System.err.println("Directory does not exist: " + directoryPath);
            return false;
        }
        String os = System.getProperty("os.name").toLowerCase();
        try{
            if(os.contains("win")){
                String zipFilePath = directoryPath+".zip";
                try(FileOutputStream fos = new FileOutputStream(zipFilePath); ZipOutputStream zos = new ZipOutputStream(fos)){
                    zipFolder(dir,dir.getName(),zos);
                }
                deleteDirectory(dir);
                System.out.println("Compressed directory to " + zipFilePath);
            }else{
                String tarGzFile = directoryPath+".tar.gz";
                ProcessBuilder processBuilder = new ProcessBuilder("tar","-czf",tarGzFile,"-C",dir.getParent(),dir.getName());
                processBuilder.inheritIO();
                Process p = processBuilder.start();
                int rc = p.waitFor();
                if(rc != 0) return false;
                deleteDirectory(dir);
                System.out.println("Compressed directory to " + tarGzFile);
            }
            return true;
        } catch (Exception e) {
            System.err.println("Failed to compress directory: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    // Recursive helper for ZIP
    private static void zipFolder(File folder, String parentName, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipFolder(file, parentName + "/" + file.getName(), zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry entry = new ZipEntry(parentName + "/" + file.getName());
                    zos.putNextEntry(entry);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
    }


    public static int runProcessAndWait(List<String> command, String workingDir) {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            // Read process output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int rc = process.waitFor();
            if (rc != 0) {
                System.err.println("Process failed with exit code " + rc + ": " + String.join(" ", command));
            }
            return rc;
        } catch (Exception e) {
            System.err.println("Failed to run process: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    // Delete directory recursively
    private static void deleteDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                deleteDirectory(f);
            }
        }
        Files.delete(dir.toPath());
    }
    public static boolean decompressFile(String compressedFilePath,String outputDir){
        try{
            List<String> cmd = new ArrayList<>();
            if(System.getProperty("os.name").toLowerCase().contains("win")){
                cmd.add("tar");
                cmd.add("-xzf");
            }else{
                cmd.add("tar");
                cmd.add("-xzf");
            }
            cmd.add(compressedFilePath);
            cmd.add("-C");
            cmd.add(outputDir);
            int rc = runProcessAndWait(cmd,outputDir);
            return rc == 0;
        } catch (Exception e) {
            System.err.println("Failed to decompress file: " + e.getMessage());
            return false;
        }
    }

}
