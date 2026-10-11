import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import {
  ApiError,
  CreateLoanRequestRequest,
  LoanRequestApi,
  LoanRequestResponse,
} from './loan-request-api';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly api = inject(LoanRequestApi);

  protected readonly requestReference = signal('');
  protected readonly clientCode = signal('');
  protected readonly amount = signal('100000');
  protected readonly termMonths = signal('12');

  protected readonly submitting = signal(false);
  protected readonly loadingRecent = signal(false);
  protected readonly result = signal<LoanRequestResponse | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly recent = signal<LoanRequestResponse[]>([]);

  constructor() {
    this.loadRecent();
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const request: CreateLoanRequestRequest = {
      requestReference: this.requestReference().trim(),
      clientCode: this.clientCode().trim(),
      amount: this.numberOrZero(this.amount()),
      termMonths: this.numberOrZero(this.termMonths()),
    };

    this.submitting.set(true);
    this.result.set(null);
    this.errorMessage.set(null);

    this.api.create(request).subscribe({
      next: (response) => {
        this.result.set(response);
        this.submitting.set(false);
        this.loadRecent();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.describeError(error));
        this.submitting.set(false);
      },
    });
  }

  protected loadRecent(): void {
    this.loadingRecent.set(true);
    this.api.findRecent().subscribe({
      next: (requests) => {
        this.recent.set(requests);
        this.loadingRecent.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.describeError(error));
        this.loadingRecent.set(false);
      },
    });
  }

  protected formatProcessedAt(value: string): string {
    return new Date(value).toLocaleString();
  }

  private numberOrZero(value: string): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private describeError(error: HttpErrorResponse): string {
    const apiError = error.error as ApiError | null;
    if (apiError?.message) {
      return `${apiError.status} ${apiError.error}: ${apiError.message}`;
    }
    return `Error ${error.status || ''} ${error.message}`.trim();
  }
}
