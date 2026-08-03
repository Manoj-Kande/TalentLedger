'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { DashboardStats } from '@/types';

const DASHBOARD_KEY = ['dashboard'];

export function useDashboardStats() {
  return useQuery({
    queryKey: DASHBOARD_KEY,
    queryFn: () => apiClient.get<DashboardStats>('/api/v1/me/stats'),
  });
}

// Item #2: real usage/quota display — /api/v1/me previously fetched quota
// server-side but never returned it (fixed in UserController), and nothing
// on the frontend called this endpoint at all.
export function useProfile() {
  return useQuery({
    queryKey: ['profile'],
    queryFn: () =>
      apiClient.get<{
        id: string;
        plan: string;
        isGuest: boolean;
        quotas?: {
          storageBytesUsed: number;
          storageBytesLimit: number;
          contactsStoredCount: number;
          contactsStoredLimit: number;
        };
      }>('/api/v1/me'),
    staleTime: 30_000,
  });
}
