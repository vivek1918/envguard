import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  path: string;
  roles?: Array<'ADMINISTRATOR' | 'DEVELOPER' | 'VIEWER'>;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly navItems = computed(() => {
    const all: NavItem[] = [
      { label: 'Dashboard', path: '/dashboard' },
      { label: 'Projects', path: '/projects' },
      {
        label: 'Approvals',
        path: '/approvals',
        roles: ['ADMINISTRATOR', 'DEVELOPER'],
      },
      { label: 'Audit', path: '/audit' },
    ];
    return all.filter((item) => !item.roles || this.auth.hasRole(...item.roles));
  });

  logout(): void {
    this.auth.logout();
  }
}
