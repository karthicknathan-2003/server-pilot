import { Bell, Menu } from "lucide-react";

export default function Topbar({ title, onMenuOpen, serverName }) {
  return (
    <header className="flex items-center gap-3 border-b border-white/8 bg-gray-950/80 backdrop-blur px-4 py-3 shrink-0">
      {/* Mobile hamburger */}
      <button onClick={onMenuOpen} className="md:hidden text-white/50 hover:text-white">
        <Menu size={18} />
      </button>

      <h1 className="flex-1 text-sm font-semibold text-white/90">{title}</h1>

      {/* Active server indicator */}
      {serverName && (
        <div className="hidden sm:flex items-center gap-2 rounded-lg border border-white/8 bg-white/5 px-3 py-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
          <span className="text-xs text-white/50">{serverName}</span>
        </div>
      )}

      <button className="relative p-1.5 text-white/40 hover:text-white/80 rounded-lg hover:bg-white/5">
        <Bell size={16} />
      </button>
    </header>
  );
}