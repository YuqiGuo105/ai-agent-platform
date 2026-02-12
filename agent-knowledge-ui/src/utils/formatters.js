/**
 * Utility functions for formatting dates, durations, and other values
 */

/**
 * Format a date/time string to a readable format
 * @param {string|Date} dateStr - ISO date string or Date object
 * @returns {string} Formatted date string
 */
export const formatDateTime = (dateStr) => {
  if (!dateStr) return '-';
  try {
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return dateStr;
  }
};

/**
 * Format a date to relative time (e.g., "2 hours ago")
 * @param {string|Date} dateStr - ISO date string or Date object
 * @returns {string} Relative time string
 */
export const formatRelativeTime = (dateStr) => {
  if (!dateStr) return '-';
  try {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now - date;
    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffSecs / 60);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSecs < 60) return `${diffSecs}秒前`;
    if (diffMins < 60) return `${diffMins}分钟前`;
    if (diffHours < 24) return `${diffHours}小时前`;
    if (diffDays < 7) return `${diffDays}天前`;
    return formatDateTime(dateStr);
  } catch {
    return dateStr;
  }
};

/**
 * Calculate and format duration between two dates
 * @param {string|Date} startTime - Start time
 * @param {string|Date} endTime - End time
 * @returns {string} Formatted duration
 */
export const formatDuration = (startTime, endTime) => {
  if (!startTime || !endTime) return '-';
  try {
    const start = new Date(startTime);
    const end = new Date(endTime);
    const durationMs = end - start;
    
    if (durationMs < 0) return '-';
    if (durationMs < 1000) return `${durationMs}ms`;
    
    const seconds = Math.floor(durationMs / 1000);
    if (seconds < 60) return `${seconds}秒`;
    
    const minutes = Math.floor(seconds / 60);
    const remainingSecs = seconds % 60;
    if (minutes < 60) return `${minutes}分${remainingSecs}秒`;
    
    const hours = Math.floor(minutes / 60);
    const remainingMins = minutes % 60;
    return `${hours}时${remainingMins}分`;
  } catch {
    return '-';
  }
};

/**
 * Truncate a string to a maximum length
 * @param {string} str - Input string
 * @param {number} maxLength - Maximum length
 * @returns {string} Truncated string with ellipsis if needed
 */
export const truncate = (str, maxLength = 100) => {
  if (!str) return '';
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength) + '...';
};

/**
 * Format JSON for display
 * @param {Object|string} data - JSON data or string
 * @returns {string} Pretty-printed JSON string
 */
export const formatJson = (data) => {
  if (!data) return '';
  try {
    const obj = typeof data === 'string' ? JSON.parse(data) : data;
    return JSON.stringify(obj, null, 2);
  } catch {
    return String(data);
  }
};

/**
 * Format bytes to human-readable size
 * @param {number} bytes - Size in bytes
 * @returns {string} Formatted size string
 */
export const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

/**
 * Get freshness badge class based on cache freshness value
 * @param {string} freshness - FRESH, STALE, or MISS
 * @returns {string} CSS class name
 */
export const getFreshnessClass = (freshness) => {
  switch (freshness?.toUpperCase()) {
    case 'FRESH': return 'fresh';
    case 'STALE': return 'stale';
    case 'MISS': return 'miss';
    default: return '';
  }
};
