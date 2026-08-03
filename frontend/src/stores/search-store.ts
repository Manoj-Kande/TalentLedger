import { create } from 'zustand';

type ContactViewMode = 'company' | 'flat';
type SortOption = { field: string; direction: 'asc' | 'desc' };

interface SearchState {
  searchName: string;
  searchCompany: string;
  searchEmail: string;
  searchLinkedin: string;
  searchPhone: string;
  searchTitle: string;
  searchStatus: string;
  searchTag: string;
  viewMode: ContactViewMode;
  sortBy: SortOption;
  dumpSort: string;
  dumpStatusFilter: string;
  dumpViewMode: 'grid' | 'list';
  setSearchField: (field: string, value: string) => void;
  clearSearch: () => void;
  setViewMode: (mode: ContactViewMode) => void;
  setSortBy: (sort: SortOption) => void;
  setDumpSort: (sort: string) => void;
  setDumpStatusFilter: (status: string) => void;
  setDumpViewMode: (mode: 'grid' | 'list') => void;
  reset: () => void;
}

const initialState = {
  searchName: '', searchCompany: '', searchEmail: '',
  searchLinkedin: '', searchPhone: '', searchTitle: '',
  searchStatus: '', searchTag: '',
  viewMode: 'company' as ContactViewMode,
  sortBy: { field: 'createdAt', direction: 'desc' } as SortOption,
  dumpSort: 'createdAt', dumpStatusFilter: '', dumpViewMode: 'grid' as const,
};

export const useSearchStore = create<SearchState>((set) => ({
  ...initialState,
  setSearchField: (field, value) => set({ [field]: value }),
  clearSearch: () => set({ searchName: '', searchCompany: '', searchEmail: '', searchLinkedin: '', searchPhone: '', searchTitle: '', searchStatus: '', searchTag: '' }),
  setViewMode: (mode) => set({ viewMode: mode }),
  setSortBy: (sort) => set({ sortBy: sort }),
  setDumpSort: (sort) => set({ dumpSort: sort }),
  setDumpStatusFilter: (status) => set({ dumpStatusFilter: status }),
  setDumpViewMode: (mode) => set({ dumpViewMode: mode }),
  reset: () => set(initialState),
}));
