'use client';

import { useState, useMemo, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Search, MoreHorizontal, Pencil, Trash2, Filter, Download,
  Users, Building2, ListFilter, X, SlidersHorizontal, Check, Mail, Phone,
  Linkedin, ChevronDown, ChevronRight, ArrowUpDown, Loader2,
} from 'lucide-react';
import { toast } from 'sonner';
import { useContacts, useDeleteContact, useCreateContact } from '@/hooks/use-contacts';
import { useCompanies } from '@/hooks/use-companies';
import { useAuthStore } from '@/stores/auth-store';
import { useSearchStore } from '@/stores/search-store';
import { useDebounce } from '@/hooks/use-debounce';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardContent } from '@/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { StatusBadge } from '@/components/shared/status-badge';
import { EmptyState } from '@/components/shared/empty-state';
import { ErrorState } from '@/components/shared/error-state';
import type { Contact } from '@/types';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] as const } } };

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');

function ContactsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user } = useAuthStore();
  const {
    viewMode, setViewMode, searchName, searchCompany, searchEmail,
    searchLinkedin, searchPhone, searchTitle, setSearchField, clearSearch,
  } = useSearchStore();
  const isFree = user?.plan === 'FREE';

  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    firstName: '', lastName: '', email: '', phone: '', company: '', title: '', notes: '',
  });
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [sortField, setSortField] = useState<string>('createdAt');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  const debouncedName = useDebounce(searchName, 300);
  const debouncedCompany = useDebounce(searchCompany, 300);
  const debouncedEmail = useDebounce(searchEmail, 300);

  const { data, isLoading, isError, refetch } = useContacts({
    search: debouncedName || debouncedEmail || undefined,
    company: debouncedCompany || undefined,
    sortBy: sortField,
    sortDir,
    limit: 50,
  });

  const { data: companiesData } = useCompanies({ limit: 100 });

  const deleteMutation = useDeleteContact();
  const createMutation = useCreateContact();

  const contacts = data?.items ?? [];

  const groupedByCompany = useMemo(() => {
    const map = new Map<string, Contact[]>();
    contacts.forEach((c) => {
      const key = c.companyName || 'Unknown';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(c);
    });
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([company, contacts]) => ({ company, contacts }));
  }, [contacts]);

  const contactLetters = useMemo(() => {
    const letters = new Set<string>();
    contacts.forEach((c) => letters.add((c.name?.[0] || '').toUpperCase()));
    return ALPHABET.filter((l) => letters.has(l));
  }, [contacts]);

  const handleDelete = async () => {
    if (!deleteId) return;
    try {
      await deleteMutation.mutateAsync(deleteId);
      toast.success('Contact deleted');
      setDeleteId(null);
      setSelectedIds((prev) => { const next = new Set(prev); next.delete(deleteId); return next; });
    } catch { toast.error('Failed to delete'); }
  };

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === contacts.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(contacts.map((c) => c.id)));
    }
  };

  const hasActiveFilters = searchName || searchCompany || searchEmail || searchLinkedin || searchPhone || searchTitle;

  const searchInputs = [
    { key: 'searchName', placeholder: 'Name', icon: Users },
    { key: 'searchCompany', placeholder: 'Company', icon: Building2 },
    { key: 'searchEmail', placeholder: 'Email', icon: Mail },
    { key: 'searchLinkedin', placeholder: 'LinkedIn', icon: Linkedin },
    { key: 'searchPhone', placeholder: 'Phone', icon: Phone },
  ];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      {/* Header */}
      <motion.div variants={item} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Contacts</h2>
          <p className="text-muted-foreground">{contacts.length} contacts in directory</p>
        </div>
        <div className="flex gap-2">
          <motion.div whileTap={{ scale: 0.98 }}>
            <Button
              variant="outline" size="sm"
              disabled={isFree}
              onClick={() => isFree ? toast.info('Upgrade to Pro to export') : toast.success('Export started')}
              className="gap-1.5"
            >
              <Download className="h-4 w-4" />
              Export
              {isFree && <Badge variant="secondary" className="ml-1 text-[10px] px-1.5 h-4">PRO</Badge>}
            </Button>
          </motion.div>
          <motion.div whileTap={{ scale: 0.98 }}>
            <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
              <Plus className="h-4 w-4" />
              Add Contact
            </Button>
          </motion.div>
        </div>
      </motion.div>

      {/* View toggle + filters row */}
      <motion.div variants={item} className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <div className="flex rounded-md border bg-card p-0.5">
            <button
              onClick={() => setViewMode('company')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${viewMode === 'company' ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground'}`}
            >
              Company View
            </button>
            <button
              onClick={() => setViewMode('flat')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${viewMode === 'flat' ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground'}`}
            >
              All Profiles
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Quick search */}
          <div className="relative flex-1 sm:w-64">
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              placeholder="Quick search..."
              className="pl-8 h-8 text-xs"
              value={searchName}
              onChange={(e) => setSearchField('searchName', e.target.value)}
            />
          </div>
          <Button variant="outline" size="sm" className="h-8 gap-1.5 text-xs" onClick={() => setFilterOpen(!filterOpen)}>
            <SlidersHorizontal className="h-3.5 w-3.5" />
            Filters
            {hasActiveFilters && <span className="h-2 w-2 rounded-full bg-foreground" />}
          </Button>
        </div>
      </motion.div>

      {/* Expanded search fields */}
      <AnimatePresence>
        {filterOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <Card>
              <CardContent className="p-4">
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {searchInputs.map((input) => (
                    <div key={input.key} className="relative">
                      <input.icon className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
                      <Input
                        placeholder={input.placeholder}
                        className="pl-8 h-8 text-xs"
                        value={(input.key === 'searchName' ? searchName : input.key === 'searchCompany' ? searchCompany : input.key === 'searchEmail' ? searchEmail : input.key === 'searchLinkedin' ? searchLinkedin : input.key === 'searchPhone' ? searchPhone : searchTitle) || ''}
                        onChange={(e) => setSearchField(input.key, e.target.value)}
                      />
                    </div>
                  ))}
                  <div className="relative">
                    <Input
                      placeholder="Title / Role"
                      className="pl-8 h-8 text-xs"
                      value={searchTitle}
                      onChange={(e) => setSearchField('searchTitle', e.target.value)}
                    />
                  </div>
                </div>
                {hasActiveFilters && (
                  <div className="mt-3 flex items-center justify-between">
                    <p className="text-xs text-muted-foreground">Filters active</p>
                    <button onClick={clearSearch} className="text-xs text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1">
                      <X className="h-3 w-3" /> Clear all
                    </button>
                  </div>
                )}
              </CardContent>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Bulk action bar */}
      <AnimatePresence>
        {selectedIds.size > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className="flex items-center gap-3 rounded-lg border bg-card px-4 py-2"
          >
            <span className="text-sm font-medium">{selectedIds.size} selected</span>
            <div className="flex-1" />
            <Button variant="outline" size="sm" className="h-7 text-xs gap-1" onClick={() => toast.info('Add to List coming soon')}>Add to List</Button>
            <Button variant="outline" size="sm" className="h-7 text-xs gap-1" disabled={isFree} onClick={() => toast.info('Export coming soon')}>
              <Download className="h-3 w-3" /> Export
            </Button>
            <Button variant="destructive" size="sm" className="h-7 text-xs gap-1" onClick={() => {
              selectedIds.forEach(id => deleteMutation.mutate(id));
              toast.success(`${selectedIds.size} contacts deleted`);
              setSelectedIds(new Set());
            }}>
              <Trash2 className="h-3 w-3" /> Delete
            </Button>
            <button onClick={() => setSelectedIds(new Set())} className="text-muted-foreground hover:text-foreground">
              <X className="h-4 w-4" />
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Main content: A-Z rail + contacts */}
      <motion.div variants={item} className="flex gap-4">
        {/* A-Z rolodex rail */}
        {contactLetters.length > 0 && (
          <div className="hidden md:flex flex-col items-center gap-0.5 shrink-0">
            {ALPHABET.map((letter) => {
              const hasContacts = contactLetters.includes(letter);
              return (
                <button
                  key={letter}
                  className={`w-6 h-6 rounded text-[10px] font-medium flex items-center justify-center transition-colors ${
                    hasContacts ? 'text-foreground hover:bg-accent cursor-pointer' : 'text-muted-foreground/30 cursor-default'
                  }`}
                  onClick={() => hasContacts && document.getElementById(`letter-${letter}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                >
                  {letter}
                </button>
              );
            })}
          </div>
        )}

        <div className="flex-1 min-w-0">
          {isError ? (
            <ErrorState message="Failed to load contacts" onRetry={() => refetch()} />
          ) : isLoading ? (
            <Card>
              <CardContent className="p-6 space-y-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}</CardContent>
            </Card>
          ) : contacts.length === 0 ? (
            <EmptyState
              icon={Users}
              title="No contacts found"
              description={hasActiveFilters ? 'Try adjusting your filters or search terms.' : 'Add your first contact to get started.'}
              action={hasActiveFilters ? (
                <Button variant="outline" size="sm" onClick={clearSearch}>Clear Filters</Button>
              ) : (
                <Button size="sm" onClick={() => setCreateOpen(true)}><Plus className="mr-2 h-4 w-4" /> Add Contact</Button>
              )}
            />
          ) : viewMode === 'company' ? (
            <CompanyView grouped={groupedByCompany} onRowClick={(id) => router.push(`/contacts/${id}`)} selectedIds={selectedIds} toggleSelect={toggleSelect} />
          ) : (
            <FlatTable contacts={contacts} onRowClick={(id) => router.push(`/contacts/${id}`)} selectedIds={selectedIds} toggleSelect={toggleSelect} toggleSelectAll={toggleSelectAll} />
          )}
        </div>
      </motion.div>

      {/* Create Dialog */}
      <Dialog open={createOpen} onOpenChange={(open) => { if (!open) { setCreateOpen(false); setCreateForm({ firstName: '', lastName: '', email: '', phone: '', company: '', title: '', notes: '' }); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Add New Contact</DialogTitle>
            <DialogDescription>Fill in the details to create a new contact</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2"><Label>First Name</Label><Input placeholder="John" value={createForm.firstName} onChange={(e) => setCreateForm((f) => ({ ...f, firstName: e.target.value }))} /></div>
              <div className="space-y-2"><Label>Last Name</Label><Input placeholder="Doe" value={createForm.lastName} onChange={(e) => setCreateForm((f) => ({ ...f, lastName: e.target.value }))} /></div>
            </div>
            <div className="space-y-2"><Label>Email</Label><Input type="email" placeholder="john@company.com" value={createForm.email} onChange={(e) => setCreateForm((f) => ({ ...f, email: e.target.value }))} /></div>
            <div className="space-y-2"><Label>Phone</Label><Input placeholder="+1 (555) 000-0000" value={createForm.phone} onChange={(e) => setCreateForm((f) => ({ ...f, phone: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2"><Label>Company</Label><Input placeholder="Acme Inc." value={createForm.company} onChange={(e) => setCreateForm((f) => ({ ...f, company: e.target.value }))} /></div>
              <div className="space-y-2"><Label>Title</Label><Input placeholder="Software Engineer" value={createForm.title} onChange={(e) => setCreateForm((f) => ({ ...f, title: e.target.value }))} /></div>
            </div>
            <div className="space-y-2"><Label>Notes</Label><Textarea placeholder="Any additional notes..." value={createForm.notes} onChange={(e) => setCreateForm((f) => ({ ...f, notes: e.target.value }))} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)} disabled={createMutation.isPending}>Cancel</Button>
            <Button disabled={createMutation.isPending || !createForm.firstName || !createForm.lastName || !createForm.email} onClick={() => {
              createMutation.mutate({
                name: `${createForm.firstName} ${createForm.lastName}`.trim(),
                email: createForm.email,
                phone: createForm.phone || undefined,
                title: createForm.title || undefined,
                notes: createForm.notes || undefined,
              }, {
                onSuccess: () => {
                  toast.success('Contact created');
                  setCreateOpen(false);
                  setCreateForm({ firstName: '', lastName: '', email: '', phone: '', company: '', title: '', notes: '' });
                },
                onError: () => toast.error('Failed to create contact'),
              });
            }}>
              {createMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Create Contact
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Dialog */}
      <Dialog open={!!deleteId} onOpenChange={() => setDeleteId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Contact</DialogTitle>
            <DialogDescription>Are you sure? This action cannot be undone.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteId(null)}>Cancel</Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleteMutation.isPending}>
              {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </motion.div>
  );
}

function CompanyView({ grouped, onRowClick, selectedIds, toggleSelect }: {
  grouped: { company: string; contacts: Contact[] }[];
  onRowClick: (id: string) => void;
  selectedIds: Set<string>;
  toggleSelect: (id: string) => void;
}) {
  return (
    <div className="space-y-4">
      {grouped.map(({ company, contacts: cs }) => (
        <motion.div key={company} layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="rounded-lg border bg-card">
          <div className="flex items-center gap-3 px-4 py-3 border-b bg-muted/30">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-foreground text-background text-xs font-bold">
              {company.charAt(0)}
            </div>
            <span className="text-sm font-semibold">{company}</span>
            <Badge variant="secondary" className="text-xs">{cs.length}</Badge>
          </div>
          <div className="divide-y">
            {cs.map((contact) => (
              <motion.div
                key={contact.id}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                whileHover={{ backgroundColor: 'var(--accent)' }}
                className="flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors"
                onClick={() => onRowClick(contact.id)}
              >
                <Checkbox
                  checked={selectedIds.has(contact.id)}
                  onCheckedChange={() => toggleSelect(contact.id)}
                  onClick={(e) => e.stopPropagation()}
                  className="shrink-0"
                />
                <div className="flex h-7 w-7 items-center justify-center rounded-full bg-muted text-[11px] font-medium shrink-0">
                  {(contact.name || '?').split(' ').map(n=>n[0]).join('').slice(0,2)}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{contact.name}</p>
                  <p className="text-xs text-muted-foreground truncate">{contact.title || contact.email}</p>
                </div>
                <div className="hidden sm:flex items-center gap-2 text-xs text-muted-foreground">
                  <StatusBadge status={contact.status} size="sm" />
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>
      ))}
    </div>
  );
}

function FlatTable({ contacts, onRowClick, selectedIds, toggleSelect, toggleSelectAll }: {
  contacts: Contact[];
  onRowClick: (id: string) => void;
  selectedIds: Set<string>;
  toggleSelect: (id: string) => void;
  toggleSelectAll: () => void;
}) {
  return (
    <Card>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <Checkbox
                checked={contacts.length > 0 && selectedIds.size === contacts.length}
                onCheckedChange={toggleSelectAll}
              />
            </TableHead>
            <TableHead>
              <button className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
                Name <ArrowUpDown className="h-3 w-3" />
              </button>
            </TableHead>
            <TableHead className="hidden md:table-cell">Email</TableHead>
            <TableHead className="hidden lg:table-cell">Company</TableHead>
            <TableHead className="hidden lg:table-cell">Status</TableHead>
            <TableHead className="hidden sm:table-cell">Source</TableHead>
            <TableHead className="w-10"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {contacts.map((contact) => (
            <motion.tr
              key={contact.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              whileHover={{ backgroundColor: 'var(--accent)' }}
              className="cursor-pointer border-b border-border transition-colors"
              onClick={() => onRowClick(contact.id)}
            >
              <TableCell>
                <Checkbox
                  checked={selectedIds.has(contact.id)}
                  onCheckedChange={() => toggleSelect(contact.id)}
                  onClick={(e) => e.stopPropagation()}
                />
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-xs font-medium shrink-0">
                    {(contact.name || '?').split(' ').map(n=>n[0]).join('').slice(0,2)}
                  </div>
                  <div>
                    <p className="font-medium text-sm">{contact.name}</p>
                    <p className="text-xs text-muted-foreground md:hidden">{contact.email}</p>
                  </div>
                </div>
              </TableCell>
              <TableCell className="hidden md:table-cell text-muted-foreground text-sm">{contact.email}</TableCell>
              <TableCell className="hidden lg:table-cell text-muted-foreground text-sm">{contact.companyName || '—'}</TableCell>
              <TableCell className="hidden lg:table-cell"><StatusBadge status={contact.status} size="sm" /></TableCell>
              <TableCell className="hidden sm:table-cell text-muted-foreground text-sm">{contact.source || '—'}</TableCell>
              <TableCell>
                <button onClick={(e) => e.stopPropagation()} className="p-1 rounded hover:bg-accent">
                  <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
                </button>
              </TableCell>
            </motion.tr>
          ))}
        </TableBody>
      </Table>
    </Card>
  );
}

export default function ContactsPage() {
  return (
    <Suspense fallback={<div className="p-6"><Skeleton className="h-10 w-48" /><Skeleton className="h-64 w-full mt-4" /></div>}>
      <ContactsContent />
    </Suspense>
  );
}
