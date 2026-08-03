import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  Contact,
  ContactSearchParams,
  CreateContactRequest,
  UpdateContactRequest,
  PaginatedResponse,
  CursorPaginationParams,
} from '@/types';

const CONTACTS_KEY = ['contacts'];

export function useContacts(params?: CursorPaginationParams) {
  return useQuery({
    queryKey: [...CONTACTS_KEY, params],
    queryFn: async () => {
      // Backend only exposes GET /api/v1/contacts/search (there is no bare
      // GET /api/v1/contacts) and expects q/sort/size/company/cursor, not
      // search/sortBy+sortDir/limit — this hook was calling a nonexistent
      // route with the wrong param names, so uploaded contacts never
      // rendered even though they were stored correctly.
      const backendParams: Record<string, string> = {};
      if (params?.search) backendParams.q = params.search;
      if (params?.company) backendParams.company = params.company;
      if (params?.cursor) backendParams.cursor = params.cursor;
      if (params?.limit) backendParams.size = String(params.limit);
      if (params?.sortBy) {
        backendParams.sort = params.sortDir ? `${params.sortBy}_${params.sortDir}` : params.sortBy;
      }

      const raw = await apiClient.get<{ contacts: Contact[]; nextCursor: string | null; hasMore: boolean }>(
        '/api/v1/contacts/search',
        backendParams
      );

      return {
        items: raw.contacts,
        nextCursor: raw.nextCursor ?? undefined,
        hasMore: raw.hasMore,
      } satisfies PaginatedResponse<Contact>;
    },
  });
}

export function useContact(id: string) {
  return useQuery({
    queryKey: [...CONTACTS_KEY, id],
    queryFn: () => apiClient.get<Contact>(`/api/v1/contacts/${id}`),
    enabled: !!id,
  });
}

export function useCreateContact() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateContactRequest) =>
      apiClient.post<Contact>('/api/v1/contacts', data),
    onSuccess: () => qc.invalidateQueries({ queryKey: CONTACTS_KEY }),
  });
}

export function useUpdateContact() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateContactRequest }) =>
      apiClient.put<Contact>(`/api/v1/contacts/${id}`, data),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: CONTACTS_KEY });
      qc.invalidateQueries({ queryKey: [...CONTACTS_KEY, vars.id] });
    },
  });
}

export function useDeleteContact() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/contacts/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: CONTACTS_KEY }),
  });
}

export function useSearchContacts(params?: ContactSearchParams) {
  return useQuery({
    queryKey: [...CONTACTS_KEY, 'search', params],
    queryFn: () => apiClient.get<PaginatedResponse<Contact>>('/api/v1/contacts/search', params as Record<string, string>),
  });
}

export function useImportContacts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (contactIds: string[]) =>
      apiClient.post<{ imported: number }>('/api/v1/contacts/bulk', { contactIds }),
    onSuccess: () => qc.invalidateQueries({ queryKey: CONTACTS_KEY }),
  });
}
