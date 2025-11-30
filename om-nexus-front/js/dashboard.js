const API_BASE = 'http://localhost:9090/api';

// Get cluster ID from URL or sessionStorage
const urlParams = new URLSearchParams(window.location.search);
let clusterId = urlParams.get('clusterId') || sessionStorage.getItem('currentClusterId');

// State
let selectedDatabase = null;
let cachedDatabases = [];
let cachedShards = [];

// DOM Elements
const clusterTitle = document.getElementById('cluster-title');
const clusterStatus = document.getElementById('cluster-status');
const refreshBtn = document.getElementById('refresh-btn');
const stopBtn = document.getElementById('stop-btn');
const deleteBtn = document.getElementById('delete-btn');
const rebalanceBtn = document.getElementById('rebalance-btn');

// Stats elements
const statStatus = document.getElementById('stat-status');
const statNodes = document.getElementById('stat-nodes');
const statShards = document.getElementById('stat-shards');
const statConfig = document.getElementById('stat-config');

// Table elements
const nodesTbody = document.getElementById('nodes-tbody');
const nodesManagementTbody = document.getElementById('nodes-management-tbody');
const shardsTbody = document.getElementById('shards-tbody');
const databasesTbody = document.getElementById('databases-tbody');

// Navigation
const navItems = document.querySelectorAll('.nav-item[data-section]');
const sections = document.querySelectorAll('.section');

// Initialize
if (!clusterId) {
  // Show error instead of immediately redirecting
  console.error('No cluster ID found');
  alert('No cluster selected. Redirecting to cluster creation page.');
  window.location.href = 'index.html';
} else {
  clusterTitle.textContent = `Cluster: ${clusterId}`;
  sessionStorage.setItem('currentClusterId', clusterId);
}

// =====================
// TOAST NOTIFICATIONS
// =====================
function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `
    <span>${message}</span>
    <button class="toast-close" onclick="this.parentElement.remove()">&times;</button>
  `;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 5000);
}

// =====================
// MODAL FUNCTIONS
// =====================
function openModal(title, bodyHtml, footerHtml = '') {
  document.getElementById('modal-title').textContent = title;
  document.getElementById('modal-body').innerHTML = bodyHtml;
  document.getElementById('modal-footer').innerHTML = footerHtml;
  document.getElementById('modal-overlay').classList.remove('hidden');
}

function closeModal() {
  document.getElementById('modal-overlay').classList.add('hidden');
}

// Navigation handling
navItems.forEach(item => {
  item.addEventListener('click', (e) => {
    e.preventDefault();
    const sectionId = item.dataset.section;

    navItems.forEach(nav => nav.classList.remove('active'));
    item.classList.add('active');

    sections.forEach(section => {
      section.classList.remove('active');
      if (section.id === `${sectionId}-section`) {
        section.classList.add('active');
      }
    });

    loadSectionData(sectionId);
  });
});

// Load section data
function loadSectionData(section) {
  switch(section) {
    case 'overview':
      loadClusterStatus();
      loadNodes();
      break;
    case 'nodes':
      loadNodesManagement();
      break;
    case 'shards':
      loadShards();
      break;
    case 'databases':
      loadDatabases();
      break;
    case 'data':
      loadDataSection();
      break;
    case 'backup':
      loadBackupSection();
      break;
  }
}

// API Functions
async function loadClusterStatus() {
  try {
    // Use monitoring endpoint for accurate status
    const response = await fetch(`${API_BASE}/monitoring/cluster/${clusterId}`);
    const data = await response.json();

    if (data.error) {
      clusterStatus.textContent = 'Error';
      clusterStatus.className = 'cluster-status stopped';
      return;
    }

    // Use the status from monitoring service
    const status = data.status || 'stopped';

    if (status === 'running') {
      clusterStatus.textContent = 'Running';
      clusterStatus.className = 'cluster-status running';
      statStatus.textContent = 'Running';
      stopBtn.textContent = 'Stop Cluster';
    } else if (status === 'partial') {
      clusterStatus.textContent = 'Partial';
      clusterStatus.className = 'cluster-status';
      statStatus.textContent = 'Partial';
    } else {
      clusterStatus.textContent = 'Stopped';
      clusterStatus.className = 'cluster-status stopped';
      statStatus.textContent = 'Stopped';
      stopBtn.textContent = 'Start Cluster';
    }

    statNodes.textContent = data.totalNodes || 0;
    statShards.textContent = data.shardServers || 0;
    statConfig.textContent = data.configServers || 0;

  } catch (error) {
    console.error('Error loading cluster status:', error);
    clusterStatus.textContent = 'Error';
    clusterStatus.className = 'cluster-status stopped';
  }
}

