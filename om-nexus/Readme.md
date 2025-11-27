# 🗄️ MongoDB Sharded Cluster Manager

A comprehensive **MongoDB sharded cluster management system** built with Spring Boot, enabling dynamic cluster lifecycle management, monitoring, shard operations, and data management.

---

## 📊 Project Overview

This system allows you to:

* Create, start, stop, and delete sharded MongoDB clusters
* Monitor cluster health, nodes, and shard distribution
* Manage individual nodes and shards
* Perform data operations such as enabling sharding, creating collections, inserting documents, and moving chunks
* **Track which shard each document belongs to**
* **Split and distribute chunks across shards**
* Handle cluster lifecycle entirely from RESTful APIs

---

## ✅ Features

### **1. Cluster Management**

* Create, start, stop, initialize, and delete clusters
* Automatic port and data directory setup
* Replica set initialization for config servers and shards
* Cluster cleanup and stale process handling

### **2. Monitoring & Observability**

* Real-time cluster and node status (auto-detected via connection)
* Cluster metrics and detailed health checks
* Replica set and shard distribution monitoring
* **Per-document shard tracking**

### **3. Node Management**

* Start, stop, restart individual nodes
* Remove nodes from cluster
* Get node status and detailed information

### **4. Shard Management**

* Add or remove shards
* Trigger shard rebalancing
* Monitor shard data distribution
* Retrieve shard information and statistics
* **Split chunks at custom split points**
* **Distribute chunks evenly across shards**
* **Move individual chunks between shards**

### **5. Data Operations**

* Enable sharding on databases
* Create sharded collections with pre-split points
* Insert documents into collections (auto-routed to correct shard)
* Get collection statistics
* Retrieve detailed shard distribution per collection
* Move chunks between shards
* Bulk JSON insert with batching support
* **List documents with their shard location**

### **6. MongoDB 8.0 Compatibility** ✅ NEW

* **UUID-based chunk lookup** (MongoDB 5.0+ stores chunks by UUID, not namespace)
* **Proper shard name resolution** (uses replica set name)
* **Fallback to primary shard** when chunks not found
* **Proper ObjectId serialization** (hex string instead of object)

---

## 🛠️ Technical Stack

* **Framework:** Spring Boot
* **Database:** MongoDB 8.2
* **Language:** Java
* **Architecture:** RESTful API
* **Process Management:** Java ProcessBuilder
* **Configuration:** JSON file persistence
* **Build Tool:** Maven

---

## 🚀 Getting Started

### Prerequisites

* Java 17+
* Maven
* MongoDB binaries in PATH (or bundled in project)
* Windows or Linux environment

### Running the Project

1. Clone the repository:

```bash
git clone <repository-url>
cd om-nexus
```

2. Build the project:

```bash
mvn clean install
```

3. Run the Spring Boot application:

```bash
mvn spring-boot:run
```

4. Access REST API endpoints at:

```
http://localhost:9090/api/
```

---

## 📁 Project Structure

```
om-nexus/
├── configs/            # Cluster configuration JSON files
├── data/               # MongoDB data directories
│   ├── config/         # Config server data
│   └── shard/          # Shard server data
├── src/
│   ├── main/
│   │   ├── java/com/omnexus/
│   │   │   ├── config/         # Spring configuration
│   │   │   ├── controller/     # REST controllers
│   │   │   ├── model/          # Data models
│   │   │   ├── service/        # Business logic services
│   │   │   └── util/           # Utilities (MongoConnectionUtil, ProcessManager)
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       └── templates/
│   └── test/                   # Unit and integration tests
├── target/                       # Maven build output
├── pom.xml                       # Maven dependencies
└── README.md                     # This file
```

---

## 📈 API Reference (For Frontend Development)

---

### 🟢 Group 1: Cluster Management APIs ✅ WORKING

#### 1.1 Create Cluster
```
POST /api/cluster/create
```
**What it does:** Creates a new MongoDB sharded cluster configuration

**Request Body:**
```json
{
  "clusterId": "my-cluster",
  "numberOfShards": 2,
  "numberOfConfigServers": 3
}
```

**Response:**
```json
{
  "success": true,
  "message": "Cluster created successfully",
  "clusterId": "my-cluster"
}
```

#### 1.2 Start Cluster
```
POST /api/cluster/{clusterId}/start
```
**What it does:** Starts all MongoDB processes (config servers, shards, mongos)

