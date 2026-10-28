import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardStats } from '../../core/models/api.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { extractErrorMessage } from '../../shared/utils/http-error';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, LoadingState, ErrorState],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly stats = signal<DashboardStats | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.getStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(extractErrorMessage(err, 'Failed to load dashboard'));
        this.loading.set(false);
      },
    });
  }

  statusEntries(byStatus: Record<string, number> | undefined): Array<{ key: string; value: number }> {
    if (!byStatus) {
      return [];
    }
    return Object.entries(byStatus).map(([key, value]) => ({ key, value }));
  }
}
