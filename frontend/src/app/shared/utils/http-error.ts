import { HttpErrorResponse } from '@angular/common/http';

export function extractErrorMessage(err: unknown, fallback = 'Unexpected error'): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string; data?: { message?: string } } | string | null;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object') {
      if (body.message) {
        return body.message;
      }
      if (body.data?.message) {
        return body.data.message;
      }
    }
    if (err.status === 0) {
      return 'Unable to reach the API. Is the backend running?';
    }
    if (err.status === 403) {
      return 'You do not have permission for this action.';
    }
    if (err.status === 404) {
      return 'Resource not found.';
    }
    return err.message || fallback;
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
}
