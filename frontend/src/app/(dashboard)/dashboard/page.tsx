'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Upload, Database, Users, HardDrive, Calendar, FileText, FileSpreadsheet,
  MoreHorizontal, Pin, Archive, Trash2, Loader2, X, RefreshCw,
  ChevronDown, LayoutGrid, List, Filter, Clock, CheckCircle2,
  XCircle, AlertTriangle, Inbox, Zap,
} from 'lucide-react';
import { toast } from 'sonner';
import Link from 'next/link';
import { useDumps, useUploadDump, useUpdateDump, useDeleteDump } from '@/hooks/use-dumps';
import { useDashboardStats } from '@/hooks/use-dashboard';
import { useAuthStore } from '@/stores/auth-store';
import { useSearchStore } from '@/stores/search-store';
import { apiClient } from '@/lib/api-client';
import type { DumpUpload, DumpStatus, DumpProgress } from '@/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Progress } from '@/components/ui/progress';
import { StatusBadge } from '@/components/shared/status-badge';
import { EmptyState } from '@/components/shared/empty-state';
import { QuotaBar } from '@/components/shared/quota-bar';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';

const container = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.06 } },
};

const item = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.25, 0.4, 0.25, 1] as const } },
};

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function getFileIcon(type: string) {
  if (type === 'csv') return <FileText className="h-4 w-4 text-success" />;
  if (type === 'xlsx' || type === 'xls') return <FileSpreadsheet className="h-4 w-4 text-info" />;
  return <FileText className="h-4 w-4 text-muted-foreground" />;
}

const fileTypeColors: Record<string, string> = {
  csv: 'bg-success/10 text-success border-success/20',
  xlsx: 'bg-info/10 text-info border-info/20',
  xls: 'bg-info/10 text-info border-info/20',
  json: 'bg-warning/10 text-warning border-warning/20',
};

