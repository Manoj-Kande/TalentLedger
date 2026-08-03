'use client';

import { useState, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import {
  Settings as SettingsIcon, CreditCard, Shield, Bell, User, Key,
  Smartphone, Check, Loader2, Trash2, Zap,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/auth-store';
import { useMe, useUpdateMe } from '@/hooks/use-admin';
import { apiClient } from '@/lib/api-client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { QuotaBar } from '@/components/shared/quota-bar';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } } };

const planDetails = {
  FREE: { name: 'Free', price: '$0', contacts: '1,000', storage: '100 MB', features: ['Basic contact management', 'CSV upload', '5 dumps/month'] },
  PRO: { name: 'Pro', price: '$29', contacts: '50,000', storage: '10 GB', features: ['Advanced filters & search', 'Export to CSV/Excel', 'Unlimited dumps', 'Campaign management', 'Priority support'] },
  TEAM: { name: 'Team', price: '$29', contacts: '50,000/user', storage: '100 MB', features: ['Everything in Pro', 'Shared lists & campaigns', '50K contacts/user', '100MB uploads', 'API access', 'Priority support'] },
  ENTERPRISE: { name: 'Enterprise', price: 'Custom', contacts: 'Unlimited', storage: 'Unlimited', features: ['Everything in Pro', 'SSO / SAML', 'API access', 'Dedicated support', 'Custom integrations'] },
};

function SettingsContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const tab = searchParams.get('tab') || 'account';
  const { user, setUser } = useAuthStore();

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item}>
        <h2 className="text-2xl font-bold tracking-tight">Settings</h2>
        <p className="text-muted-foreground">Manage your account and preferences</p>
      </motion.div>

      <motion.div variants={item}>
        <Tabs value={tab} onValueChange={(v) => router.push(`/settings?tab=${v}`)}>
          <TabsList>
            <TabsTrigger value="account"><User className="mr-2 h-4 w-4" />Account</TabsTrigger>
            <TabsTrigger value="billing"><CreditCard className="mr-2 h-4 w-4" />Billing</TabsTrigger>
            <TabsTrigger value="security"><Shield className="mr-2 h-4 w-4" />Security</TabsTrigger>
            <TabsTrigger value="notifications"><Bell className="mr-2 h-4 w-4" />Notifications</TabsTrigger>
          </TabsList>

          <div className="mt-6">
            <TabsContent value="account"><AccountTab /></TabsContent>
            <TabsContent value="billing"><BillingTab /></TabsContent>
            <TabsContent value="security"><SecurityTab /></TabsContent>
            <TabsContent value="notifications"><NotificationsTab /></TabsContent>
          </div>
        </Tabs>
      </motion.div>
    </motion.div>
  );
}

