import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { LoadingState } from '../../../shared/components/loading-state/loading-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LoadingState, ErrorState],
  templateUrl: './project-form.html',
  styleUrl: './project-form.scss',
})
export class ProjectForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly projects = inject(ProjectService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly bootstrapping = signal(false);
  readonly error = signal<string | null>(null);
  readonly projectId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
    defaultBranch: ['main', [Validators.required]],
  });

  get isEdit(): boolean {
    return !!this.projectId();
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.projectId.set(id);
      this.bootstrapping.set(true);
      this.form.controls.name.disable();
      this.projects.get(id).subscribe({
        next: (project) => {
          this.form.patchValue({
            name: project.name,
            description: project.description || '',
            defaultBranch: project.defaultBranch || 'main',
          });
          this.bootstrapping.set(false);
        },
        error: (err: unknown) => {
          this.error.set(extractErrorMessage(err, 'Failed to load project'));
          this.bootstrapping.set(false);
        },
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);

    const id = this.projectId();
    const request$ = id
      ? this.projects.update(id, {
          description: raw.description || undefined,
          defaultBranch: raw.defaultBranch,
        })
      : this.projects.create({
          name: raw.name,
          description: raw.description || undefined,
          defaultBranch: raw.defaultBranch,
        });

    request$.subscribe({
      next: (project) => {
        this.loading.set(false);
        void this.router.navigate(['/projects', project.id, 'environments']);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(extractErrorMessage(err, 'Failed to save project'));
      },
    });
  }
}
