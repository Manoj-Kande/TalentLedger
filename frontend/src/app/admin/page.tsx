'use client';

import { motion } from 'framer-motion';
import { Users, Database, Building2, Megaphone, HardDrive, TrendingUp } from 'lucide-react';
import { useAnalyticsOverview } from '@/hooks/use-admin';
import { Skeleton } from '@/components/ui/skeleton';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function AdminDashboardPage() {
  const { data: overview, isLoading } = useAnalyticsOverview();

  const stats = overview ? [
    { label: 'Total Users', value: overview.totalUsers.toLocaleString(), icon: Users, color: 'text-foreground' },
    { label: 'Total Contacts', value: overview.totalContacts.toLocaleString(), icon: Database, color: 'text-success' },
    { label: 'Total Dumps', value: overview.totalDumps.toLocaleString(), icon: HardDrive, color: 'text-info' },
    { label: 'Total Companies', value: overview.totalCompanies.toLocaleString(), icon: Building2, color: 'text-warning' },
    { label: 'Active Campaigns', value: overview.activeCampaigns.toLocaleString(), icon: Megaphone, color: 'text-chart-5' },
    { label: 'Storage Used', value: `${(overview.storageUsed / (1024 * 1024 * 1024)).toFixed(1)} GB`, icon: TrendingUp, color: 'text-destructive' },
  ] : [];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item}>
        <h2 className="text-2xl font-bold tracking-tight">Admin Dashboard</h2>
        <p className="text-muted-foreground">System overview and recent activity</p>
      </motion.div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-28 rounded-lg" />)}
        </div>
      ) : (
        <motion.div variants={item} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stats.map((stat) => (
            <motion.div
              key={stat.label}
              whileHover={{ y: -2, boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}
              className="rounded-lg border bg-card p-4"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-medium text-muted-foreground">{stat.label}</span>
                <stat.icon className={`h-4 w-4 ${stat.color}`} />
              </div>
              <p className="text-2xl font-bold tracking-tight">{stat.value}</p>
            </motion.div>
          ))}
        </motion.div>
      )}


    </motion.div>
  );
}