async function loadNodes() {
  try {
    // Use monitoring endpoint to get nodes with live status
    const response = await fetch(`${API_BASE}/monitoring/cluster/${clusterId}`);
    const data = await response.json();
    const nodes = data.nodesStatuses || [];

    if (!Array.isArray(nodes) || nodes.length === 0) {
      nodesTbody.innerHTML = '<tr><td colspan="4" class="loading">No nodes found</td></tr>';
      return;
    }

    nodesTbody.innerHTML = nodes.map(node => {
      const isRunning = node.status === 'running';
      return `
        <tr>
          <td>${node.nodeId}</td>
          <td>${node.type || 'unknown'}</td>
          <td>${node.port}</td>
          <td><span class="status-badge ${isRunning ? 'status-running' : 'status-stopped'}">
            ${isRunning ? 'Running' : 'Stopped'}
          </span></td>
        </tr>
      `;
    }).join('');

  } catch (error) {
    console.error('Error loading nodes:', error);
    nodesTbody.innerHTML = '<tr><td colspan="4" class="loading">Error loading nodes</td></tr>';
  }
}

async function loadNodesManagement() {
  try {
    // Use monitoring endpoint to get nodes with live status
    const response = await fetch(`${API_BASE}/monitoring/cluster/${clusterId}`);
    const data = await response.json();
    const nodes = data.nodesStatuses || [];

    if (!Array.isArray(nodes) || nodes.length === 0) {
      nodesManagementTbody.innerHTML = '<tr><td colspan="5" class="loading">No nodes found</td></tr>';
      return;
    }

    nodesManagementTbody.innerHTML = nodes.map(node => {
      const isRunning = node.status === 'running';
      return `
        <tr>
          <td>${node.nodeId}</td>
          <td>${node.type || 'unknown'}</td>
          <td>${node.port}</td>
          <td><span class="status-badge ${isRunning ? 'status-running' : 'status-stopped'}">
            ${isRunning ? 'Running' : 'Stopped'}
          </span></td>
          <td>
            <button class="btn-primary btn-sm" onclick="restartNode('${node.nodeId}')">Restart</button>
          </td>
        </tr>
      `;
    }).join('');

  } catch (error) {
    console.error('Error loading nodes:', error);
    nodesManagementTbody.innerHTML = '<tr><td colspan="5" class="loading">Error loading nodes</td></tr>';
  }
}

async function loadShards() {
  try {
    const response = await fetch(`${API_BASE}/cluster/${clusterId}/shards`);
    const shards = await response.json();

    // API returns array of ShardInfo objects directly
    if (!Array.isArray(shards) || shards.length === 0) {
      shardsTbody.innerHTML = '<tr><td colspan="3" class="loading">No shards found</td></tr>';
      return;
    }

    shardsTbody.innerHTML = shards.map(shard => {
      const isRunning = shard.status === 'running';
      return `
        <tr>
          <td>${shard.shardId || 'N/A'}</td>
          <td>${shard.host || 'localhost'}:${shard.port || 'N/A'}</td>
          <td><span class="status-badge ${isRunning ? 'status-running' : 'status-stopped'}">
            ${isRunning ? 'Running' : 'Stopped'}
          </span></td>
        </tr>
      `;
    }).join('');

  } catch (error) {
    console.error('Error loading shards:', error);
    shardsTbody.innerHTML = '<tr><td colspan="3" class="loading">Error loading shards</td></tr>';
  }
}