**Response:**
```json
{
  "success": true,
  "message": "Cluster started successfully"
}
```

#### 1.3 Initialize Cluster
```
POST /api/cluster/{clusterId}/initialize
```
**What it does:** Initializes replica sets and adds shards to the cluster

**Response:**
```json
{
  "success": true,
  "message": "Cluster initialized successfully"
}
```

#### 1.4 Stop Cluster
```
POST /api/cluster/{clusterId}/stop
```
**What it does:** Stops all MongoDB processes in the cluster

**Response:**
```json
{
  "success": true,
  "message": "Cluster stopped"
}
```

#### 1.5 Delete Cluster
```
DELETE /api/cluster/{clusterId}
```
**What it does:** Deletes cluster configuration and optionally data

**Response:**
```json
{
  "success": true,
  "message": "Cluster deleted"
}
```

#### 1.6 Get Cluster Status
```
GET /api/cluster/{clusterId}
```
**What it does:** Returns cluster configuration and status

**Response:**
```json
{
  "clusterId": "test",
  "numberOfShards": 2,
  "numberOfConfigServers": 3,
  "nodes": [
    {"nodeId": "config-1", "type": "config", "port": 28000, "status": "running"},
    {"nodeId": "shard-1", "type": "shard", "port": 28003, "status": "running"}
  ]
}
```

---

### 🟢 Group 2: Node Management APIs ✅ WORKING

#### 2.1 List All Nodes
```
GET /api/clusters/{clusterId}/nodes
```
**What it does:** Returns all nodes in the cluster

**Response:**
```json
[
  {"nodeId": "config-1", "type": "config", "port": 28000, "status": "running"},
  {"nodeId": "shard-1", "type": "shard", "port": 28003, "status": "running"},
  {"nodeId": "mongos", "type": "mongos", "port": 27999, "status": "running"}
]
```

#### 2.2 Get Node Info
```
GET /api/clusters/{clusterId}/nodes/{nodeId}
```
**What it does:** Returns detailed info about a specific node

**Response:**
```json
{
  "nodeId": "shard-1",
  "type": "shard",
  "port": 28003,
  "status": "running",
  "replicaSet": "shard1",
  "dataPath": "C:\\...\\data\\shard\\shard1"
}
```

#### 2.3 Start/Stop/Restart Node
```
POST /api/clusters/{clusterId}/nodes/{nodeId}/start
POST /api/clusters/{clusterId}/nodes/{nodeId}/stop
POST /api/clusters/{clusterId}/nodes/{nodeId}/restart
```
**What it does:** Controls individual node lifecycle

**Response:**
```json
{
  "success": true,
  "message": "Node started successfully"
}
```

---

### 🟢 Group 3: Shard Management APIs ✅ WORKING

#### 3.1 List All Shards
```
GET /api/cluster/{clusterId}/shards
```
**What it does:** Returns all shards with their status

**Response:**
```json
[
  {
    "shardId": "shard-1",
    "replicaSet": "shard1",
    "host": "localhost",
    "port": 28003,
    "status": "running",
    "dataSize": 0,
    "chunkCount": 0,
    "primary": true
  },
  {
    "shardId": "shard-2",
    "replicaSet": "shard2",
    "host": "localhost",
    "port": 28004,
    "status": "running"
  }
]
```

#### 3.2 Get Specific Shard
```
GET /api/cluster/{clusterId}/shards/{shardId}
```
**What it does:** Returns info about a specific shard

**Response:**
```json
{
  "shardId": "shard-1",
  "replicaSet": "shard1",
  "port": 28003,
  "status": "running"
}
```

#### 3.3 Add New Shard
```
POST /api/cluster/{clusterId}/shards/add?shardId=shard-3
```
**What it does:** Creates and adds a new shard to the cluster

**Response (Success):**
```json
{
  "success": true,
  "message": "New shard created and added successfully"
}
```

**Response (Error - already exists):**
```json
{
  "success": false,
  "message": "Failed to create/add shard"
}
```

#### 3.4 Remove Shard
```
DELETE /api/cluster/{clusterId}/shards/{shardId}
```
**What it does:** Removes a shard from the cluster

**Response:**
```json
{
  "success": true,
  "message": "Shard removed successfully"
}
```

#### 3.5 Start Rebalancer
```
POST /api/cluster/{clusterId}/shards/rebalance
```
**What it does:** Starts the MongoDB balancer to redistribute chunks

