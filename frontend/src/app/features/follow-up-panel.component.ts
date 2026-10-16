import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { DraftAnswer, FollowUpBatch, FollowUpItem, FollowUpMode, LinkedFollowUp, Question } from '../core/models';

@Component({
  selector: 'app-follow-up-panel', standalone: true, imports: [FormsModule, RouterLink],
  template: `
  <div class="panel">
    <div class="row">
      <label class="inline">Count
        <select [(ngModel)]="count">@for (n of [1, 2, 3, 4, 5]; track n) { <option [ngValue]="n">{{ n }}</option> }</select></label>
      <label class="inline">Mode
        <select [(ngModel)]="mode">
          <option value="SESSION_ONLY">Practice only (nothing saved)</option>
          <option value="REVIEW">Allow saving for review</option>
        </select></label>
      <label class="inline">Interview style
        <select [(ngModel)]="companyId"><option [ngValue]="null">Neutral</option>
          @for (c of q.companies; track c.companyId) { <option [ngValue]="c.companyId">{{ c.companyName }}</option> }
        </select></label>
      <button (click)="generate()" [disabled]="busy()">{{ busy() ? 'Generating…' : 'Generate follow-ups' }}</button>
      <button class="secondary" (click)="draft()" [disabled]="busy()">Draft answer with AI</button>
    </div>
    @if (error()) { <div class="banner error">{{ error() }}</div> }

    @if (linked().length) {
      <div class="muted" style="margin-top:.5rem">Saved follow-ups:</div>
      @for (l of linked(); track l.questionId) {
        <div><span class="chip" [class.ok]="l.approvalStatus === 'APPROVED'" [class.warn]="l.approvalStatus === 'PENDING'">{{ l.approvalStatus }}</span>
          <a [routerLink]="['/questions', l.questionId, 'edit']">#{{ l.questionId }}</a> {{ l.question }}</div>
      }
    }

    @if (draftAnswer(); as d) {
      <div class="card" style="margin-top:.75rem">
        <div class="muted">{{ d.note }} ({{ d.model }})</div>
        <p class="pre">{{ d.draft }}</p>
        <button class="secondary" (click)="copy(d.draft)">Copy</button>
      </div>
    }

    @if (batch(); as b) {
      <p class="muted" style="margin-top:.75rem">{{ b.disclaimer }} · {{ b.model }}
        @if (b.rejected.length) { · {{ b.rejected.length }} invalid item(s) discarded }</p>
      @for (f of b.items; track f.question; let i = $index) {
        <div class="card">
          <div class="row" style="justify-content:space-between">
            <strong style="flex:1">{{ i + 1 }}. {{ f.question }}</strong>
            @if (f.difficulty) { <span class="chip diff">{{ f.difficulty }}</span> }
          </div>
          <div class="muted">Why: {{ f.reason }}</div>
          <div>
            @for (t of f.existingTags; track t) { <span class="chip">#{{ t }}</span> }
            @for (t of f.suggestedNewTags; track t) { <span class="chip warn" title="New tag: applied only if a reviewer accepts it">#{{ t }} (new)</span> }
          </div>
          @if (f.duplicate; as d) {
            <div class="banner error" style="margin:.5rem 0">{{ d.matchType }}: matches
              <a [routerLink]="['/questions', d.matchingQuestionId, 'edit']">#{{ d.matchingQuestionId }}</a> ({{ d.matchingApproval }}) — {{ d.matchingText }}</div>
          }
          <button class="secondary" (click)="toggle(i)">{{ open().has(i) ? 'Hide' : 'Show' }} expected answer points</button>
          @if (open().has(i)) { <ul>@for (p of f.expectedAnswerPoints; track p) { <li>{{ p }}</li> }</ul> }
          @if (f.persistence !== 'SESSION_ONLY') {
            @if (saved()[i]; as msg) { <div class="banner ok" style="margin-top:.5rem">{{ msg }}</div> }
            @else {
              <button style="margin-left:.5rem" (click)="save(f, i)">{{ f.persistence === 'LINK_EXISTING' ? 'Link existing question' : 'Save for review' }}</button>
            }
          }
        </div>
      }
    }
  </div>`,
  styles: [`.panel { border-top: 1px solid var(--line); margin-top: .75rem; padding-top: .75rem; }
            label.inline { display: inline-flex; gap: .3rem; align-items: center; margin: 0; font-weight: 500; }
            label.inline select { width: auto; }`]
})
export class FollowUpPanelComponent implements OnInit {
  @Input({ required: true }) q!: Question;
  private api = inject(ApiService);
  count = 3; mode: FollowUpMode = 'SESSION_ONLY'; companyId: number | null = null;
  busy = signal(false); error = signal<string | null>(null);
  batch = signal<FollowUpBatch | null>(null); draftAnswer = signal<DraftAnswer | null>(null);
  linked = signal<LinkedFollowUp[]>([]); open = signal(new Set<number>()); saved = signal<Record<number, string>>({});

  ngOnInit() { this.loadLinked(); }
  loadLinked() { this.api.followUps(this.q.id).subscribe({ next: v => this.linked.set(v), error: () => {} }); }

  generate() {
    this.busy.set(true); this.error.set(null); this.saved.set({}); this.open.set(new Set());
    this.api.generateFollowUps(this.q.id, { count: this.count, mode: this.mode, companyId: this.companyId }).subscribe({
      next: b => { this.batch.set(b); this.busy.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); }
    });
  }
  draft() {
    this.busy.set(true); this.error.set(null);
    this.api.draftAnswer(this.q.id).subscribe({
      next: d => { this.draftAnswer.set(d); this.busy.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); }
    });
  }
  toggle(i: number) { const s = new Set(this.open()); s.has(i) ? s.delete(i) : s.add(i); this.open.set(s); }
  save(f: FollowUpItem, i: number) {
    this.api.saveFollowUp(this.q.id, f).subscribe({
      next: r => { this.saved.set({ ...this.saved(), [i]: r.message }); this.loadLinked(); },
      error: e => this.error.set(errorMessage(e))
    });
  }
  copy(text: string) { navigator.clipboard?.writeText(text); }
}
