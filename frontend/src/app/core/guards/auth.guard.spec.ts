import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('auth guards', () => {
  let auth: {
    isAuthenticated: ReturnType<typeof vi.fn>;
    hasRole: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    auth = {
      isAuthenticated: vi.fn(),
      hasRole: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('authGuard allows authenticated users', () => {
    auth.isAuthenticated.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  it('authGuard redirects anonymous users to login', () => {
    auth.isAuthenticated.mockReturnValue(false);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(String(result)).toContain('login');
  });

  it('guestGuard redirects authenticated users to dashboard', () => {
    auth.isAuthenticated.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(String(result)).toContain('dashboard');
  });

  it('roleGuard enforces roles', () => {
    auth.isAuthenticated.mockReturnValue(true);
    auth.hasRole.mockReturnValue(false);
    const guard = roleGuard('ADMINISTRATOR');
    const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    expect(String(result)).toContain('dashboard');
  });

  it('roleGuard allows matching role', () => {
    auth.isAuthenticated.mockReturnValue(true);
    auth.hasRole.mockReturnValue(true);
    const guard = roleGuard('ADMINISTRATOR');
    const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    expect(result).toBe(true);
  });
});