function AccountTab() {
  const { user, setUser } = useAuthStore();
  const { data: meData } = useMe();
  const updateMe = useUpdateMe();
  const [name, setName] = useState(user?.name || '');
  const [email, setEmail] = useState(user?.email || '');

  const handleSave = async () => {
    try {
      await updateMe.mutateAsync({ name, email });
      if (user) setUser({ ...user, name, email });
      toast.success('Profile updated');
    } catch { toast.error('Failed to update profile'); }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-4">
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
            <CardDescription>Update your personal information</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center gap-4">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-primary-foreground text-2xl font-bold">
                {user?.name?.charAt(0)?.toUpperCase() || 'U'}
              </div>
              <div>
                <p className="text-lg font-medium">{user?.name}</p>
                <Badge variant="secondary">{user?.role || 'MEMBER'}</Badge>
              </div>
            </div>
            <Separator />
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2"><Label>Full Name</Label><Input value={name} onChange={(e) => setName(e.target.value)} /></div>
              <div className="space-y-2"><Label>Email</Label><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
            </div>
            <div className="flex justify-end">
              <Button onClick={handleSave} disabled={updateMe.isPending}>
                {updateMe.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Saving...</> : 'Save Changes'}
              </Button>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}

function BillingTab() {
  const { user } = useAuthStore();
  const currentPlan = user?.plan || 'FREE';
  const plan = planDetails[currentPlan as keyof typeof planDetails] ?? planDetails.FREE;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-4">
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Zap className="h-5 w-5" /> Current Plan</CardTitle>
            <CardDescription>You&apos;re currently on the {plan.name} plan</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="flex-1">
                <p className="text-3xl font-bold">{plan.price}<span className="text-sm font-normal text-muted-foreground">/mo</span></p>
                <Badge variant="secondary" className="mt-1">{plan.name}</Badge>
              </div>
              <Button variant="outline" onClick={() => toast.info('Upgrade flow coming soon')}>
                Upgrade Plan
              </Button>
            </div>
            <Separator />
            <div className="grid gap-3 sm:grid-cols-3">
              <div><p className="text-xs text-muted-foreground">Contact Limit</p><p className="text-sm font-medium">{plan.contacts}</p></div>
              <div><p className="text-xs text-muted-foreground">Storage</p><p className="text-sm font-medium">{plan.storage}</p></div>
              <div><p className="text-xs text-muted-foreground">Status</p><p className="text-sm font-medium text-success">Active</p></div>
            </div>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle>Plan Features</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2">
              {plan.features.map((feature) => (
                <li key={feature} className="flex items-center gap-2 text-sm">
                  <Check className="h-4 w-4 text-success shrink-0" />
                  {feature}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}

function SecurityTab() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [changingPassword, setChangingPassword] = useState(false);

  const handleChangePassword = async () => {
    if (newPassword !== confirmPassword) { toast.error('Passwords do not match'); return; }
    if (newPassword.length < 8) { toast.error('Password must be at least 8 characters'); return; }
    setChangingPassword(true);
    try {
      await apiClient.post('/api/v1/me/password', { currentPassword, newPassword });
      toast.success('Password changed');
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch { toast.error('Failed to change password'); }
    finally { setChangingPassword(false); }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-4">
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Key className="h-5 w-5" /> Change Password</CardTitle>
            <CardDescription>Update your password to keep your account secure</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Current Password</Label>
              <Input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>New Password</Label>
              <Input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Confirm New Password</Label>
              <Input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
            </div>
            <div className="flex justify-end">
              <Button onClick={handleChangePassword} disabled={changingPassword}>{changingPassword ? 'Changing...' : 'Change Password'}</Button>
            </div>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Smartphone className="h-5 w-5" /> Two-Factor Authentication</CardTitle>
            <CardDescription>Add an extra layer of security to your account</CardDescription>
          </CardHeader>
          <CardContent className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">2FA is currently disabled</p>
              <p className="text-xs text-muted-foreground">Enable two-factor authentication for enhanced security</p>
            </div>
            <Button variant="outline" onClick={() => toast.info('2FA setup coming soon')}>Enable 2FA</Button>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle>Active Sessions</CardTitle>
            <CardDescription>Manage your active sessions</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between py-2">
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-muted"><Smartphone className="h-4 w-4" /></div>
                <div>
                  <p className="text-sm font-medium">Current Session</p>
                  <p className="text-xs text-muted-foreground">Chrome · macOS · Active now</p>
                </div>
              </div>
              <Badge variant="secondary" className="text-xs">Current</Badge>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}

function NotificationsTab() {
  const [settings, setSettings] = useState({
    emailDigest: true,
    uploadComplete: true,
    campaignUpdates: false,
    newContact: true,
    weeklyReport: true,
  });

  const toggles = [
    { key: 'emailDigest' as const, label: 'Email Digest', desc: 'Receive a daily summary of your activity' },
    { key: 'uploadComplete' as const, label: 'Upload Complete', desc: 'Get notified when a dump finishes parsing' },
    { key: 'campaignUpdates' as const, label: 'Campaign Updates', desc: 'Notifications about campaign performance' },
    { key: 'newContact' as const, label: 'New Contact Added', desc: 'Alert when contacts are added to your lists' },
    { key: 'weeklyReport' as const, label: 'Weekly Report', desc: 'Weekly summary of your pipeline metrics' },
  ];

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-4">
      <motion.div variants={item}>
        <Card>
          <CardHeader>
            <CardTitle>Notification Preferences</CardTitle>
            <CardDescription>Choose what notifications you receive</CardDescription>
          </CardHeader>
          <CardContent className="space-y-0">
            {toggles.map((toggle, i) => (
              <div key={toggle.key}>
                <div className="flex items-center justify-between py-4">
                  <div>
                    <p className="text-sm font-medium">{toggle.label}</p>
                    <p className="text-xs text-muted-foreground">{toggle.desc}</p>
                  </div>
                  <Switch
                    checked={settings[toggle.key]}
                    onCheckedChange={(v) => setSettings((s) => ({ ...s, [toggle.key]: v }))}
                  />
                </div>
                {i < toggles.length - 1 && <Separator />}
              </div>
            ))}
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}

export default function SettingsPage() {
  return (
    <Suspense fallback={<div className="p-6"><Skeleton className="h-8 w-48" /><Skeleton className="h-64 w-full mt-4" /></div>}>
      <SettingsContent />
    </Suspense>
  );
}
