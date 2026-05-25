export type ApiMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiRequestOptions<TBody = unknown> {
  path: string;
  method?: ApiMethod;
  body?: TBody;
  token?: string;
}

export class ApiRequestError extends Error {
  status: number;
  code?: string;

  constructor(status: number, code?: string) {
    super(code ? `API request failed: ${status} (${code})` : `API request failed: ${status}`);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
  }
}

const resolveBaseUrl = () => {
  if (typeof globalThis !== 'undefined' && 'location' in globalThis) {
    return (globalThis as typeof globalThis & { __AGENTX_API_BASE_URL__?: string }).__AGENTX_API_BASE_URL__ ?? 'http://localhost:8080';
  }

  return 'http://localhost:8080';
};

export async function apiRequest<TResponse, TBody = unknown>(
  options: ApiRequestOptions<TBody>
): Promise<TResponse> {
  const response = await fetch(`${resolveBaseUrl()}${options.path}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  const contentType = response.headers.get('Content-Type') ?? '';
  const responseBody = contentType.includes('application/json') ? await response.json() : null;

  if (!response.ok) {
    throw new ApiRequestError(response.status, responseBody?.code);
  }

  return responseBody as TResponse;
}

export interface HealthResponse {
  status: string;
  service: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthSession {
  userId: number;
  tenantId: number | null;
  email: string;
  displayName: string;
  roles: string[];
  accessToken: string;
}

export async function login(request: LoginRequest): Promise<AuthSession> {
  return apiRequest<AuthSession, LoginRequest>({
    path: '/api/public/auth/login',
    method: 'POST',
    body: request
  });
}

export interface TenantSummary {
  id: number;
  code: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  contactName: string | null;
  contactEmail: string | null;
}

export interface PlanSummary {
  id: number;
  code: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  limits: Record<string, number>;
}

export interface CreatePlanRequest {
  code: string;
  name: string;
  limits: Record<string, number>;
}

export interface UpdatePlanRequest {
  name: string;
  limits: Record<string, number>;
}

export interface TenantQuotaSummary {
  tenantId: number;
  planId: number;
  overrides: Record<string, number>;
}

export interface TenantQuotaOverview {
  tenantId: number;
  planId: number;
  planCode: string;
  planName: string;
  planStatus: 'ACTIVE' | 'DISABLED';
  limits: Record<string, number>;
  overrides: Record<string, number>;
  effectiveLimits: Record<string, number>;
  usage: Record<string, number>;
}

export interface AuditLogSummary {
  id: number;
  tenantId: number | null;
  actorUserId: number | null;
  actionType: string;
  targetType: string;
  targetId: string | null;
  result: string;
  riskLevel: string;
  createdAt: string;
}

export interface AuditLogDetail extends AuditLogSummary {
  context: Record<string, unknown>;
}

export interface ChatbotSummary {
  id: number;
  tenantId: number;
  name: string;
  description: string | null;
  language: string;
  status: 'DRAFT' | 'ACTIVE' | 'DISABLED' | 'DELETED';
  publicCode: string;
  themeColor: string;
  welcomeMessage: string;
  fallbackMessage: string;
}

export interface CreateChatbotRequest {
  tenantId: number;
  name: string;
  description: string;
  language: string;
  status: ChatbotSummary['status'];
}

export interface UpdateChatbotRequest {
  name: string;
  description: string;
  language: string;
  status: ChatbotSummary['status'];
}

export interface ChatbotDetail extends ChatbotSummary {
  brandVisible: boolean;
  launcherPosition: string;
  allowDirectModel: boolean;
  allowFeedback: boolean;
  allowHandoff: boolean;
}

export interface UpdateChatbotAppearanceRequest {
  themeColor: string;
  welcomeMessage: string;
  brandVisible: boolean;
  launcherPosition: string;
}

export interface UpdateChatbotBehaviorRequest {
  fallbackMessage: string;
  allowDirectModel: boolean;
  allowFeedback: boolean;
  allowHandoff: boolean;
}

export interface FaqSummary {
  id: number;
  tenantId: number;
  chatbotId: number;
  language: string;
  status: 'ACTIVE' | 'DISABLED' | 'DELETED';
  question: string;
  alternateQuestions: string[];
  answer: string;
}

export interface CreateFaqRequest {
  tenantId: number;
  chatbotId: number;
  language: string;
  question: string;
  alternateQuestions: string[];
  answer: string;
}

export interface UpdateFaqRequest {
  language: string;
  question: string;
  alternateQuestions: string[];
  answer: string;
}

export interface ListFaqFilters {
  tenantId: number;
  chatbotId: number;
  language?: string;
  keyword?: string;
  status?: FaqSummary['status'];
}

export interface ImportFaqItem {
  language: string;
  status?: FaqSummary['status'];
  question: string;
  alternateQuestions: string[];
  answer: string;
}

export interface ImportFaqResult {
  importedCount: number;
  failures: Array<{
    index: number;
    field: string;
    reason: string;
  }>;
}

export interface KnowledgeSourceSummary {
  id: number;
  tenantId: number;
  chatbotId: number;
  sourceType: 'FILE' | 'WEB';
  status: 'UPLOADED' | 'PROCESSING' | 'ACTIVE' | 'DISABLED' | 'FAILED' | 'DELETED';
  sourceName: string;
  sourceUri: string | null;
  contentType: string | null;
  fileSizeBytes: number;
  createdAt: string;
}

export interface KnowledgeSourceDetail {
  id: number;
  tenantId: number;
  chatbotId: number;
  sourceType: 'FILE' | 'WEB';
  status: 'UPLOADED' | 'PROCESSING' | 'ACTIVE' | 'DISABLED' | 'FAILED' | 'DELETED';
  sourceName: string;
  sourceUri: string | null;
  failureReason: string | null;
  metadata: Record<string, string>;
  chunks: Array<{
    id: number;
    chunkIndex: number;
    summary: string | null;
    content: string;
    sourceLink: string | null;
  }>;
  createdAt: string;
}

export interface CreateWebKnowledgeSourceRequest {
  name: string;
  url: string;
}

export interface ConversationSummary {
  id: number;
  tenantId: number;
  chatbotId: number;
  chatbotName: string;
  anonymousVisitorId: string;
  entryType: string;
  status: 'ACTIVE' | 'ENDED' | 'HANDOFF_PENDING' | 'DELETED';
  latestMessage: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: number;
  role: string;
  status: string;
  content: string;
  metadata: Record<string, unknown>;
  createdAt: string;
}

export interface ConversationDetail extends Omit<ConversationSummary, 'latestMessage' | 'messageCount'> {
  metadata: Record<string, unknown>;
  messages: ConversationMessage[];
}

export interface TenantAdminSummary {
  id: number;
  email: string;
  displayName: string;
}

export interface TenantDetail extends TenantSummary {
  notes: string | null;
  admin: TenantAdminSummary | null;
}

export interface CreateTenantRequest {
  code: string;
  name: string;
  contactName: string;
  contactEmail: string;
  notes: string;
  adminEmail: string;
  adminDisplayName: string;
  adminPassword: string;
}

export interface UpdateTenantRequest {
  name: string;
  contactName: string;
  contactEmail: string;
  notes: string;
}

export async function listTenants(token: string): Promise<TenantSummary[]> {
  return apiRequest<TenantSummary[]>({
    path: '/api/admin/tenants',
    token
  });
}

export async function getTenant(token: string, tenantId: number): Promise<TenantDetail> {
  return apiRequest<TenantDetail>({
    path: `/api/admin/tenants/${tenantId}`,
    token
  });
}

export async function createTenant(token: string, request: CreateTenantRequest): Promise<TenantSummary> {
  return apiRequest<TenantSummary, CreateTenantRequest>({
    path: '/api/admin/tenants',
    method: 'POST',
    body: request,
    token
  });
}

export async function updateTenant(
  token: string,
  tenantId: number,
  request: UpdateTenantRequest
): Promise<TenantDetail> {
  return apiRequest<TenantDetail, UpdateTenantRequest>({
    path: `/api/admin/tenants/${tenantId}`,
    method: 'PATCH',
    body: request,
    token
  });
}

export async function updateTenantStatus(
  token: string,
  tenantId: number,
  status: TenantSummary['status']
): Promise<TenantSummary> {
  return apiRequest<TenantSummary, { status: TenantSummary['status'] }>({
    path: `/api/admin/tenants/${tenantId}/status`,
    method: 'PATCH',
    body: { status },
    token
  });
}

export async function listPlans(token: string): Promise<PlanSummary[]> {
  return apiRequest<PlanSummary[]>({
    path: '/api/admin/plans',
    token
  });
}

export async function createPlan(token: string, request: CreatePlanRequest): Promise<PlanSummary> {
  return apiRequest<PlanSummary, CreatePlanRequest>({
    path: '/api/admin/plans',
    method: 'POST',
    body: request,
    token
  });
}

export async function updatePlan(token: string, planId: number, request: UpdatePlanRequest): Promise<PlanSummary> {
  return apiRequest<PlanSummary, UpdatePlanRequest>({
    path: `/api/admin/plans/${planId}`,
    method: 'PATCH',
    body: request,
    token
  });
}

export async function updatePlanStatus(
  token: string,
  planId: number,
  status: PlanSummary['status']
): Promise<PlanSummary> {
  return apiRequest<PlanSummary, { status: PlanSummary['status'] }>({
    path: `/api/admin/plans/${planId}/status`,
    method: 'PATCH',
    body: { status },
    token
  });
}

export async function assignTenantPlan(
  token: string,
  request: { tenantId: number; planId: number; overrides: Record<string, number> }
): Promise<TenantQuotaSummary> {
  return apiRequest<TenantQuotaSummary, { tenantId: number; planId: number; overrides: Record<string, number> }>({
    path: '/api/admin/plans/assignments',
    method: 'POST',
    body: request,
    token
  });
}

export async function getTenantPlanAssignment(token: string, tenantId: number): Promise<TenantQuotaSummary> {
  return apiRequest<TenantQuotaSummary>({
    path: `/api/admin/plans/assignments/${tenantId}`,
    token
  });
}

export async function getTenantQuotaOverview(token: string): Promise<TenantQuotaOverview> {
  return apiRequest<TenantQuotaOverview>({
    path: '/api/admin/quota',
    token
  });
}

export async function listAuditLogs(
  token: string,
  filters: {
    tenantId?: number;
    actorUserId?: number;
    actionType?: string;
    result?: string;
    riskLevel?: string;
    createdFrom?: string;
    createdTo?: string;
  }
): Promise<AuditLogSummary[]> {
  const searchParams = new URLSearchParams();

  Object.entries(filters).forEach(([key, value]) => {
    if (value != null && value !== '') {
      searchParams.set(key, String(value));
    }
  });

  const query = searchParams.toString();

  return apiRequest<AuditLogSummary[]>({
    path: `/api/admin/audit${query ? `?${query}` : ''}`,
    token
  });
}

export async function getAuditLog(token: string, auditLogId: number): Promise<AuditLogDetail> {
  return apiRequest<AuditLogDetail>({
    path: `/api/admin/audit/${auditLogId}`,
    token
  });
}

export async function listChatbots(token: string, tenantId: number): Promise<ChatbotSummary[]> {
  return apiRequest<ChatbotSummary[]>({
    path: `/api/admin/chatbots?tenantId=${tenantId}`,
    token
  });
}

export async function createChatbot(token: string, request: CreateChatbotRequest): Promise<ChatbotSummary> {
  return apiRequest<ChatbotSummary, CreateChatbotRequest>({
    path: '/api/admin/chatbots',
    method: 'POST',
    body: request,
    token
  });
}

export async function getChatbot(token: string, chatbotId: number): Promise<ChatbotDetail> {
  return apiRequest<ChatbotDetail>({
    path: `/api/admin/chatbots/${chatbotId}`,
    token
  });
}

export async function updateChatbot(
  token: string,
  chatbotId: number,
  request: UpdateChatbotRequest
): Promise<ChatbotSummary> {
  return apiRequest<ChatbotSummary, UpdateChatbotRequest>({
    path: `/api/admin/chatbots/${chatbotId}`,
    method: 'PATCH',
    body: request,
    token
  });
}

export async function updateChatbotAppearance(
  token: string,
  chatbotId: number,
  request: UpdateChatbotAppearanceRequest
): Promise<ChatbotDetail> {
  return apiRequest<ChatbotDetail, UpdateChatbotAppearanceRequest>({
    path: `/api/admin/chatbots/${chatbotId}/appearance`,
    method: 'PATCH',
    body: request,
    token
  });
}

export async function updateChatbotBehavior(
  token: string,
  chatbotId: number,
  request: UpdateChatbotBehaviorRequest
): Promise<ChatbotDetail> {
  return apiRequest<ChatbotDetail, UpdateChatbotBehaviorRequest>({
    path: `/api/admin/chatbots/${chatbotId}/behavior`,
    method: 'PATCH',
    body: request,
    token
  });
}

export async function copyChatbot(token: string, chatbotId: number): Promise<ChatbotDetail> {
  return apiRequest<ChatbotDetail>({
    path: `/api/admin/chatbots/${chatbotId}/copy`,
    method: 'POST',
    token
  });
}

export async function deleteChatbot(token: string, chatbotId: number): Promise<ChatbotSummary> {
  return apiRequest<ChatbotSummary>({
    path: `/api/admin/chatbots/${chatbotId}`,
    method: 'DELETE',
    token
  });
}

export async function updateChatbotStatus(
  token: string,
  chatbotId: number,
  status: ChatbotSummary['status']
): Promise<ChatbotSummary> {
  return apiRequest<ChatbotSummary, { status: ChatbotSummary['status'] }>({
    path: `/api/admin/chatbots/${chatbotId}/status`,
    method: 'PATCH',
    body: { status },
    token
  });
}

export async function listFaqs(token: string, filters: ListFaqFilters): Promise<FaqSummary[]> {
  const searchParams = new URLSearchParams({
    tenantId: String(filters.tenantId),
    chatbotId: String(filters.chatbotId)
  });

  if (filters.language) {
    searchParams.set('language', filters.language);
  }

  if (filters.keyword) {
    searchParams.set('keyword', filters.keyword);
  }

  if (filters.status) {
    searchParams.set('status', filters.status);
  }

  return apiRequest<FaqSummary[]>({
    path: `/api/admin/faqs?${searchParams.toString()}`,
    token
  });
}

export async function createFaq(token: string, request: CreateFaqRequest): Promise<FaqSummary> {
  return apiRequest<FaqSummary, CreateFaqRequest>({
    path: '/api/admin/faqs',
    method: 'POST',
    body: request,
    token
  });
}

export async function updateFaq(token: string, faqId: number, request: UpdateFaqRequest): Promise<FaqSummary> {
  return apiRequest<FaqSummary, UpdateFaqRequest>({
    path: `/api/admin/faqs/${faqId}`,
    method: 'PATCH',
    body: request,
    token
  });
}

export async function updateFaqStatus(
  token: string,
  faqId: number,
  status: FaqSummary['status']
): Promise<FaqSummary> {
  return apiRequest<FaqSummary, { status: FaqSummary['status'] }>({
    path: `/api/admin/faqs/${faqId}/status`,
    method: 'PATCH',
    body: { status },
    token
  });
}

export async function updateFaqStatuses(
  token: string,
  faqIds: number[],
  status: FaqSummary['status']
): Promise<FaqSummary[]> {
  return apiRequest<FaqSummary[], { faqIds: number[]; status: FaqSummary['status'] }>({
    path: '/api/admin/faqs/status',
    method: 'PATCH',
    body: { faqIds, status },
    token
  });
}

export async function exportFaqs(token: string, tenantId: number, chatbotId: number): Promise<Blob> {
  const response = await fetch(`${resolveBaseUrl()}/api/admin/faqs/export?tenantId=${tenantId}&chatbotId=${chatbotId}`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  if (!response.ok) {
    const contentType = response.headers.get('Content-Type') ?? '';
    const responseBody = contentType.includes('application/json') ? await response.json() : null;
    throw new ApiRequestError(response.status, responseBody?.code);
  }

  return response.blob();
}

export async function importFaqs(
  token: string,
  tenantId: number,
  chatbotId: number,
  items: ImportFaqItem[]
): Promise<ImportFaqResult> {
  return apiRequest<ImportFaqResult, { tenantId: number; chatbotId: number; items: ImportFaqItem[] }>({
    path: '/api/admin/faqs/import',
    method: 'POST',
    body: { tenantId, chatbotId, items },
    token
  });
}

export async function listKnowledgeSources(
  token: string,
  tenantId: number,
  chatbotId: number
): Promise<KnowledgeSourceSummary[]> {
  return apiRequest<KnowledgeSourceSummary[]>({
    path: `/api/admin/knowledge-sources?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    token
  });
}

