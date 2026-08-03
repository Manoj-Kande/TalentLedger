'use client';

import { useRouter, useParams } from 'next/navigation';
import { motion } from 'framer-motion';
import { ArrowLeft, Users, Pencil, Trash2, UserPlus, Download } from 'lucide-react';
import { toast } from 'sonner';
import { useSavedList, useSavedListContacts, useDeleteSavedList } from '@/hooks/use-saved-lists';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { EmptyState } from '@/components/shared/empty-state';
import { ErrorState } from '@/components/shared/error-state';
import { StatusBadge } from '@/components/shared/status-badge';
import Link from 'next/link';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function SavedListDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = params.id as string;

  const { data: list, isLoading: listLoading } = useSavedList(id);
  const { data: contactsData, isLoading: contactsLoading } = useSavedListContacts(id, { limit: 50 });
  const deleteMutation = useDeleteSavedList();

  if (listLoading) {
    return (
      <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 rounded-lg" />
      </motion.div>
    );
  }

  if (!list) {
    return <ErrorState message="List not found" onRetry={() => router.push('/saved-lists')} />;
  }

  const contacts = contactsData?.items ?? [];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex items-center gap-4">
        <Link href="/saved-lists">
          <Button variant="ghost" size="icon" className="h-8 w-8"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold truncate">{list.name}</h2>
            <Badge variant={list.isPublic ? 'default' : 'secondary'}>{list.isPublic ? 'Public' : 'Private'}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">{list.description || 'No description'}</p>
        </div>
        <div className="flex gap-2 shrink-0">
          <Button variant="outline" size="sm" onClick={() => toast.info('Add contacts coming soon')}><UserPlus className="mr-2 h-3.5 w-3.5" /> Add</Button>
          <Button variant="outline" size="sm" onClick={() => toast.info('Export coming soon')}><Download className="mr-2 h-3.5 w-3.5" /> Export</Button>
          <Button variant="destructive" size="sm" onClick={async () => {
            try { await deleteMutation.mutateAsync(id); toast.success('List deleted'); router.push('/saved-lists'); }
            catch { toast.error('Failed to delete'); }
          }} disabled={deleteMutation.isPending}><Trash2 className="mr-2 h-3.5 w-3.5" /> Delete</Button>
        </div>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium">Contacts ({list.contactCount})</CardTitle>
          </CardHeader>
          <CardContent>
            {contactsLoading ? (
              <div className="space-y-3">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
            ) : contacts.length === 0 ? (
              <EmptyState icon={Users} title="No contacts in this list" description="Add contacts to this list to get started." />
            ) : (
              <div className="space-y-1">
                {contacts.map((contact) => (
                  <motion.div
                    key={contact.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    whileHover={{ backgroundColor: 'var(--accent)' }}
                    onClick={() => router.push(`/contacts/${contact.id}`)}
                    className="flex items-center gap-3 p-2.5 rounded-lg cursor-pointer transition-colors"
                  >
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-xs font-medium shrink-0">
                      {(contact.name || '?').split(' ').map(n=>n[0]).join('').slice(0,2)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{contact.name}</p>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        <span className="truncate">{contact.email}</span>
                        {contact.companyName && <span className="hidden sm:inline">· {contact.companyName}</span>}
                      </div>
                    </div>
                    <StatusBadge status={contact.status} size="sm" className="hidden sm:flex" />
                  </motion.div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}
