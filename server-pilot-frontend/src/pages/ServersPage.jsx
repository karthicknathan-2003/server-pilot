import { useEffect, useState } from "react";
import { Plus, Server, Wifi, Eye, EyeOff, Check, X, RefreshCw, Trash2, CrossIcon, Minus } from "lucide-react";
import { getServers, addServer, deleteServer, getMetrics } from "../api/endpoints.js";
import { Card } from "../components/ui/card.jsx";
import { Badge } from "../components/ui/badge.jsx";
import { Button } from "../components/ui/button.jsx";
import { Input } from "../components/ui/input.jsx";
import { Label } from "../components/ui/label.jsx";
import { Textarea } from "../components/ui/textarea.jsx";
import Bar from "../components/Bar.jsx";
import { CardSkeleton } from "../components/ui/skeleton.jsx";

export default function ServersPage() {
    const [servers, setServers] = useState([]);
    const [showForm, setShowForm] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testOk, setTestOk] = useState(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [deletingId, setDeletingId] = useState(null);

    const [form, setForm] = useState({
        name: "", host: "", port: "22", username: "",
        authType: "PASSWORD", password: "", privateKey: "",
    });
    const [showPw, setShowPw] = useState(false);

    const set = (k) => (e) => setForm(p => ({ ...p, [k]: e.target.value }));

    const load = () =>
        getServers()
            .then(({ data }) => setServers(data))
            .finally(() => setLoading(false));

    useEffect(() => { load(); }, []);

    const testConn = async () => {
        setTesting(true);
        setTestOk(null);
        try {
            setTestOk(true);
        } catch { setTestOk(false); }
        finally { setTesting(false); }
    };

    const submit = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        try {
            await addServer({ ...form, port: Number(form.port) });
            setShowForm(false);
            setForm({ name: "", host: "", port: "22", username: "", authType: "PASSWORD", password: "", privateKey: "" });
            load();
        } finally {
            setSubmitting(false);
        }
    };

    const remove = async (id) => {
        setDeletingId(id);
        try {
            await deleteServer(id);
            load();
        } finally {
            setDeletingId(null);
        }
    };

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <p className="text-sm text-white/40">Connect and manage your Linux servers.</p>
                <Button onClick={() => setShowForm(s => !s)} variant="default">
                    {showForm ? "Cancel" : "Add Server"}
                    {showForm ? <Minus size={13} /> : <Plus size={13} />}
                </Button>
            </div>

            {/* Add server form */}
            {showForm && (
                <Card>
                    <form onSubmit={submit} className="space-y-4">
                        <p className="text-sm font-semibold text-white/80">New server</p>
                        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                            <div className="space-y-1">
                                <Label>Server name</Label>
                                <Input placeholder="Production VM" value={form.name} onChange={set("name")} />
                            </div>
                            <div className="space-y-1">
                                <Label>Host / IP</Label>
                                <Input placeholder="192.168.1.100" value={form.host} onChange={set("host")} />
                            </div>
                            <div className="space-y-1">
                                <Label>SSH port</Label>
                                <Input placeholder="22" value={form.port} onChange={set("port")} />
                            </div>
                            <div className="space-y-1">
                                <Label>Username</Label>
                                <Input placeholder="ubuntu" value={form.username} onChange={set("username")} />
                            </div>
                        </div>

                        {/* Auth type toggle */}
                        <div>
                            <Label className="mb-2">Authentication</Label>
                            <div className="flex gap-2">
                                {["PASSWORD", "KEY"].map(t => (
                                    <Button key={t} type="button" onClick={() => setForm(p => ({ ...p, authType: t }))}
                                        variant={form.authType === t ? "default" : "outline"} size="sm"
                                    >
                                        {t === "PASSWORD" ? "Password" : "Private Key"}
                                    </Button>
                                ))}
                            </div>
                        </div>

                        {form.authType === "PASSWORD" ? (
                            <div className="space-y-1">
                                <Label>Password</Label>
                                <div className="relative">
                                    <Input type={showPw ? "text" : "password"} value={form.password}
                                        onChange={set("password")} placeholder="••••••••" className="pr-9"
                                    />
                                    <button type="button" onClick={() => setShowPw(p => !p)} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-white/30 hover:text-white/60">
                                        {showPw ? <EyeOff size={14} /> : <Eye size={14} />}
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div className="space-y-1">
                                <Label>Private Key (PEM)</Label>
                                <Textarea value={form.privateKey} onChange={set("privateKey")} rows={5}
                                    placeholder="-----BEGIN RSA PRIVATE KEY-----&#10;...&#10;-----END RSA PRIVATE KEY-----"
                                />
                            </div>
                        )}

                        <div className="flex flex-wrap items-center gap-2 pt-1">
                            <Button type="button" onClick={testConn} disabled={testing || !form.host} variant="outline">
                                {testing ? <RefreshCw size={12} className="animate-spin" /> : <Wifi size={12} />}
                                {testing ? "Testing…" : "Test connection"}
                            </Button>
                            {testOk === true && <span className="flex items-center gap-1 text-xs text-emerald-400"><Check size={12} /> Connected</span>}
                            {testOk === false && <span className="flex items-center gap-1 text-xs text-red-400"><X size={12} /> Failed</span>}
                            <Button type="submit" disabled={submitting || !form.name || !form.host} variant="default" className="ml-auto">
                                {submitting && <RefreshCw size={12} className="animate-spin" />}
                                {submitting ? "Saving…" : "Save server"}
                            </Button>
                        </div>
                    </form>
                </Card>
            )}

            {/* Server list */}
            {loading ? (
                <div className="space-y-3">
                    <CardSkeleton />
                    <CardSkeleton />
                </div>
            ) : servers.length === 0 ? (
                <div className="rounded-xl border border-dashed border-white/10 p-12 text-center">
                    <Server size={24} className="mx-auto text-white/15 mb-2" />
                    <p className="text-sm text-white/30">No servers added yet.</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {servers.map(s => (
                        <ServerCard key={s.id} server={s} isDeleting={deletingId === s.id} onDelete={() => remove(s.id)} />
                    ))}
                </div>
            )}
        </div>
    );
}

