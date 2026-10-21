import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StudyApi } from '../core/study.api';
import { WeeklyReport } from '../core/study.models';

/** "This week vs last week" card. [compact] hides it unless it's Monday/Tuesday or there's activity this week. */
@Component({
  selector: 'app-weekly-report', standalone: true, imports: [RouterLink, DatePipe],
  template: `
  @if (r(); as r) {
    @if (!compact || r.showAsMondaySummary || r.thisWeek.answers > 0) {
      <div class="card wr">
        <div class="row spread">
          <h2 style="margin:0">{{ r.showAsMondaySummary && r.thisWeek.answers === 0 ? 'Last week in review' : 'This week' }}</h2>
          <span class="muted small">{{ r.thisWeek.from | date:'MMM d' }} – today</span>
        </div>
        <div class="kp">
          <div><div class="muted small">Answers</div><div class="v">{{ r.thisWeek.answers }}</div>
            <div class="small" [class.up]="(r.answersChangePct ?? 0) > 0" [class.down]="(r.answersChangePct ?? 0) < 0">
              @if (r.answersChangePct != null) { {{ r.answersChangePct > 0 ? '▲' : r.answersChangePct < 0 ? '▼' : '■' }} {{ abs(r.answersChangePct) }}% vs last week }
              @else { last week: {{ r.lastWeek.answers }} }
            </div></div>
          <div><div class="muted small">Average score</div><div class="v">{{ r.thisWeek.averageScore ?? '—' }}</div>
            <div class="small" [class.up]="(r.scoreChange ?? 0) > 0" [class.down]="(r.scoreChange ?? 0) < 0">
              @if (r.scoreChange != null) { {{ r.scoreChange > 0 ? '▲ +' : r.scoreChange < 0 ? '▼ ' : '■ ' }}{{ r.scoreChange }} vs last week }
              @else { last week: {{ r.lastWeek.averageScore ?? '—' }} }
            </div></div>
          <div><div class="muted small">Active days</div><div class="v">{{ r.thisWeek.activeDays }}<span class="muted small">/7</span></div>
            <div class="small muted">{{ r.thisWeek.flashcards }} flashcards</div></div>
          <div><div class="muted small">Most improved</div>
            @if (r.mostImproved; as m) { <div class="v sm">{{ m.topic }}</div><div class="small up">▲ +{{ m.delta }} avg score</div> }
            @else { <div class="v sm muted">—</div><div class="small muted">practice a topic two weeks running</div> }</div>
        </div>
        @if (r.weakest.length) {
          <div class="muted small" style="margin-top:.8rem">Your weakest {{ r.weakest.length }} — fix these first:</div>
          @for (w of r.weakest; track w.questionId) {
            <a class="wk" [routerLink]="['/questions', w.questionId, 'edit']">
              <span class="chip bad">{{ w.lastScore }}</span><span class="t">{{ w.question }}</span><span class="muted small">{{ w.topic }}</span>
            </a>
          }
        }
      </div>
    }
  }`,
  styles: [`
    .kp { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1rem; margin-top: .8rem; }
    .v { font-size: 1.6rem; font-weight: 800; letter-spacing: -.02em; }
    .v.sm { font-size: 1.1rem; padding: .35rem 0; }
    .up { color: var(--ok); } .down { color: var(--bad-2); }
    .wk { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .6rem; align-items: center; padding: .4rem .2rem; color: var(--ink); text-decoration: none; border-radius: 8px; }
    .wk:hover { background: var(--bg-2); text-decoration: none; }
    .wk .t { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  `]
})
export class WeeklyReportComponent implements OnInit {
  @Input() compact = false;
  private api = inject(StudyApi);
  r = signal<WeeklyReport | null>(null);
  ngOnInit() { this.api.weekly().subscribe({ next: v => this.r.set(v), error: () => {} }); }
  abs(v: number) { return Math.abs(v); }
}
