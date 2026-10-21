import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Company, Topic } from '../core/models';
import { StudyApi } from '../core/study.api';
import { PlanSummary } from '../core/study.models';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';

@Component({
  selector: 'app-plans', standalone: true, imports: [FormsModule, RouterLink, DatePipe, IconComponent, ScoreRingComponent],
  template: `
  <div class="page-header">
    <div><h1>Interview plans</h1><p>Set the date, get a daily plan that re-balances itself as you practise.</p></div>
    @if (!showForm()) { <button class="lg" (click)="showForm.set(true)"><app-icon name="plus" /> New plan</button> }
  </div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }

  @if (showForm()) {
    <div class="card">
      <h2 style="margin-top:0">New interview plan</h2>
      <div class="grid">
        <div><label>Company</label>
          <select [(ngModel)]="companyId"><option [ngValue]="null">Not company-specific</option>
            @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }} ({{ c.questionCount }})</option> }</select></div>
        <div><label>Interview date *</label><input type="date" [(ngModel)]="date" [min]="today"></div>
        <div><label>Title</label><input [(ngModel)]="title" [placeholder]="companyName() ? companyName() + ' interview' : 'e.g. Senior Java Developer round 2'"></div>
        <div><label>Questions per day</label><input type="number" min="1" max="50" [(ngModel)]="dailyTarget" placeholder="Auto"></div>
      </div>
      <label>Focus topics <span class="muted">(optional — narrows the plan)</span></label>
      <div>@for (t of topics(); track t.id) {
        <span class="chip pick" [class.ok]="topicIds.has(t.id)" (click)="toggle(t.id)">{{ t.name }}</span>
      }</div>
      <label>Notes</label><textarea [(ngModel)]="notes" rows="2" placeholder="Role, rounds, recruiter tips…"></textarea>
      <p class="muted small">With a company, the plan uses questions you attributed to that company (narrowed to the focus topics). If there are none, the focus topics are used.</p>
      <div class="row"><button (click)="create()" [disabled]="busy() || !date"><app-icon name="check" /> Create plan</button>
        <button class="secondary" (click)="showForm.set(false)">Cancel</button></div>
    </div>
  }

  @for (p of plans(); track p.id) {
    <a class="card plan" [routerLink]="['/plans', p.id]" [class.past]="p.phase === 'PAST'">
      <div class="days"><div class="n">{{ p.phase === 'TODAY' ? '🎯' : p.phase === 'PAST' ? '✓' : p.daysLeft }}</div>
        <div class="l">{{ p.phase === 'TODAY' ? 'today' : p.phase === 'PAST' ? 'done' : p.daysLeft === 1 ? 'day' : 'days' }}</div></div>
      <div class="mid">
        <div class="ttl">{{ p.title }}</div>
        <div class="muted small">{{ p.interviewDate | date:'EEEE, MMM d, y' }} @if (p.company) { · {{ p.company }} }</div>
        <div style="margin-top:.4rem">@for (t of p.topics; track t) { <span class="chip diff">{{ t }}</span> }</div>
        <div class="muted small">{{ p.practiced }}/{{ p.questions }} practised · today {{ p.todayDone }}/{{ p.todayTarget }}</div>
      </div>
      <app-score-ring [value]="p.readiness" [percent]="true" [size]="80" label="ready" />
    </a>
  } @empty {
    @if (!showForm()) { <div class="card empty"><div class="big">📅</div>No plans yet. Add your next interview and get a day-by-day plan.</div> }
  }`,
  styles: [`
    .pick { cursor: pointer; user-select: none; }
    .plan { display: flex; gap: 1.2rem; align-items: center; color: var(--ink); text-decoration: none; }
    .plan.past { opacity: .6; }
    .days { width: 76px; height: 76px; border-radius: 16px; background: var(--grad); color: #fff; display: grid; place-content: center; text-align: center; flex-shrink: 0; }
    .days .n { font-size: 1.7rem; font-weight: 800; line-height: 1; }
    .days .l { font-size: .68rem; text-transform: uppercase; letter-spacing: .06em; }
    .mid { flex: 1; min-width: 0; }
    .ttl { font-weight: 800; font-size: 1.1rem; }
  `]
})
export class PlansComponent implements OnInit {
  private study = inject(StudyApi);
  private api = inject(ApiService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  plans = signal<PlanSummary[]>([]);
  topics = signal<Topic[]>([]);
  companies = signal<Company[]>([]);
  showForm = signal(false);
  busy = signal(false);
  error = signal<string | null>(null);
  today = new Date().toISOString().slice(0, 10);

  companyId: number | null = null;
  date = '';
  title = '';
  dailyTarget: number | null = null;
  notes = '';
  topicIds = new Set<number>();

  ngOnInit() {
    this.load();
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.companies().subscribe(v => this.companies.set(v));
    const c = this.route.snapshot.queryParamMap.get('companyId');
    if (c) { this.companyId = Number(c); this.showForm.set(true); }
  }

  load() { this.study.plans().subscribe({ next: v => this.plans.set(v), error: e => this.error.set(errorMessage(e)) }); }
  companyName() { return this.companies().find(c => c.id === this.companyId)?.name ?? null; }
  toggle(id: number) { this.topicIds.has(id) ? this.topicIds.delete(id) : this.topicIds.add(id); }

  create() {
    this.busy.set(true); this.error.set(null);
    this.study.createPlan({ title: this.title.trim() || null, companyId: this.companyId, interviewDate: this.date, topicIds: [...this.topicIds],
      dailyTarget: this.dailyTarget ? Number(this.dailyTarget) : null, notes: this.notes.trim() || null }).subscribe({
      next: d => this.router.navigate(['/plans', d.plan.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
}