**Response:**
```json
{
  "success": true,
  "message": "Balancer started successfully"
}
```

#### 3.6 Split and Distribute Chunks
```
POST /api/cluster/{clusterId}/shards/distribute?databaseName=TestDB&collectionName=Users&shardKey=userId&splitPoints=1000&splitPoints=5000
```
**What it does:** Splits chunks at specified points and distributes them across shards

**Query Parameters:**
- `databaseName` - Database name
- `collectionName` - Collection name
- `shardKey` - The shard key field
- `splitPoints` - Values to split at (can have multiple)

**Response:**
```json
{
  "success": true,
  "message": "Chunks split and distributed successfully"
}
```

#### 3.7 Move Chunks Evenly
```
POST /api/cluster/{clusterId}/shards/moveChunks?databaseName=TestDB&collectionName=Users
```
**What it does:** Moves existing chunks to distribute them evenly across shards

**Response:**
```json
{
  "success": true,
  "message": "Chunks moved successfully"
}
```

---

### 🟢 Group 4: Database & Collection APIs ✅ WORKING

#### 4.1 List Databases
```
GET /api/databases/{clusterId}/list
```
**What it does:** Returns all databases in the cluster

**Response:**
```json
["admin", "config", "local", "TestDB", "BillingDB"]
```

#### 4.2 Enable Sharding on Database
```
POST /api/databases/{clusterId}/enable?databaseName=TestDB
```
**What it does:** Enables sharding on a database

**Response:**
```json
{
  "success": true,
  "message": "Sharding enabled on database TestDB"
}
```

#### 4.3 Create Sharded Collection
```
POST /api/databases/{clusterId}/collection/create?databaseName=TestDB&collectionName=Users&shardKey=userId
```
**What it does:** Creates a new sharded collection with the specified shard key

**Optional Query Parameters:**
- `splitPoints` - Pre-split points (e.g., `splitPoints=1000&splitPoints=5000`)

**Response:**
```json
{
  "success": true,
  "message": "Sharded collection created: TestDB.Users with shard key: userId"
}
```

#### 4.4 Insert Document
```
POST /api/databases/{clusterId}/collection/{collectionName}/insert?databaseName=TestDB
```
**What it does:** Inserts a document into the collection (auto-routed to correct shard)

**Request Body:**
```json
{
  "userId": 1234,
  "name": "John Doe",
  "email": "john@example.com",
  "role": "admin"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Document inserted successfully",
  "insertedId": "674697..."
}
```

#### 4.5 List Documents with Shard Info
```
GET /api/databases/{clusterId}/collection/documents?databaseName=TestDB&collectionName=Users&shardKey=userId
```
**What it does:** Returns all documents with their shard location

**Response:**
```json
{
  "documents": [
    {
      "document": {
        "_id": "674697...",
        "userId": 100,
        "name": "John",
        "email": "john@example.com"
      },
      "shard": "shard1"
    },
    {
      "document": {
        "_id": "674698...",
        "userId": 5000,
        "name": "Jane",
        "email": "jane@example.com"
      },
      "shard": "shard2"
    }
  ],
  "success": true,
  "count": 2
}
```

#### 4.6 Get Collection Stats
```
GET /api/databases/{clusterId}/collection/{collectionName}/stats?databaseName=TestDB
```
**What it does:** Returns collection statistics

**Response:**
```json
{
  "ns": "TestDB.Users",
  "count": 4,
  "size": 1024,
  "avgObjSize": 256,
  "storageSize": 4096,
  "nindexes": 2,
  "sharded": true
}
```

#### 4.7 Get Shard Distribution per Collection
```
GET /api/databases/{clusterId}/collection/{collectionName}/distribution?databaseName=TestDB
```
**What it does:** Shows how data is distributed across shards for a collection

**Response:**
```json
{
  "shard1": {
    "host": "localhost:28003",
    "chunkCount": 2,
    "dataSize": 512
  },
  "shard2": {
    "host": "localhost:28004",
    "chunkCount": 1,
    "dataSize": 256
  }
}
```

#### 4.8 Move Chunk
```
POST /api/databases/{clusterId}/collection/{collectionName}/moveChunk?databaseName=TestDB&shardKey=userId&shardKeyValue=100&toShard=shard2
```
**What it does:** Moves a specific chunk to a target shard

