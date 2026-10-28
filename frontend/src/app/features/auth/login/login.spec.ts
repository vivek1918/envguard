import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let auth: {
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    auth = {
      login: vi.fn().mockReturnValue(
        of({
          token: 't',
          user: { id: '1', username: 'admin', roles: ['ADMINISTRATOR'] },
        }),
      ),
      register: vi.fn().mockReturnValue(
        of({
          token: 't',
          user: { id: '1', username: 'admin', roles: ['ADMINISTRATOR'] },
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([{ path: 'dashboard', children: [] }]),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should require username and password before submit', () => {
    component.submit();
    expect(auth.login).not.toHaveBeenCalled();
    expect(component.form.invalid).toBe(true);
  });

  it('should call login with credentials', () => {
    component.form.setValue({
      username: 'admin',
      password: 'password1',
      email: '',
    });
    component.submit();
    expect(auth.login).toHaveBeenCalledWith({ username: 'admin', password: 'password1' });
  });

  it('should call register with required email', () => {
    component.mode.set('register');
    component.form.setValue({
      username: 'admin',
      password: 'password1',
      email: 'a@b.co',
    });
    component.submit();
    expect(auth.register).toHaveBeenCalledWith({
      username: 'admin',
      password: 'password1',
      email: 'a@b.co',
    });
  });

  it('should surface login errors', () => {
    auth.login.mockReturnValueOnce(throwError(() => new Error('bad creds')));
    component.form.setValue({
      username: 'admin',
      password: 'password1',
      email: '',
    });
    component.submit();
    expect(component.error()).toContain('bad creds');
    expect(component.loading()).toBe(false);
  });
});
