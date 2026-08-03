'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff, Loader2, ArrowLeft, Check } from 'lucide-react';
import { toast } from 'sonner';
import { motion } from 'framer-motion';
import { apiClient } from '@/lib/api-client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Suspense } from 'react';

const resetSchema = z.object({
  password: z.string().min(8, 'Password must be at least 8 characters'),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: 'Passwords do not match',
  path: ['confirmPassword'],
});

type ResetForm = z.infer<typeof resetSchema>;

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.2 },
  },
};

const item = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const } },
};

function ResetPasswordContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetForm>({
    resolver: zodResolver(resetSchema),
  });

  useEffect(() => {
    if (!token) {
      toast.error('Invalid reset link');
      router.push('/login');
    }
  }, [token, router]);

  const onSubmit = async (data: ResetForm) => {
    if (!token) return;
    setIsLoading(true);
    try {
      await apiClient.post('/api/v1/auth/password-reset/confirm', {
        token,
        newPassword: data.password,
      });
      setDone(true);
      toast.success('Password reset successfully!');
    } catch {
      toast.error('Failed to reset password');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show">
      <Card className="border-border/60 shadow-xl shadow-black/[0.03]">
        <CardContent className="pt-8 pb-2 px-8">
          {done ? (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="text-center py-4">
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 400, damping: 20, delay: 0.1 }}
                className="flex h-12 w-12 items-center justify-center rounded-full bg-success/10 mx-auto mb-4"
              >
                <Check className="h-6 w-6 text-success" />
              </motion.div>
              <h2 className="text-lg font-semibold tracking-tight">Password reset!</h2>
              <p className="text-sm text-muted-foreground mt-2">Your password has been updated. You can now sign in with your new password.</p>
            </motion.div>
          ) : (
            <>
              <motion.div variants={item} className="text-center mb-6">
                <h1 className="text-2xl font-bold tracking-tight">Reset password</h1>
                <p className="text-sm text-muted-foreground mt-1">Enter your new password</p>
              </motion.div>

              <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                <motion.div variants={item} className="space-y-2">
                  <Label htmlFor="password">New Password</Label>
                  <div className="relative">
                    <Input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      placeholder="Enter new password"
                      autoComplete="new-password"
                      className="h-10 pr-10"
                      {...register('password')}
                    />
                    <button
                      type="button"
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                      onClick={() => setShowPassword(!showPassword)}
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
                </motion.div>

                <motion.div variants={item} className="space-y-2">
                  <Label htmlFor="confirmPassword">Confirm New Password</Label>
                  <Input
                    id="confirmPassword"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Confirm new password"
                    autoComplete="new-password"
                    className="h-10"
                    {...register('confirmPassword')}
                  />
                  {errors.confirmPassword && <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>}
                </motion.div>

                <motion.div variants={item}>
                  <motion.div whileTap={{ scale: 0.98 }}>
                    <Button type="submit" className="w-full h-10" disabled={isLoading}>
                      {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                      Reset Password
                    </Button>
                  </motion.div>
                </motion.div>
              </form>
            </>
          )}
        </CardContent>

        <CardFooter className="justify-center pb-8 pt-2 px-8">
          <motion.div variants={item}>
            <Link
              href="/login"
              className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Back to sign in
            </Link>
          </motion.div>
        </CardFooter>
      </Card>
    </motion.div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div className="text-center text-muted-foreground">Loading...</div>}>
      <ResetPasswordContent />
    </Suspense>
  );
}
