import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StudyApi } from '../core/study.api';
import { PlanSummary } from '../core/study.models';
import { ScoreRingComponent } from './score-ring.component';

/** Dashboard card for the nearest interview plan; invites you to create one when there is none. */
@Component({
  selector: 'app-countdown-card', standalone: true, imports: [RouterLink, DatePipe, ScoreRingComponent],
  template: `
  @if (loaded()) {
    @if (p(); as p) {
      <a class="card cd" [routerLink]="['/plans', p.id]">
        <div class="days"><div class="n">{{ p.phase === 'TODAY' ? '🎯' : p.daysLeft }}</div>
          <div class="l">{{ p.phase === 'TODAY' ? 'today' : p.daysLeft === 1 ? 'day left' : 'days left' }}</div></div>
        <div class="mid">
          <div class="muted small">Next interview · {{ p.interviewDate | date:'EEE, MMM d' }}</div>
          <div class="ttl">{{ p.title }}</div>
          <div class="muted small">
            @if (p.phase === 'TODAY') { Good luck! A light warm-up beats cramming. }
            @else { Today: {{ p.todayDone }}/{{ p.todayTarget }} done · {{ p.practiced }}/{{ p.questions }} questions practised }
          </div>
          <div class="bar ok" style="margin-top:.5rem"><span [style.width.%]="p.todayTarget ? p.todayDone / p.todayTarget * 100 : 100"></span></div>
        </div>
        <app-score-ring [value]="p.readiness" [percent]="true" [size]="84" label="ready" />
      </a>
    } @else {
      <a class="card cd empty-cd" routerLink="/plans">
        <div class="days"><div class="n">📅</div></div>
        <div class="mid"><div class="ttl">Got an interview coming up?</div>
          <div class="muted small">Add the date and company — you'll get a day-by-day plan and a readiness score.</div></div>
        <span class="btn secondary">Create plan</span>
      </a>
    }
  }`,
  styles: [`
    .cd { display: flex; gap: 1.2rem; align-items: center; color: var(--ink); text-decoration: none; }
    .days { width: 86px; height: 86px; border-radius: 18px; background: var(--grad); color: #fff; display: grid; place-content: center; text-align: center; flex-shrink: 0; box-shadow: var(--shadow-lg); }
    .days .n { font-size: 2rem; font-weight: 800; line-height: 1; }
    .days .l { font-size: .68rem; text-transform: uppercase; letter-spacing: .06em; opacity: .9; margin-top: .2rem; }
    .mid { flex: 1; min-width: 0; }
    .ttl { font-size: 1.15rem; font-weight: 800; margin: .1rem 0 .2rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    @media (max-width: 560px) { .cd { flex-wrap: wrap; } }
  `]
})
export class CountdownCardComponent implements OnInit {
  private api = inject(StudyApi);
  p = signal<PlanSummary | null>(null);
  loaded = signal(false);
  ngOnInit() { this.api.upcomingPlan().subscribe({ next: v => { this.p.set(v); this.loaded.set(true); }, error: () => this.loaded.set(true) }); }
}
