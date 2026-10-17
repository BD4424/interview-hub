import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Company, DIFFICULTIES, Difficulty, SessionSummary, SessionType, Tag, Topic } from '../core/models';
import { IconComponent } from '../shared/icon.component';

interface Mode { type: SessionType; icon: string; title: string; blurb: string; }

@Component({
  selector: 'app-interview-home', standalone: true, imports: [FormsModule, RouterLink, DatePipe, IconComponent],
  template: `
  <div class="page-header">
    <div><h1>Practice</h1><p>Answer out loud in your head, type it, and get graded like a real interview.</p></div>
  </div>
  @if (aiEnabled() === false) {
    <div class="banner info">AI grading is off, so you'll score your own answers against the reference. <a routerLink="/settings">Connect OpenAI in Settings</a> for AI feedback, follow-ups and adaptive mock interviews.</div>
  }
  @if (error()) { <div class="banner error">{{ error() }}</div> }

  <div class="modes">
    @for (m of modes; track m.type) {
      <button class="mode" [class.sel]="type === m.type" (click)="type = m.type">
        <span class="mi"><app-icon [name]="m.icon" [size]="22" /></span>
        <span class="mt">{{ m.title }}</span>
        <span class="mb">{{ m.blurb }}</span>
      </button>
    }
  </div>

  <div class="card setup">
    <div class="grid">
      @if (type === 'TOPIC' || type === 'MOCK' || type === 'RANDOM' || type === 'REVISION') {
        <div><label>Topic {{ type === 'TOPIC' ? '*' : '(optional)' }}</label>
          <select [(ngModel)]="topicId"><option [ngValue]="null">{{ type === 'TOPIC' ? 'Choose…' : 'All topics' }}</option>
            @for (t of topics(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select></div>
      }
      @if (type === 'COMPANY') {
        <div><label>Company *</label>
          <select [(ngModel)]="companyId"><option [ngValue]="null">Choose…</option>
            @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }} ({{ c.questionCount }})</option> }</select></div>
      }
      @if (type === 'TOPIC' || type === 'COMPANY' || type === 'TAG' || type === 'RANDOM') {
        <div><label>Difficulty</label>
          <select [(ngModel)]="difficulty"><option [ngValue]="null">Any</option>
            @for (d of difficulties; track d) { <option [ngValue]="d">{{ d }}</option> }</select></div>
      }
      @if (type === 'MOCK') {
        <div><label>Start at</label>
          <select [(ngModel)]="difficulty">@for (d of difficulties; track d) { <option [ngValue]="d">{{ d }}</option> }</select></div>
      }
      <div><label>Questions</label><input type="number" min="1" max="30" [(ngModel)]="limit"></div>
    </div>
    @if (type === 'TAG') {
      <label>Tags * <span class="muted">({{ tagIds.size }} selected)</span></label>
      <div>@for (t of tags(); track t.id) {
        <span class="chip tagpick" [class.ok]="tagIds.has(t.id)" (click)="toggleTag(t.id)">#{{ t.name }} · {{ t.usage }}</span>
      }</div>
    }
    <p class="muted small" style="margin:.8rem 0 0">{{ explain() }}</p>
    <div class="row" style="margin-top:1rem">
      <button class="lg" (click)="start()" [disabled]="busy()"><app-icon name="practice" /> {{ busy() ? 'Preparing…' : 'Start session' }}</button>
    </div>
  </div>

  <h2>Recent sessions</h2>
  <div class="card" style="padding:.4rem .6rem">
    @for (s of sessions(); track s.id) {
      <div class="srow">
        <a [routerLink]="['/practice', s.id]"><strong>{{ s.title }}</strong>
          <div class="muted small">{{ s.startedAt | date:'MMM d, h:mm a' }} · {{ s.answered }} answered</div></a>
        <span class="row">
          @if (s.overallScore != null) { <span class="chip" [class.ok]="s.overallScore >= 7" [class.warn]="s.overallScore >= 5 && s.overallScore < 7" [class.bad]="s.overallScore < 5">{{ s.overallScore }}/10</span> }
          <span class="chip diff">{{ s.status === 'ACTIVE' ? 'in progress' : 'done' }}</span>
          <button class="ghost" title="Delete session" (click)="remove(s)"><app-icon name="x" [size]="16" /></button>
        </span>
      </div>
    } @empty { <div class="empty"><div class="big">🚀</div>Your sessions will appear here.</div> }
  </div>`,
  styles: [`
    .modes { display: grid; gap: .8rem; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); margin-bottom: 1rem; }
    .mode { flex-direction: column; align-items: flex-start; text-align: left; gap: .3rem; background: var(--card); color: var(--ink); border: 1.5px solid var(--line);
      padding: 1rem; border-radius: var(--radius); box-shadow: var(--shadow); min-height: 128px; }
    .mode:hover:not(:disabled) { border-color: var(--brand); box-shadow: var(--shadow-lg); }
    .mode.sel { border-color: var(--brand); background: var(--brand-soft); }
    .mi { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; background: var(--grad); color: #fff; }
    .mt { font-weight: 800; font-size: 1rem; }
    .mb { font-weight: 400; font-size: .8rem; color: var(--muted); line-height: 1.35; }
    .tagpick { cursor: pointer; user-select: none; }
    .srow { display: flex; justify-content: space-between; align-items: center; gap: .5rem; padding: .55rem .5rem; border-bottom: 1px solid var(--line); }
    .srow:last-child { border-bottom: 0; }
    .srow a { color: var(--ink); text-decoration: none; }
  `]
})
export class InterviewHomeComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  difficulties = DIFFICULTIES;
  modes: Mode[] = [
    { type: 'MOCK', icon: 'mic', title: 'Mock interview', blurb: 'AI interviewer adapts difficulty and digs in with follow-ups.' },
    { type: 'TOPIC', icon: 'target', title: 'Topic drill', blurb: 'Go deep on Java, Spring, Kafka, SQL…' },
    { type: 'COMPANY', icon: 'company', title: 'Company practice', blurb: 'Only questions you attributed to a company.' },
    { type: 'TAG', icon: 'tag', title: 'Tag focus', blurb: 'Multithreading, Transactions, RxJS…' },
    { type: 'REVISION', icon: 'brain', title: 'Smart revision', blurb: 'Weak answers, bookmarks and due questions first.' },
    { type: 'RANDOM', icon: 'shuffle', title: 'Random', blurb: 'Keep yourself honest with surprises.' }
  ];
  type: SessionType = 'MOCK';
  topicId: number | null = null;
  companyId: number | null = null;
  difficulty: Difficulty | null = 'INTERMEDIATE';
  limit = 6;
  tagIds = new Set<number>();
  topics = signal<Topic[]>([]); companies = signal<Company[]>([]); tags = signal<Tag[]>([]);
  sessions = signal<SessionSummary[]>([]);
  aiEnabled = signal<boolean | null>(null);
  busy = signal(false); error = signal<string | null>(null);

  ngOnInit() {
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.companies().subscribe(v => this.companies.set(v));
    this.api.tags().subscribe(v => this.tags.set(v));
    this.api.settings().subscribe({ next: s => this.aiEnabled.set(s.ai.enabled), error: () => {} });
    this.loadSessions();
    const q = this.route.snapshot.queryParamMap;
    const t = q.get('type') as SessionType | null;
    if (t) { this.type = t; this.difficulty = null; this.limit = 10; }
    if (q.get('companyId')) this.companyId = Number(q.get('companyId'));
    if (q.get('topicId')) this.topicId = Number(q.get('topicId'));
  }

  loadSessions() { this.api.sessions().subscribe(v => this.sessions.set(v)); }
  toggleTag(id: number) { this.tagIds.has(id) ? this.tagIds.delete(id) : this.tagIds.add(id); }

  explain() {
    switch (this.type) {
      case 'MOCK': return 'One question at a time. Strong answers make the next question harder, weak ones easier; partly-right answers get a follow-up. Follow-ups are practice-only and never attributed to a company.';
      case 'REVISION': return 'Priority: questions you answered poorly → bookmarked → due by spaced repetition → never practiced.';
      case 'COMPANY': return 'Uses only questions you have attributed to this company. AI follow-ups are not claimed to be from the company.';
      default: return 'You answer first; the reference answer and feedback are revealed after you submit.';
    }
  }

  start() {
    this.error.set(null);
    if (this.type === 'TOPIC' && !this.topicId) { this.error.set('Choose a topic.'); return; }
    if (this.type === 'COMPANY' && !this.companyId) { this.error.set('Choose a company.'); return; }
    if (this.type === 'TAG' && !this.tagIds.size) { this.error.set('Choose at least one tag.'); return; }
    this.busy.set(true);
    this.api.startSession({ type: this.type, topicId: this.topicId, companyId: this.companyId, tagIds: [...this.tagIds],
      difficulty: this.difficulty, questionLimit: Math.max(1, Math.min(30, Number(this.limit) || 6)) }).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  remove(s: SessionSummary) {
    if (!confirm(`Delete "${s.title}"? Scores already recorded on questions are kept.`)) return;
    this.api.deleteSession(s.id).subscribe({ next: () => this.loadSessions(), error: e => this.error.set(errorMessage(e)) });
  }
}
