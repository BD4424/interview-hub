import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Dashboard, StartSessionRequest, Topic } from '../core/models';
import { PrefsService } from '../core/prefs.service';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';
import { SparkBarsComponent } from '../shared/spark-bars.component';

@Component({
  selector: 'app-dashboard', standalone: true, imports: [RouterLink, DatePipe, IconComponent, ScoreRingComponent, SparkBarsComponent],
  template: `
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (!d() && !error()) {
    <div class="skeleton" style="height:170px;border-radius:20px;margin-bottom:1rem"></div>
    <div class="grid"><div class="skeleton" style="height:110px"></div><div class="skeleton" style="height:110px"></div><div class="skeleton" style="height:110px"></div></div>
  }
  @if (d(); as d) {
    <section class="hero">
      <div class="hero-row">
        <div>
          <div class="eyebrow">{{ greeting() }} 👋</div>
          <h1>{{ headline() }}</h1>
          <p>{{ subline() }}</p>
          <div class="row" style="margin-top:1.1rem">
            <button class="lg" (click)="quick({ type: 'MOCK', questionLimit: 5 })" [disabled]="starting()"><app-icon name="mic" /> Quick mock interview</button>
            @if (d.practice.dueForRevision > 0) {
              <button class="lg secondary" (click)="quick({ type: 'REVISION', questionLimit: 10 })" [disabled]="starting()"><app-icon name="brain" /> Revise {{ d.practice.dueForRevision }} due</button>
            } @else {
              <a class="btn lg secondary" routerLink="/practice"><app-icon name="practice" /> Choose a practice mode</a>
            }
          </div>
        </div>
        <div class="goal">
          <app-score-ring [value]="goalPct()" [percent]="true" [size]="118" label="daily goal" />
          <div class="goal-txt">{{ d.practice.todayAttempts }} / {{ prefs.dailyGoal() }} today</div>
          <div class="streak"><app-icon name="flame" [size]="16" /> {{ d.practice.streakDays }}-day streak</div>
        </div>
      </div>
    </section>

    @if (d.pendingDuplicateReviews || d.pendingAiReviews) {
      <div class="banner info row spread">
        <span>
          @if (d.pendingDuplicateReviews) { {{ d.pendingDuplicateReviews }} import item(s) need a duplicate decision. }
          @if (d.pendingAiReviews) { {{ d.pendingAiReviews }} AI-generated question(s) await your approval. }
        </span>
        <span class="row">
          @if (d.pendingAiReviews) { <a routerLink="/review">Open AI review →</a> }
          @if (d.pendingDuplicateReviews) { <a routerLink="/import">Open imports →</a> }
        </span>
      </div>
    }

    <div class="grid kpis">
      <div class="card kpi"><div class="muted">Questions in bank</div><div class="stat">{{ d.totalApproved }}</div>
        <div class="bar" style="margin-top:.6rem"><span [style.width.%]="pct(d.answered, d.totalApproved)"></span></div>
        <div class="muted small">{{ d.answered }} with answers · {{ d.unanswered }} without</div></div>
      <div class="card kpi"><div class="muted">Questions practiced</div><div class="stat">{{ d.practice.questionsAttempted }}</div>
        <div class="bar ok" style="margin-top:.6rem"><span [style.width.%]="pct(d.practice.questionsAttempted, d.totalApproved)"></span></div>
        <div class="muted small">{{ pct(d.practice.questionsAttempted, d.totalApproved) }}% of your bank · {{ d.practice.totalAttempts }} answers total</div></div>
      <div class="card kpi row" style="gap:1rem;flex-wrap:nowrap">
        <app-score-ring [value]="d.practice.averageScore30d" [size]="78" />
        <div><div class="muted">Average score</div><div style="font-weight:700">last 30 days</div>
          <div class="muted small">{{ d.practice.sessionsCompleted }} sessions completed</div></div>
      </div>
      <a class="card kpi" routerLink="/bookmarks"><div class="muted">Bookmarked</div><div class="stat">{{ d.practice.bookmarks }}</div>
        <div class="muted small">Your must-revise list →</div></a>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="row spread"><h2 style="margin:0">Last 14 days</h2><a routerLink="/progress" class="small">Full progress →</a></div>
        <app-spark-bars [days]="d.last14Days" [height]="90" />
        <div class="muted small" style="margin-top:.4rem">Bar height = questions answered · colour = average score</div>
      </div>
      <div class="card">
        <h2 style="margin-top:0">Focus areas</h2>
        @for (w of d.weakTopics; track w.topic) {
          <div class="weak">
            <div class="row spread"><strong>{{ w.topic }}</strong><span class="chip bad">avg {{ w.averageScore }}</span></div>
            <div class="bar bad"><span [style.width.%]="(w.averageScore ?? 0) * 10"></span></div>
            <div class="row spread"><span class="muted small">{{ w.questionsAttempted }}/{{ w.totalQuestions }} questions practiced</span>
              <button class="secondary" (click)="practiceTopic(w.topic)" [disabled]="starting()">Practice</button></div>
          </div>
        } @empty {
          <div class="empty"><div class="big">🎯</div>No weak topics yet. Answer a few questions and your focus areas show up here.</div>
        }
      </div>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="row spread"><h2 style="margin:0">Recent sessions</h2><a routerLink="/practice" class="small">All →</a></div>
        @for (s of d.recentSessions; track s.id) {
          <a class="session" [routerLink]="['/practice', s.id]">
            <div><strong>{{ s.title }}</strong><div class="muted small">{{ s.startedAt | date:'MMM d, h:mm a' }} · {{ s.answered }} answered</div></div>
            @if (s.overallScore != null) { <span class="chip" [class.ok]="s.overallScore >= 7" [class.warn]="s.overallScore >= 5 && s.overallScore < 7" [class.bad]="s.overallScore < 5">{{ s.overallScore }}/10</span> }
            @else { <span class="chip diff">{{ s.status === 'ACTIVE' ? 'in progress' : '—' }}</span> }
          </a>
        } @empty { <div class="empty"><div class="big">🎤</div>No sessions yet — try a quick mock interview.</div> }
      </div>
      <div class="card">
        <h2 style="margin-top:0">Your library</h2>
        @for (b of d.byTopic.slice(0, 7); track b.label) {
          <div class="lib"><span>{{ b.label }}</span><div class="bar"><span [style.width.%]="pct(b.count, maxTopic(d))"></span></div><span class="muted small">{{ b.count }}</span></div>
        } @empty { <div class="empty"><div class="big">📚</div><a routerLink="/questions/new">Add a question</a> or <a routerLink="/import">import your notes</a>.</div> }
        @if (d.byCompany.length) {
          <div class="divider"></div>
          <div>@for (c of d.byCompany.slice(0, 10); track c.label) { <span class="chip company">{{ c.label }} · {{ c.count }}</span> }</div>
        }
      </div>
    </div>

    @if (d.recentImports.length) {
      <div class="card">
        <h2 style="margin-top:0">Recent imports</h2>
        @for (i of d.recentImports; track i.id) {
          <div class="row spread imp"><span>#{{ i.id }} {{ i.filename }}</span>
            <span class="row"><span class="chip diff">{{ i.status }}</span><span class="muted small">{{ i.imported }}/{{ i.total }} · {{ i.createdAt | date:'short' }}</span></span></div>
        }
      </div>
    }
  }`,
  styles: [`
    .hero-row { position: relative; z-index: 1; display: flex; justify-content: space-between; gap: 1.5rem; align-items: center; flex-wrap: wrap; }
    .eyebrow { font-weight: 700; opacity: .85; margin-bottom: .25rem; }
    .goal { text-align: center; background: rgba(255,255,255,.12); padding: 1rem 1.2rem; border-radius: 18px; backdrop-filter: blur(4px); }
    .goal-txt { font-weight: 700; margin-top: .5rem; }
    .streak { display: inline-flex; gap: .3rem; align-items: center; margin-top: .3rem; background: rgba(255,255,255,.18); padding: .2rem .6rem; border-radius: 999px; font-size: .85rem; }
    .kpis { grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); }
    .weak { padding: .55rem 0; border-bottom: 1px dashed var(--line); }
    .weak:last-child { border-bottom: 0; }
    .weak .bar { margin: .35rem 0; }
    .session { display: flex; justify-content: space-between; align-items: center; gap: .5rem; padding: .6rem .5rem; border-radius: 10px; color: var(--ink); text-decoration: none; }
    .session:hover { background: var(--bg-2); text-decoration: none; }
    .lib { display: grid; grid-template-columns: 150px 1fr 32px; gap: .6rem; align-items: center; margin: .4rem 0; font-size: .9rem; }
    .imp { padding: .35rem 0; }
  `]
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  prefs = inject(PrefsService);
  d = signal<Dashboard | null>(null);
  topics = signal<Topic[]>([]);
  error = signal<string | null>(null);
  starting = signal(false);

  goalPct = computed(() => Math.min(100, ((this.d()?.practice.todayAttempts ?? 0) / this.prefs.dailyGoal()) * 100));

  ngOnInit() {
    this.api.dashboard().subscribe({ next: v => this.d.set(v), error: e => this.error.set(errorMessage(e)) });
    this.api.topics().subscribe(t => this.topics.set(t));
  }

  greeting() { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; }
  headline() {
    const p = this.d()!.practice;
    if (p.totalAttempts === 0) return 'Let’s get you interview-ready';
    if (p.todayAttempts >= this.prefs.dailyGoal()) return 'Daily goal crushed. Keep the momentum!';
    if (p.streakDays > 1) return `You’re on a ${p.streakDays}-day streak — don’t break it`;
    return 'Ready for today’s practice?';
  }
  subline() {
    const d = this.d()!;
    if (d.totalApproved === 0) return 'Add or import your interview questions, then practice them with an AI interviewer that grades every answer.';
    const left = Math.max(0, this.prefs.dailyGoal() - d.practice.todayAttempts);
    return left > 0 ? `${left} more answer${left === 1 ? '' : 's'} to hit today’s goal. ${d.practice.dueForRevision} question(s) are due for revision.`
                    : `You have ${d.totalApproved} questions in your bank. Try a harder mock interview to stretch yourself.`;
  }
  pct(a: number, b: number) { return b ? Math.round((a / b) * 100) : 0; }
  maxTopic(d: Dashboard) { return Math.max(1, ...d.byTopic.map(b => b.count)); }

  practiceTopic(name: string) {
    const t = this.topics().find(x => x.name === name);
    if (t) this.quick({ type: 'TOPIC', topicId: t.id, questionLimit: 5 });
  }

  quick(r: StartSessionRequest) {
    this.starting.set(true);
    this.api.startSession(r).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => { this.starting.set(false); this.error.set(errorMessage(e)); }
    });
  }
}
