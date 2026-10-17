import { Component, Input, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { Confidence, Rubric, Session, Turn } from '../core/models';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';

@Component({
  selector: 'app-interview-session', standalone: true, imports: [FormsModule, RouterLink, IconComponent, ScoreRingComponent],
  template: `
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (!s()) { <div class="skeleton" style="height:320px;border-radius:18px"></div> }
  @if (s(); as s) {
    <div class="top">
      <div>
        <a routerLink="/practice" class="small">← Practice</a>
        <h1 style="margin:.2rem 0 0">{{ s.title }}</h1>
      </div>
      @if (s.status === 'ACTIVE') {
        <div class="row">
          <span class="chip diff"><app-icon name="clock" [size]="14" /> {{ elapsed() }}</span>
          <button class="secondary" (click)="end()" [disabled]="busy()">End session</button>
        </div>
      }
    </div>
    <div class="progress">
      <div class="bar"><span [style.width.%]="progressPct(s)"></span></div>
      <span class="muted small">Question {{ s.bankQuestionsAsked }} of {{ s.questionLimit }}</span>
    </div>
    @if (s.message && s.status === 'ACTIVE') { <div class="banner info">{{ s.message }}</div> }

    <!-- ============ COMPLETED SUMMARY ============ -->
    @if (s.status === 'COMPLETED') {
      <div class="card summary">
        <app-score-ring [value]="s.overallScore" [size]="150" label="overall" />
        <div>
          <h2 style="margin-top:0">{{ verdict(s.overallScore) }}</h2>
          <p class="muted">{{ answered(s) }} answered · {{ skipped(s) }} skipped · {{ followUps(s) }} follow-up(s)</p>
          @if (topics(s).length) {
            <div class="muted small" style="margin-bottom:.3rem">Revise next:</div>
            <div>
              @for (t of topics(s); track t) { <span class="chip warn">{{ t }}</span> }
            </div>
          }
          <div class="row" style="margin-top:1rem">
            <button (click)="revise()" [disabled]="busy()"><app-icon name="brain" /> Revise weak answers</button>
            <a class="btn secondary" routerLink="/practice">New session</a>
            <a class="btn secondary" routerLink="/progress">See progress</a>
          </div>
        </div>
      </div>

      <h2>Answers</h2>
      @for (t of s.turns; track t.id) {
        <details class="card hist">
          <summary class="row spread">
            <span><span class="muted">Q{{ t.turnNumber }}</span> {{ t.question }}</span>
            <span>
              @if (t.score != null) {
                <span [class]="scoreClass(t.score)">{{ t.score }}</span>
              } @else {
                <span class="chip diff">{{ t.status.toLowerCase() }}</span>
              }
            </span>
          </summary>
          @if (t.userAnswer) {
            <div class="label">Your answer</div>
            <p class="pre">{{ t.userAnswer }}</p>
          }
          @if (t.evaluation; as e) {
            @if (e.missedPoints.length) {
              <div class="label">Missed</div>
              <ul>
                @for (p of e.missedPoints; track p) { <li>{{ p }}</li> }
              </ul>
            }
            <div class="label">Model answer</div>
            <p class="pre">{{ e.improvedAnswer }}</p>
          } @else {
            @if (t.referenceAnswer) {
              <div class="label">Reference answer</div>
              <p class="pre">{{ t.referenceAnswer }}</p>
            }
          }
        </details>
      }
    }

    <!-- ============ ACTIVE ============ -->
    @if (s.status === 'ACTIVE') {
      @if (current(); as t) {
        <!-- current question -->
        <div class="card qcard">
          <div class="row">
            <span class="chip diff">Q{{ t.turnNumber }}</span>
            @if (t.topic) { <span class="chip">{{ t.topic }}</span> }
            @if (t.difficulty) { <span [class]="'chip ' + t.difficulty">{{ t.difficulty }}</span> }
            @if (t.sessionOnly) {
              <span class="chip warn" title="Generated for this session only; not attributed to any company">AI follow-up · practice only</span>
            }
            @for (c of t.companies; track c) { <span class="chip company">{{ c }}</span> }
          </div>
          <div class="question pre">{{ t.question }}</div>

          @if (t.status === 'ASKED') {
            <textarea [(ngModel)]="answer" rows="9"
                      placeholder="Answer as you would in the interview: key idea first, then details, trade-offs and an example."
                      (keydown)="onKey($event)" [disabled]="busy()"></textarea>
            <div class="row spread" style="margin-top:.5rem">
              <div class="row">
                <span class="muted small">Confidence</span>
                @for (c of confidences; track c) {
                  <span class="chip pill" [class.ok]="confidence === c" (click)="toggleConfidence(c)">{{ c.toLowerCase() }}</span>
                }
              </div>
              <span class="muted small">{{ words() }} words · <span class="kbd">Ctrl</span>+<span class="kbd">Enter</span> to submit</span>
            </div>
            @if (busy()) {
              <div class="thinking">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                {{ s.aiEnabled ? 'Your interviewer is reviewing the answer…' : 'Saving…' }}
              </div>
            } @else {
              <div class="row" style="margin-top:1rem">
                <button class="lg" (click)="submit()" [disabled]="!answer.trim()"><app-icon name="check" /> Submit answer</button>
                <button class="ghost" (click)="skip()">Skip</button>
              </div>
            }
          }

          @if (t.status === 'AWAITING_SELF_SCORE') {
            <div class="label">Your answer</div>
            <p class="pre mine">{{ t.userAnswer }}</p>
            <div class="label">Reference</div>
            @if (t.referenceAnswer) {
              <p class="pre">{{ t.referenceAnswer }}</p>
            } @else if (t.expectedPoints.length) {
              <ul>
                @for (p of t.expectedPoints; track p) { <li>{{ p }}</li> }
              </ul>
            } @else {
              <p class="muted">No reference answer stored for this question — score yourself honestly.</p>
            }
            <label>How did you do? <strong>{{ selfScore }}/10</strong></label>
            <input type="range" min="0" max="10" step="0.5" [(ngModel)]="selfScore">
            <button style="margin-top:.8rem" (click)="saveSelfScore(t)" [disabled]="busy()">Save score</button>
          }
        </div>
      } @else {
        @if (last(); as t) {
          <!-- feedback for the most recent turn -->
          <div class="card fb">
            <div class="fb-head">
              @if (t.score != null) {
                <app-score-ring [value]="t.score" [size]="120" label="score" />
              } @else {
                <div class="skipped">Skipped</div>
              }
              <div style="flex:1;min-width:240px">
                <div class="muted small">
                  Q{{ t.turnNumber }}
                  @if (t.sessionOnly) { · AI follow-up (practice only) }
                </div>
                <h2 style="margin:.2rem 0 .4rem">{{ t.question }}</h2>
                @if (t.evaluation?.summary) { <p style="margin:0">{{ t.evaluation?.summary }}</p> }
                @if (t.evaluation?.rubric; as r) {
                  <div class="rubric">
                    @for (d of dims(r); track d.label) {
                      @if (d.value != null) {
                        <div class="dim">
                          <span>{{ d.label }}</span>
                          <div [class]="barClass(d.value)"><span [style.width.%]="d.value * 10"></span></div>
                          <span class="muted small">{{ d.value }}</span>
                        </div>
                      }
                    }
                  </div>
                }
              </div>
            </div>

            @if (t.evaluation; as e) {
              <div class="cols">
                @if (e.correctPoints.length) {
                  <div class="box ok"><div class="label">✓ What you nailed</div>
                    <ul>@for (p of e.correctPoints; track p) { <li>{{ p }}</li> }</ul></div>
                }
                @if (e.missedPoints.length) {
                  <div class="box warn"><div class="label">◌ What you missed</div>
                    <ul>@for (p of e.missedPoints; track p) { <li>{{ p }}</li> }</ul></div>
                }
                @if (e.incorrectStatements.length) {
                  <div class="box bad"><div class="label">✗ Incorrect statements</div>
                    <ul>@for (p of e.incorrectStatements; track p) { <li>{{ p }}</li> }</ul></div>
                }
              </div>
              <details class="more" open><summary>Model answer</summary><p class="pre">{{ e.improvedAnswer }}</p></details>
              @if (e.revisionTopics.length) {
                <div style="margin-top:.6rem">
                  <span class="muted small">Revise:</span>
                  @for (r of e.revisionTopics; track r) { <span class="chip warn">{{ r }}</span> }
                </div>
              }
              <div class="muted small" style="margin-top:.4rem">Graded by {{ e.model }}</div>
            }
            @if (t.userAnswer) {
              <details class="more"><summary>Your answer</summary><p class="pre">{{ t.userAnswer }}</p></details>
            }
            @if (t.referenceAnswer) {
              <details class="more"><summary>Reference answer from your bank</summary><p class="pre">{{ t.referenceAnswer }}</p></details>
            }
            @if (!t.referenceAnswer && t.expectedPoints.length && !t.evaluation) {
              <details class="more" open><summary>Expected points</summary>
                <ul>@for (p of t.expectedPoints; track p) { <li>{{ p }}</li> }</ul></details>
            }

            <div class="row" style="margin-top:1.2rem">
              <button class="lg" (click)="nextQ()" [disabled]="busy()">{{ busy() ? 'Loading…' : 'Next question' }} <app-icon name="arrow" /></button>
              @if (t.followUpAvailable) {
                <button class="lg secondary" (click)="followUp(t)" [disabled]="busy()"><app-icon name="spark" /> Ask me a follow-up</button>
              }
              @if (t.questionId) {
                <a class="btn secondary" [routerLink]="['/questions', t.questionId, 'edit']">Open question</a>
              }
            </div>
          </div>
        }
      }

      @if (s.turns.length > 1) {
        <h2>Earlier in this session</h2>
        @for (t of earlier(s); track t.id) {
          <div class="mini row spread">
            <span><span class="muted">Q{{ t.turnNumber }}</span> {{ t.question }}</span>
            @if (t.score != null) {
              <span [class]="scoreClass(t.score)">{{ t.score }}</span>
            } @else {
              <span class="chip diff">{{ t.status.toLowerCase() }}</span>
            }
          </div>
        }
      }
    }
  }
  `,
  styles: [`
    .top { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; flex-wrap: wrap; margin-bottom: .8rem; }
    .progress { display: flex; align-items: center; gap: .8rem; margin-bottom: 1.2rem; }
    .progress .bar { flex: 1; }
    .qcard { padding: 1.5rem 1.6rem; border-top: 4px solid var(--brand); }
    .question { font-size: 1.35rem; font-weight: 700; letter-spacing: -.01em; line-height: 1.4; margin: .8rem 0 1.1rem; }
    .pill { cursor: pointer; user-select: none; }
    .thinking { display: flex; align-items: center; gap: .35rem; margin-top: 1rem; color: var(--brand); font-weight: 600; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--brand); animation: blink 1.2s infinite both; }
    .dot:nth-child(2) { animation-delay: .2s; }
    .dot:nth-child(3) { animation-delay: .4s; margin-right: .4rem; }
    @keyframes blink { 0%, 80%, 100% { opacity: .2; transform: scale(.8); } 40% { opacity: 1; transform: scale(1); } }
    .fb-head { display: flex; gap: 1.5rem; align-items: flex-start; flex-wrap: wrap; }
    .skipped { width: 120px; height: 120px; border-radius: 50%; display: grid; place-items: center; background: var(--bg-2); color: var(--muted); font-weight: 700; }
    .rubric { margin-top: .8rem; display: grid; gap: .35rem; }
    .dim { display: grid; grid-template-columns: 190px 1fr 28px; gap: .6rem; align-items: center; font-size: .85rem; }
    .cols { display: grid; gap: .8rem; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); margin-top: 1rem; }
    .box { border-radius: 12px; padding: .7rem .9rem; font-size: .9rem; }
    .box ul { margin: .3rem 0 0; padding-left: 1.1rem; }
    .box.ok { background: var(--ok-soft); }
    .box.warn { background: var(--warn-soft); }
    .box.bad { background: var(--bad-soft); }
    .label { font-size: .75rem; font-weight: 800; text-transform: uppercase; letter-spacing: .06em; color: var(--muted); margin-top: .6rem; }
    .more { margin-top: .8rem; border: 1px solid var(--line); border-radius: 12px; padding: .5rem .9rem; }
    .more summary, .hist summary { cursor: pointer; font-weight: 700; }
    .mine { background: var(--bg-2); padding: .6rem .8rem; border-radius: 10px; }
    .summary { display: flex; gap: 2rem; align-items: center; flex-wrap: wrap; padding: 1.6rem; }
    .mini { padding: .5rem .2rem; border-bottom: 1px solid var(--line); font-size: .92rem; }
    .hist { padding: .8rem 1rem; }
    @media (max-width: 640px) { .dim { grid-template-columns: 120px 1fr 28px; } .question { font-size: 1.15rem; } }
  `]
})
export class InterviewSessionComponent implements OnInit, OnDestroy {
  @Input() id!: string;
  api = inject(ApiService);
  private router = inject(Router);
  s = signal<Session | null>(null);
  busy = signal(false);
  error = signal<string | null>(null);
  now = signal(Date.now());
  answer = '';
  confidence: Confidence | null = null;
  selfScore = 5;
  confidences: Confidence[] = ['LOW', 'MEDIUM', 'HIGH'];
  private timer?: ReturnType<typeof setInterval>;

  current = computed(() => { const s = this.s(); return s?.turns.find(t => t.id === s.currentTurnId) ?? null; });
  last = computed(() => { const s = this.s(); return s && s.turns.length ? s.turns[s.turns.length - 1] : null; });
  elapsed = computed(() => {
    const s = this.s();
    if (!s) return '0:00';
    const sec = Math.max(0, Math.floor((this.now() - new Date(s.startedAt).getTime()) / 1000));
    return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;
  });

  ngOnInit() {
    this.load();
    this.timer = setInterval(() => this.now.set(Date.now()), 1000);
  }

  ngOnDestroy() { if (this.timer) clearInterval(this.timer); }

  private load() { this.run(this.api.session(+this.id)); }

  private run(obs: Observable<Session>, after?: () => void) {
    this.busy.set(true);
    this.error.set(null);
    obs.subscribe({
      next: s => { this.s.set(s); this.busy.set(false); after?.(); window.scrollTo({ top: 0, behavior: 'smooth' }); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  words() { const t = this.answer.trim(); return t ? t.split(/\s+/).length : 0; }

  onKey(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); this.submit(); }
  }

  toggleConfidence(c: Confidence) { this.confidence = this.confidence === c ? null : c; }

  submit() {
    const s = this.s(), t = this.current();
    if (!s || !t || !this.answer.trim() || this.busy()) return;
    this.run(this.api.answer(s.id, t.id, this.answer, this.confidence), () => {
      this.answer = ''; this.confidence = null; this.selfScore = 5;
    });
  }

  saveSelfScore(t: Turn) { this.run(this.api.selfScore(this.s()!.id, t.id, Number(this.selfScore))); }
  skip() { const t = this.current(); if (t) this.run(this.api.skip(this.s()!.id, t.id)); }
  followUp(t: Turn) { this.run(this.api.sessionFollowUp(this.s()!.id, t.id)); }
  nextQ() { this.run(this.api.next(this.s()!.id)); }

  end() {
    if (confirm('End this session? Unanswered questions are marked skipped.')) this.run(this.api.complete(this.s()!.id));
  }

  revise() {
    this.busy.set(true);
    this.api.startSession({ type: 'REVISION', questionLimit: 8 }).subscribe({
      next: n => { this.router.navigate(['/practice', n.id]).then(() => { this.id = String(n.id); this.load(); }); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  progressPct(s: Session) {
    if (s.status === 'COMPLETED') return 100;
    return Math.min(100, (Math.max(0, s.bankQuestionsAsked - (this.current() ? 1 : 0)) / s.questionLimit) * 100);
  }
  answered(s: Session) { return s.turns.filter(t => t.status === 'EVALUATED').length; }
  skipped(s: Session) { return s.turns.filter(t => t.status === 'SKIPPED').length; }
  followUps(s: Session) { return s.turns.filter(t => t.type === 'FOLLOW_UP').length; }
  earlier(s: Session) { return s.turns.slice(0, -1).reverse(); }

  topics(s: Session) {
    const set = new Set<string>();
    s.turns.forEach(t => t.evaluation?.revisionTopics.forEach(r => set.add(r)));
    return [...set].slice(0, 10);
  }

  verdict(v?: number | null) {
    if (v == null) return 'Session complete';
    if (v >= 8.5) return 'Outstanding — interview-ready on this set 🏆';
    if (v >= 7) return 'Strong performance 💪';
    if (v >= 5) return 'Solid base, a few gaps to close';
    return 'Good practice — now revise the gaps';
  }

  scoreClass(v: number) { return 'chip ' + (v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }
  barClass(v: number) { return 'bar ' + (v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }

  dims(r: Rubric) {
    return [
      { label: 'Technical correctness', value: r.technicalCorrectness },
      { label: 'Conceptual understanding', value: r.conceptualUnderstanding },
      { label: 'Completeness', value: r.completeness },
      { label: 'Practical application', value: r.practicalApplication },
      { label: 'Edge cases & trade-offs', value: r.edgeCasesAndTradeoffs },
      { label: 'Clarity', value: r.clarity }
    ];
  }
}
