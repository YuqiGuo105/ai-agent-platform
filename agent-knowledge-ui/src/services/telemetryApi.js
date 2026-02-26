import axios from 'axios';

/**
 * Axios instance configured for telemetry API
 * Proxy is configured in vite.config.js to forward /telemetry-api to localhost:8082
 */
const telemetryApi = axios.create({
  baseURL: '/telemetry-api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response interceptor for error handling
telemetryApi.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('Telemetry API Error:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

// ==================== Run Logs API ====================

/**
 * Search knowledge runs with optional filters
 * @param {Object} params - Query parameters
 * @param {string} [params.sessionId] - Filter by session ID
 * @param {string} [params.status] - Filter by status (RUNNING, DONE, FAILED, CANCELLED)
 * @param {number} [params.page] - Page number (0-indexed)
 * @param {number} [params.size] - Page size (default: 20)
 * @returns {Promise<{content: Array, totalElements: number, totalPages: number}>}
 */
export const searchRuns = async (params = {}) => {
  const response = await telemetryApi.get('/runs', { params });
  return response.data;
};

/**
 * Get run detail by ID including tool calls
 * @param {string} runId - Run UUID
 * @returns {Promise<Object>} Run detail with toolCalls array
 */
export const getRunDetail = async (runId) => {
  const response = await telemetryApi.get(`/runs/${runId}`);
  return response.data;
};

/**
 * Get tool calls for a specific run
 * @param {string} runId - Run UUID
 * @returns {Promise<Array>} List of tool calls
 */
export const getRunToolCalls = async (runId) => {
  const response = await telemetryApi.get(`/runs/${runId}/tools`);
  return response.data;
};

/**
 * Delete a run and all associated data (tool calls, events)
 * @param {string} runId - Run UUID
 * @returns {Promise<{deleted: boolean, runId: string, toolCallsDeleted: number, eventsDeleted: number}>}
 */
export const deleteRun = async (runId) => {
  const response = await telemetryApi.delete(`/runs/${runId}`);
  return response.data;
};

/**
 * Batch delete multiple runs and all associated data
 * @param {string[]} runIds - Array of Run UUIDs
 * @returns {Promise<{deleted: boolean, runsDeleted: number, toolCallsDeleted: number, eventsDeleted: number}>}
 */
export const deleteRunsBatch = async (runIds) => {
  const response = await telemetryApi.delete('/runs/batch', { data: runIds });
  return response.data;
};

// ==================== DLQ API ====================

/**
 * Get DLQ messages with optional filters
 * @param {Object} params - Query parameters
 * @param {string} [params.status] - Filter by status (PENDING, REQUEUED, IGNORED)
 * @param {number} [params.page] - Page number (0-indexed)
 * @param {number} [params.size] - Page size
 * @returns {Promise<{content: Array, totalElements: number, totalPages: number}>}
 */
export const getDlqMessages = async (params = {}) => {
  const response = await telemetryApi.get('/dlq', { params });
  return response.data;
};

/**
 * Get DLQ statistics
 * @returns {Promise<{totalCount: number, pendingCount: number, requeuedCount: number, ignoredCount: number}>}
 */
export const getDlqStats = async () => {
  const response = await telemetryApi.get('/dlq/stats');
  return response.data;
};

/**
 * Get single DLQ message by ID
 * @param {number} id - DLQ message ID
 * @returns {Promise<Object>} DLQ message detail
 */
export const getDlqMessage = async (id) => {
  const response = await telemetryApi.get(`/dlq/${id}`);
  return response.data;
};

/**
 * Requeue a single DLQ message
 * @param {number} id - DLQ message ID
 * @returns {Promise<void>}
 */
export const requeueDlqMessage = async (id) => {
  const response = await telemetryApi.post(`/dlq/${id}/requeue`);
  return response.data;
};

/**
 * Batch requeue multiple DLQ messages
 * @param {number[]} ids - Array of DLQ message IDs
 * @returns {Promise<{successCount: number, failedIds: number[]}>}
 */
export const batchRequeueDlqMessages = async (ids) => {
  const response = await telemetryApi.post('/dlq/requeue', ids);
  return response.data;
};

/**
 * Ignore a DLQ message
 * @param {number} id - DLQ message ID
 * @returns {Promise<void>}
 */
export const ignoreDlqMessage = async (id) => {
  const response = await telemetryApi.post(`/dlq/${id}/ignore`);
  return response.data;
};

// ==================== Event Stream API ====================

/**
 * Create EventSource for real-time telemetry events
 * Note: This uses SSE (Server-Sent Events)
 * @param {string} endpoint - SSE endpoint path
 * @returns {EventSource}
 */
export const createEventSource = (endpoint = '/events/stream') => {
  return new EventSource(`/telemetry-api${endpoint}`);
};

export default telemetryApi;
