import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EnvironmentInfo, Project } from '../../../core/models/api.model';
import { ProjectService } from '../../../core/services/project.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { LoadingState } from '../../../shared/components/loading-state/loading-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  selector: 'app-environment-list',
  standalone: true,
  imports: [RouterLink, LoadingState, ErrorState],
  templateUrl: './environment-list.html',
  styleUrl: './environment-list.scss',
})
export class EnvironmentList implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly projects = inject(ProjectService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly project = signal<Project | null>(null);
  readonly environments = signal<EnvironmentInfo[]>([]);
  readonly projectId = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.projectId.set(id);
    this.load();
  }

  load(): void {
    const id = this.projectId();
    if (!id) {
      this.error.set('Missing project id');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.projects.get(id).subscribe({
      next: (project) => this.project.set(project),
      error: () => undefined,
    });

    this.projects.getEnvironments(id).subscribe({
      next: (envs) => {
        this.environments.set(envs ?? []);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(extractErrorMessage(err, 'Failed to load environments'));
        this.loading.set(false);
      },
    });
  }
}
