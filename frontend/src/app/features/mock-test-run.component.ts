import { Component, HostListener, Input, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { MockApi } from '../core/mock.api';
import { MockItem, MockTest } from '../core/mock.models';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';

type Filter = 'ALL' | 'WEAK' | 'UNANSWERED';

@Component({
  selector: 'app-mock-test-run', standalone: true, imports: [FormsModule, RouterLink, DatePipe, IconComponent, ScoreRingComponent],
  template: `
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (!t()) { <div class="skeleton" style="height:360px;border-radius:18px"></div> }
  @if (t(); as t) {
    @switch (t.status) {
      @case ('IN_PROGRESS') {
        <div class="top">
          <div style="min-width:0">
            <a routerLink="/mock-tests" class="small">← Mock tests</a>
            <h1 style="margin:.2rem 0 0">{{ t.title }}</h1>
            <div class="muted small">{{ t.topics.length ? t.topics.join(', ') : 'All topics' }} · answers autosave · nothing is graded until you submit</div>
          </div>
          <div class="row">
            <span [class]="timerClass()"><app-icon name="clock" [size]="16" /> {{ clock(t) }}</span>
            <button (click)="askSubmit()" [disabled]="busy()">Submit test</button>
          </div>
        </div>

        <div class="layout">
          <aside class="card navp">
            <div class="row spread"><strong class="small">Questions</strong><span class="muted small">{{ answeredCount() }}/{{ t.questionCount }}</span></div>
            <div class="cells">
              @for (it of t.items; track it.id; let i = $index) {
                <button [class]="cellClass(it, i)" (click)="go(i)" [title]="it.topic || ''">{{ i + 1 }}</button>
              }
            </div>
            <div class="legend small muted">
              <span><i class="sw done"></i>answered</span><span><i class="sw flag"></i>flagged</span><span><i class="sw"></i>not yet</span>
            </div>
          </aside>

          @if (item(); as it) {
            <section class="card qpane">
              <div class="row spread">
                <span class="chip diff">Question {{ idx() + 1 }} of {{ t.questionCount }}</span>
                <span class="row">
                  @if (it.topic) { <span class="chip">{{ it.topic }}</span> }
                  @if (it.difficulty) { <span [class]="'chip ' + it.difficulty">{{ it.difficulty }}</span> }
                </span>
              </div>
              <div class="question pre">{{ it.question }}</div>
              <textarea rows="12" [ngModel]="draft(it)" (ngModelChange)="edit(it, $event)"
                        placeholder="Write your answer as you would explain it in the interview: key idea first, then details, trade-offs and an example."></textarea>
              <div class="row spread small" style="margin-top:.4rem">
                <span [class]="saveClass()">{{ saveLabel() }}</span>
                <span class="muted">{{ words(it) }} words · <span class="kbd">Ctrl</span>+<span class="kbd">Enter</span> next</span>
              </div>
              <div class="row spread" style="margin-top:1rem">
                <button class="secondary" (click)="go(idx() - 1)" [disabled]="idx() === 0">← Previous</button>
                <button [class]="it.flagged ? 'secondary flag-on' : 'secondary'" (click)="toggleFlag(it)">⚑ {{ it.flagged ? 'Flagged for review' : 'Flag for review' }}</button>
                @if (idx() < t.items.length - 1) {
                  <button (click)="go(idx() + 1)">Next →</button>
                } @else {
                  <button (click)="askSubmit()">Finish & submit</button>
                }
              </div>
            </section>
          }
        </div>

        @if (confirming()) {
          <div class="overlay" (click)="confirming.set(false)">
            <div class="card modal" (click)="$event.stopPropagation()">
              <h2 style="margin-top:0">Submit your test?</h2>
              <div class="sumrow"><span>Answered</span><strong>{{ answeredCount() }} / {{ t.questionCount }}</strong></div>
              <div class="sumrow"><span>Not answered (score 0)</span><strong>{{ t.questionCount - answeredCount() }}</strong></div>
              <div class="sumrow"><span>Flagged for review</span><strong>{{ flaggedCount() }}</strong></div>
              <p class="muted small">You can't change answers after submitting. {{ t.aiAvailable ? 'Grading takes a minute or two; you can leave the page.' : 'AI is off, so you will score your answers against the references.' }}</p>
              <div class="row" style="justify-content:flex-end">
                <button class="secondary" (click)="confirming.set(false)">Keep going</button>
                <button (click)="doSubmit()" [disabled]="busy()">Submit test</button>
              </div>
            </div>
          </div>
        }
      }
      @case ('EVALUATING') {
        <a routerLink="/mock-tests" class="small">← Mock tests</a>
        <div class="card grading">
          <div class="spinner"></div>
          <h2>Grading your test…</h2>
          <p class="muted">{{ t.evaluatedCount + t.failedCount }} of {{ t.evaluatedCount + t.pendingCount + t.failedCount }} answers graded.
            Your score is revealed when every answer is graded — you can leave this page and come back.</p>
          <div class="bar" style="max-width:420px;margin:0 auto"><span [style.width.%]="gradePct(t)"></span></div>
        </div>
      }

      @case ('AWAITING_SELF_SCORE') {
        <a routerLink="/mock-tests" class="small">← Mock tests</a>
        <h1 style="margin:.3rem 0">{{ t.title }}</h1>
        <div class="banner info row spread">
          <span>AI grading is off. Compare each answer with the reference and score it honestly — the total is calculated when you finish.</span>
          @if (t.aiAvailable) { <button class="secondary" (click)="gradeWithAi(t)" [disabled]="busy()">Grade with AI instead</button> }
        </div>
        @for (it of answeredItems(t); track it.id) {
          <div class="card">
            <div class="muted small">Q{{ it.position }} @if (it.topic) { · {{ it.topic }} }</div>
            <h3 class="pre">{{ it.question }}</h3>
            <div class="label">Your answer</div><p class="pre mine">{{ it.userAnswer }}</p>
            <div class="label">Reference</div>
            @if (it.referenceAnswer) { <p class="pre">{{ it.referenceAnswer }}</p> } @else { <p class="muted">No reference answer stored for this question.</p> }
            <label>Score: <strong>{{ scoreOf(it) }}/10</strong></label>
            <input type="range" min="0" max="10" step="0.5" [ngModel]="scoreOf(it)" (ngModelChange)="setScore(it, $event)">
          </div>
        }
        <button class="lg" (click)="saveSelfScores(t)" [disabled]="busy()">Finish grading</button>
      }
      @case ('COMPLETED') {
        <a routerLink="/mock-tests" class="small">← Mock tests</a>
        @if (t.results; as r) {
          <div class="card result">
            <app-score-ring [value]="r.percent" [percent]="true" [size]="168" label="score" />
            <div style="flex:1;min-width:260px">
              <div class="muted small">{{ t.title }} · {{ t.completedAt | date:'MMM d, h:mm a' }} · graded {{ t.evaluationMode === 'SELF' ? 'by you' : 'by AI' }}</div>
              <h1 style="margin:.2rem 0 .8rem">{{ r.verdict }}</h1>
              <div class="stats">
                <div><div class="v">{{ r.overallScore ?? '—' }}<span class="muted small">/10</span></div><div class="muted small">overall</div></div>
                <div><div class="v">{{ r.answered }}<span class="muted small">/{{ t.questionCount }}</span></div><div class="muted small">answered</div></div>
                <div><div class="v">{{ r.averageOnAnswered ?? '—' }}</div><div class="muted small">avg on answered</div></div>
                <div><div class="v">{{ duration(t.timeTakenSeconds) }}</div><div class="muted small">time @if (t.timeLimitMinutes) { of {{ t.timeLimitMinutes }}m }</div></div>
              </div>
              @if (r.strongestTopic) {
                <div style="margin-top:.8rem"><span class="chip ok">Strongest: {{ r.strongestTopic }}</span><span class="chip bad">Weakest: {{ r.weakestTopic }}</span></div>
              }
              <div class="row" style="margin-top:1rem">
                @if (r.weakCount) { <button (click)="retake(t, true)" [disabled]="busy()"><app-icon name="brain" /> Retake {{ r.weakCount }} weak</button> }
                <button class="secondary" (click)="retake(t, false)" [disabled]="busy()">Retake all</button>
                <a class="btn secondary" routerLink="/mock-tests">New mock test</a>
                <a class="btn secondary" routerLink="/progress">Progress</a>
              </div>
            </div>
          </div>

          @if (r.failed) {
            <div class="banner error row spread">
              <span>{{ r.failed }} answer(s) couldn't be graded, so they're left out of the score for now.</span>
              <button class="secondary" (click)="retryFailed(t)" [disabled]="busy() || !t.aiAvailable">Retry grading</button>
            </div>
          }

          <div class="grid-2">
            <div class="card"><h2 style="margin-top:0">By topic</h2>
              @for (g of r.byTopic; track g.label) {
                <div class="g"><span class="lbl" [title]="g.label">{{ g.label }}</span>
                  <div [class]="barClass(g.score)"><span [style.width.%]="(g.score ?? 0) * 10"></span></div>
                  <span class="muted small">{{ g.score ?? '—' }} · {{ g.answered }}/{{ g.questions }}</span></div>
              }
            </div>
            <div class="card"><h2 style="margin-top:0">By difficulty</h2>
              @for (g of r.byDifficulty; track g.label) {
                <div class="g"><span class="lbl">{{ g.label }}</span>
                  <div [class]="barClass(g.score)"><span [style.width.%]="(g.score ?? 0) * 10"></span></div>
                  <span class="muted small">{{ g.score ?? '—' }} · {{ g.answered }}/{{ g.questions }}</span></div>
              }
              @if (r.revisionTopics.length) {
                <div class="divider"></div>
                <div class="muted small" style="margin-bottom:.3rem">Revise next</div>
                <div>@for (x of r.revisionTopics; track x) { <span class="chip warn">{{ x }}</span> }</div>
              }
            </div>
          </div>

          <div class="row spread"><h2>Question review</h2>
            <div class="row">
              @for (f of filters; track f.key) {
                <button [class]="filter() === f.key ? '' : 'secondary'" (click)="filter.set(f.key)">{{ f.label }}</button>
              }
            </div>
          </div>
          @for (it of filtered(t); track it.id) {
            <details class="card rev">
              <summary>
                <span class="muted">Q{{ it.position }}</span>
                <span class="qt">{{ it.question }}</span>
                @if (!it.userAnswer) { <span class="chip bad">not answered · 0</span> }
                @else if (it.status === 'FAILED') { <span class="chip warn">not graded</span> }
                @else { <span [class]="scoreChip(it.score)">{{ it.score }}/10</span> }
              </summary>
              <div class="body">
                @if (it.topic) { <span class="chip">{{ it.topic }}</span> }
                @if (it.userAnswer) { <div class="label">Your answer</div><p class="pre mine">{{ it.userAnswer }}</p> }
                @if (it.error) { <div class="banner error">{{ it.error }}</div> }
                @if (it.evaluation; as e) {
                  @if (e.summary) { <p>{{ e.summary }}</p> }
                  <div class="cols">
                    @if (e.correctPoints.length) { <div class="box ok"><div class="label">✓ Nailed</div><ul>@for (p of e.correctPoints; track p) { <li>{{ p }}</li> }</ul></div> }
                    @if (e.missedPoints.length) { <div class="box warn"><div class="label">◌ Missed</div><ul>@for (p of e.missedPoints; track p) { <li>{{ p }}</li> }</ul></div> }
                    @if (e.incorrectStatements.length) { <div class="box bad"><div class="label">✗ Incorrect</div><ul>@for (p of e.incorrectStatements; track p) { <li>{{ p }}</li> }</ul></div> }
                  </div>
                  <div class="label">Model answer</div><p class="pre">{{ e.improvedAnswer }}</p>
                }
                @if (it.referenceAnswer) { <div class="label">Reference answer from your bank</div><p class="pre">{{ it.referenceAnswer }}</p> }
                <div class="row" style="margin-top:.6rem">
                  @if (it.questionId) {
                    <button class="secondary" (click)="practiseOne(it)">Practise this again</button>
                    <a class="btn secondary" [routerLink]="['/questions', it.questionId, 'edit']">Open question</a>
                  }
                </div>
              </div>
            </details>
          } @empty { <div class="card empty">Nothing in this view.</div> }
        }
      }
    }
  }`,
  styles: [`
    .top { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .timer { display: inline-flex; gap: .4rem; align-items: center; font-weight: 800; font-variant-numeric: tabular-nums; padding: .45rem .8rem;
      border-radius: 10px; background: var(--bg-2); }
    .timer.low { background: var(--bad-soft); color: var(--bad-2); animation: pulse 1.2s infinite; }
    @keyframes pulse { 50% { opacity: .6; } }
    .layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 1rem; align-items: start; }
    .navp { position: sticky; top: 1rem; }
    .cells { display: grid; grid-template-columns: repeat(5, 1fr); gap: .35rem; margin: .7rem 0; }
    .cell { padding: 0; height: 34px; border-radius: 8px; background: var(--bg-2); color: var(--ink-2); border: 1.5px solid var(--line); font-size: .82rem; }
    .cell.done { background: var(--ok-soft); color: var(--ok); border-color: color-mix(in srgb, var(--ok) 40%, transparent); }
    .cell.flag { background: var(--warn-soft); color: var(--warn); border-color: color-mix(in srgb, var(--warn) 50%, transparent); }
    .cell.cur { border-color: var(--brand); box-shadow: 0 0 0 2px color-mix(in srgb, var(--brand) 35%, transparent); font-weight: 800; }
    .legend { display: flex; flex-wrap: wrap; gap: .6rem; }
    .sw { display: inline-block; width: 10px; height: 10px; border-radius: 3px; background: var(--bg-2); border: 1px solid var(--line); margin-right: .25rem; vertical-align: -1px; }
    .sw.done { background: var(--ok-soft); } .sw.flag { background: var(--warn-soft); }
    .qpane { padding: 1.4rem 1.6rem; border-top: 4px solid var(--brand); }
    .question { font-size: 1.3rem; font-weight: 700; line-height: 1.45; margin: .9rem 0 1rem; }
    .flag-on { border-color: var(--warn) !important; color: var(--warn) !important; }
    .saved { color: var(--ok); } .saving { color: var(--muted); } .unsaved { color: var(--warn); } .err { color: var(--bad-2); }
    .overlay { position: fixed; inset: 0; background: rgba(8, 12, 24, .55); display: grid; place-items: center; z-index: 50; padding: 1rem; }
    .modal { max-width: 440px; width: 100%; margin: 0; }
    .sumrow { display: flex; justify-content: space-between; padding: .45rem 0; border-bottom: 1px solid var(--line); }
    .grading { text-align: center; padding: 3rem 1.5rem; margin-top: .6rem; }
    .spinner { width: 54px; height: 54px; margin: 0 auto 1rem; border-radius: 50%; border: 5px solid var(--bg-2); border-top-color: var(--brand); animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .label { font-size: .75rem; font-weight: 800; text-transform: uppercase; letter-spacing: .06em; color: var(--muted); margin-top: .7rem; }
    .mine { background: var(--bg-2); padding: .6rem .8rem; border-radius: 10px; }
    .result { display: flex; gap: 2rem; align-items: center; flex-wrap: wrap; padding: 1.6rem; margin-top: .6rem; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); gap: 1rem; }
    .stats .v { font-size: 1.6rem; font-weight: 800; letter-spacing: -.02em; }
    .g { display: grid; grid-template-columns: minmax(0, 1fr) minmax(60px, 40%) 5.5rem; gap: .6rem; align-items: center; margin: .45rem 0; font-size: .9rem; }
    .g .lbl { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .rev { padding: .7rem 1rem; }
    .rev summary { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .6rem; align-items: center; cursor: pointer; }
    .rev .qt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
    .rev .body { margin-top: .6rem; }
    .cols { display: grid; gap: .8rem; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); margin-top: .6rem; }
    .box { border-radius: 12px; padding: .6rem .9rem; font-size: .9rem; }
    .box ul { margin: .3rem 0 0; padding-left: 1.1rem; }
    .box.ok { background: var(--ok-soft); } .box.warn { background: var(--warn-soft); } .box.bad { background: var(--bad-soft); }
    @media (max-width: 860px) {
      .layout { grid-template-columns: 1fr; }
      .navp { position: static; }
      .cells { grid-template-columns: repeat(10, 1fr); }
      .question { font-size: 1.12rem; }
    }
  `]
})
export class MockTestRunComponent implements OnInit, OnDestroy {
  @Input() id!: string;
  private mock = inject(MockApi);
  private practice = inject(ApiService);
  private router = inject(Router);

  t = signal<MockTest | null>(null);
  idx = signal(0);
  busy = signal(false);
  error = signal<string | null>(null);
  confirming = signal(false);
  now = signal(Date.now());
  saveState = signal<'idle' | 'dirty' | 'saving' | 'saved' | 'error'>('idle');
  filter = signal<Filter>('ALL');
  filters: { key: Filter; label: string }[] = [{ key: 'ALL', label: 'All' }, { key: 'WEAK', label: 'Below 6' }, { key: 'UNANSWERED', label: 'Unanswered' }];
  item = computed(() => this.t()?.items[this.idx()] ?? null);

  private selfScores: Record<number, number> = {};
  private drafts = new Map<number, string>();
  private dirty = new Set<number>();
  private deadline: number | null = null;
  private tick?: ReturnType<typeof setInterval>;
  private poll?: ReturnType<typeof setInterval>;
  private saveTimer?: ReturnType<typeof setTimeout>;
  private autoSubmitting = false;

  ngOnInit() {
    this.load();
    this.tick = setInterval(() => this.onTick(), 1000);
  }

  ngOnDestroy() {
    if (this.tick) clearInterval(this.tick);
    this.stopPolling();
    if (this.saveTimer) clearTimeout(this.saveTimer);
    if (this.dirty.size) void this.flush();
  }

  private load() {
    this.mock.get(+this.id).subscribe({ next: t => this.apply(t), error: e => this.error.set(errorMessage(e)) });
  }

  private apply(t: MockTest) {
    this.t.set(t);
    this.deadline = t.status === 'IN_PROGRESS' && t.remainingSeconds != null ? Date.now() + t.remainingSeconds * 1000 : null;
    if (t.status === 'EVALUATING') this.startPolling(); else this.stopPolling();
    if (t.status === 'AWAITING_SELF_SCORE') for (const it of t.items) if (it.status === 'ANSWERED' && this.selfScores[it.id] == null) this.selfScores[it.id] = 5;
    if (this.idx() >= t.items.length) this.idx.set(0);
  }

  // ---------------------------------------------------------------- timer
  private onTick() {
    this.now.set(Date.now());
    const t = this.t();
    if (t?.status === 'IN_PROGRESS' && this.deadline != null && Date.now() >= this.deadline && !this.autoSubmitting) {
      this.autoSubmitting = true;
      void this.doSubmit();
    }
  }
  clock(t: MockTest) {
    const n = this.now();
    const sec = this.deadline != null ? Math.max(0, Math.round((this.deadline - n) / 1000))
                                      : Math.max(0, Math.round((n - new Date(t.startedAt).getTime()) / 1000));
    return this.duration(sec);
  }
  timerClass() { return 'timer' + (this.deadline != null && this.deadline - this.now() < 5 * 60 * 1000 ? ' low' : ''); }
  duration(sec?: number | null) {
    if (sec == null) return '—';
    const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    return (h ? h + ':' + String(m).padStart(2, '0') : String(m)) + ':' + String(s).padStart(2, '0');
  }

  // ---------------------------------------------------------------- answering
  draft(it: MockItem) { return this.drafts.get(it.id) ?? it.userAnswer ?? ''; }
  isAnswered(it: MockItem) { return this.draft(it).trim().length > 0; }
  answeredCount() { return this.t()?.items.filter(i => this.isAnswered(i)).length ?? 0; }
  flaggedCount() { return this.t()?.items.filter(i => i.flagged).length ?? 0; }
  words(it: MockItem) { const s = this.draft(it).trim(); return s ? s.split(/\s+/).length : 0; }

  edit(it: MockItem, v: string) {
    this.drafts.set(it.id, v ?? '');
    this.dirty.add(it.id);
    this.saveState.set('dirty');
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => void this.flush(), 1200);
  }

  async flush(): Promise<boolean> {
    const t = this.t();
    if (!t || t.status !== 'IN_PROGRESS' || !this.dirty.size) return true;
    const ids = [...this.dirty];
    this.dirty.clear();
    this.saveState.set('saving');
    try {
      for (const id of ids) {
        const text = this.drafts.get(id) ?? '';
        const r = await firstValueFrom(this.mock.save(t.id, id, { answer: text }));
        this.patch(id, { status: r.status, userAnswer: text.trim() || null });
      }
      if (!this.dirty.size) this.saveState.set('saved');
      return true;
    } catch (e) {
      ids.forEach(i => this.dirty.add(i));
      this.saveState.set('error');
      this.error.set(errorMessage(e));
      if (e instanceof HttpErrorResponse && e.status === 400) this.load();   // time is up or already submitted
      return false;
    }
  }

  saveLabel() {
    switch (this.saveState()) {
      case 'dirty': return 'Unsaved changes…';
      case 'saving': return 'Saving…';
      case 'saved': return 'All answers saved ✓';
      case 'error': return 'Could not save — retrying when you continue';
      default: return 'Answers autosave';
    }
  }
  saveClass() {
    const s = this.saveState();
    return s === 'saved' ? 'saved' : s === 'saving' ? 'saving' : s === 'dirty' ? 'unsaved' : s === 'error' ? 'err' : 'muted';
  }

  private patch(id: number, p: Partial<MockItem>) {
    this.t.update(x => x ? { ...x, items: x.items.map(i => i.id === id ? { ...i, ...p } : i) } : x);
  }

  cellClass(it: MockItem, i: number) {
    return 'cell' + (i === this.idx() ? ' cur' : '') + (it.flagged ? ' flag' : this.isAnswered(it) ? ' done' : '');
  }

  go(i: number) {
    const t = this.t();
    if (!t || i < 0 || i >= t.items.length) return;
    void this.flush();
    this.idx.set(i);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  toggleFlag(it: MockItem) {
    const t = this.t(); if (!t) return;
    const flagged = !it.flagged;
    this.patch(it.id, { flagged });
    this.mock.save(t.id, it.id, { flagged }).subscribe({ error: e => { this.patch(it.id, { flagged: !flagged }); this.error.set(errorMessage(e)); } });
  }

  askSubmit() { void this.flush(); this.confirming.set(true); }

  async doSubmit() {
    const t = this.t(); if (!t) return;
    this.confirming.set(false);
    this.busy.set(true);
    await this.flush();
    this.mock.submit(t.id).subscribe({
      next: x => { this.busy.set(false); this.apply(x); window.scrollTo({ top: 0 }); },
      error: e => { this.busy.set(false); this.autoSubmitting = false; this.error.set(errorMessage(e)); }
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKey(e: KeyboardEvent) {
    if (this.t()?.status !== 'IN_PROGRESS' || this.confirming()) return;
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      const t = this.t()!;
      if (this.idx() < t.items.length - 1) this.go(this.idx() + 1); else this.askSubmit();
    }
  }

  @HostListener('window:beforeunload', ['$event'])
  onUnload(e: BeforeUnloadEvent) {
    if (this.dirty.size) { e.preventDefault(); e.returnValue = ''; }
  }

  // ---------------------------------------------------------------- grading
  private startPolling() {
    if (this.poll) return;
    this.poll = setInterval(() => this.mock.get(+this.id).subscribe({ next: x => this.apply(x), error: () => {} }), 2500);
  }
  private stopPolling() { if (this.poll) { clearInterval(this.poll); this.poll = undefined; } }
  gradePct(t: MockTest) {
    const total = t.evaluatedCount + t.pendingCount + t.failedCount;
    return total ? ((t.evaluatedCount + t.failedCount) / total) * 100 : 100;
  }

  answeredItems(t: MockTest) { return t.items.filter(i => i.status === 'ANSWERED'); }
  scoreOf(it: MockItem) { return this.selfScores[it.id] ?? 5; }
  setScore(it: MockItem, v: number | string) { this.selfScores[it.id] = Number(v); }
  saveSelfScores(t: MockTest) {
    const scores: Record<number, number> = {};
    for (const it of this.answeredItems(t)) scores[it.id] = this.scoreOf(it);
    this.busy.set(true);
    this.mock.selfScore(t.id, scores).subscribe({
      next: x => { this.busy.set(false); this.apply(x); window.scrollTo({ top: 0 }); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
  gradeWithAi(t: MockTest) {
    this.busy.set(true);
    this.mock.gradeWithAi(t.id).subscribe({ next: x => { this.busy.set(false); this.apply(x); }, error: e => { this.busy.set(false); this.error.set(errorMessage(e)); } });
  }

  // ---------------------------------------------------------------- results
  filtered(t: MockTest) {
    const f = this.filter();
    return t.items.filter(i => f === 'ALL' || (f === 'UNANSWERED' ? !i.userAnswer : (i.score != null && i.score < 6)));
  }
  scoreChip(v?: number | null) { return 'chip ' + (v == null ? 'diff' : v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }
  barClass(v?: number | null) { return 'bar ' + (v == null ? '' : v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }

  retryFailed(t: MockTest) {
    this.busy.set(true);
    this.mock.retryFailed(t.id).subscribe({ next: x => { this.busy.set(false); this.apply(x); }, error: e => { this.busy.set(false); this.error.set(errorMessage(e)); } });
  }

  retake(t: MockTest, onlyWeak: boolean) {
    this.busy.set(true);
    this.mock.retake(t.id, onlyWeak, onlyWeak ? null : t.timeLimitMinutes).subscribe({
      next: n => { this.busy.set(false); this.router.navigate(['/mock-tests', n.id]).then(() => { this.id = String(n.id); this.drafts.clear(); this.idx.set(0); this.apply(n); }); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  practiseOne(it: MockItem) {
    if (!it.questionId) return;
    this.practice.startSession({ type: 'SINGLE', questionId: it.questionId }).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => this.error.set(errorMessage(e))
    });
  }
}