export async function getKnowledgeSource(
  token: string,
  tenantId: number,
  chatbotId: number,
  sourceId: number
): Promise<KnowledgeSourceDetail> {
  return apiRequest<KnowledgeSourceDetail>({
    path: `/api/admin/knowledge-sources/${sourceId}?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    token
  });
}

export async function refreshKnowledgeSource(
  token: string,
  tenantId: number,
  chatbotId: number,
  sourceId: number
): Promise<KnowledgeSourceDetail> {
  return apiRequest<KnowledgeSourceDetail>({
    path: `/api/admin/knowledge-sources/${sourceId}/refresh?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    method: 'POST',
    token
  });
}

export async function retryKnowledgeSource(
  token: string,
  tenantId: number,
  chatbotId: number,
  sourceId: number
): Promise<KnowledgeSourceDetail> {
  return apiRequest<KnowledgeSourceDetail>({
    path: `/api/admin/knowledge-sources/${sourceId}/retry?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    method: 'POST',
    token
  });
}

export async function updateKnowledgeSourceStatus(
  token: string,
  tenantId: number,
  chatbotId: number,
  sourceId: number,
  status: KnowledgeSourceDetail['status']
): Promise<KnowledgeSourceDetail> {
  return apiRequest<KnowledgeSourceDetail, { status: KnowledgeSourceDetail['status'] }>({
    path: `/api/admin/knowledge-sources/${sourceId}/status?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    method: 'PATCH',
    token,
    body: { status }
  });
}

