import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Company, Topic } from '../core/models';
import { MockApi } from '../core/mock.api';
import { StudyApi } from '../core/study.api';
import { FlashCard, FlashGrade, FlashScope } from '../core/study.models';
import { IconComponent } from '../shared/icon.component';

interface Result { card: FlashCard; grade: FlashGrade; nextDays: number; }

@Component({
  selector: 'app-flashcards', standalone: true, imports: [FormsModule, RouterLink, IconComponent],
  template: `
  <div class="page-header">
    <div><h1>Flashcards</h1><p>Quick recall on its own schedule. Reviews are kept separate — they don't count as answered questions, scores or streaks.</p></div>
    @if (phase() !== 'setup') { <button class="secondary" (click)="phase.set('setup')">New deck</button> }
  </div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }

  @if (phase() === 'setup') {
    <div class="card">
      <div class="grid">
        <div><label>Cards</label>
          <select [(ngModel)]="scope">
            <option value="DUE">Due & new (recommended)</option><option value="NEW">Only never-reviewed</option><option value="ALL">Everything</option>
          </select></div>
        <div><label>Topic</label>
          <select [(ngModel)]="topicId"><option [ngValue]="null">All topics</option>
            @for (t of topics(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select></div>
        <div><label>Company</label>
          <select [(ngModel)]="companyId"><option [ngValue]="null">Any</option>
            @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select></div>
        <div><label>Deck size</label><input type="number" min="5" max="100" [(ngModel)]="limit"></div>
      </div>
      @if (tagId) { <p class="muted small">Filtered to one learning-path tag. <a href="" (click)="$event.preventDefault(); tagId = null">Remove tag filter</a></p> }
      <label class="inline"><input type="checkbox" [(ngModel)]="answeredOnly"> Only questions that have an answer</label>
      <div class="row" style="margin-top:1rem">
        <button class="lg" (click)="load()" [disabled]="busy()"><app-icon name="brain" /> {{ busy() ? 'Shuffling…' : 'Start deck' }}</button>
        <span class="muted small">Keys: <span class="kbd">Space</span> flip · <span class="kbd">1</span> didn't know · <span class="kbd">2</span> shaky · <span class="kbd">3</span> knew it</span>
      </div>
    </div>
  }

  @if (phase() === 'play') {
    @if (card(); as c) {
      <div class="progress"><div class="bar"><span [style.width.%]="(index() / cards().length) * 100"></span></div>
        <span class="muted small">{{ index() + 1 }} / {{ cards().length }} · {{ reviewedToday() }} reviewed today</span></div>
      <div class="scene">
        <div class="flip" [class.flipped]="revealed()" (click)="flip()">
          <div class="face front card">
            <div class="row"><span class="chip">{{ c.topic }}</span>
              @if (c.difficulty) { <span [class]="'chip ' + c.difficulty">{{ c.difficulty }}</span> }
              <span class="chip warn">{{ c.reason }}</span>
              @if (c.lastGrade) { <span class="chip diff">last: {{ label(c.lastGrade) }}</span> }</div>
            <div class="q pre">{{ c.question }}</div>
            <div class="hint muted small">Think of your answer, then tap or press Space</div>
          </div>
          <div class="face back card">
            <div class="muted small">{{ c.question }}</div>
            <div class="divider"></div>
            @if (c.answer) { <div class="a pre">{{ c.answer }}</div> }
            @else { <div class="muted">No answer stored for this question. Rate how well you recalled the key points.</div> }
            @if (c.tags.length) { <div style="margin-top:.6rem">@for (t of c.tags; track t) { <span class="chip diff">#{{ t }}</span> }</div> }
          </div>
        </div>
      </div>
      <div class="grades">
        @if (!revealed()) {
          <button class="lg" (click)="flip()">Reveal answer</button>
        } @else {
          <button class="g didnt" (click)="grade('DIDNT')" [disabled]="busy()"><span class="k">1</span> Didn't know</button>
          <button class="g shaky" (click)="grade('SHAKY')" [disabled]="busy()"><span class="k">2</span> Shaky</button>
          <button class="g knew" (click)="grade('KNEW')" [disabled]="busy()"><span class="k">3</span> Knew it</button>
        }
      </div>
    }
  }

  @if (phase() === 'done') {
    <div class="card done">
      <div class="big">🎉</div>
      <h2>Deck complete</h2>
      <div class="tally">
        <div class="t knew"><div class="n">{{ count('KNEW') }}</div>knew it</div>
        <div class="t shaky"><div class="n">{{ count('SHAKY') }}</div>shaky</div>
        <div class="t didnt"><div class="n">{{ count('DIDNT') }}</div>didn't know</div>
      </div>
      <p class="muted small">Flashcards don't affect your practice stats. To turn the misses into real, graded answers, take them as a mini mock test.</p>
      <div class="row" style="justify-content:center;margin-top:1rem">
        @if (misses().length) { <button (click)="testMisses()" [disabled]="busy()"><app-icon name="target" /> Mock test on {{ misses().length }} miss{{ misses().length === 1 ? '' : 'es' }}</button> }
        <button class="secondary" (click)="load()">Another deck</button>
      </div>
    </div>
    @for (r of results(); track r.card.questionId) {
      <div class="res"><span [class]="'chip ' + (r.grade === 'KNEW' ? 'ok' : r.grade === 'SHAKY' ? 'warn' : 'bad')">{{ label(r.grade) }}</span>
        <span class="t">{{ r.card.question }}</span><span class="muted small">again in {{ r.nextDays }}d</span></div>
    }
  }

  @if (phase() === 'empty') {
    <div class="card empty"><div class="big">🗂️</div>No cards match. Nothing is due right now — try "Everything" or another topic.
      <div style="margin-top:1rem"><button class="secondary" (click)="phase.set('setup')">Change filters</button></div></div>
  }`,
  styles: [`
    label.inline { display: inline-flex; gap: .4rem; align-items: center; font-weight: 500; margin-top: .8rem; }
    .progress { display: flex; align-items: center; gap: .8rem; margin-bottom: 1rem; }
    .progress .bar { flex: 1; }
    .scene { perspective: 1400px; }
    .flip { position: relative; min-height: 340px; transition: transform .5s cubic-bezier(.2, .8, .2, 1); transform-style: preserve-3d; cursor: pointer; }
    .flip.flipped { transform: rotateY(180deg); }
    .face { position: absolute; inset: 0; backface-visibility: hidden; -webkit-backface-visibility: hidden; margin: 0; overflow: auto; padding: 1.6rem; }
    .back { transform: rotateY(180deg); }
    .front { display: flex; flex-direction: column; border-top: 4px solid var(--brand); }
    .q { font-size: 1.45rem; font-weight: 750; line-height: 1.4; margin: auto 0; padding: 1rem 0; }
    .hint { text-align: center; }
    .a { line-height: 1.6; }
    .grades { display: flex; gap: .8rem; justify-content: center; margin-top: 1.2rem; flex-wrap: wrap; }
    .g { min-width: 150px; padding: .8rem 1.2rem; font-size: 1rem; border-radius: 12px; }
    .g .k { display: inline-grid; place-items: center; width: 22px; height: 22px; border-radius: 6px; background: rgba(255,255,255,.25); font-size: .8rem; }
    .g.didnt { background: linear-gradient(135deg, #ef4444, #b91c1c); }
    .g.shaky { background: linear-gradient(135deg, #f59e0b, #d97706); }
    .g.knew { background: linear-gradient(135deg, #22c55e, #15803d); }
    .done { text-align: center; padding: 2rem; }
    .done .big { font-size: 3rem; }
    .tally { display: flex; gap: 1rem; justify-content: center; margin-top: .8rem; }
    .tally .t { padding: .8rem 1.4rem; border-radius: 14px; font-size: .85rem; }
    .tally .n { font-size: 2rem; font-weight: 800; }
    .t.knew { background: var(--ok-soft); color: var(--ok); } .t.shaky { background: var(--warn-soft); color: var(--warn); } .t.didnt { background: var(--bad-soft); color: var(--bad-2); }
    .res { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .6rem; align-items: center; padding: .45rem .2rem; border-bottom: 1px solid var(--line); }
    .res .t { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    @media (max-width: 640px) { .q { font-size: 1.15rem; } .g { min-width: 0; flex: 1; } }
  `]
})
export class FlashcardsComponent implements OnInit {
  private study = inject(StudyApi);
  private api = inject(ApiService);
  private mock = inject(MockApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  topics = signal<Topic[]>([]); companies = signal<Company[]>([]);
  phase = signal<'setup' | 'play' | 'done' | 'empty'>('setup');
  cards = signal<FlashCard[]>([]);
  index = signal(0);
  revealed = signal(false);
  results = signal<Result[]>([]);
  reviewedToday = signal(0);
  busy = signal(false);
  error = signal<string | null>(null);
  card = computed(() => this.cards()[this.index()] ?? null);
  misses = computed(() => this.results().filter(r => r.grade !== 'KNEW').map(r => r.card.questionId));

  scope: FlashScope = 'DUE';
  topicId: number | null = null;
  companyId: number | null = null;
  tagId: number | null = null;
  limit = 20;
  answeredOnly = false;

  ngOnInit() {
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.companies().subscribe(v => this.companies.set(v));
    const q = this.route.snapshot.queryParamMap;
    if (q.get('topicId')) this.topicId = Number(q.get('topicId'));
    if (q.get('companyId')) this.companyId = Number(q.get('companyId'));
    if (q.get('tagId')) this.tagId = Number(q.get('tagId'));
    if (q.get('autostart') === 'true' || q.get('topicId') || q.get('tagId')) this.load();
  }

  load() {
    this.busy.set(true); this.error.set(null);
    this.study.deck({ topicId: this.topicId, companyId: this.companyId, tagIds: this.tagId ? [this.tagId] : [], scope: this.scope,
      limit: Math.max(5, Math.min(100, Number(this.limit) || 20)), answeredOnly: this.answeredOnly }).subscribe({
      next: d => {
        this.busy.set(false);
        this.cards.set(d.cards); this.index.set(0); this.revealed.set(false); this.results.set([]);
        this.reviewedToday.set(d.reviewedToday);
        this.phase.set(d.cards.length ? 'play' : 'empty');
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  flip() { if (this.phase() === 'play') this.revealed.set(!this.revealed()); }

  grade(g: FlashGrade) {
    const c = this.card();
    if (!c || !this.revealed() || this.busy()) return;
    this.busy.set(true);
    this.study.grade(c.questionId, g).subscribe({
      next: r => {
        this.busy.set(false);
        this.reviewedToday.set(this.reviewedToday() + 1);
        this.results.set([...this.results(), { card: c, grade: g, nextDays: r.nextReviewInDays }]);
        this.revealed.set(false);
        if (this.index() + 1 >= this.cards().length) this.phase.set('done');
        else setTimeout(() => this.index.set(this.index() + 1), 180);
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKey(e: KeyboardEvent) {
    if (this.phase() !== 'play') return;
    const tag = (e.target as HTMLElement | null)?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    if (e.key === ' ') { e.preventDefault(); this.flip(); }
    else if (e.key === '1') this.grade('DIDNT');
    else if (e.key === '2') this.grade('SHAKY');
    else if (e.key === '3') this.grade('KNEW');
  }

  count(g: FlashGrade) { return this.results().filter(r => r.grade === g).length; }
  label(g: FlashGrade) { return g === 'KNEW' ? 'knew' : g === 'SHAKY' ? 'shaky' : "didn't know"; }

  /** Misses become a short untimed mock test, so they're answered and graded properly (that does count as practice). */
  testMisses() {
    this.busy.set(true);
    this.mock.create({ scope: 'CUSTOM', questionIds: this.misses(), title: 'Flashcard misses · ' + this.misses().length + ' questions' }).subscribe({
      next: t => this.router.navigate(['/mock-tests', t.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
}
