import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type LoanRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface CreateLoanRequestRequest {
  requestReference: string;
  clientCode: string;
  amount: number;
  termMonths: number;
}

export interface LoanRequestResponse {
  requestReference: string;
  status: LoanRequestStatus;
  resultReason: string | null;
  processedAt: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string | null;
  path: string;
}

@Injectable({ providedIn: 'root' })
export class LoanRequestApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/loan-requests';

  create(request: CreateLoanRequestRequest): Observable<LoanRequestResponse> {
    return this.http.post<LoanRequestResponse>(this.baseUrl, request);
  }

  findRecent(): Observable<LoanRequestResponse[]> {
    return this.http.get<LoanRequestResponse[]>(`${this.baseUrl}/recent`);
  }
}
