'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';
import { useAuthStore } from '@/stores/auth-store';
import { Skeleton } from '@/components/ui/skeleton';
import { motion } from 'framer-motion';

// ─── Spring presets ───────────────────────────────────────────────
const spring = {
  gentle:  { type: 'spring' as const, stiffness: 260, damping: 24 },
};

const emptySubscribe = () => () => {};

// ─── Loading screen with TL logo pulse ────────────────────────────
function LoadingScreen() {
  return (
    <div className="flex h-screen items-center justify-center bg-background">
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={spring.gentle}
        className="flex flex-col items-center gap-4"
      >
        {/* TL logo with pulse glow */}
        <div className="relative">
          <motion.div
            animate={{
              boxShadow: [
                '0 0 0 0 rgba(0,0,0,0.00)',
                '0 0 0 8px rgba(0,0,0,0.04)',
                '0 0 0 16px rgba(0,0,0,0.00)',
              ],
            }}
            transition={{
              duration: 2,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
            className="flex h-10 w-10 items-center justify-center rounded-xl bg-foreground text-background font-bold text-base tracking-tight"
          >
            TL
          </motion.div>
        </div>

        {/* Brand text skeleton */}
        <div className="flex flex-col items-center gap-2">
          <Skeleton className="h-3.5 w-24 rounded-full" />
          <Skeleton className="h-2.5 w-16 rounded-full" />
        </div>
      </motion.div>
    </div>
  );
}

// ─── Dashboard Shell ──────────────────────────────────────────────
export function DashboardShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, isInitialized, checkAuth, startGuestSession } = useAuthStore();
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  // Item #1: no account yet? Start a free, ephemeral guest session instead of
  // redirecting to /login — they can upload/explore immediately; saving
  // permanently or full CRUD still requires a real account (enforced
  // server-side by ContactService's FREE-plan read-only gate).
  useEffect(() => {
    if (mounted && isInitialized && !isAuthenticated) {
      startGuestSession();
    }
  }, [isAuthenticated, isInitialized, mounted, startGuestSession]);

  // ── Auth loading gate ───────────────────────────────────────────
  if (!mounted || !isInitialized || !isAuthenticated) {
    return <LoadingScreen />;
  }

  // ── Main layout ─────────────────────────────────────────────────
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />
      <div className="flex flex-1 flex-col min-w-0">
        <Header />
        <main className="flex-1 overflow-auto">
          <div className="p-4 md:p-6 lg:p-8">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
