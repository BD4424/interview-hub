import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Progress, Topic } from '../core/models';
import { PrefsService } from '../core/prefs.service';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';
import { SparkBarsComponent } from '../shared/spark-bars.component';

@Component({
  selector: 'app-progress', standalone: true, imports: [RouterLink, DatePipe, FormsModule, IconComponent, ScoreRingComponent, SparkBarsComponent],
  template: `
  <div class="page-header"><div><h1>Progress</h1><p>Scores reflect your latest answer per question, so improvement shows up as you revise.</p></div>
    <div class="row"><label style="margin:0">Daily goal</label>
      <input type="number" min="1" max="50" style="width:80px" [ngModel]="prefs.dailyGoal()" (ngModelChange)="prefs.setDailyGoal($event)"></div>
  </div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (p(); as p) {
    <div class="grid">
      <div class="card row" style="gap:1rem;flex-wrap:nowrap"><app-score-ring [value]="p.summary.averageScore30d" [size]="84" />
        <div><div class="muted">Avg score</div><strong>last 30 days</strong></div></div>
      <div class="card"><div class="muted">Streak</div><div class="stat">🔥 {{ p.summary.streakDays }}</div><div class="muted small">days in a row</div></div>
      <div class="card"><div class="muted">Answers</div><div class="stat">{{ p.summary.totalAttempts }}</div><div class="muted small">{{ p.summary.questionsAttempted }} distinct questions</div></div>
      <div class="card"><div class="muted">Due for revision</div><div class="stat">{{ p.summary.dueForRevision }}</div>
        @if (p.summary.dueForRevision) { <button class="secondary" style="margin-top:.4rem" (click)="start('REVISION')">Revise now</button> }</div>
    </div>

    <div class="card">
      <div class="row spread"><h2 style="margin:0">Last 30 days</h2><span class="muted small">{{ p.summary.todayAttempts }} today · goal {{ prefs.dailyGoal() }}</span></div>
      <app-spark-bars [days]="p.last30Days" [height]="110" />
    </div>

    <div class="card">
      <h2 style="margin-top:0">Topics</h2>
      <table>
        <thead><tr><th>Topic</th><th>Coverage</th><th>Current score</th><th>Answers</th><th></th></tr></thead>
        <tbody>
          @for (t of p.topics; track t.topic) {
            <tr>
              <td><strong>{{ t.topic }}</strong></td>
              <td style="min-width:160px"><div class="bar"><span [style.width.%]="cov(t.questionsAttempted, t.totalQuestions)"></span></div>
                <span class="muted small">{{ t.questionsAttempted }}/{{ t.totalQuestions }}</span></td>
              <td style="min-width:160px"><div class="bar" [class]="barClass(t.averageScore)"><span [style.width.%]="(t.averageScore ?? 0) * 10"></span></div>
                <span class="muted small">{{ t.averageScore ?? '—' }}/10</span></td>
              <td>{{ t.attempts }}</td>
              <td><button class="secondary" (click)="practiceTopic(t.topic)">Practice</button></td>
            </tr>
          } @empty { <tr><td colspan="5"><div class="empty"><div class="big">📈</div>Practice a few questions to see per-topic progress.</div></td></tr> }
        </tbody>
      </table>
    </div>

    <div class="grid-2">
      <div class="card"><h2 style="margin-top:0">Recent answers</h2>
        @for (a of p.recentAttempts; track $index) {
          <div class="ra"><a [routerLink]="['/questions', a.questionId, 'edit']">{{ a.question }}</a>
            <span class="row"><span class="muted small">{{ a.topic }} · {{ a.at | date:'MMM d' }}</span>
              @if (a.score != null) { <span class="chip" [class.ok]="a.score >= 7" [class.warn]="a.score >= 5 && a.score < 7" [class.bad]="a.score < 5">{{ a.score }}</span> }</span></div>
        } @empty { <p class="muted">Nothing yet.</p> }
      </div>
      <div class="card"><h2 style="margin-top:0">Sessions</h2>
        @for (s of p.recentSessions; track s.id) {
          <div class="ra"><a [routerLink]="['/practice', s.id]">{{ s.title }}</a>
            <span class="row"><span class="muted small">{{ s.startedAt | date:'MMM d' }} · {{ s.answered }} answered</span>
              @if (s.overallScore != null) { <span class="chip">{{ s.overallScore }}/10</span> }</span></div>
        } @empty { <p class="muted">No sessions yet.</p> }
      </div>
    </div>
  } @else if (!error()) { <div class="skeleton" style="height:300px"></div> }`,
  styles: [`.ra { display: flex; justify-content: space-between; gap: .8rem; padding: .5rem 0; border-bottom: 1px solid var(--line); font-size: .9rem; }
            .ra:last-child { border-bottom: 0; } .ra a { color: var(--ink); }`]
})
export class ProgressComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  prefs = inject(PrefsService);
  p = signal<Progress | null>(null);
  topicsList = signal<Topic[]>([]);
  error = signal<string | null>(null);

  ngOnInit() {
    this.api.progress().subscribe({ next: v => this.p.set(v), error: e => this.error.set(errorMessage(e)) });
    this.api.topics().subscribe(v => this.topicsList.set(v));
  }
  cov(a: number, b: number) { return b ? (a / b) * 100 : 0; }
  barClass(v?: number | null) { return 'bar ' + (v == null ? '' : v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }
  practiceTopic(name: string) {
    const t = this.topicsList().find(x => x.name === name);
    if (t) this.go({ type: 'TOPIC' as const, topicId: t.id, questionLimit: 5 });
  }
  start(type: 'REVISION') { this.go({ type, questionLimit: 10 }); }
  private go(r: any) {
    this.api.startSession(r).subscribe({ next: s => this.router.navigate(['/practice', s.id]), error: e => this.error.set(errorMessage(e)) });
  }
}
