import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Bookmark } from '../core/models';
import { IconComponent } from '../shared/icon.component';
import { QuestionCardComponent } from './question-card.component';

@Component({
  selector: 'app-bookmarks', standalone: true, imports: [FormsModule, RouterLink, IconComponent, QuestionCardComponent],
  template: `
  <div class="page-header">
    <div><h1>Bookmarks</h1><p>Questions you want to nail before the interview. Revision sessions put these first.</p></div>
    @if (items().length) { <button class="lg" (click)="revise()"><app-icon name="brain" /> Revise bookmarks</button> }
  </div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @for (b of items(); track b.questionId) {
    <app-question-card [q]="b.question" (delete)="removeQuestion(b)" />
    <div class="note">
      <input placeholder="Why did you bookmark this? (note to self)" [ngModel]="b.notes" (change)="saveNote(b, $any($event.target).value)">
    </div>
  } @empty {
    <div class="card empty"><div class="big">⭐</div>No bookmarks yet. Tap the star on any question in the <a routerLink="/questions">question bank</a>.</div>
  }`,
  styles: [`.note { margin: -.6rem 0 1.2rem; padding: 0 .4rem; }`]
})
export class BookmarksComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  items = signal<Bookmark[]>([]);
  error = signal<string | null>(null);
  ngOnInit() { this.load(); }
  load() { this.api.bookmarks().subscribe({ next: v => this.items.set(v), error: e => this.error.set(errorMessage(e)) }); }
  removeQuestion(b: Bookmark) {
    if (!confirm('Delete this question permanently (not just the bookmark)?')) return;
    this.api.deleteQuestion(b.questionId).subscribe({ next: () => this.load(), error: e => this.error.set(errorMessage(e)) });
  }
  saveNote(b: Bookmark, notes: string) { this.api.bookmark(b.questionId, notes).subscribe({ error: e => this.error.set(errorMessage(e)) }); }
  revise() {
    this.api.startSession({ type: 'REVISION', questionLimit: Math.min(30, Math.max(1, this.items().length)) })
      .subscribe({ next: s => this.router.navigate(['/practice', s.id]), error: e => this.error.set(errorMessage(e)) });
  }
}
