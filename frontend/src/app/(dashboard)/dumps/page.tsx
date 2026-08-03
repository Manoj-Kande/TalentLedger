'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  Upload,
  FileText,
  CheckCircle2,
  XCircle,
  Loader2,
  Clock,
  AlertTriangle,
  RefreshCw,
} from 'lucide-react';
import { toast } from 'sonner';
import { useDumps, useUploadDump, useConfirmSaveDump, useRetryDump } from '@/hooks/use-dumps';
import { apiClient } from '@/lib/api-client';
import type { DumpStatus, DumpProgress } from '@/types';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

const statusConfig: Record<DumpStatus, { icon: React.ElementType; color: string; label: string }> = {
  PENDING: { icon: Clock, color: 'text-yellow-500', label: 'Pending' },
  PARSING: { icon: Loader2, color: 'text-blue-500', label: 'Parsing' },
  COMPLETED: { icon: CheckCircle2, color: 'text-emerald-500', label: 'Completed' },
  FAILED: { icon: XCircle, color: 'text-destructive', label: 'Failed' },
  EXPIRED: { icon: Clock, color: 'text-muted-foreground', label: 'Expired' },
};

export default function DumpsPage() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [activeProgress, setActiveProgress] = useState<Map<string, DumpProgress>>(new Map());
  const fileInputRef = useRef<HTMLInputElement>(null);
  const eventSourcesRef = useRef<Map<string, EventSource>>(new Map());

  const { data, isLoading, refetch } = useDumps({ limit: 20 });
  const confirmSaveMutation = useConfirmSaveDump();
  const retryMutation = useRetryDump();
  const [saveDialogId, setSaveDialogId] = useState<string | null>(null);

  const handleConfirmSave = async () => {
    if (!saveDialogId) return;
    try {
      await confirmSaveMutation.mutateAsync(saveDialogId);
      toast.success('Saved to your workspace');
    } catch {
      toast.error('Could not save — check your plan limits');
    } finally {
      setSaveDialogId(null);
    }
  };

  const handleRetry = async (id: string) => {
    try {
      await retryMutation.mutateAsync(id);
      toast.success('Retry queued');
    } catch {
      toast.error('Retry failed — try re-uploading the file');
    }
  };
  const uploadMutation = useUploadDump();

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (selected) {
      if (!selected.name.endsWith('.csv')) {
        toast.error('Only CSV files are supported');
        return;
      }
      setFile(selected);
    }
  };

  const handleUpload = async () => {
    if (!file) return;
    try {
      const dump = await uploadMutation.mutateAsync(file);
      toast.success(`Upload started: ${file.name}`);

      // Connect to SSE for progress
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
        } catch {
          // Ignore parse errors
        }
      };

      es.onerror = () => {
        es.close();
        eventSourcesRef.current.delete(dump.id);
      };

      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch {
      toast.error('Upload failed');
    }
  };

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const dropped = e.dataTransfer.files[0];
    if (dropped?.name.endsWith('.csv')) {
      setFile(dropped);
    } else {
      toast.error('Only CSV files are supported');
    }
  }, []);

  const handleDragOver = (e: React.DragEvent) => e.preventDefault();

  // Cleanup SSE connections on unmount
  useEffect(() => {
    return () => {
      eventSourcesRef.current.forEach((es) => es.close());
    };
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Dump Uploads</h2>
          <p className="text-muted-foreground">
            Upload CSV files to bulk import contacts
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Refresh
        </Button>
      </div>

      {/* Upload Area */}
      <Card>
        <CardContent className="p-6">
          <div
            className="border-2 border-dashed rounded-lg p-8 text-center transition-colors hover:border-primary/50 hover:bg-accent/50 cursor-pointer"
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onClick={() => fileInputRef.current?.click()}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv"
              className="hidden"
              onChange={handleFileSelect}
            />
            <Upload className="h-10 w-10 mx-auto mb-4 text-muted-foreground" />
            <p className="font-medium">Drop a CSV file here or click to browse</p>
            <p className="text-sm text-muted-foreground mt-1">
              Supports .csv files up to 50MB
            </p>
          </div>

          {file && (
            <div className="mt-4 flex items-center justify-between rounded-lg border p-3">
              <div className="flex items-center gap-3">
                <FileText className="h-5 w-5 text-primary" />
                <div>
                  <p className="text-sm font-medium">{file.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {(file.size / 1024).toFixed(1)} KB
                  </p>
                </div>
              </div>
              <Button size="sm" onClick={handleUpload} disabled={uploadMutation.isPending}>
                {uploadMutation.isPending ? (
                  <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Uploading...</>
                ) : (
                  <><Upload className="mr-2 h-4 w-4" /> Upload</>
                )}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Upload History */}
      <Card>
        <CardHeader>
          <CardTitle>Upload History</CardTitle>
          <CardDescription>Track the progress of your uploads</CardDescription>
        </CardHeader>
        {isLoading ? (
          <CardContent>
            <div className="space-y-4">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-16 w-full" />
              ))}
            </div>
          </CardContent>
        ) : !data?.items?.length ? (
          <CardContent>
            <div className="text-center py-8 text-muted-foreground">
              <FileText className="h-8 w-8 mx-auto mb-2 opacity-50" />
              <p>No uploads yet</p>
            </div>
          </CardContent>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>File</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="hidden md:table-cell">Progress</TableHead>
                <TableHead className="hidden lg:table-cell">Processed</TableHead>
                <TableHead className="hidden lg:table-cell">Errors</TableHead>
                <TableHead className="hidden sm:table-cell">Date</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.items.map((dump) => {
                const progress = activeProgress.get(dump.id);
                const config = statusConfig[dump.status];
                const isProcessing = dump.status === 'PARSING' || progress?.status === 'PARSING';
                const pct = progress?.progress ?? (dump.status === 'COMPLETED' ? 100 : dump.status === 'PARSING' ? 0 : 0);

                return (
                  <TableRow key={dump.id} className="cursor-pointer hover:bg-accent/50" onClick={() => router.push(`/dumps/${dump.id}`)}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <FileText className="h-4 w-4 text-muted-foreground shrink-0" />
                        <span className="font-medium text-sm">{dump.originalFilename}</span>
                        {!dump.isPersisted && (
                          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-warning/10 text-warning border border-warning/20 shrink-0">
                            Preview
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <config.icon className={`h-4 w-4 ${config.color} ${isProcessing ? 'animate-spin' : ''}`} />
                        <Badge
                          variant={
                            dump.status === 'COMPLETED' ? 'default' :
                            dump.status === 'FAILED' ? 'destructive' :
                            'secondary'
                          }
                          className="text-xs"
                        >
                          {progress?.status ?? dump.status}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="hidden md:table-cell">
                      {isProcessing || dump.status === 'PARSING' ? (
                        <div className="w-32">
                          <Progress value={pct} className="h-2" />
                          <p className="text-xs text-muted-foreground mt-1">{pct}%</p>
                        </div>
                      ) : dump.status === 'COMPLETED' ? (
                        <Progress value={100} className="h-2" />
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="hidden lg:table-cell text-sm">
                      {progress?.processedRows ?? dump.parsedContactsCount} / {progress?.totalRows ?? dump.totalRows}
                    </TableCell>
                    <TableCell className="hidden lg:table-cell">
                      {(progress?.errorCount ?? dump.errorCount) > 0 ? (
                        <span className="text-destructive text-sm font-medium">
                          {progress?.errorCount ?? dump.errorCount}
                        </span>
                      ) : (
                        <span className="text-sm text-muted-foreground">0</span>
                      )}
                    </TableCell>
                    <TableCell className="hidden sm:table-cell text-sm text-muted-foreground">
                      {new Date(dump.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                      {dump.status === 'FAILED' && (
                        <Button size="sm" variant="outline" className="h-7 text-xs" onClick={() => handleRetry(dump.id)} disabled={retryMutation.isPending}>
                          <RefreshCw className="h-3 w-3 mr-1" /> Retry
                        </Button>
                      )}
                      {dump.status === 'COMPLETED' && !dump.isPersisted && (
                        <Button size="sm" className="h-7 text-xs" onClick={() => setSaveDialogId(dump.id)}>
                          Save to Workspace
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Card>

      <AlertDialog open={!!saveDialogId} onOpenChange={(open) => !open && setSaveDialogId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Save to Workspace?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently store this upload and its extracted contacts in your account,
              counting against your plan&apos;s storage and contact limits. Until now it was only a
              free preview.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleConfirmSave} disabled={confirmSaveMutation.isPending}>
              Save Permanently
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
