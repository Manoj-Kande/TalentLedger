'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard, Users, Building2, Megaphone, BookmarkPlus,
  Settings, Shield, LogOut, ChevronLeft, ChevronRight, X,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/stores/auth-store';
import { useUIStore } from '@/stores/ui-store';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  Tooltip, TooltipContent, TooltipProvider, TooltipTrigger,
} from '@/components/ui/tooltip';
import { motion, AnimatePresence } from 'framer-motion';

// ─── Spring presets ───────────────────────────────────────────────
const spring = {
  snappy:  { type: 'spring' as const, stiffness: 500, damping: 35 },
  smooth:  { type: 'spring' as const, stiffness: 350, damping: 30 },
  gentle:  { type: 'spring' as const, stiffness: 300, damping: 32 },
  bouncy:  { type: 'spring' as const, stiffness: 400, damping: 25 },
  slide:   { type: 'spring' as const, stiffness: 320, damping: 28 },
  width:   { type: 'spring' as const, stiffness: 420, damping: 36 },
  overlay: { type: 'spring' as const, stiffness: 260, damping: 32 },
};

// ─── Types ────────────────────────────────────────────────────────
interface NavItem {
  label: string;
  href: string;
  icon: React.ElementType;
  adminOnly?: boolean;
}

// ─── Nav structure ─────────────────────────────────────────────────
const sections: { title: string; items: NavItem[] }[] = [
  {
    title: 'Workspace',
    items: [
      { label: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
    ],
  },
  {
    title: 'Data',
    items: [
      { label: 'Contacts', href: '/contacts', icon: Users },
      { label: 'Companies', href: '/companies', icon: Building2 },
      { label: 'Saved Lists', href: '/saved-lists', icon: BookmarkPlus },
    ],
  },
  {
    title: 'Outreach',
    items: [
      { label: 'Campaigns', href: '/campaigns', icon: Megaphone },
    ],
  },
  {
    title: 'Admin',
    items: [
      { label: 'Admin Panel', href: '/admin', icon: Shield, adminOnly: true },
      { label: 'Settings', href: '/settings', icon: Settings },
    ],
  },
];

// ─── Active indicator with layoutId ────────────────────────────────
function ActiveIndicator() {
  return (
    <motion.div
      layoutId="sidebar-active-indicator"
      className="absolute left-0 top-1/2 -translate-y-1/2 h-[18px] w-[3px] rounded-r-full bg-foreground"
      transition={spring.snappy}
    />
  );
}

// ─── Sidebar Component ────────────────────────────────────────────
export function Sidebar() {
  const pathname = usePathname();
  const { user } = useAuthStore();
  const { sidebarCollapsed, toggleSidebar, sidebarMobileOpen, setSidebarMobileOpen } = useUIStore();
  const isAdmin = user?.role === 'ADMIN';

  const isActive = (href: string) =>
    pathname === href || (href !== '/dashboard' && pathname.startsWith(href));

  // ── Nav link renderer ─────────────────────────────────────────────
  const renderNavLink = (item: NavItem, active: boolean) => {
    const Icon = item.icon;
    const link = (
      <Link
        href={item.href}
        onClick={() => setSidebarMobileOpen(false)}
        className={cn(
          'group relative flex items-center gap-3 rounded-lg px-3 py-[7px] text-[13px] font-medium transition-colors duration-200',
          sidebarCollapsed && 'justify-center px-2',
          active
            ? 'bg-sidebar-accent text-sidebar-accent-foreground'
            : 'text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground',
        )}
      >
        {/* Active left-bar indicator */}
        {active && !sidebarCollapsed && <ActiveIndicator />}

        {/* Icon */}
        <Icon
          className={cn(
            'h-[18px] w-[18px] shrink-0 transition-colors duration-200',
            active ? 'text-sidebar-accent-foreground' : 'text-sidebar-foreground/50 group-hover:text-sidebar-foreground/80',
          )}
        />

        {/* Label */}
        {!sidebarCollapsed && (
          <span className="truncate select-none">{item.label}</span>
        )}
      </Link>
    );

    // Wrap in tooltip when collapsed
    if (sidebarCollapsed) {
      return (
        <Tooltip key={item.href} delayDuration={0}>
          <TooltipTrigger asChild>{link}</TooltipTrigger>
          <TooltipContent
            side="right"
            sideOffset={10}
            className="text-xs font-medium bg-popover border shadow-md"
          >
            {item.label}
          </TooltipContent>
        </Tooltip>
      );
    }

    return <div key={item.href}>{link}</div>;
  };

  // ── Main nav content (shared between desktop & mobile) ──────────
  const navContent = (
    <div className="flex h-full flex-col">
      {/* ── Logo area ──────────────────────────────────────────── */}
      <div
        className={cn(
          'flex h-12 items-center border-b border-sidebar-border px-3',
          sidebarCollapsed ? 'justify-center' : 'gap-3',
        )}
      >
        <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-foreground text-background font-bold text-[11px] tracking-tight shadow-sm">
          TL
        </div>
        <AnimatePresence initial={false}>
          {!sidebarCollapsed && (
            <motion.span
              key="brand-text"
              initial={{ opacity: 0, x: -4 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -4 }}
              transition={spring.snappy}
              className="font-semibold text-[13px] tracking-tight whitespace-nowrap overflow-hidden select-none"
            >
              TalentLedger
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {/* ── Navigation sections ─────────────────────────────────── */}
      <ScrollArea className="flex-1 py-4 scrollbar-thin">
        <TooltipProvider>
          <nav className="flex flex-col gap-6 px-3">
            {sections.map((section) => {
              const filtered = section.items.filter((i) => !i.adminOnly || isAdmin);
              if (filtered.length === 0) return null;

              return (
                <div key={section.title}>
                  {/* Section header */}
                  <AnimatePresence initial={false}>
                    {!sidebarCollapsed && (
                      <motion.p
                        key={`section-${section.title}`}
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        exit={{ opacity: 0, height: 0 }}
                        transition={spring.snappy}
                        className="mb-2 px-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-sidebar-foreground/40 select-none overflow-hidden"
                      >
                        {section.title}
                      </motion.p>
                    )}
                  </AnimatePresence>

                  {/* Nav items */}
                  <div className="space-y-0.5">
                    {filtered.map((item) => renderNavLink(item, isActive(item.href)))}
                  </div>
                </div>
              );
            })}
          </nav>
        </TooltipProvider>
      </ScrollArea>

      {/* ── User profile footer ────────────────────────────────── */}
      <Separator className="bg-sidebar-border" />
      <div className="p-3">
        <div
          className={cn(
            'flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors hover:bg-sidebar-accent/40 cursor-pointer',
            sidebarCollapsed && 'justify-center px-0',
          )}
        >
          <Avatar className="h-7 w-7 shrink-0 ring-1 ring-sidebar-border">
            <AvatarFallback className="text-[11px] font-medium bg-muted text-muted-foreground">
              {user?.name?.charAt(0)?.toUpperCase() || 'U'}
            </AvatarFallback>
          </Avatar>
          <AnimatePresence initial={false}>
            {!sidebarCollapsed && (
              <motion.div
                key="user-info"
                initial={{ opacity: 0, x: -4 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -4 }}
                transition={spring.snappy}
                className="flex-1 min-w-0 overflow-hidden"
              >
                <p className="text-[13px] font-medium truncate leading-tight">
                  {user?.name || 'User'}
                </p>
                <Badge
                  variant="secondary"
                  className="mt-0.5 text-[9px] px-1.5 py-0 h-[18px] font-medium leading-4"
                >
                  {user?.role || 'MEMBER'}
                </Badge>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );

  // ── Return: Desktop + Mobile sidebar ────────────────────────────
  return (
    <>
      {/* ── Mobile sidebar overlay ─────────────────────────────── */}
      <AnimatePresence>
        {sidebarMobileOpen && (
          <>
            <motion.div
              key="mobile-overlay"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[4px] md:hidden"
              onClick={() => setSidebarMobileOpen(false)}
            />
            <motion.aside
              key="mobile-sidebar"
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={spring.slide}
              className="fixed inset-y-0 left-0 z-50 w-[280px] bg-sidebar border-r border-sidebar-border md:hidden shadow-2xl"
            >
              {/* Close button */}
              <button
                onClick={() => setSidebarMobileOpen(false)}
                className="absolute top-3 right-3 z-10 flex h-7 w-7 items-center justify-center rounded-md text-sidebar-foreground/60 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
              >
                <X className="h-4 w-4" />
              </button>
              {navContent}
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* ── Desktop sidebar ────────────────────────────────────── */}
      <motion.aside
        animate={{ width: sidebarCollapsed ? 56 : 240 }}
        transition={spring.width}
        className="hidden md:flex flex-col h-screen sticky top-0 bg-sidebar border-r border-sidebar-border shrink-0 overflow-hidden z-10"
      >
        {navContent}
      </motion.aside>

      {/* ── Collapse toggle button ─────────────────────────────── */}
      <motion.button
        layout
        onClick={toggleSidebar}
        transition={spring.width}
        className="hidden md:flex fixed bottom-6 z-30 h-7 w-7 items-center justify-center rounded-full border border-border bg-card text-muted-foreground shadow-sm hover:bg-accent hover:text-accent-foreground transition-colors"
        style={{ left: sidebarCollapsed ? 'calc(56px + 12px)' : 'calc(240px + 12px)' }}
      >
        <motion.div
          animate={{ rotate: sidebarCollapsed ? 0 : 180 }}
          transition={spring.snappy}
        >
          <ChevronLeft className="h-3.5 w-3.5" />
        </motion.div>
      </motion.button>
    </>
  );
}
