'use client';

import { motion } from 'framer-motion';
import { Search, ScrollText } from 'lucide-react';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/shared/empty-state';
import { ErrorState } from '@/components/shared/error-state';
import { TableSkeleton } from '@/components/shared/loading-skeleton';
import type { AuditLog, CursorPaginatedResponse } from '@/types';

const AUDIT_KEY = ['admin', 'audit'];

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] as const } } };

const actionColors: Record<string, string> = {
  user: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
  dump: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  campaign: 'bg-purple-500/10 text-purple-500 border-purple-500/20',
  contact: 'bg-orange-500/10 text-orange-500 border-orange-500/20',
  list: 'bg-pink-500/10 text-pink-500 border-pink-500/20',
  config: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20',
  system: 'bg-muted text-muted-foreground border-border',
};

const actionPrefixes = ['user', 'dump', 'campaign', 'contact', 'list', 'config'];

function timeAgo(iso: string): string {
  const now = Date.now();
  const then = new Date(iso).getTime();
  const diffMs = now - then;
  const seconds = Math.floor(diffMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) return `${days}d ago`;
  if (hours > 0) return `${hours}h ago`;
  if (minutes > 0) return `${minutes}m ago`;
  return 'just now';
}

export default function AdminAuditPage() {
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState('');

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: [...AUDIT_KEY, search, actionFilter],
    queryFn: () =>
      apiClient.get<CursorPaginatedResponse<AuditLog>>('/api/v1/admin/audit', {
        search: search || undefined,
        action: actionFilter || undefined,
        limit: 50,
      }),
  });

  const logs: AuditLog[] = data?.data ?? [];

  const filtered = actionFilter
    ? logs.filter((l) => l.action.startsWith(actionFilter))
    : logs;

  const clientFiltered = search
    ? filtered.filter(
        (l) =>
          l.userName.toLowerCase().includes(search.toLowerCase()) ||
          (l.details ?? '').toLowerCase().includes(search.toLowerCase())
      )
    : filtered;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item}>
        <h2 className="text-2xl font-bold tracking-tight">Audit Log</h2>
        <p className="text-muted-foreground">
          Track all actions across the system
          {!isLoading && data?.pagination?.total !== undefined && (
            <span className="ml-1">· {data.pagination.total.toLocaleString()} events</span>
          )}
        </p>
      </motion.div>

      <motion.div variants={item} className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 max-w-sm min-w-[200px]">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search logs..."
            className="pl-8 h-9 text-sm"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="flex flex-wrap items-center gap-1">
          <button
            onClick={() => setActionFilter('')}
            className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
              !actionFilter
                ? 'bg-foreground text-background border-foreground'
                : 'bg-card text-muted-foreground border-border hover:border-foreground/20'
            }`}
          >
            All
          </button>
          {actionPrefixes.map((p) => (
            <button
              key={p}
              onClick={() => setActionFilter(actionFilter === p ? '' : p)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border capitalize transition-colors ${
                actionFilter === p
                  ? 'bg-foreground text-background border-foreground'
                  : 'bg-card text-muted-foreground border-border hover:border-foreground/20'
              }`}
            >
              {p}
            </button>
          ))}
        </div>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-6">
                <TableSkeleton rows={8} />
              </div>
            ) : isError ? (
              <ErrorState
                message={error?.message || 'Failed to load audit logs.'}
                onRetry={() => refetch()}
              />
            ) : clientFiltered.length === 0 ? (
              <div className="p-8">
                <EmptyState
                  icon={ScrollText}
                  title={search || actionFilter ? 'No logs match your filters' : 'No audit logs found'}
                  description={
                    search || actionFilter
                      ? 'Try adjusting your search terms or filter selection.'
                      : 'No activity has been recorded yet.'
                  }
                />
              </div>
            ) : (
              <div className="divide-y">
                {clientFiltered.map((log, i) => {
                  const prefix = log.action.split('.')[0];
                  const color = actionColors[prefix] || actionColors.system;
                  return (
                    <motion.div
                      key={log.id}
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      transition={{ delay: i * 0.03 }}
                      className="flex items-start gap-3 px-4 py-3"
                    >
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-0.5">
                          <span className="text-sm font-medium">{log.userName}</span>
                          <Badge variant="outline" className={`text-[10px] ${color}`}>
                            {log.action}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {log.details || log.action}
                        </p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-xs text-muted-foreground">{timeAgo(log.createdAt)}</p>
                        <p className="text-[10px] text-muted-foreground/60">
                          {log.ipAddress || '—'}
                        </p>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>

      {data?.pagination?.hasMore && (
        <motion.div variants={item} className="text-center">
          <Button variant="outline" size="sm" disabled={isLoading} onClick={() => refetch()}>
            Load more
          </Button>
        </motion.div>
      )}
    </motion.div>
  );
}
