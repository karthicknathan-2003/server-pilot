import axios from "axios";

// Axios instance - No auth headers needed.
const api = axios.create({ baseURL: "/api" });

// Endpoints.
// Servers and metrics.
export const connectServer = (body) => api.post("/servers/connect", body);
export const getServers = () => api.get("/servers");
export const addServer = (body) => api.post("/servers", body);
export const deleteServer = (id) => api.delete(`/servers/${id}`);
export const getMetrics = (id) => api.get(`/servers/${id}/metrics`);

// Processes
export const getProcesses = (sid) => api.get(`/servers/${sid}/processes`);
export const killProcess = (sid, pid) => api.delete(`/servers/${sid}/processes/${pid}`);

// Services
export const getServices = (sid) => api.get(`/servers/${sid}/services`);
export const controlService = (sid, n, a) =>
    api.post(`/servers/${sid}/services/${encodeURIComponent(n)}/${encodeURIComponent(a)}`);