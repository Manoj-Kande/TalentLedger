'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuthStore } from '@/stores/auth-store';
import { Skeleton } from '@/components/ui/skeleton';
import { motion } from 'framer-motion';

const emptySubscribe = () => () => {};

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, isAuthenticated, isInitialized, checkAuth } = useAuthStore();
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  useEffect(() => {
    if (mounted && isInitialized) {
      if (!isAuthenticated) {
        router.replace('/login');
      } else if (user?.role !== 'ADMIN') {
        router.replace('/dashboard');
      }
    }
  }, [isAuthenticated, isInitialized, user, mounted, router]);

  if (!mounted || !isInitialized || !isAuthenticated || !user) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 rounded-md bg-foreground/10 animate-pulse" />
          <Skeleton className="h-3 w-24" />
        </div>
      </div>
    );
  }

  const navItems = [
    { label: 'Overview', href: '/admin' },
    { label: 'Users', href: '/admin/users' },
    { label: 'Dumps', href: '/admin/dumps' },
    { label: 'Audit Log', href: '/admin/audit' },
    { label: 'Config', href: '/admin/configs' },
  ];

  return (
    <div className="flex min-h-screen bg-background">
      <div className="flex flex-1 flex-col min-w-0">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-4 border-b bg-background/80 backdrop-blur-md px-4 md:px-8">
          <div className="flex items-center gap-3">
            <div className="flex h-7 w-7 items-center justify-center rounded-md bg-foreground text-background font-bold text-xs tracking-tight">
              TL
            </div>
            <span className="font-semibold text-sm tracking-tight">Admin</span>
          </div>
          <nav className="flex items-center gap-1 ml-6">
            {navItems.map((item) => {
              const active = pathname === item.href;
              return (
                <motion.button
                  key={item.href}
                  whileTap={{ scale: 0.98 }}
                  onClick={() => router.push(item.href)}
                  className={`relative px-3 py-1.5 text-[13px] font-medium rounded-md transition-colors ${
                    active
                      ? 'text-foreground'
                      : 'text-muted-foreground hover:text-foreground'
                  }`}
                >
                  {active && (
                    <motion.div
                      layoutId="admin-nav-active"
                      className="absolute inset-0 bg-secondary rounded-md"
                      transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                    />
                  )}
                  <span className="relative z-10">{item.label}</span>
                </motion.button>
              );
            })}
          </nav>
          <div className="flex-1" />
          <button
            onClick={() => router.push('/dashboard')}
            className="text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            ← Back to App
          </button>
        </header>
        <main className="flex-1 overflow-auto p-4 md:p-6 lg:p-8">
          {children}
        </main>
      </div>
    </div>
  );
}