**Query Parameters:**
- `databaseName` - Database name
- `shardKey` - Shard key field name
- `shardKeyValue` - Value to find the chunk
- `toShard` - Target shard (replica set name, e.g., "shard2")

**Response:**
```json
{
  "success": true,
  "message": "Chunk moved successfully"
}
```

#### 4.9 Bulk Upload JSON
```
POST /api/databases/{clusterId}/collection/{collectionName}/bulkUpload?databaseName=TestDB
Content-Type: multipart/form-data
```
**What it does:** Uploads and inserts documents from a JSON file

**Request:** Form-data with file field named "file"

**Response:**
```json
{
  "success": true,
  "insertedCount": 1000,
  "message": "Bulk upload completed"
}
```

---

### 🔴 Group 5: Backup & Restore APIs ❌ NEEDS WORK

> ⚠️ **These APIs require mongodump/mongorestore in PATH and need further testing**

#### 5.1 Create Backup
```
POST /api/backup/{clusterId}
```
**What it does:** Creates a backup of the cluster (config + all shards)

**Request Body (optional):**
```json
{
  "compress": true
}
```

**Response:**
```json
{
  "clusterId": "test",
  "timestamp": "2025-11-26T20-00-00Z",
  "artifacts": [
    {"type": "config", "path": "backup/test/2025.../config-db", "success": true},
    {"type": "shard", "shard": "shard1", "path": "backup/test/2025.../shard-shard1", "success": true}
  ]
}
```

#### 5.2 List Backups
```
GET /api/backup/{clusterId}
```
**What it does:** Lists all backups for a cluster

**Response:**
```json
{
  "clusterId": "test",
  "backups": ["2025-11-26T20-00-00Z", "2025-11-25T15-30-00Z"]
}
```

#### 5.3 Restore Shard
```
POST /api/backup/{clusterId}/restore
```
**What it does:** Restores a shard from backup

**Request Body:**
```json
{
  "timestamp": "2025-11-26T20-00-00Z",
  "shard": "shard1",
  "drop": true
}
```

**Response:**
```json
{
  "shard": "shard1",
  "path": "backup/test/2025.../shard-shard1",
  "restored": true
}
```

#### 5.4 Get Cluster Config
```
GET /api/backup/{clusterId}/config
```
**What it does:** Returns the current cluster configuration

**Response:**
```json
{
  "clusterId": "test",
  "numberOfShards": 2,
  "nodes": [...]
}
```

#### 5.5 Reset Cluster Config
```
POST /api/backup/{clusterId}/reset-config?numberOfShards=2
```
**What it does:** Removes failed/stopped shards from config, keeps only running ones

**Response:**
```json
{
  "success": true,
  "clusterId": "test",
  "shardsBefore": 3,
  "shardsAfter": 2,
  "message": "Config reset successfully"
}
```

---

## 📈 Progress Summary

| Phase                               | Status         |
| ----------------------------------- | -------------- |
| Phase 1: Cluster Management         | ✅ Complete     |
| Phase 2: Monitoring & Observability | ✅ Complete     |
| Phase 3: Node Management            | ✅ Complete     |
| Phase 4: Shard Management           | ✅ Complete     |
| Phase 5: Data Operations            | ✅ Complete     |
| Phase 6: MongoDB 8.0 Compatibility  | ✅ Complete     |
| Phase 7: Backup & Restore           | ❌ Needs Work   |

**Overall Completion: ~90%**

---

## 🔴 What Still Needs Work

### Backup & Restore (Group 5)

| Issue | Description |
|-------|-------------|
| **mongodump/mongorestore** | Requires MongoDB tools to be installed and in PATH |
| **Compression** | Gzip compression not fully tested |
| **Full cluster restore** | Only shard restore is implemented, not full cluster |
| **Error handling** | Needs better error messages when backup fails |
| **Progress tracking** | No progress indication for large backups |

### ⚠️ **CRITICAL: Runtime Failure Handling** ❌ NOT IMPLEMENTED

| Issue | Description | Impact |
|-------|-------------|---------|
| **No Health Monitoring** | System doesn't detect when nodes fail during runtime | Failed nodes stay dead indefinitely |
| **No Auto-Recovery** | No automatic restart of failed processes | Manual intervention required for every failure |
| **No Failover Logic** | When a shard dies, data becomes permanently unavailable | Data loss and service interruption |
| **No Alerting System** | No notifications when failures occur | Failures go unnoticed until users complain |
| **No Process Monitoring** | No scheduled health checks of running processes | Zombie processes and resource leaks |
| **No Backup-Based Recovery** | Failed nodes aren't automatically restored from backups | Data inconsistency after failures |

