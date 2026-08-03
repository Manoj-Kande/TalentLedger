'use client';

import { useRouter, useParams } from 'next/navigation';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Play, Pause, BarChart3, Send, Mail, UserCheck,
  AlertCircle, TrendingUp, Target,
} from 'lucide-react';
import { toast } from 'sonner';
import { useCampaign, useTransitionCampaignStatus } from '@/hooks/use-campaigns';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { StatusBadge } from '@/components/shared/status-badge';
import { ErrorState } from '@/components/shared/error-state';
import Link from 'next/link';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function CampaignDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = params.id as string;
  const { data: campaign, isLoading, isError } = useCampaign(id);
  const transitionMutation = useTransitionCampaignStatus();

  const handleTransition = async (action: 'ACTIVATE' | 'PAUSE' | 'RESUME') => {
    try {
      await transitionMutation.mutateAsync({ id, action });
      toast.success(action === 'ACTIVATE' ? 'Campaign launched' : action === 'PAUSE' ? 'Campaign paused' : 'Campaign resumed');
    } catch {
      toast.error('Failed to update campaign status');
    }
  };

  if (isLoading) {
    return <div className="space-y-6"><Skeleton className="h-8 w-48" /><div className="grid grid-cols-4 gap-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24" />)}</div></div>;
  }

  if (isError || !campaign) {
    return <ErrorState message="Campaign not found" onRetry={() => router.push('/campaigns')} />;
  }

  const replyRate = campaign.sentCount > 0 ? ((campaign.replyCount / campaign.sentCount) * 100).toFixed(1) : '0';
  const bounceRate = campaign.sentCount > 0 ? ((campaign.bounceCount / campaign.sentCount) * 100).toFixed(1) : '0';

  const metrics = [
    { label: 'Contacts', value: campaign.totalContacts, icon: UserCheck, color: 'text-info' },
    { label: 'Sent', value: campaign.sentCount, icon: Send, color: 'text-success' },
    { label: 'Replied', value: campaign.replyCount, icon: Mail, color: 'text-chart-5' },
    { label: 'Bounced', value: campaign.bounceCount, icon: AlertCircle, color: 'text-destructive' },
  ];

  const rates = [
    { label: 'Reply Rate', value: replyRate, color: 'bg-chart-5' },
    { label: 'Bounce Rate', value: bounceRate, color: bounceRate === '0' ? 'bg-muted' : 'bg-destructive' },
  ];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex items-center gap-4">
        <Link href="/campaigns">
          <Button variant="ghost" size="icon" className="h-8 w-8"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold truncate">{campaign.name}</h2>
            <StatusBadge status={campaign.status} />
          </div>
          <p className="text-sm text-muted-foreground">{campaign.description || 'No description'}</p>
        </div>
        <div className="flex gap-2 shrink-0">
          {campaign.status === 'DRAFT' && (
            <Button size="sm" onClick={() => handleTransition('ACTIVATE')} disabled={transitionMutation.isPending}><Play className="mr-2 h-3.5 w-3.5" /> Launch</Button>
          )}
          {campaign.status === 'ACTIVE' && (
            <Button variant="outline" size="sm" onClick={() => handleTransition('PAUSE')} disabled={transitionMutation.isPending}><Pause className="mr-2 h-3.5 w-3.5" /> Pause</Button>
          )}
          {campaign.status === 'PAUSED' && (
            <Button size="sm" onClick={() => handleTransition('RESUME')} disabled={transitionMutation.isPending}><Play className="mr-2 h-3.5 w-3.5" /> Resume</Button>
          )}
          <Button variant="outline" size="sm"><BarChart3 className="mr-2 h-3.5 w-3.5" /> Edit</Button>
        </div>
      </motion.div>

      {/* Metrics */}
      <motion.div variants={item} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {metrics.map((m) => (
          <motion.div
            key={m.label}
            whileHover={{ y: -2, boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}
            className="rounded-lg border bg-card p-4"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-muted-foreground">{m.label}</span>
              <m.icon className={`h-4 w-4 ${m.color}`} />
            </div>
            <p className="text-2xl font-bold tracking-tight">{m.value.toLocaleString()}</p>
          </motion.div>
        ))}
      </motion.div>

      {/* Rates */}
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
              <TrendingUp className="h-4 w-4" /> Performance Rates
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {rates.map((rate, i) => (
              <div key={rate.label}>
                <div className="flex items-center justify-between text-sm mb-1.5">
                  <span>{rate.label}</span>
                  <span className="font-bold">{rate.value}%</span>
                </div>
                <div className="h-2 rounded-full bg-muted overflow-hidden">
                  <motion.div
                    className={`h-full rounded-full ${rate.color}`}
                    initial={{ width: 0 }}
                    animate={{ width: `${Math.min(Number(rate.value), 100)}%` }}
                    transition={{ duration: 0.8, ease: 'easeOut', delay: i * 0.15 }}
                  />
                </div>
                {i < rates.length - 1 && <Separator className="mt-4" />}
              </div>
            ))}
          </CardContent>
        </Card>
      </motion.div>

      {/* Details */}
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Campaign Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Contacts</span>
              <span className="font-medium">{campaign.totalContacts}</span>
            </div>
            <Separator />
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Scheduled</span>
              <span className="font-medium">{campaign.scheduledAt ? new Date(campaign.scheduledAt).toLocaleDateString() : '—'}</span>
            </div>
            {campaign.completedAt && (
              <>
                <Separator />
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Completed</span>
                  <span className="font-medium">{new Date(campaign.completedAt).toLocaleDateString()}</span>
                </div>
              </>
            )}
            <Separator />
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Created</span>
              <span className="font-medium">{new Date(campaign.createdAt).toLocaleDateString()}</span>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}
