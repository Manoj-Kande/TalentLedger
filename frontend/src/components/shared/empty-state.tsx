'use client';

import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import type { LucideIcon } from 'lucide-react';

interface Props {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08,
      delayChildren: 0.05,
    },
  },
};

const childVariants = {
  hidden: { opacity: 0, y: 12 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      type: 'spring' as const,
      stiffness: 260,
      damping: 24,
    },
  },
};

export function EmptyState({ icon: Icon, title, description, action, className }: Props) {
  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className={cn(
        'flex flex-col items-center justify-center py-20 px-4 text-center',
        className
      )}
    >
      <motion.div variants={childVariants}>
        <div className="relative mb-8">
          <div className="absolute -inset-3 rounded-full bg-muted/40" />
          <div className="absolute -inset-1.5 rounded-full bg-muted/60" />
          <div className="relative flex h-16 w-16 items-center justify-center rounded-full bg-muted">
            <Icon className="h-7 w-7 text-muted-foreground" strokeWidth={1.5} />
          </div>
        </div>
      </motion.div>

      <motion.h3
        variants={childVariants}
        className="text-base font-semibold tracking-tight text-foreground"
      >
        {title}
      </motion.h3>

      {description && (
        <motion.p
          variants={childVariants}
          className="mt-2 text-sm leading-relaxed text-muted-foreground max-w-sm"
        >
          {description}
        </motion.p>
      )}

      {action && (
        <motion.div variants={childVariants} className="mt-6">
          {action}
        </motion.div>
      )}
    </motion.div>
  );
}