export async function deleteKnowledgeSource(
  token: string,
  tenantId: number,
  chatbotId: number,
  sourceId: number
): Promise<KnowledgeSourceDetail> {
  return apiRequest<KnowledgeSourceDetail>({
    path: `/api/admin/knowledge-sources/${sourceId}?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    method: 'DELETE',
    token
  });
}

export async function uploadKnowledgeFile(
  token: string,
  tenantId: number,
  chatbotId: number,
  file: File
): Promise<KnowledgeSourceSummary> {
  const formData = new FormData();
  formData.append('tenantId', String(tenantId));
  formData.append('chatbotId', String(chatbotId));
  formData.append('file', file);

  const response = await fetch(`${resolveBaseUrl()}/api/admin/knowledge-sources/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });

  const contentType = response.headers.get('Content-Type') ?? '';
  const responseBody = contentType.includes('application/json') ? await response.json() : null;

  if (!response.ok) {
    throw new ApiRequestError(response.status, responseBody?.code);
  }

  return responseBody as KnowledgeSourceSummary;
}

export async function createWebKnowledgeSource(
  token: string,
  tenantId: number,
  chatbotId: number,
  payload: CreateWebKnowledgeSourceRequest
): Promise<KnowledgeSourceSummary> {
  return apiRequest<KnowledgeSourceSummary>({
    path: `/api/admin/knowledge-sources/web?tenantId=${tenantId}&chatbotId=${chatbotId}`,
    method: 'POST',
    token,
    body: payload
  });
}

