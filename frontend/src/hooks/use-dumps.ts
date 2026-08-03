import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  Contact,
  DumpUpload,
  PaginatedResponse,
  CursorPaginationParams,
} from '@/types';

const DUMPS_KEY = ['dumps'];

export function useDumps(params?: CursorPaginationParams) {
  return useQuery({
    queryKey: [...DUMPS_KEY, params],
    queryFn: () =>
      apiClient.get<PaginatedResponse<DumpUpload>>('/api/v1/dumps', params as Record<string, string>),
    refetchInterval: (query) => {
      const items = query.state.data?.items ?? [];
      const stillParsing = items.some((d) => d.status === 'PENDING' || d.status === 'PARSING');
      return stillParsing ? 2000 : false;
    },
  });
}

export function useDump(id: string) {
  return useQuery({
    queryKey: [...DUMPS_KEY, id],
    queryFn: () => apiClient.get<DumpUpload>(`/api/v1/dumps/${id}`),
    enabled: !!id,
    // Uploads are parsed asynchronously on the backend — poll while the dump
    // is still PENDING/PARSING so stats (contact count, errors, etc.) update
    // without a manual refresh, then stop once it settles.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'PARSING' ? 2000 : false;
    },
  });
}

export function useUploadDump() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => apiClient.upload<DumpUpload>('/api/v1/dumps', file),
    onSuccess: () => qc.invalidateQueries({ queryKey: DUMPS_KEY }),
  });
}

export function useUpdateDump() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<DumpUpload> }) =>
      apiClient.put<DumpUpload>(`/api/v1/dumps/${id}`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: DUMPS_KEY }),
  });
}

// Item #6/#7: the explicit "Save to Workspace" action — every upload starts
// unconfirmed (isPersisted=false) until this is called.
export function useConfirmSaveDump() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/dumps/${id}/confirm-save`, {}),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: DUMPS_KEY });
      qc.invalidateQueries({ queryKey: [...DUMPS_KEY, id] });
    },
  });
}

// Item #7: retry a FAILED upload using the originally-uploaded file.
export function useRetryDump() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/dumps/${id}/retry`, {}),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: DUMPS_KEY });
      qc.invalidateQueries({ queryKey: [...DUMPS_KEY, id] });
    },
  });
}

export function useDeleteDump() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/dumps/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: DUMPS_KEY }),
  });
}

export function useDumpContacts(dumpId: string, params?: CursorPaginationParams) {
  // DumpController's GET /{id}/contacts reads 'cursor' and 'size' — it has no
  // idea what 'limit' means, so passing CursorPaginationParams straight
  // through silently ignored the requested page size (always fell back to
  // the backend's default of 50).
  const apiParams: Record<string, string> = {};
  if (params?.cursor) apiParams.cursor = params.cursor;
  if (params?.limit) apiParams.size = String(params.limit);

  return useQuery({
    queryKey: [...DUMPS_KEY, dumpId, 'contacts', params],
    queryFn: () =>
      apiClient.get<PaginatedResponse<Contact>>(
        `/api/v1/dumps/${dumpId}/contacts`,
        apiParams
      ),
    enabled: !!dumpId,
  });
}

export function useAdminDeleteDump() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete<void>(`/api/v1/admin/dumps/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: DUMPS_KEY }),
  });
}
