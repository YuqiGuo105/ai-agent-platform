import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'https://agent-knowledge-production.up.railway.app',
});

export default api;
