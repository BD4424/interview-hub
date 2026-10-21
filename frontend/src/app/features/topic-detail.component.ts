import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { StudyApi } from '../core/study.api';
import { Bucket, LearningPath, PathStep, TagOption, TopicOverview } from '../core/study.models';
import { renderMarkdown } from '../shared/markdown';
import { IconComponent } from '../shared/icon.component';
import { ScoreRingComponent } from '../shared/score-ring.component';

@Component({
  selector: 'app-topic-detail', standalone: true, imports: [FormsModule, RouterLink, DatePipe, IconComponent, ScoreRingComponent],
  template: `
  <a routerLink="/topics-tags" class="small">← Topics & tags</a>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (!o() && !error()) { <div class="skeleton" style="height:220px;margin-top:1rem"></div> }
  @if (o(); as o) {
    <section class="hero" style="margin-top:.6rem">
      <div class="hero-row">
        <div style="min-width:0">
          <div class="eyebrow">Topic</div>
          <h1>{{ o.name }}</h1>
          <p>{{ o.description || heroLine(o) }}</p>
          <div class="row" style="margin-top:1rem">
            <button class="lg" (click)="practiceTopic()" [disabled]="busy() || !o.totalQuestions"><app-icon name="practice" /> Practice {{ o.name }}</button>
            <button class="lg secondary" (click)="flashcards()" [disabled]="!o.totalQuestions"><app-icon name="brain" /> Flashcards</button>
          </div>
        </div>
        <app-score-ring [value]="o.mastery" [percent]="true" [size]="128" label="mastery" />
      </div>
    </section>

    <div class="grid kpis">
      <div class="card"><div class="muted">Questions</div><div class="stat">{{ o.totalQuestions }}</div><div class="muted small">{{ o.answered }} with answers</div></div>
      <div class="card"><div class="muted">Practised</div><div class="stat">{{ o.practiced }}</div>
        <div class="bar ok" style="margin-top:.5rem"><span [style.width.%]="o.coverage ?? 0"></span></div><div class="muted small">{{ o.coverage ?? 0 }}% coverage</div></div>
      <div class="card"><div class="muted">Current score</div><div class="stat">{{ o.currentScore ?? '—' }}</div><div class="muted small">latest answer per question</div></div>
      <div class="card"><div class="muted">Due for revision</div><div class="stat">{{ o.dueForRevision }}</div><div class="muted small">weak or due by spacing</div></div>
    </div>

    <!-- learning path -->
    <div class="card">
      <div class="row spread">
        <div><h2 style="margin:0">Learning path</h2>
          <div class="muted small">{{ path()?.custom ? 'Your custom order' : 'Suggested order (by question count) — customise it to match how you want to learn' }} · mastered at {{ path()?.masteredThreshold ?? 70 }}%</div></div>
        @if (!editing()) {
          <button class="secondary" (click)="startEdit()" [disabled]="!path()">Edit path</button>
        } @else {
          <div class="row"><button (click)="savePath()" [disabled]="busy()">Save path</button>
            <button class="secondary" (click)="editing.set(false)">Cancel</button>
            @if (path()?.custom) { <button class="ghost" (click)="resetPath()">Reset to suggested</button> }</div>
        }
      </div>

      @if (!editing()) {
        @if (path(); as p) {
          <div class="path">
            @for (s of p.steps; track s.tagId; let last = $last) {
              <div [class]="stepClass(s, p.nextTagId)">
                <div class="dot">@if (s.status === 'MASTERED') { <app-icon name="check" [size]="16" [stroke]="3" /> } @else { {{ s.position }} }</div>
                @if (!last) { <div class="line"></div> }
                <div class="body">
                  <div class="row spread">
                    <div><strong>{{ s.tagName }}</strong>
                      @if (s.tagId === p.nextTagId) { <span class="chip">up next</span> }
                      <span class="muted small"> · {{ s.practiced }}/{{ s.questionCount }} practised @if (s.currentScore != null) { · avg {{ s.currentScore }} }</span></div>
                    <span class="pct">{{ s.mastery ?? 0 }}%</span>
                  </div>
                  <div class="bar" [class]="barClass(s)"><span [style.width.%]="s.mastery ?? 0"></span></div>
                  @if (s.tagId === p.nextTagId) {
                    <div class="row" style="margin-top:.6rem">
                      <button (click)="practiceTag(s)" [disabled]="busy()"><app-icon name="practice" [size]="16" /> Practice #{{ s.tagName }}</button>
                      <button class="secondary" (click)="flashcards(s.tagId)">Flashcards</button>
                    </div>
                  }
                </div>
              </div>
            } @empty {
              <div class="empty"><div class="big">🧭</div>Tag this topic's questions (e.g. Collections, Concurrency, JVM) and they become steps here.</div>
            }
          </div>
          @if (p.steps.length && !p.nextTagId) { <div class="banner ok">Every step is mastered. Try a mock interview on {{ o.name }} to stay sharp.</div> }
        }
      } @else {
        <div class="edit">
          @for (s of draft; track s.tagId; let i = $index; let first = $first; let last = $last) {
            <div class="erow"><span class="muted">{{ i + 1 }}.</span><strong>{{ s.tagName }}</strong>
              <span class="muted small">{{ s.questionCount }} q</span><span style="flex:1"></span>
              <button class="ghost" (click)="move(i, -1)" [disabled]="first" title="Move up">↑</button>
              <button class="ghost" (click)="move(i, 1)" [disabled]="last" title="Move down">↓</button>
              <button class="ghost" (click)="draft.splice(i, 1)" title="Remove"><app-icon name="x" [size]="14" /></button></div>
          }
          <div class="row" style="margin-top:.6rem">
            <select [(ngModel)]="addTagId" style="max-width:320px"><option [ngValue]="null">Add a step…</option>
              @for (t of addable(); track t.tagId) { <option [ngValue]="t.tagId">{{ t.tagName }} ({{ t.questionCount }})</option> }</select>
            <button class="secondary" (click)="addStep()" [disabled]="!addTagId">Add</button>
          </div>
        </div>
      }
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="row spread"><h2 style="margin:0">Weakest answers</h2><span class="muted small">lowest latest score</span></div>
        @for (q of o.weakest; track q.questionId) {
          <div class="qrow"><span class="chip" [class]="scoreChip(q.lastScore)">{{ q.lastScore }}</span>
            <a [routerLink]="['/questions', q.questionId, 'edit']" class="t">{{ q.question }}</a>
            <button class="ghost" (click)="practiceOne(q.questionId)" title="Practice"><app-icon name="practice" [size]="16" /></button></div>
        } @empty { <div class="empty"><div class="big">💪</div>No practised questions yet.</div> }
      </div>
      <div class="card">
        <div class="row spread"><h2 style="margin:0">Never practised</h2><span class="muted small">{{ o.neverPracticedCount }} total</span></div>
        @for (q of o.neverPracticed; track q.questionId) {
          <div class="qrow">@if (q.difficulty) { <span [class]="'chip ' + q.difficulty">{{ q.difficulty.charAt(0) }}</span> } @else { <span class="chip diff">?</span> }
            <a [routerLink]="['/questions', q.questionId, 'edit']" class="t">{{ q.question }}</a>
            <button class="ghost" (click)="practiceOne(q.questionId)" title="Practice"><app-icon name="practice" [size]="16" /></button></div>
        } @empty { <div class="empty"><div class="big">✅</div>You've practised every question here.</div> }
      </div>
    </div>

    <!-- notes -->
    <div class="card">
      <div class="row spread">
        <div><h2 style="margin:0">Study notes</h2>
          <div class="muted small">Markdown supported: # heading, **bold**, \`code\`, lists, \`\`\` code blocks \`\`\`
            @if (o.notesUpdatedAt) { · saved {{ o.notesUpdatedAt | date:'MMM d, h:mm a' }} }</div></div>
        @if (!editNotes()) { <button class="secondary" (click)="openNotes(o)">{{ o.notes ? 'Edit notes' : 'Write notes' }}</button> }
        @else { <div class="row"><button (click)="saveNotes()" [disabled]="busy()">Save</button><button class="secondary" (click)="editNotes.set(false)">Cancel</button></div> }
      </div>
      @if (editNotes()) {
        <div class="notes-edit">
          <textarea [(ngModel)]="notesDraft" rows="16" placeholder="Key facts, diagrams-in-words, gotchas, links… whatever you want to re-read the night before."></textarea>
          <div class="md preview" [innerHTML]="md(notesDraft)"></div>
        </div>
      } @else if (o.notes) {
        <div class="md" [innerHTML]="md(o.notes)"></div>
      } @else {
        <div class="empty"><div class="big">📝</div>No notes yet. Capture the things you keep forgetting.</div>
      }
    </div>

    <div class="grid">
      @for (g of groups(o); track g.title) {
        <div class="card"><h2 style="margin-top:0">{{ g.title }}</h2>
          @for (b of g.items; track b.label) {
            <div class="b" [title]="b.label + ': ' + b.count"><span class="lbl">{{ b.label }}</span>
              <div class="bar"><span [style.width.%]="b.count / max(g.items) * 100"></span></div><span class="cnt">{{ b.count }}</span></div>
          } @empty { <p class="muted">—</p> }
        </div>
      }
    </div>
  }`,
  styles: [`
    .hero-row { position: relative; z-index: 1; display: flex; justify-content: space-between; gap: 1.5rem; align-items: center; flex-wrap: wrap; }
    .eyebrow { font-weight: 700; opacity: .85; text-transform: uppercase; font-size: .75rem; letter-spacing: .1em; }
    .kpis { grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); }
    .path { margin-top: 1rem; }
    .step { display: grid; grid-template-columns: 36px 1fr; column-gap: .9rem; position: relative; padding-bottom: 1rem; }
    .step .dot { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; font-weight: 800; font-size: .85rem;
      background: var(--bg-2); color: var(--muted); border: 2px solid var(--line); z-index: 1; }
    .step .line { position: absolute; left: 15px; top: 34px; bottom: 0; width: 2px; background: var(--line); }
    .step.mastered .dot { background: #22c55e; border-color: #22c55e; color: #fff; }
    .step.in_progress .dot { border-color: var(--brand); color: var(--brand); }
    .step.next .dot { background: var(--grad); color: #fff; border-color: transparent; box-shadow: var(--shadow-lg); }
    .step.next .body { background: var(--brand-soft); border-radius: 12px; padding: .7rem .9rem; margin-top: -.3rem; }
    .step.empty { opacity: .55; }
    .step .bar { margin-top: .4rem; }
    .pct { font-weight: 800; font-variant-numeric: tabular-nums; }
    .erow { display: flex; gap: .6rem; align-items: center; padding: .4rem .5rem; border: 1px solid var(--line); border-radius: 10px; margin: .4rem 0; }
    .qrow { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .6rem; align-items: center; padding: .35rem 0; border-bottom: 1px solid var(--line); }
    .qrow:last-child { border-bottom: 0; }
    .qrow .t { color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .notes-edit { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: .8rem; }
    .preview { border: 1px dashed var(--line); border-radius: 10px; padding: .6rem 1rem; max-height: 420px; overflow: auto; }
    .md { line-height: 1.65; margin-top: .6rem; }
    .md :is(h2, h3, h4, h5) { margin: 1rem 0 .4rem; }
    .md pre { background: #0f172a; color: #e2e8f0; padding: .8rem 1rem; border-radius: 10px; overflow-x: auto; }
    .md code { background: var(--bg-2); padding: .1rem .35rem; border-radius: 6px; font-size: .88em; }
    .md pre code { background: none; padding: 0; }
    .md blockquote { border-left: 3px solid var(--brand); margin: .5rem 0; padding: .2rem .8rem; color: var(--ink-2); }
    .b { display: grid; grid-template-columns: minmax(0, 1fr) minmax(40px, 35%) 2rem; gap: .6rem; align-items: center; font-size: .88rem; margin: .4rem 0; }
    .b .lbl { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .b .cnt { text-align: right; color: var(--muted); font-size: .8rem; }
    @media (max-width: 860px) { .notes-edit { grid-template-columns: 1fr; } }
  `]
})
export class TopicDetailComponent implements OnInit {
  @Input() id!: string;
  private study = inject(StudyApi);
  private api = inject(ApiService);
  private router = inject(Router);
  o = signal<TopicOverview | null>(null);
  path = signal<LearningPath | null>(null);
  error = signal<string | null>(null);
  busy = signal(false);
  editing = signal(false);
  editNotes = signal(false);
  draft: { tagId: number; tagName: string; questionCount: number }[] = [];
  addTagId: number | null = null;
  notesDraft = '';

