import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CreateProjectRequest,
  EnvironmentInfo,
  Project,
  UpdateProjectRequest,
} from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/projects`;

  list(): Observable<Project[]> {
    return this.http
      .get<ApiResponse<Project[]>>(`${this.base}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  get(id: string): Observable<Project> {
    return this.http
      .get<ApiResponse<Project>>(`${this.base}/${id}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  create(payload: CreateProjectRequest): Observable<Project> {
    return this.http
      .post<ApiResponse<Project>>(`${this.base}`, payload)
      .pipe(map((res) => this.unwrap(res)));
  }

  update(id: string, payload: UpdateProjectRequest): Observable<Project> {
    return this.http
      .put<ApiResponse<Project>>(`${this.base}/${id}`, payload)
      .pipe(map((res) => this.unwrap(res)));
  }

  getEnvironments(projectId: string): Observable<EnvironmentInfo[]> {
    return this.http
      .get<ApiResponse<EnvironmentInfo[]>>(`${this.base}/${projectId}/environments`)
      .pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: ApiResponse<T>): T {
    if (!res?.success) {
      throw new Error(res?.message || 'Request failed');
    }
    return res.data;
  }
}
