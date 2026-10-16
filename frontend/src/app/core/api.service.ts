import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import * as M from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = environment.apiBase;

  dashboard() { return this.http.get<M.Dashboard>(`${this.base}/dashboard`); }
  settings() { return this.http.get<M.Settings>(`${this.base}/settings`); }

  topics() { return this.http.get<M.Topic[]>(`${this.base}/topics`); }
  createTopic(name: string, description?: string) { return this.http.post<M.Topic>(`${this.base}/topics`, { name, description }); }
  deleteTopic(id: number) { return this.http.delete<void>(`${this.base}/topics/${id}`); }

  tags(q?: string) { return this.http.get<M.Tag[]>(`${this.base}/tags`, { params: q ? { q } : {} }); }
  createTag(name: string) { return this.http.post<M.Tag>(`${this.base}/tags`, { name }); }
  updateTag(id: number, name: string) { return this.http.put<M.Tag>(`${this.base}/tags/${id}`, { name }); }
  deleteTag(id: number) { return this.http.delete<void>(`${this.base}/tags/${id}`); }

  companies(q?: string) { return this.http.get<M.Company[]>(`${this.base}/companies`, { params: q ? { q } : {} }); }
  company(id: number) { return this.http.get<M.Company>(`${this.base}/companies/${id}`); }
  companySummary(id: number) { return this.http.get<M.CompanySummary>(`${this.base}/companies/${id}/summary`); }
  createCompany(body: Partial<M.Company>) { return this.http.post<M.Company>(`${this.base}/companies`, body); }

  questions(filter: Record<string, any>): Observable<M.Page<M.Question>> {
    let p = new HttpParams();
    Object.entries(filter).forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') return;
      if (Array.isArray(v)) v.forEach(x => p = p.append(k, String(x))); else p = p.set(k, String(v));
    });
    return this.http.get<M.Page<M.Question>>(`${this.base}/questions`, { params: p });
  }
  question(id: number) { return this.http.get<M.Question>(`${this.base}/questions/${id}`); }
  createQuestion(r: M.QuestionRequest) { return this.http.post<M.Question>(`${this.base}/questions`, r); }
  updateQuestion(id: number, r: M.QuestionRequest) { return this.http.put<M.Question>(`${this.base}/questions/${id}`, r); }
  deleteQuestion(id: number) { return this.http.delete<void>(`${this.base}/questions/${id}`); }

  // ---- imports
  private importForm(file: File, mapping?: Record<string, string> | null) {
    const fd = new FormData();
    fd.append('file', file);
    if (mapping && Object.keys(mapping).length) fd.append('columnMapping', JSON.stringify(mapping));
    return fd;
  }
  inspectImport(file: File, mapping?: Record<string, string> | null) {
    return this.http.post<M.InspectResult>(`${this.base}/imports/inspect`, this.importForm(file, mapping));
  }
  startImport(file: File, mode: M.AttributionMode, defaultCompanyId?: number | null, defaultTopicId?: number | null,
              tags?: string, mapping?: Record<string, string> | null) {
    const fd = this.importForm(file, mapping);
    fd.append('attributionMode', mode);
    if (defaultCompanyId) fd.append('defaultCompanyId', String(defaultCompanyId));
    if (defaultTopicId) fd.append('defaultTopicId', String(defaultTopicId));
    if (tags) fd.append('tags', tags);
    return this.http.post<M.ImportJob>(`${this.base}/imports`, fd);
  }
  importJob(id: number) { return this.http.get<M.ImportJob>(`${this.base}/imports/${id}`); }
  candidates(id: number) { return this.http.get<M.Candidate[]>(`${this.base}/imports/${id}/candidates`); }
  approve(id: number, candidateIds: number[]) { return this.http.post<M.ImportJob>(`${this.base}/imports/${id}/approve`, { candidateIds }); }
  resolve(id: number, cid: number, action: M.ReviewAction, reason?: string) {
    return this.http.post<M.Candidate>(`${this.base}/imports/${id}/candidates/${cid}/resolve`, { action, reason });
  }
  editCandidate(id: number, cid: number, body: any) { return this.http.put<M.Candidate>(`${this.base}/imports/${id}/candidates/${cid}`, body); }
  failedRowsUrl(id: number) { return `${this.base}/imports/${id}/failed-rows.csv`; }

  // ---- AI
  generateFollowUps(questionId: number, r: M.GenerateFollowUpsRequest) {
    return this.http.post<M.FollowUpBatch>(`${this.base}/questions/${questionId}/follow-ups/generate`, r);
  }
  saveFollowUp(questionId: number, item: M.FollowUpItem) {
    return this.http.post<M.SaveFollowUpResult>(`${this.base}/questions/${questionId}/follow-ups`, {
      question: item.question, expectedAnswerPoints: item.expectedAnswerPoints, difficulty: item.difficulty ?? null,
      tags: [...item.existingTags, ...item.suggestedNewTags], reason: item.reason
    });
  }
  followUps(questionId: number) { return this.http.get<M.LinkedFollowUp[]>(`${this.base}/questions/${questionId}/follow-ups`); }
  draftAnswer(questionId: number) { return this.http.post<M.DraftAnswer>(`${this.base}/questions/${questionId}/answer/generate`, {}); }
  decide(questionId: number, decision: 'APPROVE' | 'REJECT', applyTags: string[] = []) {
    return this.http.post<M.Question | null>(`${this.base}/questions/${questionId}/approval`, { decision, applyTags });
  }
}

export function errorMessage(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    const body = e.error as M.ApiError | null;
    if (body?.message) {
      let extra = '';
      if (body.code === 'VALIDATION_FAILED' && body.details) extra = ' — ' + Object.entries(body.details).map(([k, v]) => `${k}: ${v}`).join('; ');
      if (body.code === 'AI_INVALID_OUTPUT' && body.details?.['problems']) extra = ' — ' + (body.details['problems'] as string[]).join('; ');
      return body.message + extra;
    }
    return e.status === 0 ? 'Cannot reach the server. Is the backend running on :8080?' : `Request failed (${e.status})`;
  }
  return 'Unexpected error';
}
