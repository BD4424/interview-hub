import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Company, Topic } from '../core/models';
import { StudyApi } from '../core/study.api';
import { PlanDetail, PlanItem } from '../core/study.models';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';

@Component({
  selector: 'app-plan-detail', standalone: true, imports: [FormsModule, RouterLink, DatePipe, IconComponent, ScoreRingComponent],
  template: `
  <a routerLink="/plans" class="small">← Interview plans</a>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (!d() && !error()) { <div class="skeleton" style="height:220px;margin-top:1rem"></div> }
  @if (d(); as d) {
    <section class="hero" style="margin-top:.6rem">
      <div class="hero-row">
        <div class="count">
          <div class="n">{{ d.plan.phase === 'TODAY' ? '🎯' : d.plan.phase === 'PAST' ? '✓' : d.plan.daysLeft }}</div>
          <div class="l">{{ d.plan.phase === 'TODAY' ? 'interview today' : d.plan.phase === 'PAST' ? 'interview done' : d.plan.daysLeft === 1 ? 'day to go' : 'days to go' }}</div>
        </div>
        <div style="flex:1;min-width:220px">
          <h1>{{ d.plan.title }}</h1>
          <p>{{ d.plan.interviewDate | date:'EEEE, MMMM d, y' }} @if (d.plan.company) { · {{ d.plan.company }} }</p>
          <p class="small" style="opacity:.85;margin-top:.3rem">{{ d.scope }}</p>
        </div>
        <app-score-ring [value]="d.plan.readiness" [percent]="true" [size]="124" label="readiness" />
      </div>
    </section>

    @if (d.plan.phase === 'TODAY') {
      <div class="banner info">It's interview day. Skim your weakest answers once, then stop — rest beats cramming. Good luck! 🍀</div>
    }

    <div class="grid kpis">
      <div class="card"><div class="muted">Questions in plan</div><div class="stat">{{ d.plan.questions }}</div><div class="muted small">{{ d.plan.practiced }} practised</div></div>
      <div class="card"><div class="muted">Still to cover</div><div class="stat">{{ d.pendingTotal }}</div><div class="muted small">weak, due or never practised</div></div>
      <div class="card"><div class="muted">Daily load</div><div class="stat">{{ d.perDay }}</div><div class="muted small">{{ d.plan.dailyTarget ? 'your target' : 'auto-balanced' }}</div></div>
      <div class="card"><div class="muted">Today</div><div class="stat">{{ d.plan.todayDone }}<span class="muted" style="font-size:1rem">/{{ d.plan.todayTarget }}</span></div>
        <div class="bar ok" style="margin-top:.5rem"><span [style.width.%]="d.plan.todayTarget ? d.plan.todayDone / d.plan.todayTarget * 100 : 100"></span></div></div>
    </div>

    @if (d.plan.phase === 'UPCOMING') {
      <div class="card">
        <div class="row spread">
          <h2 style="margin:0">Today's plan</h2>
          <div class="row">
            <button (click)="startToday(d)" [disabled]="busy() || !pendingToday(d).length"><app-icon name="practice" /> Start today's session</button>
            <button class="secondary" (click)="flash(d)">Flashcards</button>
          </div>
        </div>
        <p class="muted small" style="margin:.4rem 0 .2rem">The session draws from this plan's scope ({{ d.plan.company ? 'company questions' : 'priority order' }}); ticks appear here as you answer. Use ▶ to practise a specific item.</p>
        @for (i of d.today; track i.questionId) {
          <div class="item" [class.done]="i.done">
            <span class="tick">@if (i.done) { <app-icon name="check" [size]="14" [stroke]="3" /> }</span>
            <a [routerLink]="['/questions', i.questionId, 'edit']" class="t">{{ i.question }}</a>
            <span class="muted small">{{ i.topic }}</span>
            @if (i.lastScore != null) { <span [class]="chip(i)">{{ i.lastScore }}</span> } @else { <span class="chip diff">new</span> }
            @if (!i.done) { <button class="ghost" (click)="practiceOne(i)" title="Practise"><app-icon name="practice" [size]="16" /></button> }
          </div>
        } @empty { <div class="empty"><div class="big">🌟</div>Nothing left for today — everything in scope is practised and not yet due.</div> }
        @if (d.plan.todayTarget && d.plan.todayDone >= d.plan.todayTarget) { <div class="banner ok" style="margin-top:.8rem">Today's plan is done. Great work!</div> }
      </div>

      @if (d.upcoming.length) {
        <h2>Coming up</h2>
        <div class="days">
          @for (day of d.upcoming; track day.date) {
            <details class="card day">
              <summary class="row spread"><span><strong>{{ day.date | date:'EEE, MMM d' }}</strong></span><span class="chip diff">{{ day.items.length }} questions</span></summary>
              @for (i of day.items; track i.questionId) { <div class="mini"><span class="muted small">{{ i.topic }}</span> {{ i.question }}</div> }
            </details>
          }
        </div>
        @if (d.unscheduled > 0) { <p class="muted small">{{ d.unscheduled }} more question(s) don't fit in the remaining days at this pace — raise "questions per day" to cover everything.</p> }
      }
    }

    <div class="card">
      <h2 style="margin-top:0">Readiness by topic</h2>
      @for (t of d.byTopic; track t.topic) {
        <div class="rt"><a [routerLink]="['/topics', t.topicId]">{{ t.topic }}</a>
          <div class="bar" [class]="'bar ' + ((t.readiness ?? 0) >= 70 ? 'ok' : (t.readiness ?? 0) >= 40 ? 'warn' : 'bad')"><span [style.width.%]="t.readiness ?? 0"></span></div>
          <span class="muted small">{{ t.readiness ?? 0 }}% · {{ t.practiced }}/{{ t.questions }}</span></div>
      } @empty { <p class="muted">No questions in scope yet.</p> }
    </div>

    <div class="card">
      <div class="row spread"><h2 style="margin:0">Plan settings</h2>
        @if (!editing()) { <div class="row"><button class="secondary" (click)="startEdit(d)">Edit</button><button class="danger" (click)="remove(d)">Delete</button></div> }</div>
      @if (editing()) {
        <div class="grid" style="margin-top:.6rem">
          <div><label>Title</label><input [(ngModel)]="f.title"></div>
          <div><label>Company</label><select [(ngModel)]="f.companyId"><option [ngValue]="null">Not company-specific</option>
            @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select></div>
          <div><label>Interview date</label><input type="date" [(ngModel)]="f.date"></div>
          <div><label>Questions per day</label><input type="number" min="1" max="50" [(ngModel)]="f.dailyTarget" placeholder="Auto"></div>
        </div>
        <label>Focus topics</label>
        <div>@for (t of topics(); track t.id) { <span class="chip pick" [class.ok]="f.topicIds.has(t.id)" (click)="toggle(t.id)">{{ t.name }}</span> }</div>
        <label>Notes</label><textarea [(ngModel)]="f.notes" rows="2"></textarea>
        <div class="row" style="margin-top:.8rem"><button (click)="save()" [disabled]="busy()">Save</button><button class="secondary" (click)="editing.set(false)">Cancel</button></div>
      } @else {
        @if (d.plan.notes) { <p class="pre">{{ d.plan.notes }}</p> }
        <p class="muted small">Focus: {{ d.plan.topics.length ? d.plan.topics.join(', ') : 'all topics' }}</p>
      }
    </div>
  }`,
  styles: [`
    .hero-row { position: relative; z-index: 1; display: flex; gap: 1.5rem; align-items: center; flex-wrap: wrap; }
    .count { text-align: center; background: rgba(255,255,255,.15); border-radius: 18px; padding: .9rem 1.2rem; min-width: 110px; }
    .count .n { font-size: 2.8rem; font-weight: 800; line-height: 1; }
    .count .l { font-size: .72rem; text-transform: uppercase; letter-spacing: .08em; margin-top: .3rem; }
    .kpis { grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); }
    .item { display: grid; grid-template-columns: 24px minmax(0, 1fr) auto auto auto; gap: .6rem; align-items: center; padding: .5rem .2rem; border-bottom: 1px solid var(--line); }
    .item .t { color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .item.done .t { text-decoration: line-through; color: var(--muted); }
    .tick { width: 22px; height: 22px; border-radius: 50%; border: 2px solid var(--line); display: grid; place-items: center; }
    .item.done .tick { background: #22c55e; border-color: #22c55e; color: #fff; }
    .days { display: grid; gap: .6rem; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
    .day { margin: 0; padding: .8rem 1rem; }
    .day summary { cursor: pointer; }
    .mini { font-size: .85rem; padding: .3rem 0; border-bottom: 1px dashed var(--line); }
    .rt { display: grid; grid-template-columns: minmax(0, 160px) 1fr auto; gap: .8rem; align-items: center; margin: .5rem 0; }
    .rt a { color: var(--ink); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .pick { cursor: pointer; user-select: none; }
    @media (max-width: 640px) { .item { grid-template-columns: 24px minmax(0, 1fr) auto; } .item .muted { display: none; } }
  `]
})
export class PlanDetailComponent implements OnInit {
  @Input() id!: string;
  private study = inject(StudyApi);
  private api = inject(ApiService);
  private router = inject(Router);
  d = signal<PlanDetail | null>(null);
  topics = signal<Topic[]>([]);
  companies = signal<Company[]>([]);
  error = signal<string | null>(null);
  busy = signal(false);
  editing = signal(false);
  f = { title: '', companyId: null as number | null, date: '', dailyTarget: null as number | null, notes: '', topicIds: new Set<number>() };

