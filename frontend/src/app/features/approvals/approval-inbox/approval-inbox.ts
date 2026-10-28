import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Deployment, PendingApproval } from '../../../core/models/api.model';
import { AuthService } from '../../../core/services/auth.service';
import { DeploymentService } from '../../../core/services/deployment.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { LoadingState } from '../../../shared/components/loading-state/loading-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  selector: 'app-approval-inbox',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, LoadingState, ErrorState],
  templateUrl: './approval-inbox.html',
  styleUrl: './approval-inbox.scss',
})
export class ApprovalInbox implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly deployments = inject(DeploymentService);
  private readonly auth = inject(AuthService);

  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly acting = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly items = signal<PendingApproval[]>([]);
  readonly selected = signal<PendingApproval | null>(null);
  readonly detail = signal<Deployment | null>(null);
  readonly canApprove = this.auth.canApprove();

  readonly filters = this.fb.nonNullable.group({
    projectId: [''],
    environment: [''],
  });

  readonly decision = this.fb.nonNullable.group({
    comment: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const raw = this.filters.getRawValue();
    this.deployments
      .getPendingApprovals({
        projectId: raw.projectId || undefined,
        environment: raw.environment || undefined,
      })
      .subscribe({
        next: (data) => {
          this.items.set(data ?? []);
          this.loading.set(false);
          const current = this.selected();
          if (current) {
            const still = (data ?? []).find((i) => i.id === current.id);
            if (still) {
              this.select(still);
            } else {
              this.selected.set(null);
              this.detail.set(null);
            }
          }
        },
        error: (err: unknown) => {
          this.error.set(extractErrorMessage(err, 'Failed to load approvals'));
          this.loading.set(false);
        },
      });
  }

  select(item: PendingApproval): void {
    this.selected.set(item);
    this.actionError.set(null);
    this.detailLoading.set(true);
    this.deployments.getById(item.id).subscribe({
      next: (dep) => {
        this.detail.set(dep);
        this.detailLoading.set(false);
      },
      error: (err: unknown) => {
        this.actionError.set(extractErrorMessage(err, 'Failed to load deployment detail'));
        this.detail.set(null);
        this.detailLoading.set(false);
      },
    });
  }

  approve(): void {
    this.decide(true);
  }

  reject(): void {
    this.decide(false);
  }

  private decide(approve: boolean): void {
    const item = this.selected();
    const detail = this.detail() ?? item;
    if (!item || !detail || !this.canApprove) {
      return;
    }
    this.acting.set(true);
    this.actionError.set(null);
    const body = {
      comment: this.decision.controls.comment.value || undefined,
      checksum: detail.checksum,
      artifactDigest: detail.artifactDigest,
    };
    const request$ = approve
      ? this.deployments.approve(item.id, body)
      : this.deployments.reject(item.id, body);

    request$.subscribe({
      next: () => {
        this.acting.set(false);
        this.decision.reset({ comment: '' });
        this.selected.set(null);
        this.detail.set(null);
        this.load();
      },
      error: (err: unknown) => {
        this.acting.set(false);
        this.actionError.set(
          extractErrorMessage(err, approve ? 'Approve failed' : 'Reject failed'),
        );
      },
    });
  }
}
