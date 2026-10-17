import { Component, EventEmitter, Input, OnInit, Output, Signal, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Question, QuestionStatus } from '../core/models';
import { StatusStore } from '../core/status.store';
import { IconComponent } from '../shared/icon.component';
import { FollowUpPanelComponent } from './follow-up-panel.component';

@Component({
  selector: 'app-question-card', standalone: true, imports: [RouterLink, DatePipe, IconComponent, FollowUpPanelComponent],
  template: `
  <div class="card qc hover">
    <div class="head">
      <button class="ghost star" [class.on]="status()?.bookmarked" (click)="store.toggleBookmark(q.id)"
              [title]="status()?.bookmarked ? 'Remove bookmark' : 'Bookmark'"><app-icon name="star" [size]="20" /></button>
      <div class="text pre">{{ q.questionText }}</div>
    </div>
    <div class="meta">
      <span class="chip">{{ q.topicName }}</span>
      @if (q.difficulty) { <span class="chip" [class]="q.difficulty">{{ q.difficulty }}</span> }
      @if (q.questionType) { <span class="chip diff">{{ q.questionType.replace('_', ' ').toLowerCase() }}</span> }
      @if (q.sourceType === 'AI_GENERATED') { <span class="chip warn">AI generated</span> }
      @if (q.approvalStatus === 'PENDING') { <span class="chip warn">pending review</span> }
      @for (t of q.tags; track t.id) { <span class="chip diff">#{{ t.name }}</span> }
      @for (c of q.companies; track c.companyId) { <span class="chip company">{{ c.companyName }}@if (c.interviewRound) { · {{ c.interviewRound }} }</span> }
      @if (!q.companies.length) { <span class="chip diff">company unknown</span> }
      <span class="chip" [class.ok]="q.answerText" [class.warn]="!q.answerText">{{ q.answerText ? 'answer ' + q.answerStatus.toLowerCase() : 'no answer' }}</span>
    </div>
    @if (status(); as st) {
      @if (st.attempts > 0) {
        <div class="practice-line">
          <span class="chip" [class.ok]="(st.lastScore ?? 0) >= 7" [class.warn]="(st.lastScore ?? 0) >= 5 && (st.lastScore ?? 0) < 7" [class.bad]="(st.lastScore ?? 0) < 5">last {{ st.lastScore ?? '—' }}/10</span>
          <span class="muted small">best {{ st.bestScore ?? '—' }} · {{ st.attempts }} attempt(s) · {{ st.lastAttemptAt | date:'MMM d' }}</span>
          @if (st.dueForRevision) { <span class="chip warn">due for revision</span> }
        </div>
      }
    }
    <div class="actions">
      <button (click)="practice()" [disabled]="busy()"><app-icon name="practice" [size]="16" /> Practice</button>
      @if (q.answerText) { <button class="secondary" (click)="open.set(!open())">{{ open() ? 'Hide' : 'Show' }} answer</button> }
      <button class="secondary" (click)="ai.set(!ai())"><app-icon name="spark" [size]="16" /> {{ ai() ? 'Close AI' : 'AI follow-ups' }}</button>
      <span style="flex:1"></span>
      <a class="btn secondary" [routerLink]="['/questions', q.id, 'edit']">Edit</a>
      <button class="danger" (click)="delete.emit(q)">Delete</button>
    </div>
    @if (error()) { <div class="banner error" style="margin-top:.6rem">{{ error() }}</div> }
    @if (open() && q.answerText) { <p class="pre answer">{{ q.answerText }}</p> }
    @if (ai()) { <app-follow-up-panel [q]="q" /> }
  </div>`,
  styles: [`
    .head { display: flex; gap: .4rem; align-items: flex-start; }
    .text { font-weight: 650; font-size: 1.02rem; line-height: 1.45; flex: 1; padding-top: .25rem; }
    .star { color: var(--muted); }
    .star.on { color: #f59e0b; }
    .star.on ::ng-deep path { fill: #f59e0b; }
    .meta { margin: .6rem 0 0 2.6rem; }
    .practice-line { margin: .2rem 0 0 2.6rem; display: flex; gap: .4rem; align-items: center; flex-wrap: wrap; }
    .actions { display: flex; gap: .4rem; flex-wrap: wrap; align-items: center; margin: .8rem 0 0 2.6rem; }
    .actions button, .actions .btn { padding: .4rem .75rem; font-size: .84rem; }
    .answer { margin: .8rem 0 0 2.6rem; background: var(--bg-2); padding: .8rem 1rem; border-radius: 12px; }
    @media (max-width: 640px) { .meta, .practice-line, .actions, .answer { margin-left: 0; } }
  `]
})
export class QuestionCardComponent implements OnInit {
  @Input({ required: true }) q!: Question;
  @Output() delete = new EventEmitter<Question>();
  store = inject(StatusStore);
  private api = inject(ApiService);
  private router = inject(Router);
  status!: Signal<QuestionStatus | null>;
  open = signal(false);
  ai = signal(false);
  busy = signal(false);
  error = signal<string | null>(null);

  ngOnInit() { this.status = this.store.get(this.q.id); }

  practice() {
    this.busy.set(true);
    this.api.startSession({ type: 'SINGLE', questionId: this.q.id }).subscribe({
      next: s => this.router.navigate(['/practice', s.id]),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
}
