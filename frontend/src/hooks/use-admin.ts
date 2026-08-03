import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  AdminUser,
  UpdateUserRequest,
  SystemConfig,
  UpdateConfigRequest,
  AdminDashboardStats,
  PaginatedResponse,
  CursorPaginationParams,
} from '@/types';

// --- Admin Users ---
const ADMIN_USERS_KEY = ['admin', 'users'];

export function useAdminUsers(params?: CursorPaginationParams) {
  return useQuery({
    queryKey: [...ADMIN_USERS_KEY, params],
    queryFn: () =>
      apiClient.get<PaginatedResponse<AdminUser>>('/api/v1/admin/users', params as Record<string, string>),
  });
}

export function useUpdateAdminUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateUserRequest }) =>
      apiClient.put<AdminUser>(`/api/v1/admin/users/${id}`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ADMIN_USERS_KEY }),
  });
}

// --- System Config ---
const CONFIG_KEY = ['admin', 'config'];

export function useSystemConfig() {
  return useQuery({
    queryKey: CONFIG_KEY,
    queryFn: () => apiClient.get<SystemConfig[]>('/api/v1/admin/configs'),
  });
}

export function useUpdateSystemConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, data }: { key: string; data: UpdateConfigRequest }) =>
      apiClient.put<SystemConfig>(`/api/v1/admin/configs/${key}`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: CONFIG_KEY }),
  });
}

// --- Analytics ---
export function useAnalyticsOverview() {
  return useQuery({
    queryKey: ['admin', 'stats'],
    queryFn: () => apiClient.get<AdminDashboardStats>('/api/v1/admin/stats'),
  });
}

// --- Current User / Me ---
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () => apiClient.get<{ user: AdminUser }>('/api/v1/me'),
  });
}

export function useUpdateMe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name?: string; email?: string }) =>
      apiClient.put('/api/v1/me', data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['me'] });
    },
  });
}
