import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly mode = signal<'login' | 'register'>('login');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    email: [''],
  });

  toggleMode(): void {
    this.error.set(null);
    this.mode.update((m) => (m === 'login' ? 'register' : 'login'));
  }

  fieldError(controlName: 'username' | 'password' | 'email'): string | null {
    const control = this.form.controls[controlName];
    if (!control.touched || !control.invalid) {
      return null;
    }
    if (control.hasError('required')) {
      return `${controlName === 'email' ? 'Email' : controlName === 'username' ? 'Username' : 'Password'} is required`;
    }
    if (control.hasError('minlength')) {
      const min = control.getError('minlength')?.requiredLength as number;
      return controlName === 'password'
        ? `Password must be at least ${min} characters`
        : `Username must be at least ${min} characters`;
    }
    if (control.hasError('email')) {
      return 'Enter a valid email address';
    }
    return 'Invalid value';
  }

  submit(): void {
    if (this.mode() === 'register') {
      this.form.controls.email.setValidators([Validators.required, Validators.email]);
    } else {
      this.form.controls.email.clearValidators();
    }
    this.form.controls.email.updateValueAndValidity();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(
        this.mode() === 'register'
          ? 'Fix the highlighted fields to create an account (password needs 8+ characters).'
          : 'Enter a valid username and password to sign in.',
      );
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    const { username, password, email } = this.form.getRawValue();
    const request$ =
      this.mode() === 'login'
        ? this.auth.login({ username, password })
        : this.auth.register({
            username,
            password,
            email,
          });

    request$.subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
        void this.router.navigateByUrl(returnUrl);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(
          extractErrorMessage(
            err,
            this.mode() === 'login' ? 'Login failed' : 'Registration failed',
          ),
        );
      },
    });
  }
}