  ngOnInit() { this.load(); }

  load() {
    this.study.topicOverview(+this.id).subscribe({ next: v => this.o.set(v), error: e => this.error.set(errorMessage(e)) });
    this.study.path(+this.id).subscribe({ next: v => this.path.set(v), error: e => this.error.set(errorMessage(e)) });
  }

  heroLine(o: TopicOverview) {
    if (!o.totalQuestions) return 'No questions in this topic yet.';
    if (!o.practiced) return `${o.totalQuestions} questions waiting. Start with the first step of the learning path.`;
    return `${o.practiced} of ${o.totalQuestions} questions practised. Mastery grows with both coverage and scores.`;
  }

  md(s: string | null | undefined) { return renderMarkdown(s); }
  max(items: Bucket[]) { return Math.max(1, ...items.map(i => i.count)); }
  groups(o: TopicOverview) {
    return [{ title: 'By tag', items: o.byTag.slice(0, 12) }, { title: 'By difficulty', items: o.byDifficulty }, { title: 'Asked at', items: o.byCompany.slice(0, 12) }];
  }
  stepClass(s: PathStep, next?: number | null) { return 'step ' + s.status.toLowerCase() + (s.tagId === next ? ' next' : ''); }
  barClass(s: PathStep) { return 'bar ' + (s.status === 'MASTERED' ? 'ok' : (s.mastery ?? 0) >= 40 ? 'warn' : ''); }
  scoreChip(v?: number | null) { return 'chip ' + (v == null ? 'diff' : v >= 7 ? 'ok' : v >= 5 ? 'warn' : 'bad'); }

