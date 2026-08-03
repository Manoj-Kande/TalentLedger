import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  SavedList,
  CreateSavedListRequest,
  UpdateSavedListRequest,
  Contact,
  PaginatedResponse,
  CursorPaginationParams,
} from '@/types';

const SAVED_LISTS_KEY = ['saved-lists'];

export function useSavedLists(params?: CursorPaginationParams) {
  return useQuery({
    queryKey: [...SAVED_LISTS_KEY, params],
    queryFn: () =>
      apiClient.get<PaginatedResponse<SavedList>>('/api/v1/saved-lists', params as Record<string, string>),
  });
}

export function useSavedList(id: string) {
  return useQuery({
    queryKey: [...SAVED_LISTS_KEY, id],
    queryFn: () => apiClient.get<SavedList>(`/api/v1/saved-lists/${id}`),
    enabled: !!id,
  });
}

export function useSavedListContacts(listId: string, params?: CursorPaginationParams) {
  return useQuery({
    queryKey: [...SAVED_LISTS_KEY, listId, 'contacts', params],
    queryFn: () =>
      apiClient.get<PaginatedResponse<Contact>>(
        `/api/v1/saved-lists/${listId}/contacts`,
        params as Record<string, string>
      ),
    enabled: !!listId,
  });
}

export function useCreateSavedList() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateSavedListRequest) =>
      apiClient.post<SavedList>('/api/v1/saved-lists', data),
    onSuccess: () => qc.invalidateQueries({ queryKey: SAVED_LISTS_KEY }),
  });
}

export function useUpdateSavedList() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateSavedListRequest }) =>
      apiClient.put<SavedList>(`/api/v1/saved-lists/${id}`, data),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: SAVED_LISTS_KEY });
      qc.invalidateQueries({ queryKey: [...SAVED_LISTS_KEY, vars.id] });
    },
  });
}

export function useDeleteSavedList() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/saved-lists/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: SAVED_LISTS_KEY }),
  });
}
