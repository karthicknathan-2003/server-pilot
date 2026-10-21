import { useState } from "react";
import { Server, Eye, EyeOff, Wifi, RefreshCw, Check, X, Plus } from "lucide-react";

import { connectServer, addServer, getServers } from "../api/endpoints.js";

import { Button } from "../components/ui/button.jsx";
import { Card } from "../components/ui/card.jsx";
import { Input } from "../components/ui/input.jsx";
import { Label } from "../components/ui/label.jsx";
import { Textarea } from "../components/ui/textarea.jsx";

export default function LoginPage({ onConnected }) {
  const [form, setForm] = useState({
    name: "", host: "", port: "22", username: "",
    authType: "KEY",   // default to key.
    password: "", privateKey: "",
  });
  const [showPw, setShowPw] = useState(false);
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState(null);  
  const [saving, setSaving] = useState(false);

  const set = (k) => (e) => {
    setForm(p => ({ ...p, [k]: e.target.value }));
    setResult(null); // clear result.
  };

  // Test SSH connection before saving.
  const test = async () => {
    setTesting(true);
    setResult(null);
    try {
      const { data } = await connectServer({
        ...form, port: Number(form.port),
      });
      setResult({ ok: data.connected, message: data.message });
    } catch {
      setResult({ ok: false, message: "Could not reach the server." });
    } finally {
      setTesting(false);
    }
  };

  // Save and enter the app.
  const connect = async () => {
    setSaving(true);
    try {
      // Check if this server is already saved (by host).
      const { data: existing } = await getServers();
      const already = existing.find(s => s.host === form.host && s.port === Number(form.port));
      const server = already ?? (await addServer({ ...form, port: Number(form.port) })).data;
      onConnected?.(server);
    } catch {
      setResult({ ok: false, message: "Failed to save server." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex h-screen items-center justify-center bg-gradient-to-br from-gray-950 via-gray-900 to-gray-950 p-4">
      <Card className="w-full max-w-md p-8 space-y-6">
        {/* Logo */}
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 shrink-0">
            <Server size={20} className="text-white" />
          </div>
          <div>
            <h1 className="text-base font-bold text-white tracking-tight">ServerPilot</h1>
            <p className="text-xs text-white/40">Connect to your Linux server</p>
          </div>
        </div>

        <div className="space-y-3">
          {/* Basic fields */}
          <div className="grid grid-cols-3 gap-2">
            <div className="col-span-2 space-y-1">
              <Label>Host / IP</Label>
              <Input placeholder="192.168.1.100" value={form.host} onChange={set("host")} />
            </div>
            <div className="space-y-1">
              <Label>Port</Label>
              <Input placeholder="22" value={form.port} onChange={set("port")} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <Label>Name (optional)</Label>
              <Input placeholder="My Server" value={form.name} onChange={set("name")} />
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
              {[
                { id: "KEY", label: "Private Key" },
                { id: "PASSWORD", label: "Password" },
              ].map(({ id, label }) => (
                <Button key={id} type="button"
                  variant={form.authType === id ? "default" : "outline"}
                  onClick={() => setForm(p => ({ ...p, authType: id }))}
                  className="flex-1"
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>

          {/* Credential field */}
          {form.authType === "KEY" ? (
            <div className="space-y-1">
              <Label>Private Key (PEM)</Label>
              <Textarea
                placeholder={"-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----"}
                value={form.privateKey}
                onChange={set("privateKey")}
                rows={5}
              />
            </div>
          ) : (
            <div className="space-y-1">
              <Label>Password</Label>
              <div className="relative">
                <Input
                  type={showPw ? "text" : "password"}
                  placeholder="*********"
                  value={form.password}
                  onChange={set("password")}
                  className="pr-9"
                />
                <button type="button" onClick={() => setShowPw(p => !p)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-white/30 hover:text-white/60">
                  {showPw ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Test result banner */}
        {result && (
          <div className={`flex items-start gap-2 rounded-lg border px-3 py-2.5 text-xs ${result.ok
              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
              : "border-red-500/30    bg-red-500/10    text-red-400"
            }`}>
            {result.ok ? <Check size={13} className="shrink-0 mt-0.5" /> : <X size={13} className="shrink-0 mt-0.5" />}
            <span>{result.message}</span>
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2">
          {/* Test first */}
          <Button onClick={test} variant="outline" size="lg"
            disabled={testing || !form.host || !form.username}
            className="flex-1">
            {testing
              ? <RefreshCw size={12} className="animate-spin" />
              : <Wifi size={12} />}
            {testing ? "Testing…" : "Test connection"}
          </Button>

          {/* Connect only enabled after successful test. */}
          <Button onClick={connect} variant="default" size="lg"
            disabled={!result?.ok || saving}
            className="flex-1">
            {saving ? <RefreshCw size={12} className="animate-spin" /> : <Plus size={12} />}
            {saving ? "Connecting…" : "Connect"}
          </Button>
        </div>

        <p className="text-xs text-white/20 text-center">
          Your credentials are used only to open an SSH session.
        </p>
      </Card>
    </div>
  );
}