'use client';

import { useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Menu, Bell, Moon, Sun, LogOut, Settings, User, Search } from 'lucide-react';
import { useTheme } from 'next-themes';
import { useAuthStore } from '@/stores/auth-store';
import { useUIStore } from '@/stores/ui-store';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { motion, AnimatePresence } from 'framer-motion';
import { useSyncExternalStore } from 'react';
import { CommandPalette } from '@/components/layout/command-palette';
import { useProfile } from '@/hooks/use-dashboard';

// ─── Spring presets ───────────────────────────────────────────────
const spring = {
  snappy:  { type: 'spring' as const, stiffness: 500, damping: 32 },
  gentle:  { type: 'spring' as const, stiffness: 300, damping: 28 },
};

// ─── Route title map ─────────────────────────────────────────────
const titles: Record<string, string> = {
  dashboard: 'Dashboard',
  contacts: 'Contacts',
  companies: 'Companies',
  campaigns: 'Campaigns',
  'saved-lists': 'Lists',
  settings: 'Settings',
  admin: 'Admin',
  dumps: 'Uploads',
};

// ─── Header Component ─────────────────────────────────────────────
export function Header() {
  const pathname = usePathname();
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const { user, logout } = useAuthStore();
  const { data: profile } = useProfile();
  const { setSidebarMobileOpen } = useUIStore();
  const mounted = useSyncExternalStore(() => () => {}, () => true, () => false);
  const [cmdOpen, setCmdOpen] = useState(false);

  // ── Breadcrumb generation ───────────────────────────────────────
  const segments = pathname.split('/').filter(Boolean);
  const baseSegment = segments[0];
  const pageTitle = titles[baseSegment] || 'TalentLedger';

  const breadcrumbItems = segments.map((seg, i) => ({
    label: (i === 0 ? titles[seg] : seg.charAt(0).toUpperCase() + seg.slice(1)) || seg,
    href: '/' + segments.slice(0, i + 1).join('/'),
    isLast: i === segments.length - 1,
  }));

  return (
    <>
    <header className="sticky top-0 z-20 flex h-12 items-center gap-4 border-b border-border/60 bg-background/80 backdrop-blur-xl px-4 md:px-6">
      {/* ── Mobile menu button ──────────────────────────────────── */}
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden h-8 w-8 text-muted-foreground hover:text-foreground"
        onClick={() => setSidebarMobileOpen(true)}
      >
        <Menu className="h-[18px] w-[18px]" />
      </Button>

      {/* ── Breadcrumb navigation ──────────────────────────────── */}
      <Breadcrumb className="hidden md:flex">
        <BreadcrumbList className="text-[13px]">
          <BreadcrumbItem>
            <BreadcrumbLink
              href="/dashboard"
              className="text-muted-foreground hover:text-foreground transition-colors"
            >
              Home
            </BreadcrumbLink>
          </BreadcrumbItem>
          {breadcrumbItems.map((item, i) => (
            <span key={i} className="contents">
              <BreadcrumbSeparator className="text-muted-foreground/40" />
              <BreadcrumbItem>
                {item.isLast ? (
                  <BreadcrumbPage className="font-medium text-foreground">
                    {item.label}
                  </BreadcrumbPage>
                ) : (
                  <BreadcrumbLink
                    href={item.href}
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {item.label}
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
            </span>
          ))}
        </BreadcrumbList>
      </Breadcrumb>

      {/* ── Command palette trigger ────────────────────────────── */}
      <button
        type="button"
        onClick={() => setCmdOpen(true)}
        className="hidden md:inline-flex h-8 items-center gap-2 rounded-lg border border-border/50 bg-muted/30 px-3 text-[13px] text-muted-foreground transition-all hover:bg-muted/60 hover:text-foreground hover:border-border"
      >
        <Search className="h-3.5 w-3.5" />
        <span>Search...</span>
        <kbd className="pointer-events-none ml-4 inline-flex h-5 select-none items-center gap-0.5 rounded border border-border/50 bg-background/80 px-1.5 font-mono text-[10px] font-medium text-muted-foreground/60">
          <span className="text-xs">⌘</span>K
        </kbd>
      </button>

      {/* ── Mobile search button ───────────────────────────── */}
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden h-8 w-8 text-muted-foreground hover:text-foreground"
        onClick={() => setCmdOpen(true)}
      >
        <Search className="h-[18px] w-[18px]" />
      </Button>

      {/* ── Spacer ─────────────────────────────────────────────── */}
      <div className="flex-1" />

      {/* ── Right actions ───────────────────────────────────────── */}
      <div className="flex items-center gap-0.5">
        {/* Theme toggle */}
        {mounted && (
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          >
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={theme}
                initial={{ rotate: -90, opacity: 0, scale: 0.8 }}
                animate={{ rotate: 0, opacity: 1, scale: 1 }}
                exit={{ rotate: 90, opacity: 0, scale: 0.8 }}
                transition={spring.snappy}
              >
                {theme === 'dark' ? (
                  <Sun className="h-4 w-4" />
                ) : (
                  <Moon className="h-4 w-4" />
                )}
              </motion.div>
            </AnimatePresence>
          </Button>
        )}

        {/* Notification bell */}
        <Button
          variant="ghost"
          size="icon"
          className="relative h-8 w-8 text-muted-foreground hover:text-foreground"
        >
          <Bell className="h-4 w-4" />
          <motion.span
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ ...spring.snappy, delay: 0.4 }}
            className="absolute top-[7px] right-[7px] h-2 w-2 rounded-full bg-warning ring-2 ring-background"
          />
        </Button>

        {/* Item #1: usage + Upgrade, left of profile */}
        {profile?.quotas && profile.plan === 'FREE' && (
          <>
            <div className="hidden sm:flex items-center gap-2 text-[12px] text-muted-foreground px-2">
              <span>
                {(profile.quotas.storageBytesUsed / (1024 * 1024)).toFixed(0)}MB /{' '}
                {(profile.quotas.storageBytesLimit / (1024 * 1024)).toFixed(0)}MB
              </span>
            </div>
            <Button
              size="sm"
              className="h-8 rounded-full px-3 text-[12px] font-medium"
              onClick={() => router.push('/settings?tab=billing')}
            >
              Upgrade
            </Button>
          </>
        )}

        {/* Separator dot */}
        <div className="mx-1 h-4 w-px bg-border" />

        {/* User dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              className="relative h-8 gap-2 rounded-full pl-1 pr-2 hover:bg-accent/60"
            >
              <Avatar className="h-7 w-7 ring-1 ring-border">
                <AvatarFallback className="text-[11px] font-medium bg-muted text-muted-foreground">
                  {user?.name?.charAt(0)?.toUpperCase() || 'U'}
                </AvatarFallback>
              </Avatar>
              <span className="hidden lg:block text-[13px] font-medium max-w-[120px] truncate">
                {user?.name || 'User'}
              </span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-56"
            align="end"
            forceMount
          >
            <DropdownMenuLabel className="font-normal">
              <div className="flex flex-col gap-1.5 py-1">
                <p className="text-sm font-medium leading-none">
                  {user?.name || 'User'}
                </p>
                <p className="text-xs text-muted-foreground leading-none">
                  {user?.email || ''}
                </p>
                <Badge
                  variant="secondary"
                  className="w-fit text-[10px] px-1.5 py-0 h-[18px] font-medium mt-1"
                >
                  {user?.role || 'MEMBER'}
                </Badge>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => router.push('/settings')}
              className="gap-2 text-[13px] cursor-pointer"
            >
              <User className="h-4 w-4" />
              Profile
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => router.push('/settings')}
              className="gap-2 text-[13px] cursor-pointer"
            >
              <Settings className="h-4 w-4" />
              Settings
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="gap-2 text-[13px] text-destructive focus:text-destructive cursor-pointer"
              onClick={() => {
                logout();
              }}
            >
              <LogOut className="h-4 w-4" />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>

      {/* ── Command Palette ──────────────────────────────────────── */}
      <CommandPalette open={cmdOpen} onOpenChange={setCmdOpen} />
    </>
  );
}
