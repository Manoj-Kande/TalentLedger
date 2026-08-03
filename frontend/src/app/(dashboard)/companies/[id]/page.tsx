'use client';

import { useRouter, useParams } from 'next/navigation';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Pencil, Trash2, Globe, MapPin, ExternalLink,
  Users, Building2, Plus,
} from 'lucide-react';
import { toast } from 'sonner';
import { useCompany, useDeleteCompany } from '@/hooks/use-companies';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { EmptyState } from '@/components/shared/empty-state';
import { ErrorState } from '@/components/shared/error-state';
import Link from 'next/link';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

export default function CompanyDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = params.id as string;

  const { data: company, isLoading, isError } = useCompany(id);
  const deleteMutation = useDeleteCompany();

  if (isLoading) {
    return (
      <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-5 gap-6">
          <div className="col-span-2 space-y-4">{Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-48 rounded-lg" />)}</div>
          <div className="col-span-3"><Skeleton className="h-64 rounded-lg" /></div>
        </div>
      </motion.div>
    );
  }

  if (isError || !company) {
    return <ErrorState message="Company not found" onRetry={() => router.push('/companies')} />;
  }

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync(id);
      toast.success('Company deleted');
      router.push('/companies');
    } catch { toast.error('Failed to delete company'); }
  };

  const contacts = company?.contacts ?? [];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item} className="flex items-center gap-4">
        <Link href="/companies">
          <Button variant="ghost" size="icon" className="h-8 w-8"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div className="flex items-center gap-4 flex-1 min-w-0">
          <motion.div whileHover={{ scale: 1.05 }} className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground text-xl font-bold shrink-0 shadow-lg">
            {company.displayName.charAt(0).toUpperCase()}
          </motion.div>
          <div className="min-w-0">
            <h2 className="text-xl font-bold truncate">{company.displayName}</h2>
            <div className="flex items-center gap-2 flex-wrap">
              {company.industry && <Badge variant="outline">{company.industry}</Badge>}
              {company.size && <span className="text-sm text-muted-foreground">{company.size}</span>}
              <span className="text-sm text-muted-foreground">·</span>
              <span className="text-sm text-muted-foreground">{company.contactCount} contacts</span>
            </div>
          </div>
        </div>
        <div className="flex gap-2 shrink-0">
          <Button variant="outline" size="sm" onClick={() => toast.info('Edit coming soon')}><Pencil className="mr-2 h-3.5 w-3.5" /> Edit</Button>
          <Button variant="destructive" size="sm" onClick={handleDelete} disabled={deleteMutation.isPending}><Trash2 className="mr-2 h-3.5 w-3.5" /> Delete</Button>
        </div>
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-5">
        {/* Left: Info */}
        <div className="lg:col-span-2 space-y-4">
          <motion.div variants={item}>
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium text-muted-foreground">Company Info</CardTitle>
              </CardHeader>
              <CardContent className="space-y-0">
                {company.domain && (
                  <>
                    <div className="flex items-start gap-3 py-3">
                      <Globe className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" />
                      <div><p className="text-xs text-muted-foreground">Domain</p><p className="text-sm font-medium">{company.domain}</p></div>
                    </div>
                    <Separator />
                  </>
                )}
                {company.website && (
                  <>
                    <div className="flex items-start gap-3 py-3">
                      <ExternalLink className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" />
                      <div><p className="text-xs text-muted-foreground">Website</p><a href={company.website} target="_blank" rel="noopener noreferrer" className="text-sm font-medium hover:underline">{company.website}</a></div>
                    </div>
                    <Separator />
                  </>
                )}
                {company.location && (
                  <>
                    <div className="flex items-start gap-3 py-3">
                      <MapPin className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" />
                      <div><p className="text-xs text-muted-foreground">Location</p><p className="text-sm font-medium">{company.location}</p></div>
                    </div>
                    <Separator />
                  </>
                )}
                <div className="flex justify-between py-3">
                  <span className="text-xs text-muted-foreground">Category</span>
                  <span className="text-sm font-medium">{company.category || '—'}</span>
                </div>
                <Separator />
                <div className="flex justify-between py-3">
                  <span className="text-xs text-muted-foreground">Created</span>
                  <span className="text-sm font-medium">{new Date(company.createdAt).toLocaleDateString()}</span>
                </div>
              </CardContent>
            </Card>
          </motion.div>

          <motion.div variants={item}>
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium text-muted-foreground">Description</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm leading-relaxed text-muted-foreground">{company.description || 'No description available.'}</p>
              </CardContent>
            </Card>
          </motion.div>
        </div>

        {/* Right: Contacts */}
        <div className="lg:col-span-3">
          <motion.div variants={item}>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between">
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Users className="h-4 w-4" /> Contacts ({company.contactCount})
                </CardTitle>
                <Button variant="outline" size="sm" className="h-7 text-xs gap-1" onClick={() => toast.info('Add contact coming soon')}>
                  <Plus className="h-3 w-3" /> Add
                </Button>
              </CardHeader>
              <CardContent>
                {contacts.length === 0 ? (
                  <EmptyState icon={Users} title="No contacts yet" description="Add contacts to this company." />
                ) : (
                  <div className="space-y-1">
                    {contacts.map((contact) => (
                      <motion.div
                        key={contact.id}
                        whileHover={{ backgroundColor: 'var(--accent)' }}
                        onClick={() => router.push(`/contacts/${contact.id}`)}
                        className="flex items-center gap-3 p-2.5 rounded-lg cursor-pointer transition-colors"
                      >
                        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-xs font-medium shrink-0">
                          {(contact.name || '?').split(' ').map(n=>n[0]).join('').slice(0,2)}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">{contact.name}</p>
                          <p className="text-xs text-muted-foreground truncate">{contact.title || contact.email}</p>
                        </div>
                        <span className="text-xs text-muted-foreground hidden sm:block">{contact.email}</span>
                      </motion.div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </motion.div>
        </div>
      </div>
    </motion.div>
  );
}
