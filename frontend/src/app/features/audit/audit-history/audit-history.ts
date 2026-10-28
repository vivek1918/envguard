import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuditEntry } from '../../../core/models/api.model';
import { AuditService } from '../../../core/services/audit.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { LoadingState } from '../../../shared/components/loading-state/loading-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  selector: 'app-audit-history',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, LoadingState, ErrorState],
  templateUrl: './audit-history.html',
  styleUrl: './audit-history.scss',
})
export class AuditHistory implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly audit = inject(AuditService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly entries = signal<AuditEntry[]>([]);
  readonly total = signal<number | null>(null);

  readonly filters = this.fb.nonNullable.group({
    projectId: [''],
    userId: [''],
    action: [''],
  });

  ngOnInit(): void {
    this.search();
  }

  search(): void {
    this.loading.set(true);
    this.error.set(null);
    const raw = this.filters.getRawValue();
    this.audit
      .search({
        projectId: raw.projectId || undefined,
        userId: raw.userId || undefined,
        action: raw.action || undefined,
        page: 0,
        size: 100,
      })
      .subscribe({
        next: (page) => {
          this.entries.set(page.content ?? []);
          this.total.set(page.totalElements ?? page.content?.length ?? 0);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(extractErrorMessage(err, 'Failed to load audit history'));
          this.loading.set(false);
        },
      });
  }
}
