/** Shapes aligned with EnvGuard backend ApiResponse + service view DTOs. */

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string | null;
  correlationId?: string | null;
}

export type UserRole = 'ADMINISTRATOR' | 'DEVELOPER' | 'VIEWER';

export interface User {
  id: string;
  username: string;
  email?: string | null;
  /** Backend AuthService.UserView.roles */
  roles: UserRole[];
  enabled?: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

/** Backend DashboardService.Stats */
export interface DashboardStats {
  projects: number;
  users: number;
  pendingApprovals: number;
  approved: number;
  deployed: number;
  failed: number;
  rejected: number;
  timedOut: number;
  auditEvents: number;
  byStatus?: Record<string, number>;
}

export interface ProjectMember {
  userId: string;
  username?: string | null;
  role: string;
}

/** Backend ProjectService.ProjectView */
export interface Project {
  id: string;
  name: string;
  description?: string | null;
  ownerId: string;
  defaultBranch: string;
  members?: ProjectMember[];
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  defaultBranch?: string;
}

export interface UpdateProjectRequest {
  description?: string;
  defaultBranch?: string;
}

export type EnvironmentName = 'DEV' | 'STAGING' | 'PRODUCTION' | string;

/** Backend EnvironmentService.EnvironmentView */
export interface EnvironmentInfo {
  id: string;
  projectId: string;
  name: EnvironmentName;
  requiresSecondApprover: boolean;
  approvalTimeoutMinutes: number;
  approverUserIds?: string[];
}

/** Backend DeploymentService.DeploymentView */
export interface Deployment {
  id: string;
  projectId: string;
  projectName?: string | null;
  environmentId: string;
  environment: string;
  revisionId: string;
  checksum: string;
  artifactDigest: string;
  commitSha?: string | null;
  buildId?: string | null;
  status: string;
  requesterIdentity?: string | null;
  idempotencyKey?: string | null;
  expiresAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  approvalCount: number;
  requiresSecondApprover: boolean;
}

/** Pending approvals endpoint returns DeploymentView list. */
export type PendingApproval = Deployment;

export interface DecisionRequest {
  checksum?: string;
  artifactDigest?: string;
  comment?: string;
}

export interface ApprovalFilters {
  projectId?: string;
  environment?: string;
}

/** Backend AuditEvent entity (Spring Page content). */
export interface AuditEntry {
  id: string;
  projectId?: string | null;
  userId?: string | null;
  environmentId?: string | null;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  detailsJson?: string | null;
  correlationId?: string | null;
  createdAt?: string | null;
}

export interface AuditQuery {
  projectId?: string;
  userId?: string;
  action?: string;
  page?: number;
  size?: number;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export function primaryRole(user: User | null | undefined): UserRole | null {
  if (!user?.roles?.length) {
    return null;
  }
  if (user.roles.includes('ADMINISTRATOR')) {
    return 'ADMINISTRATOR';
  }
  if (user.roles.includes('DEVELOPER')) {
    return 'DEVELOPER';
  }
  if (user.roles.includes('VIEWER')) {
    return 'VIEWER';
  }
  return user.roles[0];
}
