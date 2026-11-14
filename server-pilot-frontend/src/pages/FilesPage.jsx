import { useEffect, useState } from "react";
import { ArrowLeft, ChevronRight, Search, Plus, Upload, Folder, File, Trash2, Edit3, Download, RefreshCw } from "lucide-react";
import { getServers, listFiles, mkdir, deleteFile, renameFile } from "../api/endpoints.js";
import { Card } from "../components/ui/card.jsx";
import { Button } from "../components/ui/button.jsx";
import { Input } from "../components/ui/input.jsx";
import { TableRowSkeleton } from "../components/ui/skeleton.jsx";

export default function FilesPage() {
    const [servers, setServers] = useState([]);
    const [serverId, setServerId] = useState(null);
    const [path, setPath] = useState("/");
    const [entries, setEntries] = useState([]);
    const [selected, setSelected] = useState(null);
    const [loading, setLoading] = useState(true);
    const [creating, setCreating] = useState(false);
    const [deletingItem, setDeletingItem] = useState(null);
    const [renaming, setRenaming] = useState(false);
    const [search, setSearch] = useState("");
    const [newFolderMode, setNewFolderMode] = useState(false);
    const [newFolderName, setNewFolderName] = useState("");
    const [renameMode, setRenameMode] = useState(false);
    const [renameName, setRenameName] = useState("");

    useEffect(() => {
        getServers().then(({ data }) => {
            setServers(data);
            const first = data.find(s => s.status === "online");
            if (first) setServerId(first.id);
        });
    }, []);

    useEffect(() => {
        if (!serverId) return;
        setLoading(true);
        listFiles(serverId, path)
            .then(({ data }) => setEntries(data))
            .catch(() => setEntries([]))
            .finally(() => setLoading(false));
    }, [serverId, path]);

    const navigate = (entry) => {
        if (entry.type !== "dir") { setSelected(entry.name); return; }
        const next = path === "/" ? `/${entry.name}` : `${path}/${entry.name}`;
        setPath(next); setSelected(null); setSearch("");
    };

    const goUp = () => {
        if (path === "/") return;
        const parent = path.substring(0, path.lastIndexOf("/")) || "/";
        setPath(parent); setSelected(null);
    };

    const goTo = (p) => { setPath(p); setSelected(null); setSearch(""); };

    const crumbs = path === "/"
        ? [{ label: "/", path: "/" }]
        : path.split("/").filter(Boolean).reduce((acc, seg, i, arr) => {
            const p = "/" + arr.slice(0, i + 1).join("/");
            return [...acc, { label: seg, path: p }];
        }, [{ label: "/", path: "/" }]);

    const createFolder = async () => {
        if (!newFolderName.trim() || creating) return;
        setCreating(true);
        try {
            await mkdir(serverId, `${path}/${newFolderName}`);
            setNewFolderMode(false); setNewFolderName("");
            const { data } = await listFiles(serverId, path);
            setEntries(data);
        } finally {
            setCreating(false);
        }
    };

    const doDelete = async (name) => {
        if (deletingItem) return;
        setDeletingItem(name);
        try {
            await deleteFile(serverId, `${path}/${name}`);
            setSelected(null);
            const { data } = await listFiles(serverId, path);
            setEntries(data);
        } finally {
            setDeletingItem(null);
        }
    };

    const doRename = async () => {
        if (!renameName.trim() || renaming) return;
        setRenaming(true);
        try {
            await renameFile(serverId, `${path}/${selected}`, `${path}/${renameName}`);
            setRenameMode(false); setSelected(null);
            const { data } = await listFiles(serverId, path);
            setEntries(data);
        } finally {
            setRenaming(false);
        }
    };

    const visible = search
        ? entries.filter(e => e.name.toLowerCase().includes(search.toLowerCase()))
        : entries;

    return (
        <div className="space-y-4">
            {/* Server selector */}
            {servers.length > 1 && (
                <select value={serverId || ""} onChange={e => { setServerId(Number(e.target.value)); setPath("/"); }}
                    className="rounded-lg border border-white/10 bg-gray-900 px-3 py-1.5 text-xs text-white/60 outline-none">
                    {servers.filter(s => s.status === "online").map(s => (
                        <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                </select>
            )}

            {/* Toolbar */}
            <div className="flex flex-wrap items-center gap-2">
                <Button onClick={goUp} disabled={path === "/"} variant="outline" size="sm">
                    <ArrowLeft size={13} />
                </Button>

                {/* Breadcrumb */}
                <div className="flex items-center gap-1 text-xs font-mono flex-1 min-w-0 overflow-x-auto">
                    {crumbs.map((c, i) => (
                        <span key={c.path} className="flex items-center gap-1 shrink-0">
                            {i > 0 && <ChevronRight size={11} className="text-white/20" />}
                            <button onClick={() => goTo(c.path)} className="text-blue-400 hover:text-blue-300 cursor-pointer">{c.label}</button>
                        </span>
                    ))}
                </div>

                <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/5 px-2.5 py-1.5 text-xs text-white/50">
                    <Search size={12} />
                    <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search…"
                        className="bg-transparent outline-none w-24 sm:w-32 text-white placeholder-white/20" />
                </div>
                <Button onClick={() => setNewFolderMode(m => !m)} variant="outline" size="sm">
                    <Plus size={12} /> Folder
                </Button>
                <Button variant="default" size="sm">
                    <Upload size={12} /> Upload
                </Button>
            </div>

            {/* New folder row */}
            {newFolderMode && (
                <div className="flex items-center gap-2">
                    <Input autoFocus value={newFolderName} onChange={e => setNewFolderName(e.target.value)}
                        onKeyDown={e => { if (e.key === "Enter") createFolder(); if (e.key === "Escape") setNewFolderMode(false); }}
                        placeholder="Folder name…"
                        className="flex-1" />
                    <Button onClick={createFolder} disabled={creating || !newFolderName.trim()} variant="default" size="sm">
                        {creating && <RefreshCw size={12} className="animate-spin" />}
                        {creating ? "Creating…" : "Create"}
                    </Button>
                    <Button onClick={() => setNewFolderMode(false)} variant="outline" size="sm">Cancel</Button>
                </div>
            )}

            {/* File listing */}
            <Card className="p-0 overflow-hidden">
                <div className="grid grid-cols-12 gap-2 px-4 py-2 border-b border-white/8 text-xs text-white/30">
                    <span className="col-span-6">Name</span>
                    <span className="col-span-2 hidden sm:block">Permissions</span>
                    <span className="col-span-2 hidden sm:block">Modified</span>
                    <span className="col-span-1 hidden sm:block text-right">Size</span>
                    <span className="col-span-1" />
                </div>

                {loading ? (
                    <div className="divide-y divide-white/5">
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                    </div>
                ) : visible.length === 0 ? (
                    <div className="py-12 text-center text-xs text-white/30">
                        {search ? "No files match your search." : "Empty directory."}
                    </div>
                ) : (
                    <div className="divide-y divide-white/5">
                        {visible.map(entry => {
                            const isDir = entry.type === "dir";
                            const isSel = selected === entry.name;
                            const isDeletingThis = deletingItem === entry.name;
                            return (
                                <div key={entry.name} onClick={() => navigate(entry)}
                                    className={`grid grid-cols-12 gap-2 px-4 py-2.5 items-center cursor-pointer transition-colors
                    ${isSel ? "bg-blue-600/10" : "hover:bg-white/4"}`}>
                                    <div className="col-span-6 flex items-center gap-2.5 min-w-0">
                                        {isDir
                                            ? <Folder size={15} className="text-amber-400 shrink-0" />
                                            : <File size={15} className="text-white/30 shrink-0" />}
                                        <span className={`text-sm truncate ${isDir ? "text-white/80" : "text-white/60"}`}>{entry.name}</span>
                                        {isDir && <ChevronRight size={12} className="text-white/20 shrink-0 ml-auto" />}
                                    </div>
                                    <span className="col-span-2 hidden sm:block text-xs font-mono text-white/25">{entry.permissions}</span>
                                    <span className="col-span-2 hidden sm:block text-xs text-white/30">{entry.modified}</span>
                                    <span className="col-span-1 hidden sm:block text-xs text-white/30 text-right">{entry.size}</span>
                                    <div className="col-span-1 flex justify-end gap-0.5" onClick={e => e.stopPropagation()}>
                                        <Button onClick={() => doDelete(entry.name)} disabled={isDeletingThis} variant="destructive" size="iconSm" title="Delete">
                                            {isDeletingThis ? <RefreshCw size={12} className="animate-spin text-red-400" /> : <Trash2 size={12} />}
                                        </Button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </Card>

            {/* Selected file actions */}
            {selected && !entries.find(e => e.name === selected && e.type === "dir") && (
                <Card className="flex flex-wrap items-center gap-3">
                    <File size={14} className="text-white/40" />
                    <span className="text-sm text-white/70 flex-1 min-w-0 truncate font-mono">{path}/{selected}</span>
                    {renameMode ? (
                        <div className="flex gap-2 items-center">
                            <input autoFocus value={renameName} onChange={e => setRenameName(e.target.value)}
                                onKeyDown={e => { if (e.key === "Enter") doRename(); if (e.key === "Escape") setRenameMode(false); }}
                                className="rounded-lg border border-blue-500/40 bg-white/5 px-2 py-1 text-sm text-white outline-none" />
                            <button onClick={doRename} className="rounded-lg bg-blue-600 px-2.5 py-1 text-xs text-white">Rename</button>
                            <button onClick={() => setRenameMode(false)} className="text-xs text-white/40">Cancel</button>
                        </div>
                    ) : (
                        <div className="flex gap-2">
                            <button onClick={() => { setRenameMode(true); setRenameName(selected); }}
                                className="flex items-center gap-1 rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 px-2.5 py-1.5 text-xs text-white/60">
                                <Edit3 size={12} /> Rename
                            </button>
                            <button className="flex items-center gap-1 rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 px-2.5 py-1.5 text-xs text-white/60">
                                <Download size={12} /> Download
                            </button>
                            <button onClick={() => doDelete(selected)}
                                className="flex items-center gap-1 rounded-lg border border-red-500/20 bg-red-500/10 hover:bg-red-500/20 px-2.5 py-1.5 text-xs text-red-400">
                                <Trash2 size={12} /> Delete
                            </button>
                        </div>
                    )}
                </Card>
            )}
        </div>
    );
}