  ngOnInit() {
    this.load();
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.companies().subscribe(v => this.companies.set(v));
  }

  load() { this.study.plan(+this.id).subscribe({ next: v => this.d.set(v), error: e => this.error.set(errorMessage(e)) }); }

  pendingToday(d: PlanDetail) { return d.today.filter(i => !i.done); }
  chip(i: PlanItem) { const v = i.lastScore ?? 0; return 'chip ' + (v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }

  /** Company plans → company practice; otherwise Smart revision (same priority order the plan uses). */
  startToday(d: PlanDetail) {
    const pending = this.pendingToday(d);
    if (pending.length === 1) { this.practiceOne(pending[0]); return; }
    this.busy.set(true);
    const r = d.plan.companyId
      ? { type: 'COMPANY' as const, companyId: d.plan.companyId, questionLimit: pending.length }
      : { type: 'REVISION' as const, topicId: d.plan.topicIds.length === 1 ? d.plan.topicIds[0] : null, questionLimit: pending.length };
    this.api.startSession(r).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  practiceOne(i: PlanItem) {
    this.busy.set(true);
    this.api.startSession({ type: 'SINGLE', questionId: i.questionId }).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  flash(d: PlanDetail) {
    const qp: Record<string, any> = { autostart: true };
    if (d.plan.companyId) qp['companyId'] = d.plan.companyId;
    else if (d.plan.topicIds.length === 1) qp['topicId'] = d.plan.topicIds[0];
    this.router.navigate(['/flashcards'], { queryParams: qp });
  }

  startEdit(d: PlanDetail) {
    this.f = { title: d.plan.title, companyId: d.plan.companyId ?? null, date: d.plan.interviewDate, dailyTarget: d.plan.dailyTarget ?? null,
      notes: d.plan.notes ?? '', topicIds: new Set(d.plan.topicIds) };
    this.editing.set(true);
  }
  toggle(id: number) { this.f.topicIds.has(id) ? this.f.topicIds.delete(id) : this.f.topicIds.add(id); }

  save() {
    this.busy.set(true);
    this.study.updatePlan(+this.id, { title: this.f.title.trim() || null, companyId: this.f.companyId, interviewDate: this.f.date,
      topicIds: [...this.f.topicIds], dailyTarget: this.f.dailyTarget ? Number(this.f.dailyTarget) : null, notes: this.f.notes.trim() || null }).subscribe({
      next: v => { this.d.set(v); this.editing.set(false); this.busy.set(false); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  remove(d: PlanDetail) {
    if (!confirm(`Delete the plan "${d.plan.title}"? Your practice history is kept.`)) return;
    this.study.deletePlan(d.plan.id).subscribe({ next: () => this.router.navigate(['/plans']), error: e => this.error.set(errorMessage(e)) });
  }
}