async function loadDatabases() {
  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/listWithStatus`);
    const databases = await response.json();

    // Store just the names for other uses
    cachedDatabases = databases.map(db => db.name) || [];

    if (!Array.isArray(databases) || databases.length === 0) {
      databasesTbody.innerHTML = '<tr><td colspan="3" class="loading">No databases found</td></tr>';
      return;
    }

    databasesTbody.innerHTML = databases.map(db => {
      const shardingStatus = db.shardingEnabled ? 'Enabled' : 'Disabled';
      const statusClass = db.shardingEnabled ? 'status-running' : 'status-stopped';
      const buttonText = db.shardingEnabled ? 'Sharding Enabled' : 'Enable Sharding';
      const buttonDisabled = db.shardingEnabled ? 'disabled' : '';

      return `
        <tr>
          <td>${db.name}</td>
          <td><span class="status-badge ${statusClass}">${shardingStatus}</span></td>
          <td>
            <button class="btn-primary btn-sm" onclick="enableSharding('${db.name}')" ${buttonDisabled}>${buttonText}</button>
            <button class="btn-secondary btn-sm" onclick="selectDatabase('${db.name}')">View Collections</button>
          </td>
        </tr>
      `;
    }).join('');

  } catch (error) {
    console.error('Error loading databases:', error);
    showToast('Error loading databases: ' + error.message, 'error');
    databasesTbody.innerHTML = '<tr><td colspan="3" class="loading">Error loading databases</td></tr>';
  }
}

function selectDatabase(dbName) {
  selectedDatabase = dbName;
  document.getElementById('selected-db-name').textContent = dbName;
  document.getElementById('collections-card').style.display = 'block';
  loadCollections(dbName);
}

async function loadCollections(dbName) {
  const tbody = document.getElementById('collections-tbody');
  tbody.innerHTML = '<tr><td colspan="3" class="loading">Loading collections...</td></tr>';

  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/collections?databaseName=${dbName}`);
    const data = await response.json();

    if (data.collections && Object.keys(data.collections).length > 0) {
      tbody.innerHTML = Object.entries(data.collections).map(([name, info]) => `
        <tr>
          <td>${name}</td>
          <td><span class="status-badge ${info.sharded ? 'status-running' : 'status-stopped'}">
            ${info.sharded ? 'Sharded' : 'Not Sharded'}
          </span></td>
          <td>
            <button class="btn-primary btn-sm" onclick="viewCollectionStats('${dbName}', '${name}')">Stats</button>
          </td>
        </tr>
      `).join('');
    } else {
      tbody.innerHTML = '<tr><td colspan="3" class="loading">No collections found</td></tr>';
    }
  } catch (error) {
    console.error('Error loading collections:', error);
    tbody.innerHTML = '<tr><td colspan="3" class="loading">Error loading collections</td></tr>';
  }
}

function openCreateDatabaseModal() {
  openModal('Create Database', `
    <div class="form-group">
      <label>Database Name</label>
      <input type="text" id="new-db-name" placeholder="Enter database name">
    </div>
    <div class="form-group">
      <label>Initial Collection Name</label>
      <input type="text" id="new-db-collection" placeholder="Enter collection name (required)">
    </div>
    <div class="form-group">
      <label>Shard Key</label>
      <input type="text" id="new-db-shard-key" placeholder="e.g., _id or userId">
    </div>
    <div class="form-group">
      <label>
        <input type="checkbox" id="enable-sharding-check" checked> Enable Sharding
      </label>
    </div>
    <p style="font-size: 12px; color: #666; margin-top: 8px;">
      Note: MongoDB requires at least one collection to create a database.
    </p>
  `, `
    <button class="btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn-primary" onclick="createDatabase()">Create</button>
  `);
}

async function createDatabase() {
  const dbName = document.getElementById('new-db-name').value.trim();
  const collectionName = document.getElementById('new-db-collection').value.trim();
  const shardKey = document.getElementById('new-db-shard-key').value.trim() || '_id';
  const enableShardingFlag = document.getElementById('enable-sharding-check').checked;

  if (!dbName) {
    showToast('Please enter a database name', 'warning');
    return;
  }

  if (!collectionName) {
    showToast('Please enter a collection name', 'warning');
    return;
  }

  try {
    // Step 1: Enable sharding on the database if requested
    if (enableShardingFlag) {
      showToast('Enabling sharding on database...', 'info');
      const enableResponse = await fetch(`${API_BASE}/databases/${clusterId}/enable?databaseName=${dbName}`, {
        method: 'POST'
      });
      const enableResult = await enableResponse.json();
      if (!enableResult.success) {
        showToast(enableResult.message || 'Failed to enable sharding on database', 'error');
        return;
      }
    }

    // Step 2: Create the sharded collection
    showToast('Creating collection...', 'info');
    const createUrl = `${API_BASE}/databases/${clusterId}/collection/create?databaseName=${dbName}&collectionName=${collectionName}&shardKey=${shardKey}`;
    const createResponse = await fetch(createUrl, { method: 'POST' });
    const createResult = await createResponse.json();

    if (createResult.success) {
      showToast(`Database "${dbName}" with collection "${collectionName}" created successfully`, 'success');
      closeModal();
      loadDatabases();
    } else {
      showToast(createResult.message || 'Failed to create collection', 'error');
    }
  } catch (error) {
    showToast('Error creating database: ' + error.message, 'error');
  }
}

