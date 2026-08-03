'use client';

import { motion } from 'framer-motion';
import { AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface Props {
  message?: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorState({ message = 'Something went wrong.', onRetry, className }: Props) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 260, damping: 24 }}
      className={cn('flex flex-col items-center justify-center py-20 px-4 text-center', className)}
    >
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 320, damping: 20, delay: 0.05 }}
        className="relative mb-6"
      >
        <div className="absolute -inset-3 rounded-full bg-red-500/5" />
        <div className="relative flex h-16 w-16 items-center justify-center rounded-full bg-red-50 dark:bg-red-950/40 ring-1 ring-red-200 dark:ring-red-800">
          <AlertCircle className="h-7 w-7 text-red-500" strokeWidth={1.5} />
        </div>
      </motion.div>

      <motion.h3
        initial={{ opacity: 0, y: 4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="text-base font-semibold text-foreground"
      >
        Something went wrong
      </motion.h3>

      <motion.p
        initial={{ opacity: 0, y: 4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.15 }}
        className="mt-2 text-sm text-muted-foreground max-w-sm"
      >
        {message}
      </motion.p>

      {onRetry && (
        <motion.div
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="mt-6"
        >
          <Button variant="outline" size="sm" onClick={onRetry} className="rounded-lg">
            Try again
          </Button>
        </motion.div>
      )}
    </motion.div>
  );
}
