'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2, ArrowLeft, Mail } from 'lucide-react';
import { toast } from 'sonner';
import { motion } from 'framer-motion';
import { apiClient } from '@/lib/api-client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardFooter } from '@/components/ui/card';

const forgotSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
});

type ForgotForm = z.infer<typeof forgotSchema>;

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

export default function ForgotPasswordPage() {
  const [isLoading, setIsLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotForm>({
    resolver: zodResolver(forgotSchema),
  });

  const onSubmit = async (data: ForgotForm) => {
    setIsLoading(true);
    try {
      await apiClient.post('/api/v1/auth/password-reset', data);
      setSent(true);
      toast.success('Reset link sent to your email');
    } catch {
      toast.error('Failed to send reset email');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show">
      <Card className="border-border/60 shadow-xl shadow-black/[0.03]">
        <CardContent className="pt-8 pb-2 px-8">
          {sent ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="text-center py-4"
            >
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 400, damping: 20, delay: 0.1 }}
                className="flex h-12 w-12 items-center justify-center rounded-full bg-success/10 mx-auto mb-4"
              >
                <Mail className="h-6 w-6 text-success" />
              </motion.div>
              <h2 className="text-lg font-semibold tracking-tight">Check your email</h2>
              <p className="text-sm text-muted-foreground mt-2 max-w-xs mx-auto">
                We&apos;ve sent a password reset link to your email address. Please check your inbox and follow the instructions.
              </p>
            </motion.div>
          ) : (
            <>
              <motion.div variants={item} className="text-center mb-6">
                <h1 className="text-2xl font-bold tracking-tight">Forgot password?</h1>
                <p className="text-sm text-muted-foreground mt-1">
                  Enter your email to receive a reset link
                </p>
              </motion.div>

              <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                <motion.div variants={item} className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="name@company.com"
                    className="h-10"
                    {...register('email')}
                  />
                  {errors.email && (
                    <p className="text-xs text-destructive">{errors.email.message}</p>
                  )}
                </motion.div>

                <motion.div variants={item}>
                  <motion.div whileTap={{ scale: 0.98 }}>
                    <Button type="submit" className="w-full h-10" disabled={isLoading}>
                      {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                      Send Reset Link
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