export default function DashboardPage() {
  const { user } = useAuthStore();
  const { dumpSort, dumpStatusFilter, dumpViewMode, setDumpSort, setDumpStatusFilter, setDumpViewMode } = useSearchStore();
  const [file, setFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [showBanner, setShowBanner] = useState(true);
  const [activeProgress, setActiveProgress] = useState<Map<string, DumpProgress>>(new Map());
  const fileInputRef = useRef<HTMLInputElement>(null);
  const eventSourcesRef = useRef<Map<string, EventSource>>(new Map());

  const { data: stats, isLoading: statsLoading } = useDashboardStats();
  const { data, isLoading, refetch } = useDumps({
    sortBy: dumpSort || undefined,
    limit: 50,
  });
  const uploadMutation = useUploadDump();
  const updateDump = useUpdateDump();
  const deleteDump = useDeleteDump();
  const [deleteTarget, setDeleteTarget] = useState<DumpUpload | null>(null);

  const isFree = user?.plan === 'FREE';

  const handlePin = (dump: DumpUpload) => {
    updateDump.mutate({ id: dump.id, data: { isPinned: !dump.isPinned } });
  };

  const handleArchive = (dump: DumpUpload) => {
    updateDump.mutate({ id: dump.id, data: { isArchived: !dump.isArchived } });
  };

  const handleDelete = () => {
    if (!deleteTarget) return;
    deleteDump.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success('Dump deleted');
        setDeleteTarget(null);
      },
      onError: () => toast.error('Failed to delete dump'),
    });
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (selected) setFile(selected);
  };

  const handleUpload = async () => {
    if (!file) return;
    try {
      const dump = await uploadMutation.mutateAsync(file);
      toast.success(`Upload started: ${file.name}`);

      const progressUrl = apiClient.getDumpProgressUrl(dump.id);
      const es = new EventSource(progressUrl);
      eventSourcesRef.current.set(dump.id, es);

      es.onmessage = (event) => {
        try {
          const progress: DumpProgress = JSON.parse(event.data);
          setActiveProgress((prev) => {
            const next = new Map(prev);
            next.set(dump.id, progress);
            return next;
          });
          if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
            es.close();
            eventSourcesRef.current.delete(dump.id);
            refetch();
          }
        } catch { /* ignore */ }
      };
      es.onerror = () => { es.close(); eventSourcesRef.current.delete(dump.id); };

      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch {
      toast.error('Upload failed');
    }
  };

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const dropped = e.dataTransfer.files[0];
    if (dropped) setFile(dropped);
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback(() => setIsDragging(false), []);

  useEffect(() => {
    return () => { eventSourcesRef.current.forEach((es) => es.close()); };
  }, []);

  const statCards = [
    { label: 'Total Dumps', value: stats?.totalDumps ?? 0, icon: Database, color: 'text-foreground' },
    { label: 'Total Contacts', value: stats?.totalContacts ?? 0, icon: Users, color: 'text-success' },
    { label: 'Storage Used', value: `${formatBytes(stats?.storageUsedBytes ?? 0)}`, icon: HardDrive, color: 'text-info', sub: stats?.storageLimitBytes ? `${formatBytes(stats.storageLimitBytes)} limit` : undefined },
    { label: 'Uploads This Month', value: stats?.uploadsThisMonth ?? 0, icon: Calendar, color: 'text-warning' },
  ];

  const dumps = data?.items ?? [];
  const filteredDumps = dumpStatusFilter
    ? dumps.filter((d) => d.status === dumpStatusFilter)
    : dumps;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      {/* Free tier banner */}
      <AnimatePresence>
        {isFree && showBanner && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8, height: 0, marginTop: 0, marginBottom: 0 }}
            className="rounded-lg border border-warning/20 bg-warning/5 px-4 py-3 flex items-center justify-between gap-3"
          >
            <div className="flex items-center gap-3">
              <Zap className="h-4 w-4 text-warning shrink-0" />
              <p className="text-sm">
                You&apos;re on the <span className="font-medium">Free</span> plan.{' '}
                <Link href="/settings" className="font-medium underline underline-offset-2 hover:text-foreground/80">
                  Upgrade to Pro
                </Link>{' '}
                for unlimited contacts & advanced features.
              </p>
            </div>
            <button onClick={() => setShowBanner(false)} className="shrink-0 text-muted-foreground hover:text-foreground">
              <X className="h-4 w-4" />
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Welcome */}
      <motion.div variants={item}>
        <h2 className="text-2xl font-bold tracking-tight">
          Welcome back, {user?.name?.split(' ')[0] || 'User'}
        </h2>
        <p className="text-muted-foreground">Upload your contact data and manage your pipeline.</p>
      </motion.div>

      {/* Upload Dropzone */}
      <motion.div variants={item}>
        <Card className="overflow-hidden">
          <CardContent className="p-0">
            <motion.div
              onDrop={handleDrop}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onClick={() => fileInputRef.current?.click()}
              animate={{
                borderColor: isDragging ? 'rgba(34, 197, 94, 0.5)' : undefined,
                backgroundColor: isDragging ? 'rgba(34, 197, 94, 0.03)' : undefined,
              }}
              className={`border-2 border-dashed rounded-lg m-4 p-10 text-center transition-all cursor-pointer ${
                isDragging ? 'border-success' : 'hover:border-foreground/20 hover:bg-accent/30'
              }`}
            >
              <input ref={fileInputRef} type="file" accept=".csv,.xlsx,.xls,.json" className="hidden" onChange={handleFileSelect} />
              <motion.div animate={{ y: isDragging ? -4 : 0 }} transition={{ type: 'spring', stiffness: 300, damping: 20 }}>
                <div className={`mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl transition-colors ${
                  isDragging ? 'bg-success/10' : 'bg-muted'
                }`}>
                  <Upload className={`h-7 w-7 ${isDragging ? 'text-success' : 'text-muted-foreground'}`} />
                </div>
                <p className="font-medium text-sm">
                  {isDragging ? 'Drop your file here' : 'Drop files here or click to browse'}
                </p>
                <p className="text-xs text-muted-foreground mt-1.5">
                  Supports CSV, XLSX, JSON — up to 50MB per file
                </p>
              </motion.div>
            </motion.div>

            {/* Selected file preview */}
            <AnimatePresence>
              {file && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="overflow-hidden"
                >
                  <div className="px-4 pb-4">
                    <div className="flex items-center justify-between rounded-lg border bg-card p-3">
                      <div className="flex items-center gap-3 min-w-0">
                        {getFileIcon(file.name.split('.').pop() || '')}
                        <div className="min-w-0">
                          <p className="text-sm font-medium truncate">{file.name}</p>
                          <p className="text-xs text-muted-foreground">{formatBytes(file.size)}</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button size="sm" onClick={handleUpload} disabled={uploadMutation.isPending}>
                          {uploadMutation.isPending ? (
                            <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Uploading...</>
                          ) : (
                            <><Upload className="mr-2 h-4 w-4" /> Upload</>
                          )}
                        </Button>
                        <button onClick={() => setFile(null)} className="text-muted-foreground hover:text-foreground">
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </CardContent>
        </Card>
      </motion.div>

      {/* Stats Bar */}
      <motion.div variants={item} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {statCards.map((stat) => (
          <motion.div
            key={stat.label}
            whileHover={{ y: -2, boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}
            whileTap={{ scale: 0.98 }}
            className="rounded-lg border bg-card p-4 transition-shadow cursor-default"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-muted-foreground">{stat.label}</span>
              <stat.icon className={`h-4 w-4 ${stat.color}`} />
            </div>
            <p className="text-2xl font-bold tracking-tight">{typeof stat.value === 'number' ? stat.value.toLocaleString() : stat.value}</p>
            {stat.sub && <p className="text-xs text-muted-foreground mt-0.5">{stat.sub}</p>}
          </motion.div>
        ))}
      </motion.div>

      {/* Quota Bar for Free Tier */}
      {isFree && stats && (
        <motion.div variants={item}>
          <QuotaBar
            used={stats.totalContacts ?? 0}
            limit={stats.contactsLimit ?? 1000}
            label="Contact Quota"
            className="max-w-lg"
          />
        </motion.div>
      )}

      {/* Dump List */}
      <motion.div variants={item} className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-semibold tracking-tight">Uploads</h3>
            <Badge variant="secondary" className="text-xs">{filteredDumps.length}</Badge>
          </div>

          <div className="flex items-center gap-2">
            {/* Status filter */}
            <div className="relative">
              <Button variant="outline" size="sm" className="h-8 gap-1.5 text-xs">
                <Filter className="h-3.5 w-3.5" />
                {dumpStatusFilter || 'All'}
                <ChevronDown className="h-3 w-3 opacity-50" />
              </Button>
            </div>
            {/* Sort */}
            <div className="relative">
              <Button variant="outline" size="sm" className="h-8 gap-1.5 text-xs" onClick={() => setDumpSort(dumpSort === 'createdAt' ? 'name' : 'createdAt')}>
                <Clock className="h-3.5 w-3.5" />
                {dumpSort === 'createdAt' ? 'Date' : 'Name'}
              </Button>
            </div>
            {/* View toggle */}
            <div className="flex rounded-md border">
              <button
                onClick={() => setDumpViewMode('grid')}
                className={`p-1.5 ${dumpViewMode === 'grid' ? 'bg-accent' : ''}`}
              >
                <LayoutGrid className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => setDumpViewMode('list')}
                className={`p-1.5 ${dumpViewMode === 'list' ? 'bg-accent' : ''}`}
              >
                <List className="h-3.5 w-3.5" />
              </button>
            </div>
            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => refetch()}>
              <RefreshCw className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>

        {/* Status filter chips */}
        <div className="flex items-center gap-2 flex-wrap">
          {['', 'COMPLETED', 'PARSING', 'FAILED', 'PENDING'].map((status) => (
            <motion.button
              key={status}
              whileTap={{ scale: 0.97 }}
              onClick={() => setDumpStatusFilter(status)}
              className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
                dumpStatusFilter === status
                  ? 'bg-foreground text-background border-foreground'
                  : 'bg-card text-muted-foreground border-border hover:border-foreground/20'
              }`}
            >
              {status || 'All'}
            </motion.button>
          ))}
        </div>

        {isLoading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-32 rounded-lg" />
            ))}
          </div>
        ) : filteredDumps.length === 0 ? (
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}>
            <EmptyState
              icon={Inbox}
              title="No uploads yet"
              description="Upload your first CSV or Excel file to start parsing contacts."
              action={
                <Button size="sm" onClick={() => fileInputRef.current?.click()}>
                  <Upload className="mr-2 h-4 w-4" /> Upload File
                </Button>
              }
            />
          </motion.div>
        ) : (
          <AnimatePresence mode="popLayout">
            {dumpViewMode === 'grid' ? (
              <motion.div layout className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {filteredDumps.map((dump) => (
                  <DumpCard key={dump.id} dump={dump} progress={activeProgress.get(dump.id)} onPin={() => handlePin(dump)} onArchive={() => handleArchive(dump)} onDelete={() => setDeleteTarget(dump)} />
                ))}
              </motion.div>
            ) : (
              <motion.div layout className="space-y-2">
                {filteredDumps.map((dump) => (
                  <DumpListItem key={dump.id} dump={dump} progress={activeProgress.get(dump.id)} onPin={() => handlePin(dump)} onArchive={() => handleArchive(dump)} onDelete={() => setDeleteTarget(dump)} />
                ))}
              </motion.div>
            )}
          </AnimatePresence>
        )}
      </motion.div>

      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
        onConfirm={handleDelete}
        title={deleteTarget?.originalFilename || ''}
      />
    </motion.div>
  );
}

function DeleteConfirmDialog({ open, onOpenChange, onConfirm, title }: { open: boolean; onOpenChange: (open: boolean) => void; onConfirm: () => void; title: string }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          onClick={() => onOpenChange(false)}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="rounded-lg border bg-card p-6 max-w-sm w-full mx-4 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-semibold">Delete dump?</h3>
            <p className="text-sm text-muted-foreground mt-2">
              Are you sure you want to delete <span className="font-medium">{title}</span>? This action cannot be undone.
            </p>
            <div className="flex justify-end gap-2 mt-4">
              <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>Cancel</Button>
              <Button variant="destructive" size="sm" onClick={onConfirm}>Delete</Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function DumpCard({ dump, progress, onPin, onArchive, onDelete }: { dump: DumpUpload; progress?: DumpProgress; onPin: () => void; onArchive: () => void; onDelete: () => void }) {
  const ext = dump.originalFilename.split('.').pop()?.toLowerCase() || '';
  const pct = progress?.progress ?? (dump.status === 'COMPLETED' ? 100 : dump.parsedContactsCount > 0 ? Math.round((dump.parsedContactsCount / dump.totalRows) * 100) : 0);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -2, boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}
      whileTap={{ scale: 0.98 }}
      className="rounded-lg border bg-card p-4 cursor-pointer transition-colors"
    >
      <Link href={`/dumps/${dump.id}`} className="block">
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center gap-2 min-w-0">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
              {getFileIcon(ext)}
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{dump.name || dump.originalFilename}</p>
              <p className="text-xs text-muted-foreground truncate">{dump.originalFilename}</p>
            </div>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button onClick={(e) => e.preventDefault()} className="p-1 rounded hover:bg-accent">
                <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={(e) => { e.preventDefault(); onPin(); }}>
                <Pin className="mr-2 h-4 w-4" /> {dump.isPinned ? 'Unpin' : 'Pin'}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={(e) => { e.preventDefault(); onArchive(); }}>
                <Archive className="mr-2 h-4 w-4" /> {dump.isArchived ? 'Unarchive' : 'Archive'}
              </DropdownMenuItem>
              <DropdownMenuItem className="text-destructive" onClick={(e) => { e.preventDefault(); onDelete(); }}>
                <Trash2 className="mr-2 h-4 w-4" /> Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <div className="flex items-center gap-2 mb-3">
          <span className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[10px] font-medium uppercase ${fileTypeColors[ext] || 'bg-muted text-muted-foreground border-border'}`}>
            {ext}
          </span>
          <StatusBadge status={dump.status} size="sm" />
          {dump.isPinned && <Pin className="h-3 w-3 text-muted-foreground ml-auto" />}
        </div>

        {(dump.status === 'PARSING' || (progress && progress.status === 'PARSING')) && (
          <div className="mb-2">
            <Progress value={pct} className="h-1.5" />
            <p className="text-[10px] text-muted-foreground mt-1">{dump.parsedContactsCount}/{dump.totalRows} rows · {pct}%</p>
          </div>
        )}

        <div className="flex items-center justify-between text-xs text-muted-foreground pt-2 border-t border-border/50">
          <div className="flex items-center gap-3">
            <span>{dump.liveContactsCount} contacts</span>
            <span>{formatBytes(dump.fileSizeBytes)}</span>
          </div>
          <span>{new Date(dump.createdAt).toLocaleDateString()}</span>
        </div>
      </Link>
    </motion.div>
  );
}

