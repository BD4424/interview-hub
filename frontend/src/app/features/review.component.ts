import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Page, Question, SUGGESTED_TAGS_PREFIX } from '../core/models';

@Component({
  selector: 'app-review', standalone: true, imports: [RouterLink],
  template: `
  <h1>AI Review</h1>
  <p class="muted">AI-generated questions wait here and stay out of the question bank until you approve them.
    New tags suggested by the AI are only created if you tick them. Rejecting deletes the pending question.
    AI questions never carry company attribution; add it yourself only when you have evidence.</p>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (page(); as p) {
    @for (q of p.content; track q.id) {
      <div class="card">
        <strong class="pre">{{ q.questionText }}</strong>
        <div style="margin:.4rem 0"><span class="chip">{{ q.topicName }}</span>
          @if (q.difficulty) { <span class="chip diff">{{ q.difficulty }}</span> }
          @for (t of q.tags; track t.id) { <span class="chip">#{{ t.name }}</span> }</div>
        @if (q.answerText) { <p class="pre muted">{{ q.answerText }}</p> }
        @if (q.notes) { <p class="pre muted">{{ q.notes }}</p> }
        @if (suggested(q).length) {
          <div>Apply suggested tags:
            @for (t of suggested(q); track t) {
              <label style="display:inline-flex;gap:.25rem;margin-right:.75rem;font-weight:500">
                <input type="checkbox" style="width:auto" [checked]="isPicked(q.id, t)" (change)="pick(q.id, t)"> {{ t }}</label>
            }
          </div>
        }
        <div class="row" style="margin-top:.5rem">
          <button (click)="decide(q, 'APPROVE')">Approve</button>
          <a class="btn secondary" [routerLink]="['/questions', q.id, 'edit']">Edit first</a>
          <button class="danger" (click)="decide(q, 'REJECT')">Reject</button>
        </div>
      </div>
    } @empty { <div class="card muted">Nothing waiting for review.</div> }
  } @else { <p class="muted">Loading…</p> }`
})
export class ReviewComponent implements OnInit {
  private api = inject(ApiService);
  page = signal<Page<Question> | null>(null); error = signal<string | null>(null);
  private picked = new Map<number, Set<string>>();

  ngOnInit() { this.load(); }
  load() {
    this.api.questions({ approval: 'PENDING', size: 50, sort: 'oldest' }).subscribe({ next: p => this.page.set(p), error: e => this.error.set(errorMessage(e)) });
  }
  suggested(q: Question): string[] {
    const line = (q.notes ?? '').split('\n').find(l => l.startsWith(SUGGESTED_TAGS_PREFIX));
    return line ? line.substring(SUGGESTED_TAGS_PREFIX.length).split(',').map(s => s.trim()).filter(Boolean) : [];
  }
  isPicked(id: number, t: string) { return this.picked.get(id)?.has(t) ?? false; }
  pick(id: number, t: string) {
    const s = this.picked.get(id) ?? new Set<string>();
    s.has(t) ? s.delete(t) : s.add(t);
    this.picked.set(id, s);
  }
  decide(q: Question, d: 'APPROVE' | 'REJECT') {
    if (d === 'REJECT' && !confirm('Reject and delete this AI-generated question?')) return;
    this.api.decide(q.id, d, [...(this.picked.get(q.id) ?? [])]).subscribe({ next: () => { this.error.set(null); this.load(); }, error: e => this.error.set(errorMessage(e)) });
  }
}
