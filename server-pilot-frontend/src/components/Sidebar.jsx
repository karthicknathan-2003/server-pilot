import { useLocation } from "react-router-dom";
import { ChevronLeft, ChevronRight, Server, X } from "lucide-react";

// Dynamically import Lucide icons by name.
import * as LucideIcons from "lucide-react";

function Icon({ name, size = 16 }) {
  const Comp = LucideIcons[name];
  return Comp ? <Comp size={size} /> : null;
}

// Sidebar navigation items.
const NAV = [
  { path: "/", label: "Dashboard", icon: "LayoutDashboard" },
  { path: "/servers", label: "Servers", icon: "Server" },
];

export default function Sidebar({ onNav, collapsed, onToggle, mobileOpen, onMobileClose, serverName }) {
  const location = useLocation();
  
  const handleClick = (path) => {
    onNav(path);
  };

  return (
    <>
      {/* Mobile backdrop */}
      {mobileOpen && (
        <div className="fixed inset-0 z-20 bg-black/60 backdrop-blur-sm md:hidden"
          onClick={onMobileClose} />
      )}

      <aside className={`
        fixed md:static inset-y-0 left-0 z-30
        flex flex-col bg-gray-950/95 backdrop-blur
        border-r border-white/8 transition-all duration-200
        ${collapsed ? "w-16" : "w-56"}
        ${mobileOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"}
      `}>
        {/* Logo */}
        <div className="flex items-center gap-2.5 px-4 py-4 border-b border-white/8 shrink-0">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 shrink-0">
            <Server size={15} className="text-white" />
          </div>
          {!collapsed && <span className="text-sm font-bold text-white tracking-tight">ServerPilot</span>}
          <button onClick={onToggle}
            className="ml-auto hidden md:flex h-6 w-6 items-center justify-center rounded text-white/30 hover:text-white/70">
            {collapsed ? <ChevronRight size={13} /> : <ChevronLeft size={13} />}
          </button>
          <button onClick={onMobileClose} className="ml-auto md:hidden text-white/40">
            <X size={16} />
          </button>
        </div>

        {/* Nav links */}
        <nav className="flex-1 py-3 overflow-y-auto">
          {NAV.map(({ path, label, icon }) => {
            const isActive = location.pathname === path;
            return (
              <button key={path} onClick={() => handleClick(path)} title={collapsed ? label : undefined}
                className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors
                  ${isActive
                    ? "bg-blue-600/20 text-blue-400 border-r-2 border-blue-500"
                    : "text-white/40 hover:text-white/80 hover:bg-white/5"}`}>
                <Icon name={icon} />
                {!collapsed && <span className="truncate">{label}</span>}
              </button>
            );
          })}
        </nav>

        {/* Active server pill */}
        {!collapsed && (
          <div className="px-4 py-3 border-t border-white/8 shrink-0">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
              <span className="text-xs text-white/40 truncate">{serverName || "No server"}</span>
            </div>
          </div>
        )}
      </aside>
    </>
  );
}