function openCreateCollectionModal() {
  if (!selectedDatabase) {
    showToast('Please select a database first', 'warning');
    return;
  }

  openModal('Create Sharded Collection', `
    <div class="form-group">
      <label>Database</label>
      <input type="text" value="${selectedDatabase}" disabled>
    </div>
    <div class="form-group">
      <label>Collection Name</label>
      <input type="text" id="new-collection-name" placeholder="Enter collection name">
    </div>
    <div class="form-group">
      <label>Shard Key</label>
      <input type="text" id="new-shard-key" placeholder="e.g., userId or _id">
    </div>
    <div class="form-group">
      <label>Split Values (optional, comma separated)</label>
      <input type="text" id="split-values" placeholder="e.g., 1000, 2000, 3000">
    </div>
  `, `
    <button class="btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn-primary" onclick="createCollection()">Create</button>
  `);
}

async function createCollection() {
  const collectionName = document.getElementById('new-collection-name').value.trim();
  const shardKey = document.getElementById('new-shard-key').value.trim();
  const splitValuesStr = document.getElementById('split-values').value.trim();

  if (!collectionName || !shardKey) {
    showToast('Please enter collection name and shard key', 'warning');
    return;
  }

  try {
    let url = `${API_BASE}/databases/${clusterId}/collection/create?databaseName=${selectedDatabase}&collectionName=${collectionName}&shardKey=${shardKey}`;

    if (splitValuesStr) {
      const splitValues = splitValuesStr.split(',').map(v => v.trim());
      splitValues.forEach(v => url += `&splitValues=${v}`);
    }

    const response = await fetch(url, { method: 'POST' });
    const result = await response.json();

    if (result.success) {
      showToast('Collection created successfully', 'success');
      closeModal();
      loadCollections(selectedDatabase);
    } else {
      showToast(result.message || 'Failed to create collection', 'error');
    }
  } catch (error) {
    showToast('Error creating collection: ' + error.message, 'error');
  }
}

async function viewCollectionStats(dbName, collectionName) {
  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/collection/${collectionName}/stats?databaseName=${dbName}`);
    const stats = await response.json();

    openModal(`Stats: ${dbName}.${collectionName}`, `
      <pre style="background: #f5f5f5; padding: 16px; border-radius: 8px; overflow: auto; max-height: 400px;">
${JSON.stringify(stats, null, 2)}
      </pre>
    `, `<button class="btn-primary" onclick="closeModal()">Close</button>`);
  } catch (error) {
    showToast('Error loading stats: ' + error.message, 'error');
  }
}

// Action Functions
async function restartNode(nodeId) {
  try {
    showToast('Restarting node...', 'info');
    const response = await fetch(`${API_BASE}/clusters/${clusterId}/nodes/${nodeId}/restart`, {
      method: 'POST'
    });
    const result = await response.json();
    if (result.success) {
      showToast(result.message || 'Node restarted successfully', 'success');
    } else {
      showToast(result.message || 'Failed to restart node', 'error');
    }
    loadNodesManagement();
  } catch (error) {
    showToast('Error restarting node: ' + error.message, 'error');
  }
}

async function enableSharding(dbName) {
  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/enable?databaseName=${dbName}`, {
      method: 'POST'
    });
    const result = await response.json();
    if (result.success) {
      showToast(result.message || 'Sharding enabled', 'success');
    } else {
      showToast(result.message || 'Failed to enable sharding', 'error');
    }
    loadDatabases();
  } catch (error) {
    showToast('Error enabling sharding: ' + error.message, 'error');
  }
}

// =====================
// DATA MANAGEMENT
// =====================
async function loadDataSection() {
  await populateDatabaseSelects();
}

