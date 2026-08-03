'use client';

import { motion } from 'framer-motion';
import '@/app/globals.css';

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4 relative overflow-hidden">
      {/* Subtle dot grid background */}
      <div className="absolute inset-0 bg-[radial-gradient(circle,_var(--border)_0.5px,transparent_0.5px)] bg-[size:24px_24px] opacity-30 [mask-image:radial-gradient(ellipse_80%_60%_at_50%_0%,#000_60%,transparent_100%)]" />
      
      {/* Top ambient glow */}
      <div className="absolute top-[-20%] left-1/2 -translate-x-1/2 w-[900px] h-[600px] bg-gradient-to-b from-foreground/[0.03] via-foreground/[0.015] to-transparent rounded-full blur-3xl pointer-events-none" />
      
      {/* Subtle side accents */}
      <div className="absolute top-1/4 -left-32 w-64 h-64 bg-foreground/[0.01] rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-32 w-64 h-64 bg-foreground/[0.01] rounded-full blur-3xl pointer-events-none" />

      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ 
          type: 'spring', 
          stiffness: 200, 
          damping: 24, 
          mass: 0.8 
        }}
        className="w-full max-w-[420px] relative z-10"
      >
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ 
            type: 'spring', 
            stiffness: 300, 
            damping: 25, 
            delay: 0.05 
          }}
          className="flex items-center justify-center gap-3 mb-10"
        >
          <div className="relative flex h-11 w-11 items-center justify-center rounded-[11px] bg-foreground text-background font-bold text-lg tracking-tighter shadow-lg shadow-foreground/10">
            TL
            <div className="absolute inset-0 rounded-[11px] ring-1 ring-inset ring-white/10" />
          </div>
          <span className="font-semibold text-[22px] tracking-tight text-foreground">
            TalentLedger
          </span>
        </motion.div>
        {children}
      </motion.div>
    </div>
  );
}
