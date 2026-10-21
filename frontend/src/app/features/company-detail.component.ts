import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Bucket, CompanySummary } from '../core/models';
import { IconComponent } from '../shared/icon.component';
import { QuestionListComponent } from './question-list.component';

@Component({
  selector: 'app-company-detail', standalone: true, imports: [RouterLink, IconComponent, QuestionListComponent],
  template: `
  <a routerLink="/companies" class="small">← Companies</a>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (s(); as s) {
    <div class="page-header" style="margin-top:.4rem">
      <div><h1>{{ s.company.name }}</h1>
        <p>@if (s.company.industry) { {{ s.company.industry }} · } Only questions you attributed to {{ s.company.name }} — nothing is inferred.</p></div>
      <div class="row">
        <button class="lg" (click)="practice()" [disabled]="!s.totalQuestions"><app-icon name="practice" /> Practice {{ s.company.name }}</button>
        <button class="lg secondary" (click)="flashcards()" [disabled]="!s.totalQuestions"><app-icon name="brain" /> Flashcards</button>
        <a class="btn lg secondary" routerLink="/plans" [queryParams]="{ companyId: s.company.id }">📅 Plan interview</a>
      </div>
    </div>
    <div class="grid kpis">
      <div class="card"><div class="muted">Questions</div><div class="stat">{{ s.totalQuestions }}</div></div>
      <div class="card"><div class="muted">With answers</div><div class="stat">{{ s.answered }}</div><div class="muted small">{{ s.unanswered }} without</div></div>
      <div class="card"><div class="muted">Practiced</div><div class="stat">{{ s.practiced }}</div>
        <div class="bar ok" style="margin-top:.5rem"><span [style.width.%]="s.totalQuestions ? s.practiced / s.totalQuestions * 100 : 0"></span></div></div>
      <div class="card"><div class="muted">Bookmarked</div><div class="stat">{{ s.bookmarked }}</div></div>
    </div>
    <div class="grid breakdown">
      @for (g of groups(s); track g.title) {
        <div class="card"><h2 style="margin-top:0">{{ g.title }}</h2>
          @for (b of g.items; track b.label) {
            <div class="b" [title]="b.label + ': ' + b.count">
              <span class="lbl">{{ b.label }}</span>
              <div class="bar"><span [style.width.%]="b.count / max(g.items) * 100"></span></div>
              <span class="cnt">{{ b.count }}</span>
            </div>
          } @empty { <p class="muted">—</p> }
        </div>
      }
    </div>
    <h2>Questions</h2>
    <app-question-list [fixedCompanyId]="s.company.id" />
  }`,
  styles: [`
    .kpis, .breakdown { grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); }
    .b { display: grid; grid-template-columns: minmax(0, 1fr) minmax(40px, 35%) 2rem; gap: .6rem; align-items: center; font-size: .88rem; margin: .4rem 0; }
    .b .lbl { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .b .bar { min-width: 0; }
    .b .cnt { text-align: right; color: var(--muted); font-size: .8rem; font-variant-numeric: tabular-nums; }
  `]
})
export class CompanyDetailComponent implements OnInit {
  @Input() id!: string;
  private api = inject(ApiService);
  private router = inject(Router);
  s = signal<CompanySummary | null>(null); error = signal<string | null>(null);
  ngOnInit() { this.api.companySummary(+this.id).subscribe({ next: v => this.s.set(v), error: e => this.error.set(errorMessage(e)) }); }
  groups(s: CompanySummary): { title: string; items: Bucket[] }[] {
    return [{ title: 'By topic', items: s.byTopic }, { title: 'By difficulty', items: s.byDifficulty },
            { title: 'By interview round', items: s.byRound }, { title: 'By tag', items: s.byTag.slice(0, 10) }];
  }
  max(items: Bucket[]) { return Math.max(1, ...items.map(i => i.count)); }
  practice() { this.router.navigate(['/practice'], { queryParams: { type: 'COMPANY', companyId: this.s()!.company.id } }); }
  flashcards() { this.router.navigate(['/flashcards'], { queryParams: { companyId: this.s()!.company.id, autostart: true } }); }
}