async function populateDatabaseSelects() {
  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/list`);
    const databases = await response.json();
    cachedDatabases = databases || [];

    const selects = ['data-db-select', 'bulk-db-select', 'view-db-select'];
    selects.forEach(id => {
      const select = document.getElementById(id);
      if (select) {
        select.innerHTML = '<option value="">Select Database</option>' +
          databases.map(db => `<option value="${db}">${db}</option>`).join('');
      }
    });
  } catch (error) {
    showToast('Error loading databases: ' + error.message, 'error');
  }
}

async function loadCollectionsForSelect(dbName, selectId) {
  const select = document.getElementById(selectId);
  if (!select) return;

  select.innerHTML = '<option value="">Loading...</option>';

  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/collections?databaseName=${dbName}`);
    const data = await response.json();

    if (data.collections && Object.keys(data.collections).length > 0) {
      select.innerHTML = '<option value="">Select Collection</option>' +
        Object.keys(data.collections).map(name => `<option value="${name}">${name}</option>`).join('');
    } else {
      select.innerHTML = '<option value="">No collections found</option>';
    }
  } catch (error) {
    select.innerHTML = '<option value="">Error loading</option>';
  }
}

function loadCollectionsForData() {
  const db = document.getElementById('data-db-select').value;
  if (db) loadCollectionsForSelect(db, 'data-collection-select');
}

function loadCollectionsForBulk() {
  const db = document.getElementById('bulk-db-select').value;
  if (db) loadCollectionsForSelect(db, 'bulk-collection-select');
}

function loadCollectionsForView() {
  const db = document.getElementById('view-db-select').value;
  if (db) loadCollectionsForSelect(db, 'view-collection-select');
}

