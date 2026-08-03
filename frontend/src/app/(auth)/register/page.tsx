"use client";

import { useState, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Eye, EyeOff, Loader2, Check } from "lucide-react";
import { toast } from "sonner";
import { motion } from "framer-motion";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

const registerSchema = z
  .object({
    name: z.string().min(2, "Name must be at least 2 characters"),
    email: z.string().email("Please enter a valid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type RegisterForm = z.infer<typeof registerSchema>;

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.06, delayChildren: 0.2 },
  },
};

const item = {
  hidden: { opacity: 0, y: 12 },
  show: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.35, ease: [0.25, 0.4, 0.25, 1] as const },
  },
};

function getPasswordStrength(pwd: string) {
  let score = 0;
  if (pwd.length >= 8) score++;
  if (pwd.length >= 12) score++;
  if (/[A-Z]/.test(pwd)) score++;
  if (/[0-9]/.test(pwd)) score++;
  if (/[^A-Za-z0-9]/.test(pwd)) score++;
  if (score <= 1)
    return { label: "Weak", color: "bg-destructive", width: "20%" };
  if (score <= 2) return { label: "Fair", color: "bg-warning", width: "40%" };
  if (score <= 3) return { label: "Good", color: "bg-info", width: "60%" };
  if (score <= 4) return { label: "Strong", color: "bg-success", width: "80%" };
  return { label: "Excellent", color: "bg-success", width: "100%" };
}

