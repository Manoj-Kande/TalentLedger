'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuthStore } from '@/stores/auth-store';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof loginSchema>;

const spring = { type: 'spring' as const, stiffness: 400, damping: 30 };
const springGentle = { type: 'spring' as const, stiffness: 200, damping: 24 };

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.05, delayChildren: 0.12 },
  },
};

const item = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { ...springGentle, mass: 0.8 } },
};

export default function LoginPage() {
  const router = useRouter();
  const { login, isLoading, error, clearError } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);
  const [mounted, setMounted] = useState(false);

  // Prevent hydration mismatch — only animate after mount
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- standard post-mount hydration guard, runs once
    setMounted(true);
  }, []);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginForm) => {
    clearError();
    try {
      await login(data);
      toast.success('Welcome back!');
      router.push('/dashboard');
    } catch {
      // Error handled by store
    }
  };

  // SSR fallback: render static version to prevent hydration mismatch
  if (!mounted) {
    return (
      <div>
        <Card className="border-border/60 shadow-xl shadow-black/[0.04] backdrop-blur-sm bg-card/95">
          <CardContent className="pt-8 pb-2 px-8">
            <div className="text-center mb-7">
              <h1 className="text-[22px] font-semibold tracking-tight text-foreground">
                Sign in
              </h1>
              <p className="text-[13px] text-muted-foreground mt-1.5">
                Welcome back to TalentLedger
              </p>
            </div>
            <div className="grid grid-cols-2 gap-3 mb-6">
              <div className="h-[42px] rounded-xl border border-border/80 bg-card" />
              <div className="h-[42px] rounded-xl border border-border/80 bg-card" />
            </div>
            <div className="relative mb-6">
              <Separator className="bg-border/50" />
              <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-card px-3 text-[11px] uppercase tracking-widest text-muted-foreground/70 font-medium">
                or
              </span>
            </div>
            <div className="space-y-4">
              <div className="h-[92px]" />
              <div className="h-[92px]" />
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <motion.div variants={container} initial="hidden" animate="show">
      <motion.div
        key={error ? 'error' : 'normal'}
        animate={{ scale: error ? [1, 1.01, 1] : 1 }}
        transition={{ type: 'spring', stiffness: 300, damping: 25 }}
      >
        <Card className={`border-border/60 shadow-xl shadow-black/[0.04] backdrop-blur-sm bg-card/95 transition-colors ${error ? 'border-destructive/40' : ''}`}>
          <AnimatePresence>
            {error && (
              <motion.div
                key="error-banner"
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ ...spring, duration: 0.3 }}
                className="overflow-hidden"
              >
                <div className="mx-6 mt-6 rounded-lg bg-destructive/[0.06] border border-destructive/15 px-4 py-3 text-sm text-destructive">
                  {error}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
          <CardContent className={(error ? 'pt-6' : 'pt-8') + ' pb-2 px-8'}>
            <motion.div variants={item} className="text-center mb-7">
              <h1 className="text-[22px] font-semibold tracking-tight text-foreground">
                Sign in
              </h1>
              <p className="text-[13px] text-muted-foreground mt-1.5">
                Welcome back to TalentLedger
              </p>
            </motion.div>

            {/* OAuth Buttons */}
            <motion.div variants={item} className="grid grid-cols-2 gap-3 mb-6">
              <motion.button
                whileHover={{ scale: 1.015 }}
                whileTap={{ scale: 0.985 }}
                transition={spring}
                type="button"
                className="flex items-center justify-center gap-2.5 rounded-xl border border-border/80 bg-card px-4 py-2.5 text-[13px] font-medium text-foreground/90 transition-shadow hover:shadow-sm"
              >
                <svg className="h-4 w-4" viewBox="0 0 24 24">
                  <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/>
                  <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                  <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                  <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                </svg>
                Google
              </motion.button>
              <motion.button
                whileHover={{ scale: 1.015 }}
                whileTap={{ scale: 0.985 }}
                transition={spring}
                type="button"
                className="flex items-center justify-center gap-2.5 rounded-xl border border-border/80 bg-card px-4 py-2.5 text-[13px] font-medium text-foreground/90 transition-shadow hover:shadow-sm"
              >
                <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                GitHub
              </motion.button>
            </motion.div>

            <motion.div variants={item} className="relative mb-6">
              <Separator className="bg-border/50" />
              <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-card px-3 text-[11px] uppercase tracking-widest text-muted-foreground/70 font-medium">
                or
              </span>
            </motion.div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <motion.div variants={item} className="space-y-2">
                <Label htmlFor="email" className="text-[13px] font-medium">Email</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="name@company.com"
                  autoComplete="email"
                  className={
                    'h-11 rounded-lg border-border/70 bg-background/50 text-sm placeholder:text-muted-foreground/50 focus-visible:ring-foreground/10 transition-colors' +
                    (errors.email ? ' border-destructive/50 focus-visible:ring-destructive/20' : '')
                  }
                  {...register('email')}
                />
                {errors.email && (
                  <motion.p initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} transition={spring} className="text-[12px] text-destructive">
                    {errors.email.message}
                  </motion.p>
                )}
              </motion.div>

              <motion.div variants={item} className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password" className="text-[13px] font-medium">Password</Label>
                  <Link
                    href="/forgot-password"
                    className="text-[12px] text-muted-foreground/70 hover:text-foreground underline-offset-4 hover:underline transition-colors"
                  >
                    Forgot password?
                  </Link>
                </div>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    className={
                      'h-11 rounded-lg border-border/70 bg-background/50 pr-10 text-sm placeholder:text-muted-foreground/50 focus-visible:ring-foreground/10 transition-colors' +
                      (errors.password ? ' border-destructive/50 focus-visible:ring-destructive/20' : '')
                    }
                    {...register('password')}
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground/60 hover:text-foreground transition-colors"
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                </div>
                {errors.password && (
                  <motion.p initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} transition={spring} className="text-[12px] text-destructive">
                    {errors.password.message}
                  </motion.p>
                )}
              </motion.div>

              <motion.div variants={item} className="pt-1">
                <motion.div whileHover={{ scale: 1.01 }} whileTap={{ scale: 0.985 }} transition={spring}>
                  <Button 
                    type="submit" 
                    className="w-full h-11 rounded-lg text-[13px] font-medium shadow-sm"
                    disabled={isLoading}
                  >
                    {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Sign In
                  </Button>
                </motion.div>
              </motion.div>
            </form>
          </CardContent>

          <CardFooter className="justify-center pb-8 pt-4 px-8">
            <motion.p variants={item} className="text-[13px] text-muted-foreground text-center">
              Don&apos;t have an account?{' '}
              <Link href="/register" className="text-foreground hover:underline underline-offset-4 font-medium transition-colors">
                Create one
              </Link>
            </motion.p>
          </CardFooter>
        </Card>
      </motion.div>
    </motion.div>
  );
}