async function insertDocument() {
  const dbName = document.getElementById('data-db-select').value;
  const collectionName = document.getElementById('data-collection-select').value;
  const jsonStr = document.getElementById('document-json').value.trim();

  if (!dbName || !collectionName) {
    showToast('Please select database and collection', 'warning');
    return;
  }

  if (!jsonStr) {
    showToast('Please enter document JSON', 'warning');
    return;
  }

  let document;
  try {
    document = JSON.parse(jsonStr);
  } catch (e) {
    showToast('Invalid JSON format: ' + e.message, 'error');
    return;
  }

  try {
    const response = await fetch(`${API_BASE}/databases/${clusterId}/collection/${collectionName}/insert?databaseName=${dbName}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(document)
    });
    const result = await response.json();

    if (result.success) {
      showToast('Document inserted successfully', 'success');
      document.getElementById('document-json').value = '';
    } else {
      showToast(result.message || 'Failed to insert document', 'error');
    }
  } catch (error) {
    showToast('Error inserting document: ' + error.message, 'error');
  }
}

async function bulkUpload() {
  const dbName = document.getElementById('bulk-db-select').value;
  const collectionName = document.getElementById('bulk-collection-select').value;
  const fileInput = document.getElementById('bulk-file');
  const batchSize = document.getElementById('batch-size').value || 1000;

  if (!dbName || !collectionName) {
    showToast('Please select database and collection', 'warning');
    return;
  }

  if (!fileInput.files || !fileInput.files[0]) {
    showToast('Please select a JSON file', 'warning');
    return;
  }

  const formData = new FormData();
  formData.append('file', fileInput.files[0]);

  try {
    showToast('Uploading file...', 'info');
    const response = await fetch(
      `${API_BASE}/databases/${clusterId}/collection/${collectionName}/bulkUpload?databaseName=${dbName}&batchSize=${batchSize}`,
      { method: 'POST', body: formData }
    );
    const result = await response.json();

    if (result.success) {
      showToast(result.message || 'Bulk upload completed', 'success');
      fileInput.value = '';
    } else {
      showToast(result.message || 'Bulk upload failed', 'error');
    }
  } catch (error) {
    showToast('Error uploading file: ' + error.message, 'error');
  }
}

async function viewDocuments() {
  const dbName = document.getElementById('view-db-select').value;
  const collectionName = document.getElementById('view-collection-select').value;
  const shardKey = document.getElementById('view-shard-key').value.trim();
  const tbody = document.getElementById('documents-tbody');

  if (!dbName || !collectionName || !shardKey) {
    showToast('Please fill all fields', 'warning');
    return;
  }

  tbody.innerHTML = '<tr><td colspan="2" class="loading">Loading documents...</td></tr>';

  try {
    const response = await fetch(
      `${API_BASE}/databases/${clusterId}/collection/documents?databaseName=${dbName}&collectionName=${collectionName}&shardKey=${shardKey}`
    );
    const result = await response.json();

    if (result.success && result.documents && result.documents.length > 0) {
      tbody.innerHTML = result.documents.map(doc => `
        <tr>
          <td><div class="json-preview" title='${JSON.stringify(doc.document)}'>${JSON.stringify(doc.document).substring(0, 100)}...</div></td>
          <td>${doc.shard || 'Unknown'}</td>
        </tr>
      `).join('');
    } else {
      tbody.innerHTML = '<tr><td colspan="2" class="loading">No documents found</td></tr>';
    }
  } catch (error) {
    showToast('Error loading documents: ' + error.message, 'error');
    tbody.innerHTML = '<tr><td colspan="2" class="loading">Error loading documents</td></tr>';
  }
}

// Button Event Listeners
refreshBtn.addEventListener('click', () => {
  loadClusterStatus();
  const activeSection = document.querySelector('.nav-item.active');
  if (activeSection) {
    loadSectionData(activeSection.dataset.section);
  }
});

stopBtn.addEventListener('click', async () => {
  const isRunning = clusterStatus.textContent === 'Running';
  const action = isRunning ? 'stop' : 'start';

  if (!confirm(`Are you sure you want to ${action} the cluster?`)) return;

  try {
    const response = await fetch(`${API_BASE}/cluster/${clusterId}/${action}`, {
      method: 'POST'
    });
    const result = await response.json();
    alert(result.message || `Cluster ${action} initiated`);
    loadClusterStatus();
  } catch (error) {
    alert(`Error ${action}ing cluster: ` + error.message);
  }
});

deleteBtn.addEventListener('click', async () => {
  if (!confirm('Are you sure you want to DELETE this cluster? This cannot be undone!')) return;
  if (!confirm('This will stop all nodes and remove all data. Continue?')) return;

  try {
    const response = await fetch(`${API_BASE}/cluster/${clusterId}`, {
      method: 'DELETE'
    });
    const result = await response.json();
    alert(result.message || 'Cluster deleted');
    sessionStorage.removeItem('currentClusterId');
    window.location.href = 'index.html';
  } catch (error) {
    alert('Error deleting cluster: ' + error.message);
  }
});

rebalanceBtn.addEventListener('click', async () => {
  try {
    showToast('Rebalancing shards...', 'info');
    const response = await fetch(`${API_BASE}/cluster/${clusterId}/shards/rebalance`, {
      method: 'POST'
    });
    const result = await response.json();
    if (result.success) {
      showToast(result.message || 'Rebalance completed', 'success');
    } else {
      showToast(result.message || 'Rebalance failed', 'error');
    }
    loadShards();
  } catch (error) {
    showToast('Error rebalancing: ' + error.message, 'error');
  }
});

// =====================
// BACKUP & RESTORE
// =====================
async function loadBackupSection() {
  await loadBackups();
  await loadShardsForRestore();

  // Set up restore type toggle
  const restoreType = document.getElementById('restore-type');
  if (restoreType) {
    restoreType.addEventListener('change', () => {
      document.getElementById('shard-select-group').style.display =
        restoreType.value === 'shard' ? 'block' : 'none';
    });
  }
}

async function loadBackups() {
  const tbody = document.getElementById('backups-tbody');
  tbody.innerHTML = '<tr><td colspan="2" class="loading">Loading backups...</td></tr>';

  try {
    const response = await fetch(`${API_BASE}/backup/${clusterId}`);
    const data = await response.json();

    const backups = data.backups || [];

    if (backups.length === 0) {
      tbody.innerHTML = '<tr><td colspan="2" class="loading">No backups found</td></tr>';
      return;
    }

    tbody.innerHTML = backups.map(backup => `
      <tr>
        <td>${backup}</td>
        <td>
          <button class="btn-primary btn-sm" onclick="selectBackupForRestore('${backup}')">Select for Restore</button>
        </td>
      </tr>
    `).join('');

    // Also populate restore dropdown
    const restoreSelect = document.getElementById('restore-timestamp');
    if (restoreSelect) {
      restoreSelect.innerHTML = '<option value="">Select Backup</option>' +
        backups.map(b => `<option value="${b}">${b}</option>`).join('');
    }

  } catch (error) {
    showToast('Error loading backups: ' + error.message, 'error');
    tbody.innerHTML = '<tr><td colspan="2" class="loading">Error loading backups</td></tr>';
  }
}

async function loadShardsForRestore() {
  try {
    const response = await fetch(`${API_BASE}/cluster/${clusterId}/shards`);
    const shards = await response.json();
    cachedShards = shards || [];

    const select = document.getElementById('restore-shard');
    if (select && Array.isArray(shards)) {
      select.innerHTML = '<option value="">Select Shard</option>' +
        shards.map(s => `<option value="${s.replicaSet || s.shardId}">${s.shardId}</option>`).join('');
    }
  } catch (error) {
    console.error('Error loading shards for restore:', error);
  }
}

function selectBackupForRestore(timestamp) {
  const select = document.getElementById('restore-timestamp');
  if (select) {
    select.value = timestamp;
    showToast(`Selected backup: ${timestamp}`, 'info');
  }
}

async function createBackup(compress = false) {
  try {
    showToast('Creating backup...', 'info');
    updateBackupProgress(0, 'Starting backup...');

    const response = await fetch(`${API_BASE}/backup/${clusterId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ compress })
    });
    const result = await response.json();

    if (result.success) {
      showToast('Backup created successfully', 'success');
      updateBackupProgress(100, 'Backup completed!');
      loadBackups();
    } else {
      showToast(result.error || result.message || 'Backup failed', 'error');
      updateBackupProgress(0, 'Backup failed');
    }
  } catch (error) {
    showToast('Error creating backup: ' + error.message, 'error');
    updateBackupProgress(0, 'Backup failed: ' + error.message);
  }
}

