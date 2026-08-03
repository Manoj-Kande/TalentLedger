'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Search, MoreHorizontal, Pencil, Trash2, BookmarkPlus, Eye, Users,
} from 'lucide-react';
import { toast } from 'sonner';
import { useSavedLists, useDeleteSavedList, useCreateSavedList } from '@/hooks/use-saved-lists';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/shared/empty-state';
import { useForm } from 'react-hook-form';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function SavedListsPage() {
  const router = useRouter();
  const [search, setSearch] = useState('');
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const { data, isLoading } = useSavedLists({ search: search || undefined, limit: 20 });
  const deleteMutation = useDeleteSavedList();
  const createMutation = useCreateSavedList();

  const lists = data?.items ?? [];

  const handleDelete = async () => {
    if (!deleteId) return;
    try {
      await deleteMutation.mutateAsync(deleteId);
      toast.success('List deleted');
      setDeleteId(null);
    } catch { toast.error('Failed to delete list'); }
  };

  const handleCreate = async (formData: { name: string; description?: string; isPublic?: boolean }) => {
    try {
      await createMutation.mutateAsync({ ...formData, isPublic: formData.isPublic });
      toast.success('List created');
      setCreateOpen(false);
    } catch { toast.error('Failed to create list'); }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Saved Lists</h2>
          <p className="text-muted-foreground">{lists.length} lists</p>
        </div>
        <motion.div whileTap={{ scale: 0.98 }}>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
            <Plus className="h-4 w-4" /> New List
          </Button>
        </motion.div>
      </motion.div>

      <motion.div variants={item}>
        <div className="relative max-w-sm">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search lists..." className="pl-8 h-9 text-sm" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
      </motion.div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-lg" />)}
        </div>
      ) : lists.length === 0 ? (
        <EmptyState
          icon={BookmarkPlus}
          title="No saved lists yet"
          description="Create your first list to organize contacts."
          action={<Button size="sm" onClick={() => setCreateOpen(true)}><Plus className="mr-2 h-4 w-4" /> New List</Button>}
        />
      ) : (
        <AnimatePresence>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {lists.map((list) => (
              <motion.div
                key={list.id}
                layout
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                whileHover={{ y: -2, boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}
                whileTap={{ scale: 0.98 }}
                className="rounded-lg border bg-card p-4 cursor-pointer transition-colors"
                onClick={() => router.push(`/saved-lists/${list.id}`)}
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-muted">
                      <BookmarkPlus className="h-4 w-4 text-foreground" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold truncate">{list.name}</p>
                      <p className="text-xs text-muted-foreground">{list.description || 'No description'}</p>
                    </div>
                  </div>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <button onClick={(e) => e.stopPropagation()} className="p-1 rounded hover:bg-accent">
                        <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); router.push(`/saved-lists/${list.id}`); }}><Eye className="mr-2 h-4 w-4" /> View</DropdownMenuItem>
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); toast.info('Edit coming soon'); }}><Pencil className="mr-2 h-4 w-4" /> Edit</DropdownMenuItem>
                      <DropdownMenuItem className="text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteId(list.id); }}><Trash2 className="mr-2 h-4 w-4" /> Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

                <div className="flex items-center justify-between text-xs text-muted-foreground pt-3 border-t border-border/50">
                  <div className="flex items-center gap-1.5">
                    <Users className="h-3 w-3" />
                    <span>{list.contactCount} contacts</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={list.isPublic ? 'default' : 'secondary'} className="text-[10px]">
                      {list.isPublic ? 'Public' : 'Private'}
                    </Badge>
                    <span>{new Date(list.createdAt).toLocaleDateString()}</span>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </AnimatePresence>
      )}

      <CreateListDialog open={createOpen} onOpenChange={setCreateOpen} onSubmit={handleCreate} isLoading={createMutation.isPending} />

      <Dialog open={!!deleteId} onOpenChange={() => setDeleteId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete List</DialogTitle>
            <DialogDescription>Are you sure? Contacts will not be deleted.</DialogDescription>
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

function CreateListDialog({ open, onOpenChange, onSubmit, isLoading }: {
  open: boolean; onOpenChange: (open: boolean) => void; onSubmit: (data: { name: string; description?: string; isPublic?: boolean }) => void; isLoading: boolean;
}) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<{ name: string; description?: string }>();
  const [isPublic, setIsPublic] = useState(false);

  return (
    <Dialog open={open} onOpenChange={(v) => { onOpenChange(v); if (!v) reset(); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create New List</DialogTitle>
          <DialogDescription>Organize your contacts into a curated list</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit((data) => onSubmit({ ...data, isPublic }))} className="space-y-4">
          <div className="space-y-2">
            <Label>List Name</Label>
            <Input placeholder="e.g., Q4 Engineering Leads" {...register('name', { required: 'Name is required' })} />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>
          <div className="space-y-2">
            <Label>Description</Label>
            <Textarea placeholder="What is this list for?" {...register('description')} />
          </div>
          <div className="flex items-center justify-between">
            <div><Label>Public List</Label><p className="text-xs text-muted-foreground">Others can view this list</p></div>
            <Switch checked={isPublic} onCheckedChange={setIsPublic} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" disabled={isLoading}>{isLoading ? 'Creating...' : 'Create List'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
