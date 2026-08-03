import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  Company,
  CompanyWithContacts,
  CreateCompanyRequest,
  UpdateCompanyRequest,
  PaginatedResponse,
  CursorPaginationParams,
} from '@/types';

const COMPANIES_KEY = ['companies'];

export function useCompanies(params?: CursorPaginationParams) {
  return useQuery({
    queryKey: [...COMPANIES_KEY, params],
    queryFn: () =>
      apiClient.get<PaginatedResponse<Company>>('/api/v1/companies', params as Record<string, string>),
  });
}

export function useCompany(id: string) {
  return useQuery({
    queryKey: [...COMPANIES_KEY, id],
    queryFn: () => apiClient.get<CompanyWithContacts>(`/api/v1/companies/${id}`),
    enabled: !!id,
  });
}

export function useCreateCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateCompanyRequest) =>
      apiClient.post<Company>('/api/v1/companies', data),
    onSuccess: () => qc.invalidateQueries({ queryKey: COMPANIES_KEY }),
  });
}

export function useUpdateCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateCompanyRequest }) =>
      apiClient.put<Company>(`/api/v1/companies/${id}`, data),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: COMPANIES_KEY });
      qc.invalidateQueries({ queryKey: [...COMPANIES_KEY, vars.id] });
    },
  });
}

export function useDeleteCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/companies/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: COMPANIES_KEY }),
  });
}
