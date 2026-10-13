import { useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, Outlet, useLocation, useNavigate } from "react-router-dom";
import { RefreshCw } from "lucide-react";

import { getServers } from "./api/endpoints.js";

import Sidebar from "./components/Sidebar.jsx";
import Topbar from "./components/Topbar.jsx";
import DashboardPage from './pages/DashboardPage';
import ServersPage from './pages/ServersPage';


// Map route paths to page titles.
const ROUTE_TITLES = {
  "/": "Dashboard",
  "/servers": "Servers",
};

// Layout Shell Component.
function AppLayout({ activeServer }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  // Derive title from the current pathname.
  const title = ROUTE_TITLES[location.pathname] || "Server Pilot";

  // Navigate via React Router and close mobile drawer.
  const handleNav = (path) => {
    navigate(path);
    setMobileOpen(false);
  };

  return (
    <div className="flex h-screen overflow-hidden bg-gradient-to-br from-gray-950 via-gray-900 to-gray-950 text-white">
      <Sidebar
        collapsed={collapsed}
        onNav={handleNav}
        onToggle={() => setCollapsed(c => !c)}
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
        serverName={activeServer?.name || activeServer?.host}
      />

      <div className="flex flex-1 flex-col min-w-0 overflow-hidden">
        <Topbar
          title={title}
          onMenuOpen={() => setMobileOpen(true)}
          serverName={activeServer?.name || activeServer?.host}
        />

        <main className="flex-1 overflow-y-auto p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

// Root Application Component.
function AppContent() {
  const [activeServer, setActiveServer] = useState(null);
  const [initLoading, setInitLoading] = useState(true);

  // Check if any server exists on startup.
  useEffect(() => {
    getServers().then(({ data }) => {
      if (data && data.length > 0) {
        const firstOnline = data.find(s => s.status === "online") || data[0];
        setActiveServer(firstOnline);
      }
    })
      .catch(() => { })
      .finally(() => setInitLoading(false));
  }, []);

  if (initLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950 text-white">
        <RefreshCw size={24} className="animate-spin text-blue-500" />
      </div>
    );
  }

  // Show login page (still within BrowserRouter context).
  if (!activeServer) {
    return (
      <Routes>
        <Route path="*" element={
          <LoginPage onConnected={(server) => setActiveServer(server)} />
        } />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<AppLayout activeServer={activeServer} />}>
        <Route index element={<DashboardPage />} />
        <Route path="servers" element={<ServersPage />} />
      </Route>
    </Routes>
  );
}

// Wrapper so LoginPage import stays lazy-friendly.
import LoginPage from './pages/LoginPage';
function LoginPageWrapper({ onConnected }) {
  return <LoginPage onConnected={onConnected} />;
}

// Top-level export wraps everything in BrowserRouter.
export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}