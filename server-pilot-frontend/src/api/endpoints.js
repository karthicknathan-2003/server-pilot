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