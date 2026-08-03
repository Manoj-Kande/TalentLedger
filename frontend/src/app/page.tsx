'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { useAuthStore } from '@/stores/auth-store';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

const emptySubscribe = () => () => {};

// Landing page when not logged in
function LandingPage() {
  const router = useRouter();

  const features = [
    { icon: '📊', title: 'Upload & Parse', desc: 'Drop CSV, XLSX, or JSON files. We auto-detect columns and parse contacts instantly.' },
    { icon: '👥', title: 'Smart Directory', desc: 'Search by name, company, email, or LinkedIn. A-Z rolodex with company grouping views.' },
    { icon: '🏢', title: 'Company Intelligence', desc: 'Auto-enrich companies with industry, size, domain, and aggregate contact data.' },
    { icon: '📣', title: 'Outreach Campaigns', desc: 'Track open rates, reply rates, and bounces across your email campaigns.' },
    { icon: '📋', title: 'Saved Lists', desc: 'Organize contacts into curated lists. Share with your team or keep private.' },
    { icon: '🔒', title: 'Secure & Fast', desc: 'Enterprise-grade security with role-based access and fast, responsive UI.' },
  ];

  const [selectedPlan, setSelectedPlan] = useState('Pro');

  const plans = [
    { name: 'Free', price: '$0', period: '/mo', features: ['1,000 contacts', '100 MB storage', '5 dumps/month', 'Basic search', 'CSV upload'], cta: 'Get Started', popular: false },
    { name: 'Pro', price: '$29', period: '/mo', features: ['50,000 contacts', '10 GB storage', 'Unlimited dumps', 'Advanced filters', 'Export CSV/Excel', 'Campaign tracking', 'Priority support'], cta: 'Start Free Trial', popular: true },
    { name: 'Enterprise', price: 'Custom', period: '', features: ['Unlimited contacts', 'Unlimited storage', 'SSO / SAML', 'API access', 'Dedicated support', 'Custom integrations', 'SLA guarantee'], cta: 'Contact Sales', popular: false },
  ];

  const handlePlanCta = (planName: string) => {
    if (planName === 'Enterprise') {
      window.location.href = 'mailto:sales@talentledger.com?subject=Enterprise%20plan%20inquiry';
      return;
    }
    router.push(`/register?plan=${planName.toLowerCase()}`);
  };

  const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.08 } } };
  const item = { hidden: { opacity: 0, y: 20 }, show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.25, 0.4, 0.25, 1] as const } } };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="min-h-screen">
      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-[linear-gradient(to_right,var(--border)_1px,transparent_1px),linear-gradient(to_bottom,var(--border)_1px,transparent_1px)] bg-[size:4rem_4rem] opacity-40 [mask-image:radial-gradient(ellipse_80%_50%_at_50%_0%,#000_70%,transparent_110%)]" />
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[600px] bg-gradient-to-b from-primary/5 to-transparent rounded-full blur-3xl pointer-events-none" />

        <div className="relative max-w-5xl mx-auto px-4 pt-24 pb-20 text-center">
          <motion.div variants={item} className="flex items-center justify-center gap-3 mb-8">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-foreground text-background font-bold text-xl tracking-tight shadow-xl">
              TL
            </div>
            <span className="text-3xl font-bold tracking-tight">TalentLedger</span>
          </motion.div>

          <motion.h1 variants={item} className="text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight max-w-3xl mx-auto leading-[1.1]">
            Your contact data,<br />organized and intelligent.
          </motion.h1>

          <motion.p variants={item} className="mt-6 text-lg text-muted-foreground max-w-xl mx-auto leading-relaxed">
            Upload CSV files, auto-parse contacts, build company intelligence, and manage your entire talent pipeline — all in one beautiful platform.
          </motion.p>

          <motion.div variants={item} className="mt-10 flex items-center justify-center gap-3">
            <motion.div whileTap={{ scale: 0.98 }}>
              <Button size="lg" className="gap-2" onClick={() => router.push('/register')}>
                Get Started Free
              </Button>
            </motion.div>
            <motion.div whileTap={{ scale: 0.98 }}>
              <Button variant="outline" size="lg" className="gap-2" onClick={() => router.push('/login')}>
                Sign In
              </Button>
            </motion.div>
          </motion.div>
        </div>
      </section>

      {/* Features */}
      <section className="max-w-5xl mx-auto px-4 pb-20">
        <motion.div variants={item} className="text-center mb-12">
          <h2 className="text-2xl font-bold tracking-tight">Everything you need</h2>
          <p className="text-muted-foreground mt-2">A complete contact intelligence platform built for modern teams.</p>
        </motion.div>

        <motion.div variants={item} className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature) => (
            <motion.div
              key={feature.title}
              whileHover={{ y: -2, boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}
              className="rounded-lg border bg-card p-6 transition-shadow"
            >
              <div className="text-2xl mb-3">{feature.icon}</div>
              <h3 className="text-sm font-semibold mb-1">{feature.title}</h3>
              <p className="text-sm text-muted-foreground leading-relaxed">{feature.desc}</p>
            </motion.div>
          ))}
        </motion.div>
      </section>

      {/* Pricing */}
      <section className="max-w-5xl mx-auto px-4 pb-24">
        <motion.div variants={item} className="text-center mb-12">
          <h2 className="text-2xl font-bold tracking-tight">Simple pricing</h2>
          <p className="text-muted-foreground mt-2">Start free. Scale as you grow.</p>
        </motion.div>

        <motion.div variants={item} className="grid gap-6 sm:grid-cols-3">
          {plans.map((plan) => (
            <motion.div
              key={plan.name}
              whileHover={{ y: -4, boxShadow: '0 8px 24px rgba(0,0,0,0.1)' }}
              onClick={() => setSelectedPlan(plan.name)}
              className={`cursor-pointer rounded-xl border bg-card p-6 transition-shadow ${
                selectedPlan === plan.name ? 'border-foreground ring-2 ring-foreground shadow-lg' : ''
              }`}
            >
              {plan.popular && (
                <div className="flex justify-center mb-4">
                  <Badge className="text-xs">Most Popular</Badge>
                </div>
              )}
              <h3 className="text-lg font-bold">{plan.name}</h3>
              <div className="mt-2 flex items-baseline gap-1">
                <span className="text-3xl font-bold">{plan.price}</span>
                <span className="text-sm text-muted-foreground">{plan.period}</span>
              </div>
              <ul className="mt-6 space-y-2">
                {plan.features.map((f) => (
                  <li key={f} className="flex items-center gap-2 text-sm">
                    <span className="text-success">✓</span> {f}
                  </li>
                ))}
              </ul>
              <motion.div whileTap={{ scale: 0.98 }} className="mt-6">
                <Button
                  className="w-full"
                  variant={selectedPlan === plan.name ? 'default' : 'outline'}
                  onClick={(e) => { e.stopPropagation(); handlePlanCta(plan.name); }}
                >
                  {plan.cta}
                </Button>
              </motion.div>
            </motion.div>
          ))}
        </motion.div>
      </section>

      {/* Footer */}
      <footer className="border-t py-6 px-4">
        <div className="max-w-5xl mx-auto flex items-center justify-between text-xs text-muted-foreground">
          <p>© 2025 TalentLedger. All rights reserved.</p>
          <p>Built with care for modern teams.</p>
        </div>
      </footer>
    </motion.div>
  );
}

export default function Home() {
  const router = useRouter();
  const { isAuthenticated, checkAuth } = useAuthStore();
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  useEffect(() => {
    if (mounted && isAuthenticated) {
      router.replace('/dashboard');
    }
  }, [isAuthenticated, mounted, router]);

  if (!mounted) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 rounded-md bg-foreground/10 animate-pulse" />
          <Skeleton className="h-3 w-24" />
        </div>
      </div>
    );
  }

  if (isAuthenticated) return null;

  return <LandingPage />;
}
