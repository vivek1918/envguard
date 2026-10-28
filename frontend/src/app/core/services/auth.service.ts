import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, map, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  User,
  UserRole,
  primaryRole,
} from '../models/api.model';

const TOKEN_KEY = 'envguard_token';
const USER_KEY = 'envguard_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = `${environment.apiUrl}/auth`;

  private readonly userSignal = signal<User | null>(this.readStoredUser());
  private readonly tokenSignal = signal<string | null>(this.readStoredToken());

  readonly user = this.userSignal.asReadonly();
  readonly token = this.tokenSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal());
  readonly role = computed(() => primaryRole(this.userSignal()));

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.base}/login`, credentials)
      .pipe(
        map((res) => this.normalizeAuth(this.unwrap(res))),
        tap((auth) => this.persistSession(auth)),
      );
  }

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.base}/register`, payload)
      .pipe(
        map((res) => this.normalizeAuth(this.unwrap(res))),
        tap((auth) => this.persistSession(auth)),
      );
  }

  me(): Observable<User | null> {
    return this.http.get<ApiResponse<User>>(`${this.base}/me`).pipe(
      map((res) => this.normalizeUser(this.unwrap(res))),
      tap((user) => {
        this.userSignal.set(user);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
      }),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  logout(redirect = true): void {
    this.clearSession();
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  hasRole(...roles: UserRole[]): boolean {
    const userRoles = this.userSignal()?.roles ?? [];
    return roles.some((r) => userRoles.includes(r));
  }

  canManageProjects(): boolean {
    return this.hasRole('ADMINISTRATOR', 'DEVELOPER');
  }

  canApprove(): boolean {
    return this.hasRole('ADMINISTRATOR', 'DEVELOPER');
  }

  private normalizeAuth(auth: AuthResponse): AuthResponse {
    return { ...auth, user: this.normalizeUser(auth.user) };
  }

  private normalizeUser(user: User): User {
    const roles = Array.isArray(user?.roles)
      ? (user.roles as UserRole[])
      : user && (user as unknown as { role?: UserRole }).role
        ? [(user as unknown as { role: UserRole }).role]
        : [];
    return { ...user, roles };
  }

  private persistSession(auth: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, auth.token);
    localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
    this.tokenSignal.set(auth.token);
    this.userSignal.set(auth.user);
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenSignal.set(null);
    this.userSignal.set(null);
  }

  private readStoredToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private readStoredUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return this.normalizeUser(JSON.parse(raw) as User);
    } catch {
      return null;
    }
  }

  private unwrap<T>(res: ApiResponse<T>): T {
    if (!res?.success) {
      throw new Error(res?.message || 'Request failed');
    }
    return res.data;
  }
}