function DumpListItem({ dump, progress, onPin, onArchive, onDelete }: { dump: DumpUpload; progress?: DumpProgress; onPin: () => void; onArchive: () => void; onDelete: () => void }) {
  const ext = dump.originalFilename.split('.').pop()?.toLowerCase() || '';
  const pct = progress?.progress ?? (dump.status === 'COMPLETED' ? 100 : dump.parsedContactsCount > 0 ? Math.round((dump.parsedContactsCount / dump.totalRows) * 100) : 0);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      whileHover={{ backgroundColor: 'var(--accent)' }}
      className="flex items-center gap-3 rounded-lg border bg-card px-4 py-3 cursor-pointer transition-colors"
    >
      <Link href={`/dumps/${dump.id}`} className="flex items-center gap-3 flex-1 min-w-0">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
          {getFileIcon(ext)}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium truncate">{dump.name || dump.originalFilename}</p>
            <span className={`inline-flex items-center rounded border px-1.5 py-0 text-[10px] font-medium uppercase shrink-0 ${fileTypeColors[ext] || 'bg-muted text-muted-foreground border-border'}`}>
              {ext}
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
            <span>{dump.liveContactsCount} contacts</span>
            <span>·</span>
            <span>{formatBytes(dump.fileSizeBytes)}</span>
            {(dump.status === 'PARSING' || progress) && (
              <>
                <span>·</span>
                <span>{pct}%</span>
              </>
            )}
          </div>
        </div>
        <StatusBadge status={dump.status} size="sm" />
        <span className="text-xs text-muted-foreground shrink-0 hidden sm:block">
          {new Date(dump.createdAt).toLocaleDateString()}
        </span>
      </Link>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="p-1 rounded hover:bg-accent shrink-0">
            <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={(e) => { e.preventDefault(); onPin(); }}><Pin className="mr-2 h-4 w-4" /> {dump.isPinned ? 'Unpin' : 'Pin'}</DropdownMenuItem>
          <DropdownMenuItem onClick={(e) => { e.preventDefault(); onArchive(); }}><Archive className="mr-2 h-4 w-4" /> {dump.isArchived ? 'Unarchive' : 'Archive'}</DropdownMenuItem>
          <DropdownMenuItem className="text-destructive" onClick={(e) => { e.preventDefault(); onDelete(); }}><Trash2 className="mr-2 h-4 w-4" /> Delete</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </motion.div>
  );
}