### Production Readiness Issues:

**What Happens When Things Fail:**
- **Config Server Dies** → Cluster metadata unavailable, new operations fail ❌
- **Shard Dies** → Data on that shard becomes inaccessible ❌  
- **Mongos Dies** → Applications lose connection to cluster ❌
- **Process Crashes** → Stays crashed until manual restart ❌
- **Data Corruption** → No automatic recovery from backup ❌

### To Fix:
1. **Add HealthMonitorService** - Scheduled health checks every 30 seconds
2. **Add NodeRecoveryService** - Automatic restart and backup restoration
3. **Add FailureController** - Manual recovery endpoints
4. **Add Process Monitoring** - Track process health and auto-restart
5. **Add Alerting** - Notifications when failures occur
6. **Add Failover Logic** - Graceful handling of shard failures

**⚠️ WARNING: Current system is NOT production-ready due to lack of failure handling.**

---

## 🐛 Bug Fixes (Latest Update - Nov 26, 2025)

### Fixed Issues

| Issue | Description | Fix |
|-------|-------------|-----|
| **Shard shows "unknown"** | Documents always showed `shard: "unknown"` | Fixed chunk lookup filter logic and added UUID-based lookup for MongoDB 8.0 |
| **Wrong shard name format** | Code used `"shard-1"` instead of `"shard1"` | Changed to use `node.getReplicaSet()` instead of `node.getNodeId()` |
| **Data not distributed** | All data stayed on one shard | Added split/distribute APIs to properly split and move chunks |
| **ObjectId serialization** | `_id` showed as complex object | Added proper ObjectId to hex string conversion |
| **Shard status always "stopped"** | Even running shards showed stopped | Fixed status detection to use actual MongoDB connection |
| **Exception handling bug** | `createShardedCollection` returned true on failure | Fixed to return false on exception |
| **Duplicate shards** | Add shard created duplicate entries | Added existence check before adding |
| **Hardcoded mongos port** | Used fixed port 27999 | Now reads from cluster config |

---

## 📝 Notes

* Cluster configuration JSON files stored in `./configs/`
* Data directories created in `./data/`
* Ports start at 28000 to avoid conflicts with default MongoDB
* Mongos router runs on port 27999
* All processes run locally
* Windows-specific process cleanup implemented
* **MongoDB 8.0 compatible** - Uses UUID-based chunk lookup

---

## 🧪 API Status Summary

### ✅ Working APIs (Ready for Frontend)

| Group | API | Method | Status |
|-------|-----|--------|--------|
| Cluster | Create Cluster | POST | ✅ |
| Cluster | Start Cluster | POST | ✅ |
| Cluster | Stop Cluster | POST | ✅ |
| Cluster | Get Cluster Status | GET | ✅ |
| Nodes | List Nodes | GET | ✅ |
| Nodes | Start/Stop/Restart Node | POST | ✅ |
| Shards | List Shards | GET | ✅ |
| Shards | Add Shard | POST | ✅ |
| Shards | Remove Shard | DELETE | ✅ |
| Shards | Rebalance | POST | ✅ |
| Shards | Split & Distribute | POST | ✅ |
| Shards | Move Chunks | POST | ✅ |
| Database | List Databases | GET | ✅ |
| Database | Enable Sharding | POST | ✅ |
| Database | Create Collection | POST | ✅ |
| Database | Insert Document | POST | ✅ |
| Database | List Documents + Shard | GET | ✅ |
| Database | Get Stats | GET | ✅ |
| Database | Get Distribution | GET | ✅ |
| Database | Move Chunk | POST | ✅ |
| Database | Bulk Upload | POST | ✅ |
| Config | Get Cluster Config | GET | ✅ |
| Config | Reset Config | POST | ✅ |

### ❌ APIs Needing Work

| Group | API | Method | Issue |
|-------|-----|--------|-------|
| Backup | Create Backup | POST | Needs mongodump in PATH |
| Backup | List Backups | GET | Works but needs backups to exist |
| Backup | Restore Shard | POST | Needs mongorestore in PATH |

---

**Created with ❤️ by the OM Nexus Team**
