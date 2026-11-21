import { useEffect, useState, useRef, useCallback } from "react";
import { ArrowLeft, ChevronRight, Search, Plus, Upload, Folder, File, Trash2, Edit3, Download, RefreshCw, X } from "lucide-react";
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
    const [loading, setLoading] = useState(false);
    const [serverLoading, setServerLoading] = useState(true);
    const [serverError, setServerError] = useState(null);
    const [listingError, setListingError] = useState(null);
    const [mutationError, setMutationError] = useState(null);
    const [creating, setCreating] = useState(false);
    const [deletingItem, setDeletingItem] = useState(null);
    const [renaming, setRenaming] = useState(false);
    const [search, setSearch] = useState("");
    const [newFolderMode, setNewFolderMode] = useState(false);
    const [newFolderName, setNewFolderName] = useState("");
    const [renameMode, setRenameMode] = useState(false);
    const [renameName, setRenameName] = useState("");

    // Keep reference to current scope to avoid applying stale responses.
    const currentScopeRef = useRef({ serverId, path });
    currentScopeRef.current = { serverId, path };

    const discoverServers = useCallback(async () => {
        setServerLoading(true);
        setServerError(null);
        try {
            const { data } = await getServers();
            const list = Array.isArray(data) ? data : [];
            setServers(list);
            const onlineServers = list.filter(s => s.status === "online");
            const first = onlineServers[0];
            if (first) {
                setServerId(first.id);
            } else {
                setServerId(null);
                setLoading(false);
                setServerError("No online server available.");
            }
        } catch {
            setServers([]);
            setServerId(null);
            setLoading(false);
            setServerError("Failed to discover servers.");
        } finally {
            setServerLoading(false);
        }
    }, []);

    useEffect(() => {
        discoverServers();
    }, [discoverServers]);

    const loadListing = useCallback((targetServerId, targetPath) => {
        if (!targetServerId) {
            setEntries([]);
            setLoading(false);
            return;
        }
        const reqScope = { serverId: targetServerId, path: targetPath };
        setLoading(true);
        setListingError(null);
        listFiles(targetServerId, targetPath)
            .then(({ data }) => {
                if (currentScopeRef.current.serverId === reqScope.serverId && currentScopeRef.current.path === reqScope.path) {
                    setEntries(data || []);
                    setListingError(null);
                }
            })
            .catch(err => {
                if (currentScopeRef.current.serverId === reqScope.serverId && currentScopeRef.current.path === reqScope.path) {
                    setEntries([]);
                    setListingError(err?.response?.data?.message || "Failed to load directory contents.");
                }
            })
            .finally(() => {
                if (currentScopeRef.current.serverId === reqScope.serverId && currentScopeRef.current.path === reqScope.path) {
                    setLoading(false);
                }
            });
    }, []);

    useEffect(() => {
        if (!serverId) {
            setEntries([]);
            setLoading(false);
            return;
        }
        loadListing(serverId, path);
    }, [serverId, path, loadListing]);

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
        if (!serverId || !newFolderName.trim() || creating) return;
        setCreating(true);
        setMutationError(null);
        // Snapshot the current scope to guard against stale updates if the user navigates away.
        const targetServerId = serverId;
        const targetPath = path;

        // Perform folder creation.
        try {
            await mkdir(targetServerId, `${targetPath === "/" ? "" : targetPath}/${newFolderName}`);
            setNewFolderMode(false);
            setNewFolderName("");
        } catch (err) {
            // Report creation error and halt flow without attempting listing refresh.
            setMutationError(err?.response?.data?.message || "Failed to create folder.");
            return;
        } finally {
            setCreating(false);
        }

        // Refresh directory listing independently so refresh failure does not report creation failure.
        try {
            const { data } = await listFiles(targetServerId, targetPath);
            if (currentScopeRef.current.serverId === targetServerId && currentScopeRef.current.path === targetPath) {
                setEntries(data || []);
                setListingError(null);
            }
        } catch (err) {
            // Preserve successful folder creation, set listingError to expose the table retry action.
            if (currentScopeRef.current.serverId === targetServerId && currentScopeRef.current.path === targetPath) {
                setListingError(err?.response?.data?.message || "Failed to refresh directory listing.");
            }
        }
    };

    const doDelete = async (name) => {
        if (!serverId || deletingItem) return;
        setDeletingItem(name);
        setMutationError(null);
        // Snapshot target server and directory path before async operation.
        const targetServerId = serverId;
        const targetPath = path;

        // Perform file/directory deletion.
        try {
            await deleteFile(targetServerId, `${targetPath === "/" ? "" : targetPath}/${name}`);
            if (selected === name) setSelected(null);
        } catch (err) {
            // Report deletion failure and exit early.
            setMutationError(err?.response?.data?.message || `Failed to delete ${name}.`);
            return;
        } finally {
            setDeletingItem(null);
        }

        // Refresh directory contents separately from deletion status.
        try {
            const { data } = await listFiles(targetServerId, targetPath);
            if (currentScopeRef.current.serverId === targetServerId && currentScopeRef.current.path === targetPath) {
                setEntries(data || []);
                setListingError(null);
            }
        } catch (err) {
            // Keep deletion success intact, expose listing retry button instead of reporting deletion failure.
            if (currentScopeRef.current.serverId === targetServerId && currentScopeRef.current.path === targetPath) {
                setListingError(err?.response?.data?.message || "Failed to refresh directory listing.");
            }
        }
    };

    const doRename = async () => {
        if (!serverId || !renameName.trim() || renaming) return;
        setRenaming(true);
        setMutationError(null);
        // Capture active server and path to check scope validity after async calls.
        const targetServerId = serverId;
        const targetPath = path;

        // Perform rename operation.
        try {
            await renameFile(
                targetServerId,
                `${targetPath === "/" ? "" : targetPath}/${selected}`,
                `${targetPath === "/" ? "" : targetPath}/${renameName}`
            );
            // Reset rename input state upon successful rename.
            setRenameMode(false);
            setSelected(null);
        } catch (err) {
            // Report rename failure and prevent refresh from firing.
            setMutationError(err?.response?.data?.message || `Failed to rename ${selected}.`);
            return;
        } finally {
            setRenaming(false);
        }

        // Refresh directory listing with try/catch.
        try {
            const { data } = await listFiles(targetServerId, targetPath);
            // Only update entries if the user is still on the same server and directory path.
            if (currentScopeRef.current.serverId === targetServerId && currentScopeRef.current.path === targetPath) {
                setEntries(data || []);
                setListingError(null);
            }
        } catch (err) {
            // Maintain mutation success state and expose listing retry instead of false rename failure.
            if (currentScopeRef.current.serverId === targetServerId && currentScopeRef.current.path === targetPath) {
                setListingError(err?.response?.data?.message || "Failed to refresh directory listing.");
            }
        }
    };

    const visible = search
        ? entries.filter(e => e.name.toLowerCase().includes(search.toLowerCase()))
        : entries;

    return (
        <div className="space-y-4">
            {/* Server selector */}
            {servers.length > 1 && (
                <select
                    value={serverId || ""}
                    onChange={e => {
                        setServerId(Number(e.target.value));
                        setPath("/");
                        setSelected(null);
                        setRenameMode(false);
                        setRenameName("");
                    }}
                    className="rounded-lg border border-white/10 bg-gray-900 px-3 py-1.5 text-xs text-white/60 outline-none"
                >
                    {servers.filter(s => s.status === "online").map(s => (
                        <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                </select>
            )}

            {/* Mutation error banner */}
            {mutationError && (
                <div className="flex items-center justify-between rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-400">
                    <span>{mutationError}</span>
                    <button onClick={() => setMutationError(null)} className="hover:text-red-300">
                        <X size={12} />
                    </button>
                </div>
            )}

            {/* Toolbar */}
            <div className="flex flex-wrap items-center gap-2">
                <Button onClick={goUp} disabled={!serverId || path === "/"} variant="outline" size="sm">
                    <ArrowLeft size={13} />
                </Button>

                {/* Breadcrumb */}
                <div className="flex items-center gap-1 text-xs font-mono flex-1 min-w-0 overflow-x-auto">
                    {crumbs.map((c, i) => (
                        <span key={c.path} className="flex items-center gap-1 shrink-0">
                            {i > 0 && <ChevronRight size={11} className="text-white/20" />}
                            <button
                                onClick={() => goTo(c.path)}
                                disabled={!serverId}
                                className="text-blue-400 hover:text-blue-300 disabled:pointer-events-none disabled:text-white/40 cursor-pointer"
                            >
                                {c.label}
                            </button>
                        </span>
                    ))}
                </div>

                <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/5 px-2.5 py-1.5 text-xs text-white/50">
                    <Search size={12} />
                    <input
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        disabled={!serverId}
                        placeholder="Search…"
                        className="bg-transparent outline-none w-24 sm:w-32 text-white placeholder-white/20 disabled:cursor-not-allowed"
                    />
                </div>
                <Button onClick={() => setNewFolderMode(m => !m)} disabled={!serverId} variant="outline" size="sm">
                    <Plus size={12} /> Folder
                </Button>
                <Button variant="default" size="sm" disabled title="File transfer is unavailable">
                    <Upload size={12} /> Upload
                </Button>
            </div>

            {/* New folder row */}
            {newFolderMode && serverId && (
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

                {serverLoading || loading ? (
                    <div className="divide-y divide-white/5">
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                        <TableRowSkeleton />
                    </div>
                ) : serverError || !serverId ? (
                    <div className="py-12 text-center text-xs space-y-3">
                        <p className="text-red-400">{serverError || "No server selected."}</p>
                        <Button onClick={discoverServers} variant="outline" size="sm" className="gap-1.5 mx-auto">
                            <RefreshCw size={12} /> Retry Discovery
                        </Button>
                    </div>
                ) : listingError ? (
                    <div className="py-12 text-center text-xs space-y-3">
                        <p className="text-red-400">{listingError}</p>
                        <Button onClick={() => loadListing(serverId, path)} variant="outline" size="sm" className="gap-1.5 mx-auto">
                            <RefreshCw size={12} /> Retry
                        </Button>
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
                    <span className="text-sm text-white/70 flex-1 min-w-0 truncate font-mono">{path === "/" ? "" : path}/{selected}</span>
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
                            <button disabled title="File transfer is unavailable"
                                className="flex items-center gap-1 rounded-lg border border-white/10 bg-white/5 px-2.5 py-1.5 text-xs text-white/40 cursor-not-allowed opacity-50">
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