'use client';

import { useEffect, useState, useCallback, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { useTheme } from 'next-themes';
import { motion, AnimatePresence } from 'framer-motion';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { useAuthStore } from '@/stores/auth-store';
import {
  Command,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandSeparator,
  CommandShortcut,
} from '@/components/ui/command';
import {
  LayoutDashboard,
  Users,
  Building2,
  Megaphone,
  Bookmark,
  Upload,
  Settings,
  Shield,
  UploadCloud,
  UserPlus,
  Building,
  Rocket,
  ListPlus,
  Moon,
  Sun,
  LogOut,
  Search,
  ArrowRight,
} from 'lucide-react';

const springIn = {
  type: 'spring' as const,
  stiffness: 500,
  damping: 34,
  mass: 0.8,
};

export interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const { user, logout } = useAuthStore();
  const [query, setQuery] = useState('');
  const mounted = useSyncExternalStore(() => () => {}, () => true, () => false);

  const isAdmin = user?.role === 'ADMIN';

  const navigate = useCallback(
    (href: string) => {
      router.push(href);
      onOpenChange(false);
    },
    [router, onOpenChange],
  );

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
    onOpenChange(false);
  }, [theme, setTheme, onOpenChange]);

  const handleSignOut = useCallback(() => {
    onOpenChange(false);
    logout();
  }, [logout, onOpenChange]);

  const searchContacts = useCallback(() => {
    if (query.trim()) {
      router.push(`/contacts?search=${encodeURIComponent(query.trim())}`);
      onOpenChange(false);
    }
  }, [query, router, onOpenChange]);

  // ⌘K / Ctrl+K global shortcut
  const onOpenChangeRef = onOpenChange;
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        onOpenChangeRef(true);
      }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onOpenChangeRef]);

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <AnimatePresence>
          {open && (
            <>
              <DialogPrimitive.Overlay asChild>
                <motion.div
                  key="cmd-overlay"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.2 }}
                  className="fixed inset-0 z-50 bg-black/60 backdrop-blur-[2px]"
                />
              </DialogPrimitive.Overlay>

              <DialogPrimitive.Content asChild>
                <motion.div
                  key="cmd-content"
                  initial={{ opacity: 0, scale: 0.96, y: -10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.96, y: -10 }}
                  transition={springIn}
                  className="fixed left-1/2 top-[15%] z-50 w-full max-w-[640px] -translate-x-1/2 overflow-hidden rounded-xl border border-border/60 bg-popover shadow-2xl shadow-black/25 focus:outline-none"
                >
                  <Command
                    shouldFilter={true}
                    className="[&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-2 [&_[cmdk-group-heading]]:text-[11px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-wider [&_[cmdk-group-heading]]:text-muted-foreground/70 [&_[cmdk-input-wrapper]_svg]:h-[18px] [&_[cmdk-input-wrapper]_svg]:w-[18px] [&_[cmdk-input]]:h-12 [&_[cmdk-input]]:text-[15px] [&_[cmdk-item]]:px-3 [&_[cmdk-item]]:py-2.5 [&_[cmdk-item]]:gap-3 [&_[cmdk-item]_svg]:h-[16px] [&_[cmdk-item]_svg]:w-[16px] [&_[cmdk-item]]:rounded-lg [&_[cmdk-item]]:cursor-pointer [&_[cmdk-item]]:data-[selected=true]:bg-accent/80 [&_[cmdk-item]]:data-[selected=true]:text-accent-foreground"
                    onValueChange={setQuery}
                  >
                    <div className="flex items-center border-b border-border/40 px-4">
                      <Search className="mr-3 h-[18px] w-[18px] shrink-0 text-muted-foreground/50" />
                      <CommandInput
                        placeholder="Type a command or search..."
                        className="h-12 border-0 px-0 shadow-none focus:ring-0"
                      />
                    </div>

                    <CommandList className="max-h-[340px] px-2 py-2">
                      <CommandEmpty>
                        <div className="flex flex-col items-center gap-2 py-6">
                          <p className="text-sm text-muted-foreground">No commands found.</p>
                          <button
                            onClick={searchContacts}
                            className="inline-flex items-center gap-2 rounded-lg bg-accent/50 px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-accent"
                          >
                            <Search className="h-3.5 w-3.5" />
                            Search contacts for &ldquo;{query}&rdquo;
                            <ArrowRight className="h-3 w-3 text-muted-foreground" />
                          </button>
                        </div>
                      </CommandEmpty>

                      <CommandGroup heading="Navigation">
                        <CommandItem onSelect={() => navigate('/dashboard')}>
                          <LayoutDashboard />
                          <span>Dashboard</span>
                          <CommandShortcut>⌘D</CommandShortcut>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/contacts')}>
                          <Users />
                          <span>Contacts</span>
                          <CommandShortcut>⌘C</CommandShortcut>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/companies')}>
                          <Building2 />
                          <span>Companies</span>
                          <CommandShortcut>⌘P</CommandShortcut>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/campaigns')}>
                          <Megaphone />
                          <span>Campaigns</span>
                          <CommandShortcut>⌘M</CommandShortcut>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/saved-lists')}>
                          <Bookmark />
                          <span>Saved Lists</span>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/dumps')}>
                          <Upload />
                          <span>Uploads</span>
                          <CommandShortcut>⌘U</CommandShortcut>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/settings')}>
                          <Settings />
                          <span>Settings</span>
                          <CommandShortcut>⌘,</CommandShortcut>
                        </CommandItem>
                        {isAdmin && (
                          <CommandItem onSelect={() => navigate('/admin')}>
                            <Shield />
                            <span>Admin Panel</span>
                            <CommandShortcut>⌘⇧A</CommandShortcut>
                          </CommandItem>
                        )}
                      </CommandGroup>

                      <CommandSeparator className="my-1" />

                      <CommandGroup heading="Actions">
                        <CommandItem onSelect={() => navigate('/dumps')} keywords={['upload', 'import', 'file', 'csv']}>
                          <UploadCloud />
                          <span>New Dump Upload</span>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/contacts')} keywords={['add', 'new', 'person']}>
                          <UserPlus />
                          <span>Create Contact</span>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/companies')} keywords={['add', 'new', 'org', 'business']}>
                          <Building />
                          <span>Create Company</span>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/campaigns')} keywords={['add', 'new', 'outreach', 'email']}>
                          <Rocket />
                          <span>Create Campaign</span>
                        </CommandItem>
                        <CommandItem onSelect={() => navigate('/saved-lists')} keywords={['add', 'new', 'segment', 'group']}>
                          <ListPlus />
                          <span>Create Saved List</span>
                        </CommandItem>

                        <CommandSeparator className="my-1" />

                        <CommandItem onSelect={toggleTheme} keywords={['dark', 'light', 'mode', 'appearance']}>
                          {mounted && theme === 'dark' ? <Sun /> : <Moon />}
                          <span>Toggle {mounted && theme === 'dark' ? 'Light' : 'Dark'} Mode</span>
                        </CommandItem>
                        <CommandItem onSelect={handleSignOut} keywords={['logout', 'signout', 'exit']}>
                          <LogOut />
                          <span>Sign Out</span>
                        </CommandItem>
                      </CommandGroup>

                      {query.trim().length > 0 && (
                        <>
                          <CommandSeparator className="my-1" />
                          <CommandGroup heading="Search">
                            <CommandItem onSelect={searchContacts}>
                              <Search />
                              <span>Search contacts for &ldquo;{query}&rdquo;</span>
                              <ArrowRight className="ml-auto h-3.5 w-3.5 text-muted-foreground/60" />
                            </CommandItem>
                          </CommandGroup>
                        </>
                      )}
                    </CommandList>

                    <div className="flex items-center justify-between border-t border-border/40 bg-muted/20 px-4 py-2">
                      <span className="text-[11px] text-muted-foreground/50">
                        <kbd className="font-mono">↑↓</kbd> Navigate
                      </span>
                      <div className="flex items-center gap-3 text-[11px] text-muted-foreground/50">
                        <span>
                          <kbd className="font-mono">↵</kbd> Select
                        </span>
                        <span>
                          <kbd className="font-mono">esc</kbd> Close
                        </span>
                      </div>
                    </div>
                  </Command>
                </motion.div>
              </DialogPrimitive.Content>
            </>
          )}
        </AnimatePresence>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
