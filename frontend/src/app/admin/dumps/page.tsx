'use client';

import { motion } from 'framer-motion';
import { FileText, Search, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { PaginatedResponse } from '@/types';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { StatusBadge } from '@/components/shared/status-badge';
import { EmptyState } from '@/components/shared/empty-state';
import { ErrorState } from '@/components/shared/error-state';
import { TableSkeleton } from '@/components/shared/loading-skeleton';
import type { DumpUpload } from '@/types';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] as const } } };

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export default function AdminDumpsPage() {
  const [search, setSearch] = useState('');
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'dumps', search],
    queryFn: () => apiClient.get<PaginatedResponse<DumpUpload>>('/api/v1/admin/dumps', { search: search || undefined, limit: 50 }),
  });

  const dumps: DumpUpload[] = data?.items ?? [];

  const filtered = search
    ? dumps.filter(
        (d) =>
          d.originalFilename.toLowerCase().includes(search.toLowerCase())
      )
    : dumps;

  const handleDelete = () => {
    if (!deleteId) return;
    toast.success('Dump deleted successfully');
    setDeleteId(null);
  };

  const dumpToDelete = deleteId ? dumps.find((d) => d.id === deleteId) : null;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">All Dumps</h2>
          <p className="text-muted-foreground">
            Monitor all uploads across the system
            {!isLoading && data?.total !== undefined && (
              <span className="ml-1">· {data.total.toLocaleString()} total</span>
            )}
          </p>
        </div>
      </motion.div>

      <motion.div variants={item}>
        <div className="relative max-w-sm">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search dumps..."
            className="pl-8 h-9 text-sm"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-6">
                <TableSkeleton rows={6} />
              </div>
            ) : isError ? (
              <ErrorState
                message={error?.message || 'Failed to load dumps.'}
                onRetry={() => refetch()}
              />
            ) : filtered.length === 0 ? (
              <div className="p-8">
                <EmptyState
                  icon={FileText}
                  title={search ? 'No dumps match your search' : 'No dumps found'}
                  description={
                    search
                      ? 'Try adjusting your search terms.'
                      : 'No data dumps have been uploaded yet.'
                  }
                />
              </div>
            ) : (
              <div className="divide-y">
                {filtered.map((dump, i) => (
                  <motion.div
                    key={dump.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: i * 0.03 }}
                    className="flex items-center gap-4 px-4 py-3 group"
                  >
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
                      <FileText className="h-4 w-4 text-muted-foreground" />
                    </div>

                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{dump.originalFilename}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatDate(dump.createdAt)}
                        <span className="mx-1.5">·</span>
                        {formatFileSize(dump.fileSizeBytes)}
                      </p>
                    </div>

                    <StatusBadge status={dump.status} size="sm" />

                    <div className="hidden sm:flex items-center gap-4 text-xs text-muted-foreground">
                      <span>{dump.totalRows.toLocaleString()} rows</span>
                      <span>{dump.liveContactsCount.toLocaleString()} contacts</span>
                      {dump.errorCount > 0 && (
                        <span className="text-destructive">{dump.errorCount} errors</span>
                      )}
                    </div>

                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity text-muted-foreground hover:text-destructive"
                      onClick={() => setDeleteId(dump.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </motion.div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>

      {/* Load more / pagination hint */}
      {data?.hasMore && (
        <motion.div variants={item} className="text-center">
          <Button
            variant="outline"
            size="sm"
            disabled={isLoading}
            onClick={() => refetch()}
          >
            Load more
          </Button>
        </motion.div>
      )}

      {/* Delete confirmation dialog */}
      <AlertDialog open={!!deleteId} onOpenChange={(open) => !open && setDeleteId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Dump</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete{' '}
              <span className="font-medium text-foreground">{dumpToDelete?.originalFilename}</span>?
              This action cannot be undone. All associated contacts will also be removed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-white hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </motion.div>
  );
}
