package com.omnexus.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

            // Remove stale lock file
            File lockFile = new File(dataPath, "mongod.lock");
            if (lockFile.exists()) {
                System.out.println("Removing stale lock file: " + lockFile.getAbsolutePath());
                lockFile.delete();
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
}
