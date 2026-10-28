import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guard';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'projects',
        loadComponent: () =>
          import('./features/projects/project-list/project-list').then((m) => m.ProjectList),
      },
      {
        path: 'projects/new',
        canActivate: [roleGuard('ADMINISTRATOR', 'DEVELOPER')],
        loadComponent: () =>
          import('./features/projects/project-form/project-form').then((m) => m.ProjectForm),
      },
      {
        path: 'projects/:id/edit',
        canActivate: [roleGuard('ADMINISTRATOR', 'DEVELOPER')],
        loadComponent: () =>
          import('./features/projects/project-form/project-form').then((m) => m.ProjectForm),
      },
      {
        path: 'projects/:id/environments',
        loadComponent: () =>
          import('./features/environments/environment-list/environment-list').then(
            (m) => m.EnvironmentList,
          ),
      },
      {
        path: 'projects/:id',
        loadComponent: () =>
          import('./features/environments/environment-list/environment-list').then(
            (m) => m.EnvironmentList,
          ),
      },
      {
        path: 'approvals',
        canActivate: [roleGuard('ADMINISTRATOR', 'DEVELOPER')],
        loadComponent: () =>
          import('./features/approvals/approval-inbox/approval-inbox').then(
            (m) => m.ApprovalInbox,
          ),
      },
      {
        path: 'deployments/:id',
        loadComponent: () =>
          import('./features/deployments/deployment-detail/deployment-detail').then(
            (m) => m.DeploymentDetail,
          ),
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/audit/audit-history/audit-history').then((m) => m.AuditHistory),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
