import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { DIFFICULTIES, Difficulty, Topic } from '../core/models';
import { MockApi } from '../core/mock.api';
import { MockScope, MockSummary } from '../core/mock.models';
import { IconComponent } from '../shared/icon.component';

type Mode = 'FULL' | 'MULTI' | 'SINGLE';

@Component({
  selector: 'app-mock-tests', standalone: true, imports: [FormsModule, RouterLink, DatePipe, IconComponent],
  template: `
  <div class="page-header">
    <div><h1>Mock tests</h1>
      <p>Exam-style: answer the whole paper, submit, then get one score with feedback on every answer.
        Want a conversational interviewer with follow-ups instead? Use <a routerLink="/practice">Mock interview</a>.</p></div>
  </div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }

  <div class="modes">
    @for (m of modes; track m.mode) {
      <button class="mode" [class.sel]="mode === m.mode" (click)="setMode(m.mode)">
        <span class="mi">{{ m.emoji }}</span><span class="mt">{{ m.title }}</span><span class="mb">{{ m.blurb }}</span>
      </button>
    }
  </div>

  <div class="card">
    @if (mode === 'SINGLE') {
      <label>Topic *</label>
      <select [ngModel]="singleTopicId" (ngModelChange)="singleTopicId = $event; refresh()" style="max-width:360px">
        <option [ngValue]="null">Choose a topic…</option>
        @for (t of topics(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }
      </select>
    }
    @if (mode === 'MULTI') {
      <label>Topics * <span class="muted">({{ topicIds.size }} selected)</span></label>
      <div>@for (t of topics(); track t.id) {
        <span class="chip pick" [class.ok]="topicIds.has(t.id)" (click)="toggle(t.id)">{{ t.name }}</span>
      }</div>
    }
    <div class="grid" style="margin-top:.4rem">
      <div><label>Difficulty</label>
        <select [ngModel]="difficulty" (ngModelChange)="difficulty = $event; refresh()">
          <option [ngValue]="null">Mixed (easy → hard)</option>
          @for (d of difficulties; track d) { <option [ngValue]="d">{{ d }}</option> }
        </select></div>
      <div><label>Questions</label>
        <input type="number" min="1" max="60" [ngModel]="count" (ngModelChange)="setCount($event)"></div>
      <div><label class="inline"><input type="checkbox" [(ngModel)]="timed"> Timed</label>
        <input type="number" min="1" max="300" [disabled]="!timed" [ngModel]="minutes" (ngModelChange)="minutes = $event; minutesTouched = true">
        <div class="muted small">minutes (~{{ perQuestion() }} min per question)</div></div>
    </div>
    <div class="row spread" style="margin-top:1rem">
      <span class="muted small">
        @if (available() === null) { Choose topics to see how many questions are available. }
        @else if (available() === 0) { No approved questions match. }
        @else { {{ available() }} questions available · this paper uses {{ paperSize() }}{{ mode === 'FULL' || mode === 'MULTI' ? ', balanced across topics' : '' }}. }
      </span>
      <button class="lg" (click)="start()" [disabled]="busy() || !available()"><app-icon name="target" /> {{ busy() ? 'Preparing paper…' : 'Start mock test' }}</button>
    </div>
    <p class="muted small" style="margin:.8rem 0 0">Answers autosave. Nothing is graded or revealed until you submit — unanswered questions score 0.
      Graded answers count towards your practice, progress and streak.</p>
  </div>

  <h2>Your mock tests</h2>
  <div class="card" style="padding:.4rem .6rem">
    @for (t of tests(); track t.id) {
      <div class="trow">
        <a [routerLink]="['/mock-tests', t.id]" class="tl"><strong>{{ t.title }}</strong>
          <div class="muted small">{{ t.startedAt | date:'MMM d, h:mm a' }} · {{ t.answeredCount }}/{{ t.questionCount }} answered
            @if (t.timeLimitMinutes) { · {{ t.timeLimitMinutes }} min }</div></a>
        <span class="row">
          @if (t.status === 'COMPLETED' && t.percent != null) {
            <span [class]="pctChip(t.percent)">{{ t.percent }}%</span>
          } @else {
            <span [class]="statusChip(t.status)">{{ statusLabel(t.status) }}</span>
          }
          @if (t.status === 'IN_PROGRESS') { <a class="btn secondary" [routerLink]="['/mock-tests', t.id]">Resume</a> }
          <button class="ghost" title="Delete" (click)="remove(t)"><app-icon name="x" [size]="16" /></button>
        </span>
      </div>
    } @empty { <div class="empty"><div class="big">📝</div>No mock tests yet. Take your first one above.</div> }
  </div>`,
  styles: [`
    .modes { display: grid; gap: .8rem; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); margin-bottom: 1rem; }
    .mode { flex-direction: column; align-items: flex-start; text-align: left; gap: .3rem; background: var(--card); color: var(--ink);
      border: 1.5px solid var(--line); padding: 1rem; border-radius: var(--radius); box-shadow: var(--shadow); min-height: 120px; }
    .mode:hover:not(:disabled) { border-color: var(--brand); box-shadow: var(--shadow-lg); }
    .mode.sel { border-color: var(--brand); background: var(--brand-soft); }
    .mi { font-size: 1.6rem; line-height: 1; }
    .mt { font-weight: 800; font-size: 1rem; }
    .mb { font-weight: 400; font-size: .8rem; color: var(--muted); line-height: 1.35; }
    .pick { cursor: pointer; user-select: none; }
    label.inline { display: inline-flex; gap: .4rem; align-items: center; }
    .trow { display: flex; justify-content: space-between; align-items: center; gap: .6rem; padding: .6rem .5rem; border-bottom: 1px solid var(--line); }
    .trow:last-child { border-bottom: 0; }
    .tl { color: var(--ink); text-decoration: none; min-width: 0; }
  `]
})
export class MockTestsComponent implements OnInit {
  private api = inject(ApiService);
  private mock = inject(MockApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  difficulties = DIFFICULTIES;
  modes: { mode: Mode; emoji: string; title: string; blurb: string }[] = [
    { mode: 'FULL', emoji: '🧪', title: 'Full mock', blurb: 'Every topic in your bank, balanced — the closest thing to the real day.' },
    { mode: 'MULTI', emoji: '🧩', title: 'Cumulative', blurb: 'Pick several topics, e.g. Java + Spring Boot + Microservices.' },
    { mode: 'SINGLE', emoji: '🎯', title: 'Single topic', blurb: 'One topic, end to end. Great before a focused round.' }
  ];
  mode: Mode = 'FULL';
  topicIds = new Set<number>();
  singleTopicId: number | null = null;
  difficulty: Difficulty | null = null;
  count = 20;
  timed = true;
  minutes = 50;
  minutesTouched = false;
  topics = signal<Topic[]>([]);
  tests = signal<MockSummary[]>([]);
  available = signal<number | null>(null);
  busy = signal(false);
  error = signal<string | null>(null);

  ngOnInit() {
    this.api.topics().subscribe(v => this.topics.set(v));
    this.loadTests();
    const t = this.route.snapshot.queryParamMap.get('topicId');
    if (t) { this.mode = 'SINGLE'; this.singleTopicId = Number(t); }
    this.refresh();
  }

  loadTests() { this.mock.list().subscribe({ next: v => this.tests.set(v), error: e => this.error.set(errorMessage(e)) }); }

  setMode(m: Mode) { this.mode = m; this.refresh(); }
  toggle(id: number) { this.topicIds.has(id) ? this.topicIds.delete(id) : this.topicIds.add(id); this.refresh(); }
  setCount(v: number) {
    this.count = Math.max(1, Math.min(60, Math.round(Number(v) || 1)));
    if (!this.minutesTouched) this.minutes = Math.max(5, Math.round((this.count * 2.5) / 5) * 5);
  }
  perQuestion() { return this.count ? Math.round((Number(this.minutes) / this.count) * 10) / 10 : 0; }
  paperSize() { return Math.min(this.count, this.available() ?? 0); }

  private scope(): { scope: MockScope; topicIds: number[] } | null {
    if (this.mode === 'FULL') return { scope: 'FULL', topicIds: [] };
    if (this.mode === 'SINGLE') return this.singleTopicId ? { scope: 'TOPICS', topicIds: [this.singleTopicId] } : null;
    return this.topicIds.size ? { scope: 'TOPICS', topicIds: [...this.topicIds] } : null;
  }

  refresh() {
    const s = this.scope();
    if (!s) { this.available.set(null); return; }
    this.mock.available(s.scope, s.topicIds, this.difficulty).subscribe({ next: v => this.available.set(v.available), error: () => this.available.set(null) });
  }

  start() {
    const s = this.scope();
    if (!s) { this.error.set(this.mode === 'SINGLE' ? 'Choose a topic.' : 'Choose at least one topic.'); return; }
    this.busy.set(true); this.error.set(null);
    this.mock.create({ scope: s.scope, topicIds: s.topicIds, difficulty: this.difficulty, questionCount: this.count,
      timeLimitMinutes: this.timed ? Math.max(1, Math.min(300, Number(this.minutes) || 30)) : null }).subscribe({
      next: t => this.router.navigate(['/mock-tests', t.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  remove(t: MockSummary) {
    if (!confirm(`Delete "${t.title}"? Scores already recorded on questions are kept.`)) return;
    this.mock.delete(t.id).subscribe({ next: () => this.loadTests(), error: e => this.error.set(errorMessage(e)) });
  }

  statusLabel(s: string) {
    return s === 'IN_PROGRESS' ? 'in progress' : s === 'EVALUATING' ? 'grading…' : s === 'AWAITING_SELF_SCORE' ? 'needs your scores' : 'done';
  }
  statusChip(s: string) { return 'chip ' + (s === 'IN_PROGRESS' ? 'warn' : s === 'COMPLETED' ? 'diff' : ''); }
  pctChip(p: number) { return 'chip ' + (p >= 70 ? 'ok' : p >= 50 ? 'warn' : 'bad'); }
}
