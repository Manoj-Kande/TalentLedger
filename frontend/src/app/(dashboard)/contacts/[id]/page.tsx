'use client';

import { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { motion } from 'framer-motion';
import {
  ArrowLeft, Pencil, Trash2, Mail, Phone, Building2, Globe, MapPin,
  Tag, Linkedin, Briefcase, Plus, Clock, MessageSquare, Calendar,
} from 'lucide-react';
import { toast } from 'sonner';
import { useContact, useUpdateContact, useDeleteContact } from '@/hooks/use-contacts';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { StatusBadge } from '@/components/shared/status-badge';
import { ErrorState } from '@/components/shared/error-state';
import Link from 'next/link';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

const mockTimeline = [
  { id: '1', type: 'EMAIL', subject: 'Initial outreach', content: 'Sent intro email', status: 'OPENED', createdAt: '2025-01-15T10:00:00Z' },
  { id: '2', type: 'NOTE', subject: 'Call notes', content: 'Had a great call about partnership opportunities', createdAt: '2025-01-10T14:00:00Z' },
  { id: '3', type: 'LINKEDIN', subject: 'Connection request', content: 'Sent LinkedIn connection', status: 'ACCEPTED', createdAt: '2025-01-05T09:00:00Z' },
];

const timelineIcons: Record<string, React.ElementType> = {
  EMAIL: Mail, LINKEDIN: Linkedin, CALL: Phone, NOTE: MessageSquare,
};

export default function ContactDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = params.id as string;
  const [isEditing, setIsEditing] = useState(false);
  const [editNotes, setEditNotes] = useState('');

  const { data: contact, isLoading, isError } = useContact(id);
  const updateMutation = useUpdateContact();
  const deleteMutation = useDeleteContact();

  if (isLoading) {
    return (
      <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-5 gap-6">
          <div className="col-span-2 space-y-4">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-40 rounded-lg" />)}</div>
          <div className="col-span-3 space-y-4"><Skeleton className="h-64 rounded-lg" /></div>
        </div>
      </motion.div>
    );
  }

  if (isError || !contact) {
    return <ErrorState message="Contact not found" onRetry={() => router.push('/contacts')} />;
  }

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync(id);
      toast.success('Contact deleted');
      router.push('/contacts');
    } catch { toast.error('Failed to delete contact'); }
  };

  const handleSaveNotes = async () => {
    try {
      await updateMutation.mutateAsync({ id, data: { notes: editNotes } });
      toast.success('Notes saved');
      setIsEditing(false);
    } catch { toast.error('Failed to save notes'); }
  };

  const initials = (contact.name || '?').split(' ').map((n) => n[0]).join('').slice(0,2);

  const fields = [
    { label: 'Email', value: contact.email, icon: Mail },
    { label: 'Phone', value: contact.phone, icon: Phone },
    { label: 'Company', value: contact.companyName, icon: Building2, href: contact.companyId ? `/companies/${contact.companyId}` : undefined },
    { label: 'Title', value: contact.title, icon: Briefcase },
    { label: 'Location', value: contact.location, icon: MapPin },
    { label: 'LinkedIn', value: contact.linkedinUrl, icon: Linkedin },
    { label: 'Seniority', value: contact.seniorityLevel, icon: Briefcase },
  ].filter((f) => f.value);

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      {/* Header */}
      <motion.div variants={item} className="flex items-center gap-4">
        <Link href="/contacts">
          <Button variant="ghost" size="icon" className="h-8 w-8"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div className="flex items-center gap-4 flex-1 min-w-0">
          <motion.div
            whileHover={{ scale: 1.05 }}
            className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary text-primary-foreground text-lg font-bold shrink-0 shadow-lg"
          >
            {initials}
          </motion.div>
          <div className="min-w-0">
            <h2 className="text-xl font-bold truncate">{contact.name}</h2>
            <div className="flex items-center gap-2 text-muted-foreground">
              {contact.title && <span className="text-sm">{contact.title}</span>}
              {contact.companyName && (
                <>
                  {contact.title && <span className="text-sm">at</span>}
                  <span className="text-sm font-medium">{contact.companyName}</span>
                </>
              )}
            </div>
          </div>
          <StatusBadge status={contact.status} className="hidden sm:flex" />
        </div>
        <div className="flex gap-2 shrink-0">
          <motion.div whileTap={{ scale: 0.98 }}>
            <Button variant="outline" size="sm" onClick={() => toast.info('Edit functionality coming soon')} className="gap-1.5">
              <Pencil className="h-3.5 w-3.5" /> Edit
            </Button>
          </motion.div>
          <motion.div whileTap={{ scale: 0.98 }}>
            <Button variant="destructive" size="sm" onClick={handleDelete} disabled={deleteMutation.isPending} className="gap-1.5">
              <Trash2 className="h-3.5 w-3.5" /> Delete
            </Button>
          </motion.div>
        </div>
      </motion.div>

      {/* Two-column layout */}
      <div className="grid gap-6 lg:grid-cols-5">
        {/* Left column */}
        <div className="lg:col-span-2 space-y-4">
          <motion.div variants={item}>
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium text-muted-foreground">Contact Information</CardTitle>
              </CardHeader>
              <CardContent className="space-y-0">
                {fields.map((field, i) => (
                  <div key={field.label}>
                    <div className="flex items-start gap-3 py-3">
                      <field.icon className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" />
                      <div className="min-w-0 flex-1">
                        <p className="text-xs text-muted-foreground">{field.label}</p>
                        {field.href ? (
                          <Link href={field.href} className="text-sm font-medium hover:underline">{field.value}</Link>
                        ) : field.label === 'LinkedIn' ? (
                          <a href={field.value!.startsWith('http') ? field.value : `https://linkedin.com/in/${field.value}`} target="_blank" rel="noopener noreferrer" className="text-sm font-medium hover:underline">{field.value}</a>
                        ) : (
                          <p className="text-sm font-medium break-all">{field.value}</p>
                        )}
                      </div>
                    </div>
                    {i < fields.length - 1 && <Separator />}
                  </div>
                ))}
              </CardContent>
            </Card>
          </motion.div>

          {/* Tags */}
          {contact.tags && contact.tags.length > 0 && (
            <motion.div variants={item}>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                    <Tag className="h-4 w-4" /> Tags
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="flex flex-wrap gap-2">
                    {contact.tags.map((tag) => (
                      <Badge key={tag} variant="outline" className="text-xs">{tag}</Badge>
                    ))}
                    <button className="inline-flex items-center gap-1 rounded-md border border-dashed px-2 py-0.5 text-xs text-muted-foreground hover:border-foreground/30 hover:text-foreground transition-colors">
                      <Plus className="h-3 w-3" /> Add
                    </button>
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          )}

          {/* Meta */}
          <motion.div variants={item}>
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium text-muted-foreground">Details</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Status</span>
                  <StatusBadge status={contact.status} size="sm" />
                </div>
                <Separator />
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Source</span>
                  <span className="font-medium">{contact.source || '—'}</span>
                </div>
                <Separator />
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Created</span>
                  <span className="font-medium">{new Date(contact.createdAt).toLocaleDateString()}</span>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </div>

        {/* Right column */}
        <div className="lg:col-span-3 space-y-4">
          {/* Notes */}
          <motion.div variants={item}>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between">
                <CardTitle className="text-sm font-medium text-muted-foreground">Notes</CardTitle>
                <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => { setIsEditing(!isEditing); setEditNotes(contact.notes || ''); }}>
                  {isEditing ? 'Cancel' : 'Edit'}
                </Button>
              </CardHeader>
              <CardContent>
                {isEditing ? (
                  <div className="space-y-3">
                    <Textarea value={editNotes} onChange={(e) => setEditNotes(e.target.value)} placeholder="Add notes..." rows={4} />
                    <div className="flex justify-end">
                      <Button size="sm" onClick={handleSaveNotes} disabled={updateMutation.isPending}>
                        {updateMutation.isPending ? 'Saving...' : 'Save Notes'}
                      </Button>
                    </div>
                  </div>
                ) : (
                  <p className="text-sm leading-relaxed text-muted-foreground">{contact.notes || 'No notes yet. Click Edit to add notes about this contact.'}</p>
                )}
              </CardContent>
            </Card>
          </motion.div>

          {/* Outreach Timeline */}
          <motion.div variants={item}>
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                  <Clock className="h-4 w-4" /> Outreach Timeline
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="relative space-y-4">
                  {mockTimeline.map((event, i) => {
                    const Icon = timelineIcons[event.type] || MessageSquare;
                    return (
                      <motion.div
                        key={event.id}
                        initial={{ opacity: 0, x: -8 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: i * 0.1 }}
                        className="flex gap-3"
                      >
                        <div className="flex flex-col items-center">
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted">
                            <Icon className="h-4 w-4 text-muted-foreground" />
                          </div>
                          {i < mockTimeline.length - 1 && <div className="w-px flex-1 bg-border mt-1" />}
                        </div>
                        <div className="flex-1 pb-4">
                          <div className="flex items-center gap-2 mb-0.5">
                            <span className="text-sm font-medium">{event.subject}</span>
                            {event.status && <Badge variant="secondary" className="text-[10px]">{event.status}</Badge>}
                          </div>
                          <p className="text-xs text-muted-foreground">{event.content}</p>
                          <p className="text-[10px] text-muted-foreground mt-1">
                            {new Date(event.createdAt).toLocaleDateString()} at {new Date(event.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </p>
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </div>
      </div>
    </motion.div>
  );
}
