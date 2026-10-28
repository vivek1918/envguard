import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardService } from '../../core/services/dashboard.service';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let component: Dashboard;
  let dashboardService: { getStats: ReturnType<typeof vi.fn> };

  const stats = {
    projects: 3,
    users: 5,
    pendingApprovals: 4,
    approved: 2,
    deployed: 8,
    failed: 1,
    rejected: 1,
    timedOut: 0,
    auditEvents: 12,
    byStatus: { READY_FOR_APPROVAL: 4, DEPLOYED: 8 },
  };

  beforeEach(async () => {
    dashboardService = {
      getStats: vi.fn().mockReturnValue(of(stats)),
    };

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: dashboardService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
  });

  it('loads stats on init', () => {
    fixture.detectChanges();
    expect(dashboardService.getStats).toHaveBeenCalled();
    expect(component.loading()).toBe(false);
    expect(component.stats()?.projects).toBe(3);
    expect(component.error()).toBeNull();
  });

  it('sets error state when loading fails', () => {
    dashboardService.getStats.mockReturnValueOnce(throwError(() => new Error('boom')));
    fixture.detectChanges();
    expect(component.error()).toContain('boom');
    expect(component.loading()).toBe(false);
  });
});
