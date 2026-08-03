'use client';

import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import { AlertTriangle } from 'lucide-react';

interface Props {
  used: number;
  limit: number;
  label?: string;
  className?: string;
  showValue?: boolean;
}

export function QuotaBar({ used, limit, label, className, showValue = true }: Props) {
  const pct = limit > 0 ? Math.min((used / limit) * 100, 100) : 0;
  const isWarning = pct >= 90;
  const isCaution = pct >= 70 && !isWarning;

  const gradientClass = isWarning
    ? 'bg-gradient-to-r from-red-500 to-red-400'
    : isCaution
      ? 'bg-gradient-to-r from-amber-500 to-amber-400'
      : 'bg-gradient-to-r from-emerald-500 to-emerald-400';

  return (
    <div className={cn('space-y-2', className)}>
      {label && (
        <div className="flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5">
            <span className="text-muted-foreground">{label}</span>
            {isWarning && (
              <AlertTriangle className="h-3 w-3 text-red-500" />
            )}
          </div>
          {showValue && (
            <span
              className={cn(
                'font-semibold tabular-nums',
                isWarning ? 'text-red-600 dark:text-red-400' : 'text-foreground'
              )}
            >
              {used.toLocaleString()} / {limit.toLocaleString()}
            </span>
          )}
        </div>
      )}
      <div className="h-2 w-full rounded-full bg-muted overflow-hidden ring-1 ring-inset ring-border">
        <motion.div
          className={cn('h-full rounded-full', gradientClass)}
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{
            type: 'spring',
            stiffness: 120,
            damping: 20,
            mass: 0.8,
          }}
        />
      </div>
    </div>
  );
}
