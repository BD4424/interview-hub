import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Difficulty } from './models';
import * as M from './mock.models';

@Injectable({ providedIn: 'root' })
export class MockApi {
  private http = inject(HttpClient);
  private base = `${environment.apiBase}/mock-tests`;

  list() { return this.http.get<M.MockSummary[]>(this.base); }
  available(scope: M.MockScope, topicIds: number[], difficulty: Difficulty | null) {
    let p = new HttpParams().set('scope', scope);
    topicIds.forEach(t => p = p.append('topicIds', t));
    if (difficulty) p = p.set('difficulty', difficulty);
    return this.http.get<{ available: number }>(`${this.base}/available`, { params: p });
  }
  create(r: M.CreateMockRequest) { return this.http.post<M.MockTest>(this.base, r); }
  get(id: number) { return this.http.get<M.MockTest>(`${this.base}/${id}`); }
  save(id: number, itemId: number, body: { answer?: string; flagged?: boolean }) {
    return this.http.put<M.MockSaveResult>(`${this.base}/${id}/items/${itemId}`, body);
  }
  submit(id: number) { return this.http.post<M.MockTest>(`${this.base}/${id}/submit`, {}); }
  selfScore(id: number, scores: Record<number, number>) { return this.http.post<M.MockTest>(`${this.base}/${id}/self-score`, { scores }); }
  gradeWithAi(id: number) { return this.http.post<M.MockTest>(`${this.base}/${id}/grade-with-ai`, {}); }
  retryFailed(id: number) { return this.http.post<M.MockTest>(`${this.base}/${id}/retry-failed`, {}); }
  retake(id: number, onlyWeak: boolean, timeLimitMinutes?: number | null) {
    return this.http.post<M.MockTest>(`${this.base}/${id}/retake`, { onlyWeak, timeLimitMinutes: timeLimitMinutes ?? null });
  }
  delete(id: number) { return this.http.delete<void>(`${this.base}/${id}`); }
}