export default function RegisterPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const {
    register: registerUser,
    isLoading,
    error,
    clearError,
  } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const requestedPlan = searchParams.get("plan")?.toUpperCase();
  const requestedPlanLabel =
    requestedPlan === "PRO" ||
    requestedPlan === "TEAM" ||
    requestedPlan === "ENTERPRISE"
      ? requestedPlan.charAt(0) + requestedPlan.slice(1).toLowerCase()
      : "Free";

  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
  });

  const password = watch("password", "");
  const strength = useMemo(() => getPasswordStrength(password), [password]);

  const onSubmit = async (data: Omit<RegisterForm, "confirmPassword">) => {
    if (!termsAccepted) {
      toast.error("Please accept the terms and conditions");
      return;
    }
    if (process.env.NODE_ENV === "development") {
      console.debug("[register-page] submit:start", {
        email: data.email,
        nameLength: data.name?.length ?? 0,
        acceptedTerms: termsAccepted,
      });
    }
    clearError();
    try {
      await registerUser({
        name: data.name,
        email: data.email,
        password: data.password,
        acceptedTerms: termsAccepted,
      });
      if (process.env.NODE_ENV === "development") {
        console.debug("[register-page] submit:success", { email: data.email });
      }
      toast.success("Account created successfully!");
      router.push("/dashboard");
    } catch {
      if (process.env.NODE_ENV === "development") {
        console.debug("[register-page] submit:error", { email: data.email });
      }
      // Error handled by store
    }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show">
      <Card className="border-border/60 shadow-xl shadow-black/3">
        <CardContent className="pt-8 pb-2 px-8">
          <motion.div variants={item} className="text-center mb-6">
            <h1 className="text-2xl font-bold tracking-tight">
              Create account
            </h1>
            <p className="text-sm text-muted-foreground mt-1">
              Get started with TalentLedger
            </p>
            <p className="mt-3 text-xs text-muted-foreground">
              {requestedPlan === "PRO" ||
              requestedPlan === "TEAM" ||
              requestedPlan === "ENTERPRISE"
                ? `You selected ${requestedPlanLabel}. In this build, premium access is assigned by an admin after signup.`
                : "New accounts start on the Free plan by default."}
            </p>
          </motion.div>

          {error && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="rounded-lg bg-destructive/10 border border-destructive/20 p-3 text-sm text-destructive mb-4"
            >
              {error}
            </motion.div>
          )}

          <motion.div variants={item} className="grid grid-cols-2 gap-3 mb-5">
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              type="button"
              className="flex items-center justify-center gap-2 rounded-lg border border-border bg-card px-4 py-2.5 text-sm font-medium hover:bg-accent transition-colors"
            >
              <svg className="h-4 w-4" viewBox="0 0 24 24">
                <path
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"
                  fill="#4285F4"
                />
                <path
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  fill="#34A853"
                />
                <path
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                  fill="#FBBC05"
                />
                <path
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                  fill="#EA4335"
                />
              </svg>
              Google
            </motion.button>
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              type="button"
              className="flex items-center justify-center gap-2 rounded-lg border border-border bg-card px-4 py-2.5 text-sm font-medium hover:bg-accent transition-colors"
            >
              <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
              </svg>
              GitHub
            </motion.button>
          </motion.div>

          <motion.div variants={item} className="relative mb-5">
            <Separator />
            <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-card px-2 text-xs text-muted-foreground">
              or continue with email
            </span>
          </motion.div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <motion.div variants={item} className="space-y-2">
              <Label htmlFor="name">Full Name</Label>
              <Input
                id="name"
                placeholder="John Doe"
                autoComplete="name"
                className="h-10"
                {...register("name")}
              />
              {errors.name && (
                <p className="text-xs text-destructive">
                  {errors.name.message}
                </p>
              )}
            </motion.div>

            <motion.div variants={item} className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="name@company.com"
                autoComplete="email"
                className="h-10"
                {...register("email")}
              />
              {errors.email && (
                <p className="text-xs text-destructive">
                  {errors.email.message}
                </p>
              )}
            </motion.div>

            <motion.div variants={item} className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Create a password"
                  autoComplete="new-password"
                  className="h-10 pr-10"
                  {...register("password")}
                />
                <button
                  type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
              {password.length > 0 && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="space-y-1.5"
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-muted-foreground">
                      Password strength
                    </span>
                    <span className="font-medium text-muted-foreground">
                      {strength.label}
                    </span>
                  </div>
                  <div className="h-1 w-full rounded-full bg-muted overflow-hidden">
                    <motion.div
                      className={`h-full rounded-full ${strength.color}`}
                      initial={{ width: 0 }}
                      animate={{ width: strength.width }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                </motion.div>
              )}
              {errors.password && (
                <p className="text-xs text-destructive">
                  {errors.password.message}
                </p>
              )}
            </motion.div>

            <motion.div variants={item} className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm Password</Label>
              <Input
                id="confirmPassword"
                type={showPassword ? "text" : "password"}
                placeholder="Confirm your password"
                autoComplete="new-password"
                className="h-10"
                {...register("confirmPassword")}
              />
              {errors.confirmPassword && (
                <p className="text-xs text-destructive">
                  {errors.confirmPassword.message}
                </p>
              )}
            </motion.div>

            <motion.div variants={item} className="flex items-start gap-2">
              <button
                type="button"
                onClick={() => setTermsAccepted(!termsAccepted)}
                className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded border border-border bg-background transition-colors"
              >
                {termsAccepted && (
                  <motion.div
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    transition={{ type: "spring", stiffness: 500, damping: 30 }}
                  >
                    <Check className="h-3 w-3 text-foreground" />
                  </motion.div>
                )}
              </button>
              <p className="text-xs text-muted-foreground leading-relaxed">
                I agree to the{" "}
                <a
                  href="#"
                  className="text-foreground underline underline-offset-4 hover:text-foreground/80"
                >
                  Terms of Service
                </a>{" "}
                and{" "}
                <a
                  href="#"
                  className="text-foreground underline underline-offset-4 hover:text-foreground/80"
                >
                  Privacy Policy
                </a>
              </p>
            </motion.div>

            <motion.div variants={item}>
              <motion.div whileTap={{ scale: 0.98 }}>
                <Button
                  type="submit"
                  className="w-full h-10"
                  disabled={isLoading || !termsAccepted}
                >
                  {isLoading && (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Create Account
                </Button>
              </motion.div>
            </motion.div>
          </form>
        </CardContent>

        <CardFooter className="justify-center pb-8 pt-2 px-8">
          <motion.p
            variants={item}
            className="text-sm text-muted-foreground text-center"
          >
            Already have an account?{" "}
            <Link
              href="/login"
              className="text-foreground hover:underline underline-offset-4 font-medium transition-colors"
            >
              Sign in
            </Link>
          </motion.p>
        </CardFooter>
      </Card>
    </motion.div>
  );
}
