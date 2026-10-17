import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { QuestionStatus } from './models';

/**
 * Practice status (bookmark, attempts, last score) for question cards. Cards request their id; requests made in the
 * same tick are batched into ONE http call, so a page of 20 cards costs a single request.
 */
@Injectable({ providedIn: 'root' })
export class StatusStore {
  private api = inject(ApiService);
  private cache = new Map<number, WritableSignal<QuestionStatus | null>>();
  private pending = new Set<number>();
  private scheduled = false;

  get(id: number): Signal<QuestionStatus | null> {
    let s = this.cache.get(id);
    if (!s) {
      s = signal<QuestionStatus | null>(null);
      this.cache.set(id, s);
    }
    // always re-fetch (batched) so scores are fresh after a practice session; the cached value shows meanwhile
    this.pending.add(id);
    this.schedule();
    return s;
  }

  refresh(id: number) { this.pending.add(id); this.schedule(); }

  toggleBookmark(id: number) {
    const s = this.cache.get(id);
    const current = s?.() ?? null;
    const on = !(current?.bookmarked);
    s?.set(current ? { ...current, bookmarked: on } : { questionId: id, bookmarked: on, attempts: 0, dueForRevision: false });
    const onError = { error: () => this.refresh(id) };
    if (on) this.api.bookmark(id).subscribe(onError);
    else this.api.unbookmark(id).subscribe(onError);
  }

  private schedule() {
    if (this.scheduled) return;
    this.scheduled = true;
    queueMicrotask(() => {
      this.scheduled = false;
      const ids = [...this.pending];
      this.pending.clear();
      if (!ids.length) return;
      this.api.statuses(ids).subscribe({
        next: list => list.forEach(st => this.cache.get(st.questionId)?.set(st)),
        error: () => { /* status is decorative; cards still work */ }
      });
    });
  }
}
