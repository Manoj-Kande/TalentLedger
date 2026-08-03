'use client';

import { cn } from '@/lib/utils';

export function LoadingSkeleton({ className }: { className?: string }) {
  return <div className={cn('shimmer rounded-lg', className)} />;
}

export function CardSkeleton() {
  return (
    <div className="rounded-xl border bg-card p-6 space-y-4">
      <div className="flex items-center justify-between">
        <LoadingSkeleton className="h-4 w-1/3 rounded-md" />
        <LoadingSkeleton className="h-8 w-8 rounded-lg" />
      </div>
      <LoadingSkeleton className="h-3 w-2/3 rounded-md" />
      <LoadingSkeleton className="h-3 w-1/2 rounded-md" />
      <div className="pt-2">
        <LoadingSkeleton className="h-2 w-full rounded-md" />
      </div>
    </div>
  );
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex gap-4 items-center py-3">
          <LoadingSkeleton className="h-8 w-8 rounded-full" />
          <LoadingSkeleton className="h-4 w-32 rounded-md" />
          <LoadingSkeleton className="h-4 flex-1 rounded-md" />
          <LoadingSkeleton className="h-6 w-20 rounded-full" />
          <LoadingSkeleton className="h-4 w-24 rounded-md" />
        </div>
      ))}
    </div>
  );
}

export function TextSkeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('space-y-2', className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <LoadingSkeleton
          key={i}
          className={cn(
            'h-3 rounded-md',
            i === lines - 1 ? 'w-2/3' : 'w-full'
          )}
        />
      ))}
    </div>
  );
}

export function AvatarSkeleton() {
  return (
    <div className="flex items-center gap-3">
      <div className="shimmer h-10 w-10 rounded-full" />
      <div className="space-y-1.5">
        <LoadingSkeleton className="h-3 w-24 rounded-md" />
        <LoadingSkeleton className="h-2.5 w-16 rounded-md" />
      </div>
    </div>
  );
}

export function StatCardSkeleton() {
  return (
    <div className="rounded-xl border bg-card p-5 space-y-3">
      <div className="flex items-center justify-between">
        <LoadingSkeleton className="h-3 w-24 rounded-md" />
        <LoadingSkeleton className="h-4 w-4 rounded" />
      </div>
      <LoadingSkeleton className="h-7 w-16 rounded-md" />
      <LoadingSkeleton className="h-2.5 w-20 rounded-md" />
    </div>
  );
}

export function PageSkeleton() {
  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <LoadingSkeleton className="h-8 w-48 rounded-lg" />
        <LoadingSkeleton className="h-4 w-64 rounded-md" />
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <StatCardSkeleton key={i} />
        ))}
      </div>
      <div className="space-y-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-20 rounded-xl shimmer" />
        ))}
      </div>
    </div>
  );
}
