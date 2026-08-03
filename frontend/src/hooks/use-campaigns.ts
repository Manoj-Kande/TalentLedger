import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  Campaign,
  CreateCampaignRequest,
  UpdateCampaignRequest,
  CampaignTransitionAction,
} from '@/types';

const CAMPAIGNS_KEY = ['campaigns'];

// NOTE: GET /api/v1/campaigns returns a plain array (List<CampaignResponse>),
// not a cursor-paginated envelope — there's no `.items`/`.nextCursor` on it.
// This was previously typed as PaginatedResponse<Campaign> and the page read
// `data?.items`, which is always undefined against a raw array — the
// campaigns list silently rendered empty regardless of what existed server-side.
export function useCampaigns(params?: { search?: string }) {
  return useQuery({
    queryKey: [...CAMPAIGNS_KEY, params],
    queryFn: async () => {
      const campaigns = await apiClient.get<Campaign[]>('/api/v1/campaigns');
      if (!params?.search) return campaigns;
      const q = params.search.toLowerCase();
      return campaigns.filter(
        (c) => c.name.toLowerCase().includes(q) || c.description?.toLowerCase().includes(q)
      );
    },
  });
}

export function useCampaign(id: string) {
  return useQuery({
    queryKey: [...CAMPAIGNS_KEY, id],
    queryFn: () => apiClient.get<Campaign>(`/api/v1/campaigns/${id}`),
    enabled: !!id,
  });
}

export function useCreateCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateCampaignRequest) =>
      apiClient.post<Campaign>('/api/v1/campaigns', data),
    onSuccess: () => qc.invalidateQueries({ queryKey: CAMPAIGNS_KEY }),
  });
}

export function useUpdateCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateCampaignRequest }) =>
      apiClient.put<Campaign>(`/api/v1/campaigns/${id}`, data),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: CAMPAIGNS_KEY });
      qc.invalidateQueries({ queryKey: [...CAMPAIGNS_KEY, vars.id] });
    },
  });
}

export function useDeleteCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/campaigns/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: CAMPAIGNS_KEY }),
  });
}

// Was entirely missing — Play/Pause controls in the UI had nothing to call,
// and UpdateCampaignRequest.status was silently dropped by the backend
// anyway since PUT /campaigns/{id} never accepted a status field. Status
// changes go through the dedicated PATCH /{id}/status action endpoint.
export function useTransitionCampaignStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: CampaignTransitionAction }) =>
      apiClient.patch<Campaign>(`/api/v1/campaigns/${id}/status`, { action }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: CAMPAIGNS_KEY });
      qc.invalidateQueries({ queryKey: [...CAMPAIGNS_KEY, vars.id] });
    },
  });
}

export function useAddCampaignContacts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, contactIds }: { id: string; contactIds: string[] }) =>
      apiClient.post(`/api/v1/campaigns/${id}/contacts`, { contactIds }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: CAMPAIGNS_KEY });
      qc.invalidateQueries({ queryKey: [...CAMPAIGNS_KEY, vars.id] });
    },
  });
}

export function useRemoveCampaignContact() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, contactId }: { id: string; contactId: string }) =>
      apiClient.delete(`/api/v1/campaigns/${id}/contacts/${contactId}`),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: CAMPAIGNS_KEY });
      qc.invalidateQueries({ queryKey: [...CAMPAIGNS_KEY, vars.id] });
    },
  });
}
