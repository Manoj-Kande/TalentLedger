import { create } from "zustand";
import { apiClient, ApiError } from "@/lib/api-client";
import type { User, LoginRequest, RegisterRequest } from "@/types";

const isDev = process.env.NODE_ENV === "development";

function authTrace(label: string, data?: unknown) {
  if (!isDev) return;
  if (data !== undefined) {
    console.debug(`[auth] ${label}`, data);
    return;
  }
  console.debug(`[auth] ${label}`);
}

function maskEmail(email: string): string {
  const [localPart, domainPart] = email.split("@");
  if (!domainPart) return email;
  const visible = localPart.slice(0, 2);
  return `${visible}${localPart.length > 2 ? "***" : ""}@${domainPart}`;
}

interface AuthState {
  user: User | null;
  sessionToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  // True once checkAuth() has run at least once. Route guards must wait for
  // this before deciding to redirect — reading isAuthenticated before this
  // is true means reading its default `false`, which caused every page
  // refresh to bounce logged-in users to /login (the redirect effect ran
  // before checkAuth's localStorage read had a chance to update state).
  isInitialized: boolean;
  error: string | null;

  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  clearError: () => void;
  checkAuth: () => void;
  setUser: (user: User) => void;
  // Item #1: anonymous free-preview flow. Bootstraps a real (ephemeral)
  // session so the whole authenticated app works for guests unmodified.
  startGuestSession: () => Promise<void>;
  // Called after a guest completes real register/login, to pull their
  // preview data onto the new account (item #6 "Save to Workspace").
  claimGuestData: (guestUserId: string) => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  sessionToken: null,
  isAuthenticated: false,
  isLoading: false,
  isInitialized: false,
  error: null,

  login: async (credentials) => {
    set({ isLoading: true, error: null });
    authTrace("login:start", { email: maskEmail(credentials.email) });
    try {
      const prevUserStr = localStorage.getItem("user");
      const prevUser = prevUserStr ? (JSON.parse(prevUserStr) as User) : null;
      const guestUserId = prevUser?.isGuest ? prevUser.id : null;

      const response = await apiClient.post<{
        user: User;
        sessionToken: string;
      }>("/api/v1/auth/login", credentials);

      const { user, sessionToken } = response;
      localStorage.setItem("session_token", sessionToken);
      localStorage.setItem("user", JSON.stringify(user));
      set({
        user,
        sessionToken,
        isAuthenticated: true,
        isLoading: false,
        isInitialized: true,
      });
      if (guestUserId) {
        apiClient.post("/api/v1/auth/guest/claim", { guestUserId }).catch(() => {});
      }
      authTrace("login:success", {
        userId: user.id,
        email: maskEmail(user.email),
      });
    } catch (err) {
      const message =
        err instanceof ApiError
          ? err.message
          : "Login failed. Please try again.";
      authTrace("login:error", {
        message,
        code: err instanceof ApiError ? err.code : "UNKNOWN",
        status: err instanceof ApiError ? err.status : undefined,
      });
      set({ error: message, isLoading: false });
      throw err;
    }
  },

  startGuestSession: async () => {
    set({ isLoading: true, error: null });
    try {
      const response = await apiClient.post<{ user: User; sessionToken: string }>(
        "/api/v1/auth/guest",
        {}
      );
      const { user, sessionToken } = response;
      localStorage.setItem("session_token", sessionToken);
      localStorage.setItem("user", JSON.stringify(user));
      set({ user, sessionToken, isAuthenticated: true, isLoading: false, isInitialized: true });
    } catch {
      set({ isLoading: false, isInitialized: true });
    }
  },

  claimGuestData: async (guestUserId: string) => {
    await apiClient.post("/api/v1/auth/guest/claim", { guestUserId });
  },

  register: async (data) => {
    set({ isLoading: true, error: null });
    authTrace("register:start", {
      nameLength: data.name?.length ?? 0,
      email: maskEmail(data.email),
      acceptedTerms: data.acceptedTerms,
    });
    try {
      const prevUserStr = localStorage.getItem("user");
      const prevUser = prevUserStr ? (JSON.parse(prevUserStr) as User) : null;
      const guestUserId = prevUser?.isGuest ? prevUser.id : null;

      const response = await apiClient.post<{
        user: User;
        sessionToken: string;
      }>("/api/v1/auth/register", data);

      const { user, sessionToken } = response;
      localStorage.setItem("session_token", sessionToken);
      localStorage.setItem("user", JSON.stringify(user));
      set({
        user,
        sessionToken,
        isAuthenticated: true,
        isLoading: false,
        isInitialized: true,
      });
      if (guestUserId) {
        apiClient.post("/api/v1/auth/guest/claim", { guestUserId }).catch(() => {});
      }
      authTrace("register:success", {
        userId: user.id,
        email: maskEmail(user.email),
      });
    } catch (err) {
      const message =
        err instanceof ApiError
          ? err.message
          : "Registration failed. Please try again.";
      authTrace("register:error", {
        message,
        code: err instanceof ApiError ? err.code : "UNKNOWN",
        status: err instanceof ApiError ? err.status : undefined,
      });
      set({ error: message, isLoading: false });
      throw err;
    }
  },

  logout: () => {
    // Fire-and-forget logout call to server
    try {
      apiClient.post("/api/v1/auth/logout").catch(() => {});
    } catch {}

    // Clear all auth state
    localStorage.removeItem("session_token");
    localStorage.removeItem("user");
    localStorage.removeItem("dev_user_id");
    set({
      user: null,
      sessionToken: null,
      isAuthenticated: false,
      isInitialized: true,
      error: null,
    });

    // Hard redirect to login to prevent any stale state issues
    if (typeof window !== "undefined") {
      window.location.replace("/login");
    }
  },

  clearError: () => set({ error: null }),

  checkAuth: () => {
    const token = localStorage.getItem("session_token");
    const userStr = localStorage.getItem("user");
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr) as User;
        set({ user, sessionToken: token, isAuthenticated: true, isInitialized: true });
      } catch {
        localStorage.removeItem("session_token");
        localStorage.removeItem("user");
        set({ user: null, sessionToken: null, isAuthenticated: false, isInitialized: true });
      }
    } else {
      set({ user: null, sessionToken: null, isAuthenticated: false, isInitialized: true });
    }
  },

  setUser: (user) => {
    localStorage.setItem("user", JSON.stringify(user));
    set({ user });
  },
}));