export async function listConversations(
  token: string,
  filters: { chatbotId?: number; status?: ConversationSummary['status'] }
): Promise<ConversationSummary[]> {
  const searchParams = new URLSearchParams();

  if (filters.chatbotId != null) {
    searchParams.set('chatbotId', String(filters.chatbotId));
  }

  if (filters.status) {
    searchParams.set('status', filters.status);
  }

  const query = searchParams.toString();

  return apiRequest<ConversationSummary[]>({
    path: `/api/admin/conversations${query ? `?${query}` : ''}`,
    token
  });
}

export async function getConversation(token: string, conversationId: number): Promise<ConversationDetail> {
  return apiRequest<ConversationDetail>({
    path: `/api/admin/conversations/${conversationId}`,
    token
  });
}

export async function exportConversation(token: string, conversationId: number): Promise<Blob> {
  const response = await fetch(`${resolveBaseUrl()}/api/admin/conversations/${conversationId}/export`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  if (!response.ok) {
    const contentType = response.headers.get('Content-Type') ?? '';
    const responseBody = contentType.includes('application/json') ? await response.json() : null;
    throw new ApiRequestError(response.status, responseBody?.code);
  }

  return response.blob();
}

export async function updateConversationStatus(
  token: string,
  conversationId: number,
  status: ConversationSummary['status']
): Promise<ConversationSummary> {
  return apiRequest<ConversationSummary, { status: ConversationSummary['status'] }>({
    path: `/api/admin/conversations/${conversationId}/status`,
    method: 'PATCH',
    body: { status },
    token
  });
}

export async function deleteConversation(token: string, conversationId: number): Promise<ConversationSummary> {
  return apiRequest<ConversationSummary>({
    path: `/api/admin/conversations/${conversationId}`,
    method: 'DELETE',
    token
  });
}