async function restoreBackup() {
  const timestamp = document.getElementById('restore-timestamp').value;
  const restoreType = document.getElementById('restore-type').value;
  const drop = document.getElementById('restore-drop').checked;

  if (!timestamp) {
    showToast('Please select a backup', 'warning');
    return;
  }

  if (restoreType === 'shard') {
    const shard = document.getElementById('restore-shard').value;
    if (!shard) {
      showToast('Please select a shard', 'warning');
      return;
    }
    await restoreShard(timestamp, shard, drop);
  } else {
    await restoreCluster(timestamp, drop);
  }
}

async function restoreShard(timestamp, shard, drop) {
  if (!confirm(`Are you sure you want to restore shard "${shard}" from backup "${timestamp}"?`)) return;

  try {
    showToast('Restoring shard...', 'info');
    const response = await fetch(`${API_BASE}/backup/${clusterId}/restore`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ timestamp, shard, drop })
    });
    const result = await response.json();

    if (result.success) {
      showToast('Shard restored successfully', 'success');
    } else {
      showToast(result.error || result.message || 'Restore failed', 'error');
    }
  } catch (error) {
    showToast('Error restoring shard: ' + error.message, 'error');
  }
}

async function restoreCluster(timestamp, drop) {
  if (!confirm(`Are you sure you want to restore the ENTIRE cluster from backup "${timestamp}"?`)) return;
  if (!confirm('This will affect all data in the cluster. Continue?')) return;

  try {
    showToast('Restoring cluster...', 'info');
    updateBackupProgress(0, 'Starting restore...');

    const response = await fetch(`${API_BASE}/backup/${clusterId}/restore-cluster`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ timestamp, drop })
    });
    const result = await response.json();

    if (result.success) {
      showToast('Cluster restored successfully', 'success');
      updateBackupProgress(100, 'Restore completed!');
    } else {
      showToast(result.error || result.message || 'Restore failed', 'error');
      updateBackupProgress(0, 'Restore failed');
    }
  } catch (error) {
    showToast('Error restoring cluster: ' + error.message, 'error');
    updateBackupProgress(0, 'Restore failed: ' + error.message);
  }
}

function updateBackupProgress(percent, text) {
  const fill = document.getElementById('backup-progress-fill');
  const textEl = document.getElementById('backup-progress-text');
  if (fill) fill.style.width = `${percent}%`;
  if (textEl) textEl.textContent = text;
}

// Check backup progress periodically when backup is running
let backupProgressInterval = null;

async function checkBackupProgress() {
  try {
    const response = await fetch(`${API_BASE}/backup/${clusterId}/progress`);
    const data = await response.json();

    if (data.inProgress) {
      updateBackupProgress(data.percent || 50, data.status || 'Backup in progress...');
    } else if (data.completed) {
      updateBackupProgress(100, 'Backup completed!');
      if (backupProgressInterval) {
        clearInterval(backupProgressInterval);
        backupProgressInterval = null;
      }
    }
  } catch (error) {
    console.error('Error checking backup progress:', error);
  }
}

// Initial load
loadClusterStatus();
loadNodes();
