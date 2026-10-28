import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  template: `
    <div class="state state--loading" role="status" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <p>{{ message }}</p>
    </div>
  `,
  styles: [
    `
      .state {
        display: grid;
        place-items: center;
        gap: 0.75rem;
        padding: 2.5rem 1rem;
        color: var(--eg-muted);
      }
      .spinner {
        width: 1.75rem;
        height: 1.75rem;
        border: 2px solid var(--eg-border);
        border-top-color: var(--eg-teal);
        border-radius: 50%;
        animation: spin 0.7s linear infinite;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
      p {
        margin: 0;
        font-size: 0.95rem;
      }
    `,
  ],
})
export class LoadingState {
  @Input() message = 'Loading…';
}
