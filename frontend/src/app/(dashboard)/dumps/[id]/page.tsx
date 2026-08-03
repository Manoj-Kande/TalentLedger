'use client';

import { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { motion } from 'framer-motion';
import { ArrowLeft, FileText, Users, Database, AlertTriangle, Clock, CheckCircle2, XCircle } from 'lucide-react';
import { useDump, useDumpContacts, useConfirmSaveDump, useRetryDump } from '@/hooks/use-dumps';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Progress } from '@/components/ui/progress';
import { StatusBadge } from '@/components/shared/status-badge';
import { ErrorState } from '@/components/shared/error-state';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Separator } from '@/components/ui/separator';
import { toast } from 'sonner';
import Link from 'next/link';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function DumpDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = params.id as string;
  const { data: dump, isLoading, isError } = useDump(id);
  const { data: contactsData } = useDumpContacts(id, { limit: 5 });
  const confirmSaveMutation = useConfirmSaveDump();
  const retryMutation = useRetryDump();
  const [showSaveConfirm, setShowSaveConfirm] = useState(false);

  const handleConfirmSave = async () => {
    try {
      await confirmSaveMutation.mutateAsync(id);
      toast.success('Saved to your workspace');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to save — please try again');
    } finally {
      setShowSaveConfirm(false);
    }
  };

  const handleRetry = async () => {
    try {
      await retryMutation.mutateAsync(id);
      toast.success('Retry queued');
    } catch {
      toast.error('Failed to retry — please try again');
    }
  };

  if (isLoading) {
    return (
      <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-4 gap-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-lg" />)}</div>
        <Skeleton className="h-64 rounded-lg" />
      </motion.div>
    );
  }

  if (isError || !dump) {
    return <ErrorState message="Dump not found" onRetry={() => router.push('/dashboard')} />;
  }

  const pct = dump.totalRows > 0 ? Math.round((dump.parsedContactsCount / dump.totalRows) * 100) : 0;
  const stats = [
    { label: 'Total Rows', value: dump.totalRows.toLocaleString(), icon: Database },
    { label: 'Processed', value: dump.parsedContactsCount.toLocaleString(), icon: CheckCircle2 },
    { label: 'Contacts Found', value: dump.liveContactsCount.toLocaleString(), icon: Users },
    { label: 'Errors', value: dump.errorCount.toLocaleString(), icon: dump.errorCount > 0 ? AlertTriangle : CheckCircle2, iconColor: dump.errorCount > 0 ? 'text-destructive' : 'text-success' },
  ];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex items-center gap-4">
        <Link href="/dashboard">
          <Button variant="ghost" size="icon" className="h-8 w-8"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold truncate">{dump.name || dump.originalFilename}</h2>
            <StatusBadge status={dump.status} />
            {!dump.isPersisted && (
              <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-warning/10 text-warning border border-warning/20">
                Preview — not saved
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground">{dump.originalFilename} · {formatBytes(dump.fileSizeBytes)}</p>
        </div>
        {dump.status === 'FAILED' && (
          <Button variant="outline" size="sm" onClick={handleRetry} disabled={retryMutation.isPending}>
            Retry
          </Button>
        )}
        {!dump.isPersisted && dump.status === 'COMPLETED' && (
          <Button size="sm" onClick={() => setShowSaveConfirm(true)} disabled={confirmSaveMutation.isPending}>
            Save to Workspace
          </Button>
        )}
        <Button variant="outline" size="sm" asChild>
          <Link href={`/dumps/${id}/contacts`}>View Contacts</Link>
        </Button>
      </motion.div>

      <ConfirmDialog
        open={showSaveConfirm}
        onOpenChange={setShowSaveConfirm}
        title="Save this upload to your workspace?"
        description="This will permanently store the extracted contacts in your account and count against your plan's dump/upload limits. You can't undo this from here."
        confirmLabel="Save permanently"
        onConfirm={handleConfirmSave}
      />

      <motion.div variants={item} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <motion.div
            key={stat.label}
            whileHover={{ y: -2, boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}
            className="rounded-lg border bg-card p-4"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-muted-foreground">{stat.label}</span>
              <stat.icon className={`h-4 w-4 ${stat.iconColor || 'text-muted-foreground'}`} />
            </div>
            <p className="text-2xl font-bold tracking-tight">{stat.value}</p>
          </motion.div>
        ))}
      </motion.div>

      {/* Progress */}
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Parse Progress</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span>{dump.parsedContactsCount.toLocaleString()} / {dump.totalRows.toLocaleString()} rows</span>
              <span className="font-medium">{pct}%</span>
            </div>
            <Progress value={pct} className="h-2" />
          </CardContent>
        </Card>
      </motion.div>

      {/* Parse errors (row-level) live on GET /dumps/{id}/errors — surfaced via the "View errors" link on the errors stat instead of a single message string, which the API doesn't return. */}

      {/* Metadata */}
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Metadata</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">File Type</span>
              <Badge variant="outline" className="uppercase text-xs">{dump.fileType}</Badge>
            </div>
            <Separator />
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Created</span>
              <span className="font-medium">{new Date(dump.createdAt).toLocaleString()}</span>
            </div>
            <Separator />
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Updated</span>
              <span className="font-medium">{new Date(dump.updatedAt).toLocaleString()}</span>
            </div>
          </CardContent>
        </Card>
      </motion.div>

      {/* Recent contacts preview */}
      {contactsData?.items && contactsData.items.length > 0 && (
        <motion.div variants={item}>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-sm font-medium">Recent Contacts</CardTitle>
              <Button variant="ghost" size="sm" asChild>
                <Link href={`/dumps/${id}/contacts`}>View all →</Link>
              </Button>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {contactsData.items.map((c) => (
                  <Link key={c.id} href={`/contacts/${c.id}`} className="flex items-center gap-3 p-2 rounded-md hover:bg-accent transition-colors">
                    <div className="flex h-7 w-7 items-center justify-center rounded-full bg-muted text-xs font-medium">
                      {(c.name || "?").split(" ").map((n) => n[0]).join("").slice(0,2)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{c.name}</p>
                      <p className="text-xs text-muted-foreground truncate">{c.email}</p>
                    </div>
                    {c.companyName && <span className="text-xs text-muted-foreground hidden sm:block">{c.companyName}</span>}
                  </Link>
                ))}
              </div>
            </CardContent>
          </Card>
        </motion.div>
      )}
    </motion.div>
  );
}
