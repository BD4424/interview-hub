import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { Company, DIFFICULTIES, Page, Question, QUESTION_TYPES, Tag, Topic } from '../core/models';
import { QuestionCardComponent } from './question-card.component';

@Component({
  selector: 'app-question-list', standalone: true, imports: [ReactiveFormsModule, RouterLink, QuestionCardComponent],
  template: `
  @if (!fixedCompanyId) {
    <div class="row" style="justify-content: space-between"><h1>Question Bank</h1><a class="btn" routerLink="/questions/new">+ Add question</a></div>
  }
  <form class="card" [formGroup]="f">
    <div class="grid">
      <div><label>Search</label><input formControlName="q" placeholder="Search question text"></div>
      <div><label>Topic</label><select formControlName="topicId"><option [ngValue]="null">All</option>
        @for (t of topics(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select></div>
      @if (!fixedCompanyId) {
        <div><label>Company</label><select formControlName="companyId"><option [ngValue]="null">All</option>
          @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select></div>
      }
      <div><label>Difficulty</label><select formControlName="difficulty"><option [ngValue]="null">All</option>
        @for (d of difficulties; track d) { <option [ngValue]="d">{{ d }}</option> }</select></div>
      <div><label>Type</label><select formControlName="type"><option [ngValue]="null">All</option>
        @for (t of types; track t) { <option [ngValue]="t">{{ t }}</option> }</select></div>
      <div><label>Answer</label><select formControlName="hasAnswer"><option [ngValue]="null">Any</option>
        <option [ngValue]="true">Has answer</option><option [ngValue]="false">No answer</option></select></div>
      <div><label>Sort</label><select formControlName="sort"><option value="newest">Newest</option><option value="oldest">Oldest</option>
        <option value="topic">Topic</option><option value="difficulty">Difficulty</option></select></div>
    </div>
    <label>Tags (click to filter)</label>
    <div>
      @for (t of tags(); track t.id) {
        <span class="chip" style="cursor:pointer" [class.ok]="selectedTags().includes(t.id)" (click)="toggleTag(t.id)">#{{ t.name }} ({{ t.usage }})</span>
      }
      @if (selectedTags().length > 1) {
        <label style="display:inline-flex;gap:.3rem;align-items:center"><input type="checkbox" style="width:auto" formControlName="matchAllTags"> match all selected tags</label>
      }
    </div>
  </form>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (loading()) { <p class="muted">Loading…</p> }
  @if (page(); as p) {
    <p class="muted">{{ p.totalElements }} question(s)</p>
    @for (q of p.content; track q.id) { <app-question-card [q]="q" (delete)="remove($event)" /> }
    @empty { <div class="card muted">No questions match these filters.</div> }
    @if (p.totalPages > 1) {
      <div class="row"><button class="secondary" [disabled]="p.page === 0" (click)="go(p.page - 1)">Prev</button>
        <span class="muted">Page {{ p.page + 1 }} / {{ p.totalPages }}</span>
        <button class="secondary" [disabled]="p.page + 1 >= p.totalPages" (click)="go(p.page + 1)">Next</button></div>
    }
  }`
})
export class QuestionListComponent implements OnInit {
  @Input() fixedCompanyId: number | null = null;
  private api = inject(ApiService);
  difficulties = DIFFICULTIES; types = QUESTION_TYPES;
  topics = signal<Topic[]>([]); companies = signal<Company[]>([]); tags = signal<Tag[]>([]);
  selectedTags = signal<number[]>([]);
  page = signal<Page<Question> | null>(null); loading = signal(false); error = signal<string | null>(null);
  f = inject(FormBuilder).group({
    q: [''], topicId: [null as number | null], companyId: [null as number | null], difficulty: [null as string | null],
    type: [null as string | null], hasAnswer: [null as boolean | null], sort: ['newest'], matchAllTags: [false]
  });

  ngOnInit() {
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.companies().subscribe(v => this.companies.set(v));
    this.api.tags().subscribe(v => this.tags.set(v));
    this.f.valueChanges.pipe(debounceTime(250)).subscribe(() => this.go(0));
    this.go(0);
  }
  toggleTag(id: number) {
    const s = this.selectedTags();
    this.selectedTags.set(s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
    this.go(0);
  }
  go(page: number) {
    this.loading.set(true); this.error.set(null);
    const v = this.f.getRawValue();
    this.api.questions({ ...v, companyId: this.fixedCompanyId ?? v.companyId, tagIds: this.selectedTags(), page, size: 20 }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.loading.set(false); }
    });
  }
  remove(q: Question) {
    if (!confirm(`Delete this question permanently?\n\n${q.questionText.slice(0, 200)}`)) return;
    this.api.deleteQuestion(q.id).subscribe({ next: () => this.go(this.page()?.page ?? 0), error: e => this.error.set(errorMessage(e)) });
  }
}
