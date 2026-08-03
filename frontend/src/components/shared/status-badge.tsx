'use client';

import { cn } from '@/lib/utils';

interface StatusBadgeProps {
  status: string;
  className?: string;
  size?: 'sm' | 'md';
}

const cfg: Record<string, string> = {
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
  PARSING: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-400 dark:border-blue-800',
  COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800',
  FAILED: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/40 dark:text-red-400 dark:border-red-800',
  EXPIRED: 'bg-neutral-100 text-neutral-500 border-neutral-200 dark:bg-neutral-800/40 dark:text-neutral-400 dark:border-neutral-700',
  DRAFT: 'bg-neutral-100 text-neutral-500 border-neutral-200 dark:bg-neutral-800/40 dark:text-neutral-400 dark:border-neutral-700',
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800',
  PAUSED: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
  ARCHIVED: 'bg-neutral-100 text-neutral-500 border-neutral-200 dark:bg-neutral-800/40 dark:text-neutral-400 dark:border-neutral-700',
  PROCESSING: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-400 dark:border-blue-800',
  INACTIVE: 'bg-neutral-100 text-neutral-500 border-neutral-200 dark:bg-neutral-800/40 dark:text-neutral-400 dark:border-neutral-700',
  BOUNCED: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/40 dark:text-red-400 dark:border-red-800',
  UNSUBSCRIBED: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800',
};

const dots: Record<string, string> = {
  COMPLETED: 'bg-emerald-500',
  ACTIVE: 'bg-emerald-500',
  FAILED: 'bg-red-500',
  BOUNCED: 'bg-red-500',
  PARSING: 'bg-blue-500',
  PROCESSING: 'bg-blue-500',
  PENDING: 'bg-amber-500',
  PAUSED: 'bg-amber-500',
};

const pulseDots = new Set(['PARSING', 'PROCESSING']);

export function StatusBadge({ status, className, size = 'sm' }: StatusBadgeProps) {
  const c = cfg[status] || 'bg-neutral-100 text-neutral-500 border-neutral-200 dark:bg-neutral-800/40 dark:text-neutral-400 dark:border-neutral-700';
  const dot = dots[status] || 'bg-neutral-400';
  const shouldPulse = pulseDots.has(status);

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border font-medium',
        size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs',
        c,
        className
      )}
    >
      <span className="relative flex items-center justify-center mr-1.5">
        <span
          className={cn(
            'rounded-full',
            size === 'sm' ? 'h-1.5 w-1.5' : 'h-2 w-2',
            dot
          )}
        />
        {shouldPulse && (
          <span
            className={cn(
              'absolute rounded-full animate-ping',
              size === 'sm' ? 'h-1.5 w-1.5' : 'h-2 w-2',
              dot,
              'opacity-75'
            )}
          />
        )}
      </span>
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}