function ServerCard({ server: s, onDelete, isDeleting }) {
    const [metrics, setMetrics] = useState(null);
    const [expanded, setExpanded] = useState(false);

    useEffect(() => {
        if (!expanded || s.status !== "online") return;
        getMetrics(s.id).then(({ data }) => setMetrics(data));
    }, [expanded, s.id, s.status]);

    return (
        <Card>
            <div className="flex flex-wrap items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/10 shrink-0">
                    <Server size={18} className="text-blue-400" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                        <span className="font-semibold text-white">{s.name}</span>
                        <Badge status={s.status} />
                    </div>
                    <p className="text-xs text-white/40">{s.host}:{s.port} · {s.username} · {s.authType}</p>
                </div>
                <div className="flex gap-2">
                    <Button onClick={() => setExpanded(p => !p)} variant="outline" size="sm">
                        {expanded ? "Collapse" : "Details"}
                    </Button>
                    <Button onClick={onDelete} disabled={isDeleting} variant="destructive" size="iconSm">
                        {isDeleting ? <RefreshCw size={12} className="animate-spin" /> : <Trash2 size={12} />}
                    </Button>
                </div>
            </div>

            {expanded && metrics && (
                <div className="mt-3 grid grid-cols-3 gap-3 pt-3 border-t border-white/8">
                    {[
                        { label: "CPU", value: `${metrics.cpuPercent.toFixed(1)}%`, pct: metrics.cpuPercent },
                        { label: "RAM", value: `${metrics.ramUsedGb}/${metrics.ramTotalGb}GB`, pct: (metrics.ramUsedGb / metrics.ramTotalGb) * 100 },
                        { label: "Disk", value: `${metrics.diskUsedGb}/${metrics.diskTotalGb}GB`, pct: (metrics.diskUsedGb / metrics.diskTotalGb) * 100 },
                    ].map(m => (
                        <div key={m.label}>
                            <div className="flex justify-between text-xs text-white/40 mb-1">
                                <span>{m.label}</span><span>{m.value}</span>
                            </div>
                            <Bar value={m.pct} />
                        </div>
                    ))}
                </div>
            )}
        </Card>
    );
}