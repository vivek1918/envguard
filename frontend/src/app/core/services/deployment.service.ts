import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  ApprovalFilters,
  DecisionRequest,
  Deployment,
  PendingApproval,
} from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class DeploymentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/deployments`;

  getPendingApprovals(filters: ApprovalFilters = {}): Observable<PendingApproval[]> {
    let params = new HttpParams();
    if (filters.projectId) {
      params = params.set('projectId', filters.projectId);
    }
    if (filters.environment) {
      params = params.set('environment', filters.environment);
    }
    return this.http
      .get<ApiResponse<PendingApproval[]>>(`${this.base}/pending-approvals`, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  getById(id: string): Observable<Deployment> {
    return this.http
      .get<ApiResponse<Deployment>>(`${this.base}/${id}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  getAuditTrail(id: string): Observable<Record<string, unknown>[]> {
    return this.http
      .get<ApiResponse<Record<string, unknown>[]>>(`${this.base}/${id}/audit`)
      .pipe(map((res) => this.unwrap(res)));
  }

  approve(id: string, body: DecisionRequest): Observable<Deployment> {
    return this.http
      .post<ApiResponse<Deployment>>(`${this.base}/${id}/approve`, body)
      .pipe(map((res) => this.unwrap(res)));
  }

  reject(id: string, body: DecisionRequest = {}): Observable<Deployment> {
    return this.http
      .post<ApiResponse<Deployment>>(`${this.base}/${id}/reject`, body)
      .pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: ApiResponse<T>): T {
    if (!res?.success) {
      throw new Error(res?.message || 'Request failed');
    }
    return res.data;
  }
}
