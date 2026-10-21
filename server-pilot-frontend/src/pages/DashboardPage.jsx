import { useEffect, useState } from "react";
import { Clock, Globe, Zap, Shield, Database, Wifi } from "lucide-react";
import { getServers, getMetrics } from "../api/endpoints.js";
import { Card } from "../components/ui/card.jsx";
import { Badge } from "../components/ui/badge.jsx";
import RingGauge from "../components/RingGauge.jsx";
import { CardSkeleton, GaugeCardSkeleton } from "../components/ui/skeleton.jsx";

export default function DashboardPage() {
    const [servers, setServers] = useState([]);
    const [metrics, setMetrics] = useState(null);
    const [activeId, setActiveId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    // Load server list, then auto-select the first online one.
    useEffect(() => {
        getServers()
            .then(({ data }) => {
                setServers(data);
                const first = data.find(s => s.status === "online");
                if (first) setActiveId(first.id);
            })
            .catch(() => setError("Could not load servers."))
            .finally(() => setLoading(false));
    }, []);

    // Fetch metrics whenever active server changes, refresh every 30 seconds.
    useEffect(() => {
        if (!activeId) return;
        let cancelled = false;
        setMetrics(null);
        const load = () =>
            getMetrics(activeId)
                .then(({ data }) => { if (!cancelled) setMetrics(data); })
                .catch(() => { if (!cancelled) setMetrics(null); });
        load();
        const interval = setInterval(load, 30_000);
        return () => { cancelled = true; clearInterval(interval); };
    }, [activeId]);

    if (loading) {
        return (
            <div className="space-y-5">
                <CardSkeleton />
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                    <GaugeCardSkeleton />
                    <GaugeCardSkeleton />
                    <GaugeCardSkeleton />
                    <GaugeCardSkeleton />
                </div>
            </div>
        );
    }
    if (error) return <p className="text-red-400 text-sm">{error}</p>;

    const serverMetrics = metrics;
    const pctOf = (used, total) => (!total || total <= 0 ? 0 : Math.min(Math.round((used / total) * 100), 100));
    const ramPct = serverMetrics ? pctOf(serverMetrics.ramUsedGb, serverMetrics.ramTotalGb) : 0;
    const diskPct = serverMetrics ? pctOf(serverMetrics.diskUsedGb, serverMetrics.diskTotalGb) : 0;

    return (
        <div className="space-y-5">
            {/* Server selector (if multiple) */}
            {servers.length > 1 && (
                <div className="flex gap-2 flex-wrap">
                    {servers.map(s => (
                        <button key={s.id} onClick={() => setActiveId(s.id)}
                            className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs border transition-colors ${activeId === s.id
                                ? "border-blue-500/50 bg-blue-600/20 text-blue-400"
                                : "border-white/10 bg-white/5 text-white/50 hover:bg-white/10"}`}>
                            <Badge status={s.status} />
                            {s.name}
                        </button>
                    ))}
                </div>
            )}

            {/* Server banner */}
            {servers.find(s => s.id === activeId) && (() => {
                const s = servers.find(sv => sv.id === activeId);
                return (
                    <Card>
                        <div className="flex flex-wrap items-center justify-between gap-3">
                            <div className="flex items-center gap-3">
                                <div className="relative flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/10 shrink-0">
                                    <Globe size={18} className="text-blue-400" />
                                    <span className="absolute -top-0.5 -right-0.5 h-2.5 w-2.5 rounded-full bg-emerald-500 border-2 border-gray-950" />
                                </div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <span className="font-semibold text-white">{s.name}</span>
                                        <Badge status={s.status} />
                                    </div>
                                    <p className="text-xs text-white/40 mt-0.5">{s.host}:{s.port} · {s.username}</p>
                                </div>
                            </div>
                            {serverMetrics && (
                                <div className="flex items-center gap-4 text-xs text-white/40">
                                    <span className="flex items-center gap-1"><Clock size={12} />{serverMetrics.uptime}</span>
                                    <span className="flex items-center gap-1"><Wifi size={12} />{serverMetrics.networkDownload} ↓</span>
                                </div>
                            )}
                        </div>
                    </Card>
                );
            })()}

            {/* Metrics - shimmer while loading */}
            {!serverMetrics ? (
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                    <GaugeCardSkeleton />
                    <GaugeCardSkeleton />
                    <GaugeCardSkeleton />
                    <GaugeCardSkeleton />
                </div>
            ) : (
                <>
                    {/* Ring gauges */}
                    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                        <Card className="flex flex-col items-center py-4 gap-1">
                            <RingGauge value={serverMetrics.cpuPercent} label="CPU" color="#3b82f6" />
                        </Card>
                        <Card className="flex flex-col items-center py-4 gap-1">
                            <RingGauge value={ramPct} label="RAM" color="#8b5cf6" />
                            <p className="text-xs text-white/40">{serverMetrics.ramUsedGb} / {serverMetrics.ramTotalGb} GB</p>
                        </Card>
                        <Card className="flex flex-col items-center py-4 gap-1">
                            <RingGauge value={diskPct} label="Disk" color="#f59e0b" />
                            <p className="text-xs text-white/40">{serverMetrics.diskUsedGb} / {serverMetrics.diskTotalGb} GB</p>
                        </Card>
                        {/* System info card */}
                        <Card className="flex flex-col justify-center gap-2">
                            {[
                                { icon: <Globe size={12} className="text-blue-400" />, text: serverMetrics.os },
                                { icon: <Zap size={12} className="text-amber-400" />, text: serverMetrics.kernel },
                                { icon: <Shield size={12} className="text-emerald-400" />, text: serverMetrics.arch },
                                { icon: <Database size={12} className="text-purple-400" />, text: serverMetrics.uptime },
                            ].map((row, i) => (
                                <div key={i} className="flex items-center gap-2 text-xs text-white/50">
                                    {row.icon}
                                    <span className="truncate">{row.text}</span>
                                </div>
                            ))}
                        </Card>
                    </div>
                </>
            )}
        </div>
    );
}