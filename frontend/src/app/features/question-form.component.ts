import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, errorMessage } from '../core/api.service';
import { Company, DIFFICULTIES, Question, QUESTION_TYPES, QuestionRequest, Tag, Topic } from '../core/models';

@Component({
  selector: 'app-question-form', standalone: true, imports: [ReactiveFormsModule, RouterLink],
  template: `
  <h1>{{ id ? 'Edit question' : 'Add question' }}</h1>
  @if (error()) { <div class="banner error">{{ error() }}
    @if (duplicateId()) { — <a [routerLink]="['/questions', duplicateId(), 'edit']">open the existing question</a> to add companies or tags instead. }
  </div> }
  <form class="card" [formGroup]="f" (ngSubmit)="save()">
    <label>Question *</label>
    <textarea formControlName="questionText"></textarea>
    @if (f.controls.questionText.touched && f.controls.questionText.invalid) { <div class="error">Question text is required (max 10,000 characters).</div> }

    <label>Answer / explanation (optional)</label>
    <textarea formControlName="answerText" rows="6"></textarea>

    <div class="grid">
      <div><label>Primary topic *</label>
        <select formControlName="topicId"><option [ngValue]="null" disabled>Select…</option>
          @for (t of topics(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select>
        @if (f.controls.topicId.touched && f.controls.topicId.invalid) { <div class="error">Pick a primary topic.</div> }
      </div>
      <div><label>Difficulty</label><select formControlName="difficulty"><option [ngValue]="null">Unspecified</option>
        @for (d of difficulties; track d) { <option [ngValue]="d">{{ d }}</option> }</select></div>
      <div><label>Question type</label><select formControlName="questionType"><option [ngValue]="null">Unspecified</option>
        @for (t of types; track t) { <option [ngValue]="t">{{ t }}</option> }</select></div>
      <div><label>Answer status</label><select formControlName="answerStatus">
        <option [ngValue]="null">Auto</option><option value="UNVERIFIED">Unverified</option><option value="VERIFIED">Verified</option></select></div>
    </div>

    <label>Tags (comma separated; new tags are created)</label>
    <input formControlName="tags" list="tag-list" placeholder="HashMap, Collections">
    <datalist id="tag-list">@for (t of allTags(); track t.id) { <option [value]="t.name"></option> }</datalist>

    <h2>Company attribution (optional)</h2>
    <p class="muted">Only add a company when you know the question was asked there. Leave empty for unknown.</p>
    <datalist id="company-list">@for (c of allCompanies(); track c.id) { <option [value]="c.name"></option> }</datalist>
    <div formArrayName="companies">
      @for (g of companies.controls; track g; let i = $index) {
        <div class="card" [formGroupName]="i">
          <div class="grid">
            <div><label>Company * (existing or new)</label><input formControlName="companyName" list="company-list"></div>
            <div><label>Interview round</label><input formControlName="interviewRound" placeholder="e.g. Technical 1"></div>
            <div><label>Role</label><input formControlName="role"></div>
            <div><label>Job level</label><input formControlName="jobLevel"></div>
            <div><label>Date asked</label><input type="date" formControlName="dateAsked"></div>
            <div><label>Attribution</label><select formControlName="attributionStatus">
              <option value="REPORTED">Reported</option><option value="VERIFIED">Verified</option><option value="UNVERIFIED">Unverified</option></select></div>
          </div>
          <label>Source / evidence</label><input formControlName="sourceReference">
          <button type="button" class="secondary" style="margin-top:.5rem" (click)="removeCompany(i)">Remove company</button>
        </div>
      }
    </div>
    <button type="button" class="secondary" (click)="addCompany()">+ Add company</button>

    <h2>Source & notes</h2>
    <label>Source reference</label><input formControlName="sourceReference">
    <label>Notes</label><textarea formControlName="notes"></textarea>

    <div class="row" style="margin-top:1rem">
      <button type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save' }}</button>
      <a class="btn secondary" routerLink="/questions">Cancel</a>
    </div>
  </form>`
})
export class QuestionFormComponent implements OnInit {
  @Input() id?: string;  // bound from route param
  private api = inject(ApiService); private router = inject(Router); private fb = inject(FormBuilder);
  difficulties = DIFFICULTIES; types = QUESTION_TYPES;
  topics = signal<Topic[]>([]); allTags = signal<Tag[]>([]); allCompanies = signal<Company[]>([]);
  error = signal<string | null>(null); duplicateId = signal<number | null>(null); saving = signal(false);

