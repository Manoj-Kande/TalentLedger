'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus, Search, MoreHorizontal, Pencil, Trash2, Play, Pause,
  Filter, Megaphone, Send, Mail, UserCheck, AlertCircle,
} from 'lucide-react';
import { toast } from 'sonner';
import { useCampaigns, useDeleteCampaign, useCreateCampaign, useTransitionCampaignStatus } from '@/hooks/use-campaigns';
import type { CampaignStatus } from '@/types';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Skeleton } from '@/components/ui/skeleton';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { StatusBadge } from '@/components/shared/status-badge';
import { EmptyState } from '@/components/shared/empty-state';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function CampaignsPage() {
  const router = useRouter();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [campaignName, setCampaignName] = useState('');
  const [campaignDesc, setCampaignDesc] = useState('');

  const { data, isLoading } = useCampaigns({ search: search || undefined });
  const deleteMutation = useDeleteCampaign();
  const createMutation = useCreateCampaign();
  const transitionMutation = useTransitionCampaignStatus();

  const campaigns = data ?? [];
  const filtered = statusFilter === 'all' ? campaigns : campaigns.filter((c) => c.status === statusFilter);

  const handleTransition = async (id: string, action: 'ACTIVATE' | 'PAUSE' | 'RESUME') => {
    try {
      await transitionMutation.mutateAsync({ id, action });
      toast.success(action === 'ACTIVATE' ? 'Campaign launched' : action === 'PAUSE' ? 'Campaign paused' : 'Campaign resumed');
    } catch {
      toast.error('Failed to update campaign status');
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    try { await deleteMutation.mutateAsync(deleteId); toast.success('Campaign deleted'); setDeleteId(null); }
    catch { toast.error('Failed to delete'); }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Campaigns</h2>
          <p className="text-muted-foreground">{filtered.length} campaigns</p>
        </div>
        <motion.div whileTap={{ scale: 0.98 }}>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
            <Plus className="h-4 w-4" /> New Campaign
          </Button>
        </motion.div>
      </motion.div>

      <motion.div variants={item} className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search campaigns..." className="pl-8 h-9 text-sm" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="flex items-center gap-1">
          {['all', 'DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED'].map((status) => (
            <motion.button
              key={status}
              whileTap={{ scale: 0.97 }}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
                statusFilter === status ? 'bg-foreground text-background border-foreground' : 'bg-card text-muted-foreground border-border hover:border-foreground/20'
              }`}
            >
              {status === 'all' ? 'All' : status.charAt(0) + status.slice(1).toLowerCase()}
            </motion.button>
          ))}
        </div>
      </motion.div>

      {isLoading ? (
        <div className="space-y-3">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-16 w-full rounded-lg" />)}</div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={Megaphone}
          title="No campaigns yet"
          description="Create your first outreach campaign."
          action={<Button size="sm" onClick={() => setCreateOpen(true)}><Plus className="mr-2 h-4 w-4" /> New Campaign</Button>}
        />
      ) : (
        <AnimatePresence>
          <div className="space-y-2">
            {filtered.map((campaign) => (
              <motion.div
                key={campaign.id}
                layout
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                whileHover={{ y: -1, boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}
                className="rounded-lg border bg-card p-4 cursor-pointer transition-colors"
                onClick={() => router.push(`/campaigns/${campaign.id}`)}
              >
                <div className="flex items-center gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <p className="text-sm font-semibold truncate">{campaign.name}</p>
                      <StatusBadge status={campaign.status} size="sm" />
                    </div>
                    <p className="text-xs text-muted-foreground truncate">{campaign.description || 'No description'}</p>
                  </div>
                  <div className="hidden sm:flex items-center gap-6 text-xs text-muted-foreground">
                    <div className="text-center">
                      <p className="font-semibold text-foreground">{campaign.sentCount}</p>
                      <p>Sent</p>
                    </div>
                    <div className="text-center">
                      <p className="font-semibold text-foreground">{campaign.replyCount}</p>
                      <p>Replied</p>
                    </div>
                    <div className="text-center">
                      <p className="font-semibold text-foreground">{campaign.bounceCount}</p>
                      <p>Bounced</p>
                    </div>
                  </div>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <button onClick={(e) => e.stopPropagation()} className="p-1 rounded hover:bg-accent">
                        <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); router.push(`/campaigns/${campaign.id}`); }}><Pencil className="mr-2 h-4 w-4" /> View</DropdownMenuItem>
                      {campaign.status === 'ACTIVE' && (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); handleTransition(campaign.id, 'PAUSE'); }}><Pause className="mr-2 h-4 w-4" /> Pause</DropdownMenuItem>
                      )}
                      {campaign.status === 'PAUSED' && (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); handleTransition(campaign.id, 'RESUME'); }}><Play className="mr-2 h-4 w-4" /> Resume</DropdownMenuItem>
                      )}
                      {campaign.status === 'DRAFT' && (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); handleTransition(campaign.id, 'ACTIVATE'); }}><Play className="mr-2 h-4 w-4" /> Launch</DropdownMenuItem>
                      )}
                      <DropdownMenuItem className="text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteId(campaign.id); }}><Trash2 className="mr-2 h-4 w-4" /> Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </motion.div>
            ))}
          </div>
        </AnimatePresence>
      )}

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Create Campaign</DialogTitle>
            <DialogDescription>Set up your new outreach campaign</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2"><Label>Campaign Name</Label><Input placeholder="Summer 2025 Outreach" value={campaignName} onChange={(e) => setCampaignName(e.target.value)} /></div>
            <div className="space-y-2"><Label>Description</Label><Textarea placeholder="Campaign goals..." value={campaignDesc} onChange={(e) => setCampaignDesc(e.target.value)} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>Cancel</Button>
            <Button onClick={() => { createMutation.mutate({ name: campaignName, description: campaignDesc || undefined }, { onSuccess: () => { toast.success('Campaign created'); setCreateOpen(false); setCampaignName(''); setCampaignDesc(''); }, onError: () => toast.error('Failed to create campaign') }); }} disabled={createMutation.isPending}>{createMutation.isPending ? 'Creating...' : 'Create Campaign'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!deleteId} onOpenChange={() => setDeleteId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Campaign</DialogTitle>
            <DialogDescription>Are you sure? This cannot be undone.</DialogDescription>
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
