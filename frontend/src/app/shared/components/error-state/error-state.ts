import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-error-state',
  standalone: true,
  template: `
    <div class="state state--error" role="alert">
      <h3>{{ title }}</h3>
      <p>{{ message }}</p>
      @if (retryable) {
        <button type="button" class="btn btn--ghost" (click)="retry.emit()">Retry</button>
      }
    </div>
  `,
  styles: [
    `
      .state {
        display: grid;
        gap: 0.5rem;
        padding: 1.5rem;
        border: 1px solid color-mix(in srgb, var(--eg-danger) 35%, transparent);
        background: color-mix(in srgb, var(--eg-danger) 8%, transparent);
        border-radius: var(--eg-radius);
      }
      h3 {
        margin: 0;
        font-size: 1rem;
        color: var(--eg-danger);
      }
      p {
        margin: 0;
        color: var(--eg-text);
        font-size: 0.92rem;
      }
    `,
  ],
})
export class ErrorState {
  @Input() title = 'Something went wrong';
  @Input() message = 'Unable to load data.';
  @Input() retryable = true;
  @Output() readonly retry = new EventEmitter<void>();
}