  f = this.fb.group({
    questionText: ['', [Validators.required, Validators.maxLength(10000)]],
    answerText: [''], topicId: [null as number | null, Validators.required],
    difficulty: [null as string | null], questionType: [null as string | null], answerStatus: [null as string | null],
    tags: [''], sourceReference: [''], notes: [''],
    companies: this.fb.array([] as any[])
  });
  get companies() { return this.f.controls.companies as FormArray; }

  ngOnInit() {
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.tags().subscribe(v => this.allTags.set(v));
    this.api.companies().subscribe(v => this.allCompanies.set(v));
    if (this.id) this.api.question(+this.id).subscribe({ next: q => this.load(q), error: e => this.error.set(errorMessage(e)) });
  }

  private companyGroup(c: any = {}) {
    return this.fb.group({
      companyName: [c.companyName ?? '', [Validators.required, Validators.maxLength(200)]],
      interviewRound: [c.interviewRound ?? ''], role: [c.role ?? ''], jobLevel: [c.jobLevel ?? ''],
      dateAsked: [c.dateAsked ?? ''], sourceReference: [c.sourceReference ?? ''], attributionStatus: [c.attributionStatus ?? 'REPORTED']
    });
  }
  addCompany() { this.companies.push(this.companyGroup()); }
  removeCompany(i: number) {
    const name = this.companies.at(i).value.companyName;
    if (!name || confirm(`Remove the ${name} attribution from this question?`)) this.companies.removeAt(i);
  }

  private load(q: Question) {
    this.f.patchValue({
      questionText: q.questionText, answerText: q.answerText ?? '', topicId: q.topicId, difficulty: q.difficulty ?? null,
      questionType: q.questionType ?? null, answerStatus: q.answerStatus === 'NONE' ? null : q.answerStatus,
      tags: q.tags.map(t => t.name).join(', '), sourceReference: q.sourceReference ?? '', notes: q.notes ?? ''
    });
    this.companies.clear();
    q.companies.forEach(c => this.companies.push(this.companyGroup(c)));
  }

  save() {
    this.f.markAllAsTouched();
    if (this.f.invalid) { this.error.set('Please fix the highlighted fields.'); return; }
    const v = this.f.getRawValue() as any;
    const blank = (s: string) => (s && s.trim() ? s.trim() : null);
    const req: QuestionRequest = {
      questionText: v.questionText, answerText: blank(v.answerText), topicId: v.topicId,
      tags: (v.tags as string).split(',').map(s => s.trim()).filter(Boolean),
      difficulty: v.difficulty, questionType: v.questionType, answerStatus: v.answerStatus,
      sourceReference: blank(v.sourceReference), notes: blank(v.notes),
      companies: (v.companies as any[]).map(c => ({
        companyName: c.companyName.trim(), interviewRound: blank(c.interviewRound), role: blank(c.role), jobLevel: blank(c.jobLevel),
        dateAsked: blank(c.dateAsked), sourceReference: blank(c.sourceReference), attributionStatus: c.attributionStatus
      }))
    };
    this.saving.set(true); this.error.set(null); this.duplicateId.set(null);
    const call = this.id ? this.api.updateQuestion(+this.id, req) : this.api.createQuestion(req);
    call.subscribe({
      next: () => this.router.navigate(['/questions']),
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
        if (e instanceof HttpErrorResponse && e.error?.code === 'DUPLICATE_QUESTION') this.duplicateId.set(e.error.details.matchingQuestionId);
      }
    });
  }
}