  // ---- path editing
  startEdit() {
    const p = this.path(); if (!p) return;
    this.draft = p.steps.map(s => ({ tagId: s.tagId, tagName: s.tagName, questionCount: s.questionCount }));
    this.addTagId = null;
    this.editing.set(true);
  }
  addable(): TagOption[] {
    const p = this.path(); if (!p) return [];
    const inDraft = new Set(this.draft.map(d => d.tagId));
    const fromPath = p.steps.filter(s => !inDraft.has(s.tagId)).map(s => ({ tagId: s.tagId, tagName: s.tagName, questionCount: s.questionCount }));
    return [...fromPath, ...p.availableTags.filter(t => !inDraft.has(t.tagId))];
  }
  addStep() {
    const t = this.addable().find(x => x.tagId === this.addTagId);
    if (t) this.draft.push({ ...t });
    this.addTagId = null;
  }
  move(i: number, d: number) { const j = i + d; [this.draft[i], this.draft[j]] = [this.draft[j], this.draft[i]]; }
  savePath() {
    this.busy.set(true);
    this.study.savePath(+this.id, this.draft.map(d => d.tagId)).subscribe({
      next: p => { this.path.set(p); this.editing.set(false); this.busy.set(false); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
  resetPath() {
    if (!confirm('Go back to the suggested order?')) return;
    this.study.resetPath(+this.id).subscribe({ next: p => { this.path.set(p); this.editing.set(false); }, error: e => this.error.set(errorMessage(e)) });
  }

  // ---- notes
  openNotes(o: TopicOverview) { this.notesDraft = o.notes ?? ''; this.editNotes.set(true); }
  saveNotes() {
    this.busy.set(true);
    this.study.saveNotes(+this.id, this.notesDraft).subscribe({
      next: v => { this.o.set(v); this.editNotes.set(false); this.busy.set(false); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  // ---- actions
  private start(r: Parameters<ApiService['startSession']>[0]) {
    this.busy.set(true);
    this.api.startSession(r).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
  practiceTopic() { this.start({ type: 'TOPIC', topicId: +this.id, questionLimit: 8 }); }
  practiceTag(s: PathStep) { this.start({ type: 'TAG', tagIds: [s.tagId], questionLimit: 6 }); }
  practiceOne(questionId: number) { this.start({ type: 'SINGLE', questionId }); }
  flashcards(tagId?: number) {
    this.router.navigate(['/flashcards'], { queryParams: { topicId: this.id, ...(tagId ? { tagId } : {}) } });
  }
}
