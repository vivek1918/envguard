import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, AuditEntry, AuditQuery, PageResult } from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/audit`;

  search(query: AuditQuery = {}): Observable<PageResult<AuditEntry>> {
    let params = new HttpParams();
    if (query.projectId) {
      params = params.set('projectId', query.projectId);
    }
    if (query.userId) {
      params = params.set('userId', query.userId);
    }
    if (query.action) {
      params = params.set('action', query.action);
    }
    if (query.page != null) {
      params = params.set('page', String(query.page));
    }
    if (query.size != null) {
      params = params.set('size', String(query.size));
    }
    return this.http
      .get<ApiResponse<PageResult<AuditEntry>>>(this.base, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: ApiResponse<T>): T {
    if (!res?.success) {
      throw new Error(res?.message || 'Request failed');
    }
    return res.data;
  }
}
