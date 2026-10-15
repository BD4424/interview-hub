import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Bucket, CompanySummary } from '../core/models';
import { QuestionListComponent } from './question-list.component';

@Component({
  selector: 'app-company-detail', standalone: true, imports: [RouterLink, QuestionListComponent],
  template: `
  <a routerLink="/companies">← Companies</a>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (s(); as s) {
    <h1>{{ s.company.name }}</h1>
    <p class="muted">Counts reflect questions you have attributed to this company; nothing here is inferred.</p>
    <div class="grid">
      <div class="card"><div class="muted">Questions</div><div class="stat">{{ s.totalQuestions }}</div></div>
      <div class="card"><div class="muted">Answered</div><div class="stat">{{ s.answered }}</div></div>
      <div class="card"><div class="muted">Unanswered</div><div class="stat">{{ s.unanswered }}</div></div>
    </div>
    <div class="grid">
      @for (g of groups(s); track g.title) {
        <div class="card"><h2>{{ g.title }}</h2>
          @for (b of g.items; track b.label) { <div>{{ b.label }} <span class="muted">· {{ b.count }}</span></div> }
          @empty { <p class="muted">—</p> }
        </div>
      }
    </div>
    <p class="muted">Bookmarked/practiced breakdowns and company practice arrive with milestone 11.</p>
    <h2>Questions</h2>
    <app-question-list [fixedCompanyId]="s.company.id" />
  }`
})
export class CompanyDetailComponent implements OnInit {
  @Input() id!: string;
  private api = inject(ApiService);
  s = signal<CompanySummary | null>(null); error = signal<string | null>(null);
  ngOnInit() { this.api.companySummary(+this.id).subscribe({ next: v => this.s.set(v), error: e => this.error.set(errorMessage(e)) }); }
  groups(s: CompanySummary): { title: string; items: Bucket[] }[] {
    return [{ title: 'By topic', items: s.byTopic }, { title: 'By difficulty', items: s.byDifficulty },
            { title: 'By interview round', items: s.byRound }, { title: 'By tag', items: s.byTag }];
  }
}
