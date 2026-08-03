'use client';

import { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Search, MoreHorizontal, Pencil, Trash2, Building2, Globe,
  MapPin, Users, Filter, Loader2,
} from 'lucide-react';
import { toast } from 'sonner';
import { useCompanies, useDeleteCompany, useCreateCompany } from '@/hooks/use-companies';
import { useDebounce } from '@/hooks/use-debounce';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Card } from '@/components/ui/card';
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
import { EmptyState } from '@/components/shared/empty-state';
import { ErrorState } from '@/components/shared/error-state';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] as const } } };
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');

export default function CompaniesPage() {
  const router = useRouter();
  const [search, setSearch] = useState('');
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [viewMode, setViewMode] = useState<'list' | 'cards'>('list');

  const debouncedSearch = useDebounce(search, 300);
  const { data, isLoading, isError, refetch } = useCompanies({ search: debouncedSearch || undefined, limit: 50 });
  const deleteMutation = useDeleteCompany();
  const createMutation = useCreateCompany();
  const [createForm, setCreateForm] = useState({
    displayName: '', domain: '', industry: '', size: '', website: '', location: '', description: '',
  });

  const companies = data?.items ?? [];

  const companyLetters = useMemo(() => {
    const letters = new Set<string>();
    companies.forEach((c) => letters.add((c.displayName[0] || '').toUpperCase()));
    return ALPHABET.filter((l) => letters.has(l));
  }, [companies]);

  const handleDelete = async () => {
    if (!deleteId) return;
    try {
      await deleteMutation.mutateAsync(deleteId);
      toast.success('Company deleted');
      setDeleteId(null);
    } catch { toast.error('Failed to delete company'); }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Companies</h2>
          <p className="text-muted-foreground">{companies.length} companies in directory</p>
        </div>
        <motion.div whileTap={{ scale: 0.98 }}>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
            <Plus className="h-4 w-4" /> Add Company
          </Button>
        </motion.div>
      </motion.div>

      <motion.div variants={item} className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search companies..." className="pl-8 h-9 text-sm" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="hidden md:flex flex-col items-center gap-0.5 shrink-0">
          <div className="flex">
            {ALPHABET.map((letter) => {
              const hasCompanies = companyLetters.includes(letter);
              return (
                <button
                  key={letter}
                  className={`w-5 h-5 rounded text-[9px] font-medium flex items-center justify-center transition-colors ${
                    hasCompanies ? 'text-foreground hover:bg-accent cursor-pointer' : 'text-muted-foreground/30 cursor-default'
                  }`}
                  onClick={() => hasCompanies && document.getElementById(`comp-${letter}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                >
                  {letter}
                </button>
              );
            })}
          </div>
        </div>
      </motion.div>

      {isError ? (
        <ErrorState message="Failed to load companies" onRetry={() => refetch()} />
      ) : isLoading ? (
        <div className="space-y-3">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-16 w-full rounded-lg" />)}</div>
      ) : companies.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="No companies found"
          description="Add your first company or adjust your search."
          action={<Button size="sm" onClick={() => setCreateOpen(true)}><Plus className="mr-2 h-4 w-4" /> Add Company</Button>}
        />
      ) : (
        <AnimatePresence>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {companies.map((company) => (
              <motion.div
                key={company.id}
                layout
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                whileHover={{ y: -2, boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}
                whileTap={{ scale: 0.98 }}
                className="rounded-lg border bg-card p-4 cursor-pointer transition-colors"
                onClick={() => router.push(`/companies/${company.id}`)}
                id={`comp-${company.displayName[0]?.toUpperCase() || ''}`}
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-foreground text-background text-sm font-bold">
                      {company.displayName.charAt(0).toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold truncate">{company.displayName}</p>
                      {company.domain && <p className="text-xs text-muted-foreground truncate">{company.domain}</p>}
                    </div>
                  </div>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <button onClick={(e) => e.stopPropagation()} className="p-1 rounded hover:bg-accent">
                        <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); router.push(`/companies/${company.id}`); }}><Pencil className="mr-2 h-4 w-4" /> Edit</DropdownMenuItem>
                      <DropdownMenuItem className="text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteId(company.id); }}><Trash2 className="mr-2 h-4 w-4" /> Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

                <div className="flex flex-wrap gap-1.5 mb-3">
                  {company.industry && <Badge variant="outline" className="text-[10px]">{company.industry}</Badge>}
                  {company.size && <Badge variant="secondary" className="text-[10px]">{company.size}</Badge>}
                  {company.category && <Badge variant="secondary" className="text-[10px]">{company.category}</Badge>}
                </div>

                <div className="flex items-center justify-between text-xs text-muted-foreground pt-2 border-t border-border/50">
                  <div className="flex items-center gap-1">
                    <Users className="h-3 w-3" />
                    <span>{company.contactCount} contacts</span>
                  </div>
                  <span>{new Date(company.createdAt).toLocaleDateString()}</span>
                </div>
              </motion.div>
            ))}
          </div>
        </AnimatePresence>
      )}

      {/* Create Dialog */}
      <Dialog open={createOpen} onOpenChange={(open) => { if (!open) { setCreateOpen(false); setCreateForm({ displayName: '', domain: '', industry: '', size: '', website: '', location: '', description: '' }); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Add New Company</DialogTitle>
            <DialogDescription>Fill in the company details</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2"><Label>Company Name</Label><Input placeholder="Acme Inc." value={createForm.displayName} onChange={(e) => setCreateForm((f) => ({ ...f, displayName: e.target.value }))} /></div>
            <div className="space-y-2"><Label>Domain</Label><Input placeholder="acme.com" value={createForm.domain} onChange={(e) => setCreateForm((f) => ({ ...f, domain: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2"><Label>Industry</Label><Input placeholder="Technology" value={createForm.industry} onChange={(e) => setCreateForm((f) => ({ ...f, industry: e.target.value }))} /></div>
              <div className="space-y-2"><Label>Size</Label><Input placeholder="1-50" value={createForm.size} onChange={(e) => setCreateForm((f) => ({ ...f, size: e.target.value }))} /></div>
            </div>
            <div className="space-y-2"><Label>Website</Label><Input placeholder="https://acme.com" value={createForm.website} onChange={(e) => setCreateForm((f) => ({ ...f, website: e.target.value }))} /></div>
            <div className="space-y-2"><Label>Location</Label><Input placeholder="San Francisco, CA" value={createForm.location} onChange={(e) => setCreateForm((f) => ({ ...f, location: e.target.value }))} /></div>
            <div className="space-y-2"><Label>Description</Label><Textarea placeholder="Brief company description..." value={createForm.description} onChange={(e) => setCreateForm((f) => ({ ...f, description: e.target.value }))} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)} disabled={createMutation.isPending}>Cancel</Button>
            <Button disabled={createMutation.isPending || !createForm.displayName} onClick={() => {
              createMutation.mutate(createForm, {
                onSuccess: () => {
                  toast.success('Company created');
                  setCreateOpen(false);
                  setCreateForm({ displayName: '', domain: '', industry: '', size: '', website: '', location: '', description: '' });
                },
                onError: () => toast.error('Failed to create company'),
              });
            }}>
              {createMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Create Company
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!deleteId} onOpenChange={() => setDeleteId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Company</DialogTitle>
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
