# 🗄️ MongoDB Sharded Cluster Manager

A comprehensive **MongoDB sharded cluster management system** built with Spring Boot, enabling dynamic cluster lifecycle management, monitoring, shard operations, and data management.

---

## 📊 Project Overview

This system allows you to:

- Create, start, stop, and delete sharded MongoDB clusters
- Monitor cluster health, nodes, and shard distribution
- Manage individual nodes and shards
- Perform data operations such as enabling sharding, creating collections, inserting documents, and moving chunks
- Handle cluster lifecycle entirely from RESTful APIs

---

## ✅ Features

### **1. Cluster Management**
- Create, start, stop, initialize, and delete clusters
- Automatic port and data directory setup
- Replica set initialization for config servers and shards
- Cluster cleanup and stale process handling

### **2. Monitoring & Observability**
- Real-time cluster and node status
- Cluster metrics and detailed health checks
- Replica set and shard distribution monitoring

### **3. Node Management**
- Start, stop, restart individual nodes
- Remove nodes from cluster
- Get node status and detailed information

### **4. Shard Management**
- Add or remove shards
- Trigger shard rebalancing
- Monitor shard data distribution
- Retrieve shard information and statistics

### **5. Data Operations**
- Enable sharding on databases
- Create sharded collections
- Insert documents into collections
- Get collection statistics
- Retrieve detailed shard distribution
- Move chunks between shards
- Bulk JSON insert with batching support

### **6. Advanced Features (Planned)**
- Backup & restore functionality
- Failure simulation tools
- Advanced metrics collection
- **Advanced validation on data before insert** (optional checks, schema validation)
- **Progress tracking for large/bulk uploads** (real-time upload status)
- **Support for compressed uploads** (gzip or other compressed formats)

---

## 🛠️ Technical Stack

- **Framework:** Spring Boot
- **Database:** MongoDB 8.2
- **Language:** Java
- **Architecture:** RESTful API
- **Process Management:** Java ProcessBuilder
- **Configuration:** JSON file persistence
- **Build Tool:** Maven

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven
- MongoDB binaries in PATH (or bundled in project)
- Windows or Linux environment

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

## 📈 API Overview

### Cluster APIs
- `POST /api/cluster/create` – Create cluster
- `POST /api/cluster/{clusterId}/start` – Start cluster
- `POST /api/cluster/{clusterId}/initialize` – Initialize cluster
- `POST /api/cluster/{clusterId}/stop` – Stop cluster
- `DELETE /api/cluster/{clusterId}` – Delete cluster
- `GET /api/cluster/{clusterId}` – Get cluster config

### Node APIs
- `GET /api/clusters/{clusterId}/nodes` – List all nodes
- `GET /api/clusters/{clusterId}/nodes/{nodeId}` – Node info
- `POST /api/clusters/{clusterId}/nodes/{nodeId}/start` – Start node
- `POST /api/clusters/{clusterId}/nodes/{nodeId}/stop` – Stop node
- `POST /api/clusters/{clusterId}/nodes/{nodeId}/restart` – Restart node
- `DELETE /api/clusters/{clusterId}/nodes/{nodeId}` – Remove node

### Shard APIs
- `GET /api/clusters/{clusterId}/shards` – List shards
- `GET /api/clusters/{clusterId}/shards/{shardId}` – Shard details
- `POST /api/clusters/{clusterId}/shards/add?shardId={shardId}` – Add shard
- `DELETE /api/clusters/{clusterId}/shards/{shardId}` – Remove shard
- `POST /api/clusters/{clusterId}/shards/{shardId}/rebalance` – Rebalance shard

### Database & Collection APIs
- `POST /api/databases/{databaseName}/enableSharding` – Enable sharding
- `POST /api/databases/{databaseName}/collection/{collectionName}` – Create sharded collection
- `POST /api/databases/{databaseName}/collection/{collectionName}/insert` – Insert document or bulk upload
- `GET /api/databases/{databaseName}/collection/{collectionName}/stats` – Collection stats
- `GET /api/databases/{databaseName}/shardDistribution` – Shard distribution
- `POST /api/databases/{databaseName}/collection/{collectionName}/moveChunk` – Move chunk

---

## 📈 Progress Summary

| Phase | Status |
|-------|--------|
| Phase 1: Cluster Management | ✅ Complete |
| Phase 2: Monitoring & Observability | ✅ Complete |
| Phase 3: Node Management | ✅ Complete |
| Phase 4: Shard Management | ✅ Complete |
| Phase 5: Data Operations | ✅ Complete |
| Phase 6: Advanced Features | ❌ In Progress |

**Overall Completion: ~90–95%**

---

## 🎯 Next Steps

1. Implement Advanced Features (Phase 6)
- Backup & restore
- Failure simulation
- Advanced metrics
- Advanced validation before insert
- Progress tracking for bulk uploads
- Support for compressed uploads

---

## 📝 Notes

- Cluster configuration JSON files stored in `./configs/`
- Data directories created in `./data/`
- Ports start at 28000 to avoid conflicts with default MongoDB
- Mongos router runs on port 27999
- All processes run locally
- Windows-specific process cleanup implemented

---

**Created with ❤️ by the OM Nexus Team**
