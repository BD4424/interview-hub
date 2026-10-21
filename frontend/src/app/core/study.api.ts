import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map } from 'rxjs';
import { environment } from '../../environments/environment';
import * as S from './study.models';

/** Endpoints for topic pages, learning paths, flashcards, interview plans and the weekly report. */
@Injectable({ providedIn: 'root' })
export class StudyApi {
  private http = inject(HttpClient);
  private base = environment.apiBase;

  topicOverview(id: number) { return this.http.get<S.TopicOverview>(`${this.base}/topics/${id}/overview`); }
  saveNotes(id: number, notes: string) { return this.http.put<S.TopicOverview>(`${this.base}/topics/${id}/notes`, { notes }); }
  path(id: number) { return this.http.get<S.LearningPath>(`${this.base}/topics/${id}/path`); }
  savePath(id: number, tagIds: number[]) { return this.http.put<S.LearningPath>(`${this.base}/topics/${id}/path`, { tagIds }); }
  resetPath(id: number) { return this.http.delete<S.LearningPath>(`${this.base}/topics/${id}/path`); }

  deck(f: { topicId?: number | null; companyId?: number | null; tagIds?: number[]; scope: S.FlashScope; limit: number; answeredOnly: boolean }) {
    let p = new HttpParams().set('scope', f.scope).set('limit', f.limit).set('answeredOnly', f.answeredOnly);
    if (f.topicId) p = p.set('topicId', f.topicId);
    if (f.companyId) p = p.set('companyId', f.companyId);
    (f.tagIds ?? []).forEach(t => p = p.append('tagIds', t));
    return this.http.get<S.FlashDeck>(`${this.base}/flashcards`, { params: p });
  }
  grade(questionId: number, grade: S.FlashGrade) { return this.http.post<S.GradeResult>(`${this.base}/flashcards/${questionId}/grade`, { grade }); }

  plans() { return this.http.get<S.PlanSummary[]>(`${this.base}/plans`); }
  upcomingPlan() {
    return this.http.get<S.PlanSummary | null>(`${this.base}/plans/upcoming`, { observe: 'response' }).pipe(map(r => r.status === 204 ? null : r.body));
  }
  plan(id: number) { return this.http.get<S.PlanDetail>(`${this.base}/plans/${id}`); }
  createPlan(r: S.PlanRequest) { return this.http.post<S.PlanDetail>(`${this.base}/plans`, r); }
  updatePlan(id: number, r: S.PlanRequest) { return this.http.put<S.PlanDetail>(`${this.base}/plans/${id}`, r); }
  deletePlan(id: number) { return this.http.delete<void>(`${this.base}/plans/${id}`); }

  weekly() { return this.http.get<S.WeeklyReport>(`${this.base}/reports/weekly`); }
}
