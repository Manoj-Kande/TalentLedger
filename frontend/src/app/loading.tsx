'use client';

import { motion } from 'framer-motion';
import { PageSkeleton } from '@/components/shared/loading-skeleton';

export default function Loading() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex min-h-screen items-center justify-center bg-background p-4"
    >
      <div className="w-full max-w-2xl space-y-6">
        <PageSkeleton />
      </div>
    </motion.div>
  );
}
