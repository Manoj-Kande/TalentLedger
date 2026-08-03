// ===========================
// TalentLedger API Types
// ===========================

// --- Auth Types ---
export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
  acceptedTerms: boolean;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface AuthResponse {
  user: User;
  sessionToken: string;
}

// --- User Types ---
export interface User {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  plan: UserPlan;
  isActive: boolean;
  isGuest?: boolean;
  avatarUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export type UserRole = "ADMIN" | "PREMIUM" | "USER";
export type UserPlan = "FREE" | "PRO" | "TEAM" | "ENTERPRISE";

export interface UserQuota {
  contactsUsed: number;
  contactsLimit: number;
  storageUsed: number;
  storageLimit: number;
  dumpsThisMonth: number;
  dumpsLimit: number;
}

export interface UpdateProfileRequest {
  name?: string;
  email?: string;
}

// --- Contact Types ---
export interface Contact {
  id: string;
  name: string;
  email: string;
  phone?: string;
  companyName?: string;
  companyId?: string;
  title?: string;
  linkedinUrl?: string;
  location?: string;
  seniorityLevel?: string;
  source?: string;
  status: ContactStatus;
  tags: string[];
  notes?: string;
  primaryDumpId?: string;
  createdAt: string;
  updatedAt: string;
}

export type ContactStatus = "ACTIVE" | "INACTIVE" | "BOUNCED" | "UNSUBSCRIBED";

export interface CreateContactRequest {
  name: string;
  email: string;
  phone?: string;
  linkedinUrl?: string;
  title?: string;
  seniorityLevel?: string;
  location?: string;
  notes?: string;
  tags?: string[];
  companyId?: string;
  primaryDumpId?: string;
}

export interface UpdateContactRequest {
  name?: string;
  phone?: string;
  linkedinUrl?: string;
  title?: string;
  seniorityLevel?: string;
  location?: string;
  notes?: string;
  tags?: string[];
}

export interface ContactSearchParams {
  name?: string;
  company?: string;
  email?: string;
  linkedin?: string;
  phone?: string;
  title?: string;
  status?: ContactStatus;
  tag?: string;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  archived?: boolean;
  cursor?: string;
  limit?: number;
}

// --- Company Types ---
export interface Company {
  id: string;
  displayName: string;
  domain?: string;
  industry?: string;
  category?: string;
  size?: string;
  website?: string;
  location?: string;
  description?: string;
  contactCount: number;
  logoUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyWithContacts extends Company {
  contacts: Contact[];
}

export interface CreateCompanyRequest {
  displayName: string;
  domain?: string;
  industry?: string;
  size?: string;
  website?: string;
  location?: string;
  description?: string;
}

export interface UpdateCompanyRequest {
  displayName?: string;
  domain?: string;
  industry?: string;
  size?: string;
  website?: string;
  location?: string;
  description?: string;
}

// --- Campaign Types ---
export interface Campaign {
  id: string;
  name: string;
  description?: string;
  templateId?: string;
  status: CampaignStatus;
  totalContacts: number;
  sentCount: number;
  replyCount: number;
  bounceCount: number;
  scheduledAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export type CampaignStatus =
  | "DRAFT"
  | "ACTIVE"
  | "PAUSED"
  | "COMPLETED"
  | "ARCHIVED";

export interface CreateCampaignRequest {
  name: string;
  description?: string;
}

export interface UpdateCampaignRequest {
  name?: string;
  description?: string;
  scheduledAt?: string;
}

export type CampaignTransitionAction = "ACTIVATE" | "PAUSE" | "RESUME" | "COMPLETE" | "ARCHIVE";

// --- Saved List Types ---
export interface SavedList {
  id: string;
  name: string;
  description?: string;
  contactCount: number;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SavedListWithContacts extends SavedList {
  contacts: Contact[];
}

export interface CreateSavedListRequest {
  name: string;
  description?: string;
  isPublic?: boolean;
}

export interface UpdateSavedListRequest {
  name?: string;
  description?: string;
  isPublic?: boolean;
}

// --- Dump / Upload Types ---
export type DumpStatus =
  | "PENDING"
  | "PARSING"
  | "COMPLETED"
  | "FAILED"
  | "EXPIRED";

export interface DumpProgress {
  id: string;
  status: DumpStatus;
  progress: number;
  processedRows: number;
  totalRows: number;
  errorCount: number;
  message?: string;
}

export interface DashboardStats {
  totalDumps: number;
  totalContacts: number;
  totalCompanies: number;
  storageUsedBytes: number;
  storageLimitBytes: number;
  uploadsThisMonth: number;
  contactsLimit: number;
}

// --- Admin Types ---
export interface AdminUser {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  plan: UserPlan;
  isActive: boolean;
  lastLoginAt?: string;
  contactCount: number;
  createdAt: string;
}

export interface AdminDashboardStats {
  totalUsers: number;
  totalContacts: number;
  totalDumps: number;
  totalCompanies: number;
  activeCampaigns: number;
  storageUsed: number;
}

export interface AuditLog {
  id: string;
  userId: string;
  userName: string;
  action: string;
  resource: string;
  resourceId?: string;
  details?: string;
  ipAddress?: string;
  createdAt: string;
}

export interface SystemConfig {
  key: string;
  value: string;
  description?: string;
  updatedAt: string;
}

export interface UpdateConfigRequest {
  value: string;
}

// --- Outreach / Notes ---
export interface OutreachEvent {
  id: string;
  contactId: string;
  type: "EMAIL" | "CALL" | "LINKEDIN" | "NOTE" | "OTHER";
  subject?: string;
  content?: string;
  status?: string;
  createdAt: string;
}

// --- Analytics Types ---
export interface AnalyticsOverview {
  totalContacts: number;
  totalCompanies: number;
  totalCampaigns: number;
  totalSavedLists: number;
  recentActivity: ActivityItem[];
}

export interface ActivityItem {
  id: string;
  type: string;
  description: string;
  timestamp: string;
  actorName: string;
}

// --- Pagination ---
export interface CursorPaginationParams {
  cursor?: string;
  limit?: number;
  search?: string;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  company?: string;
  status?: string;
  archived?: boolean;
}

export interface PaginatedResponse<T> {
  items: T[];
  nextCursor?: string;
  hasMore: boolean;
  total?: number;
}

export interface CursorPaginatedResponse<T> {
  data: T[];
  pagination: {
    nextCursor?: string;
    hasMore: boolean;
    total?: number;
  };
}

export interface DumpUpload {
  id: string;
  name?: string;
  description?: string;
  originalFilename: string;
  fileType: string;
  fileSizeBytes: number;
  status: DumpStatus;
  totalRows: number;
  parsedContactsCount: number;
  liveContactsCount: number;
  errorCount: number;
  isPinned: boolean;
  isArchived: boolean;
  // Item #6: uploads start as an unconfirmed preview and must be explicitly
  // saved ("Save to Workspace") — see useConfirmSaveDump. expiresAt is when
  // an unconfirmed preview gets auto-purged if never saved.
  isPersisted: boolean;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateUserRequest {
  role?: UserRole;
  isActive?: boolean;
  plan?: UserPlan;
  banReason?: string;
}

// --- API Envelope ---
export interface ApiSuccessResponse<T> {
  success: true;
  data: T;
}

export interface ApiErrorResponse {
  success: false;
  error: {
    code: string;
    message: string;
  };
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiErrorResponse;

// --- Contact grouped by company ---
export interface CompanyContactGroup {
  company: Company;
  contacts: Contact[];
}

// --- Subscription / Billing ---
export interface Subscription {
  id: string;
  plan: UserPlan;
  status: string;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  trialEnd?: string;
}

export interface PaymentHistory {
  id: string;
  amount: number;
  currency: string;
  status: string;
  description: string;
  createdAt: string;
}
