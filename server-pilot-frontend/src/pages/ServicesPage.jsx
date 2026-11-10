import { useEffect, useState } from "react";
import { Search, RefreshCw, X, Server } from "lucide-react";
import { getServers, getServices, controlService, getProcesses, killProcess} from "../api/endpoints.js";
import { Card } from "../components/ui/card.jsx";
import { Badge } from "../components/ui/badge.jsx";
import { Button } from "../components/ui/button.jsx";
import { TableRowSkeleton } from "../components/ui/skeleton.jsx";

export default function ServicesPage() {
  const [tab, setTab] = useState("services");
  const [serverId, setServerId] = useState(null);
  const [services, setServices] = useState([]);
  const [processes, setProcesses] = useState([]);
  const [procSearch, setProcSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [actionName, setActionName] = useState(null);
  const [killingPid, setKillingPid] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getServers()
      .then(({ data }) => {
        if (cancelled) return;
        if (data && data.length > 0) {
          const target = data.find(s => s.status === "online") || data[0];
          setServerId(target.id);
        }
      })
      .catch(() => {
        if (!cancelled) setError("Failed to load servers.");
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!serverId) return;
    let cancelled = false;
    setLoading(true);
    setError("");
    if (tab === "services") {
      getServices(serverId)
        .then(({ data }) => { if (!cancelled) setServices(data); })
        .catch(() => { if (!cancelled) setError("Failed to load services."); })
        .finally(() => { if (!cancelled) setLoading(false); });
    } else {
      getProcesses(serverId)
        .then(({ data }) => { if (!cancelled) setProcesses(data); })
        .catch(() => { if (!cancelled) setError("Failed to load processes."); })
        .finally(() => { if (!cancelled) setLoading(false); });
    }
    return () => { cancelled = true; };
  }, [serverId, tab]);

  const control = async (name, action) => {
    setActionName(name);
    setError("");
    try {
      await controlService(serverId, name, action);
      const { data } = await getServices(serverId);
      setServices(data);
    } catch (err) {
      setError(err?.response?.data?.message || `Failed to ${action} service ${name}.`);
    } finally {
      setActionName(null);
    }
  };

  const kill = async (pid) => {
    if (!window.confirm(`Are you sure you want to stop process ${pid}?`)) {
      return;
    }
    setKillingPid(pid);
    setError("");
    try {
      await killProcess(serverId, pid);
      setProcesses(prev => prev.filter(p => p.pid !== pid));
    } catch (err) {
      setError(err?.response?.data?.message || `Failed to kill process ${pid}.`);
    } finally {
      setKillingPid(null);
    }
  };

  const filteredProcs = processes.filter(p =>
    p.name.toLowerCase().includes(procSearch.toLowerCase()) ||
    p.command.toLowerCase().includes(procSearch.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <div className="flex gap-1 rounded-xl bg-white/5 p-1 w-fit">
        {[["services", "Services"], ["processes", "Processes"]].map(([id, label]) => (
          <button key={id} onClick={() => setTab(id)}
            className={`rounded-lg px-4 py-1.5 text-xs font-medium transition-colors ${
              tab === id ? "bg-blue-600 text-white" : "text-white/50 hover:text-white/80"}`}>
            {label}
          </button>
        ))}
      </div>

      {error && (
        <div className="flex items-center justify-between rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-400">
          <span>{error}</span>
          <button onClick={() => setError("")} className="hover:text-red-300">
            <X size={12} />
          </button>
        </div>
      )}

      {!serverId && !loading && (
        <div className="rounded-xl border border-dashed border-white/10 p-12 text-center">
          <Server size={24} className="mx-auto text-white/15 mb-2" />
          <p className="text-sm text-white/30">{error || "No servers available."}</p>
        </div>
      )}

      {loading && (
        <Card className="p-0 overflow-hidden">
          <div className="divide-y divide-white/5">
            <TableRowSkeleton />
            <TableRowSkeleton />
            <TableRowSkeleton />
            <TableRowSkeleton />
            <TableRowSkeleton />
          </div>
        </Card>
      )}

      {/* Services */}
      {tab === "services" && !loading && serverId && (
        <Card className="p-0 overflow-hidden">
          <div className="divide-y divide-white/5">
            {services.map(svc => {
              const isBusy = actionName === svc.name;
              return (
                <div key={svc.name} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-white/3 transition-colors">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-mono font-medium text-white/80">{svc.name}</p>
                    <p className="text-xs text-white/30 truncate">{svc.description}</p>
                  </div>
                  <Badge status={svc.status} />
                  <div className="flex gap-1">
                    {svc.status === "active" ? (
                      <Button onClick={() => control(svc.name, "stop")} disabled={isBusy} variant="destructive" size="sm">
                        {isBusy && <RefreshCw size={11} className="animate-spin" />} Stop
                      </Button>
                    ) : (
                      <Button onClick={() => control(svc.name, "start")} disabled={isBusy} variant="success" size="sm">
                        {isBusy && <RefreshCw size={11} className="animate-spin" />} Start
                      </Button>
                    )}
                    <Button onClick={() => control(svc.name, "restart")} disabled={isBusy} variant="outline" size="sm">
                      <RefreshCw size={11} className={isBusy ? "animate-spin" : ""} /> Restart
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      )}

      {/* Processes */}
      {tab === "processes" && !loading && serverId && (
        <div className="space-y-3">
          <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/5 px-2.5 py-1.5 text-xs w-fit">
            <Search size={12} className="text-white/40" />
            <input value={procSearch} onChange={e => setProcSearch(e.target.value)} placeholder="Filter…"
              className="bg-transparent outline-none w-48 text-white placeholder-white/20 text-xs" />
          </div>
          <Card className="p-0 overflow-hidden">
            <div className="grid grid-cols-12 gap-2 px-4 py-2 border-b border-white/8 text-xs text-white/30">
              <span className="col-span-1">PID</span>
              <span className="col-span-2">User</span>
              <span className="col-span-3">Name</span>
              <span className="col-span-4 hidden sm:block">Command</span>
              <span className="col-span-1 text-right">CPU</span>
              <span className="col-span-1" />
            </div>
            <div className="divide-y divide-white/5 overflow-y-auto" style={{ maxHeight: "400px" }}>
              {filteredProcs.map(p => {
                const isKillingThis = killingPid === p.pid;
                return (
                  <div key={p.pid} className="grid grid-cols-12 gap-2 px-4 py-2.5 items-center hover:bg-white/3 transition-colors">
                    <span className="col-span-1 text-xs font-mono text-white/30">{p.pid}</span>
                    <span className="col-span-2 text-xs text-white/40 truncate">{p.user}</span>
                    <span className="col-span-3 text-xs text-white/70 truncate">{p.name}</span>
                    <span className="col-span-4 hidden sm:block text-xs font-mono text-white/25 truncate">{p.command}</span>
                    <span className={`col-span-1 text-xs text-right ${p.cpu > 10 ? "text-amber-400" : "text-white/40"}`}>{p.cpu}%</span>
                    <div className="col-span-1 flex justify-end">
                      <Button onClick={() => kill(p.pid)} disabled={isKillingThis} variant="destructive" size="iconSm" title="Kill process">
                        {isKillingThis ? <RefreshCw size={12} className="animate-spin text-red-400" /> : <X size={12} />}
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}