import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Deployment } from '../../../core/models/api.model';
import { DeploymentService } from '../../../core/services/deployment.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { LoadingState } from '../../../shared/components/loading-state/loading-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  selector: 'app-deployment-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, LoadingState, ErrorState],
  templateUrl: './deployment-detail.html',
  styleUrl: './deployment-detail.scss',
})
export class DeploymentDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly deployments = inject(DeploymentService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly deployment = signal<Deployment | null>(null);
  readonly trail = signal<Record<string, unknown>[]>([]);
  readonly id = signal('');

  ngOnInit(): void {
    this.id.set(this.route.snapshot.paramMap.get('id') || '');
    this.load();
  }

  load(): void {
    const id = this.id();
    if (!id) {
      this.error.set('Missing deployment id');
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.deployments.getById(id).subscribe({
      next: (data) => {
        this.deployment.set(data);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(extractErrorMessage(err, 'Failed to load deployment'));
        this.loading.set(false);
      },
    });
    this.deployments.getAuditTrail(id).subscribe({
      next: (data) => this.trail.set(data ?? []),
      error: () => this.trail.set([]),
    });
  }
}
