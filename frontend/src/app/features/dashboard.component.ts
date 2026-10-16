import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Dashboard } from '../core/models';

@Component({
  selector: 'app-dashboard', standalone: true, imports: [RouterLink, DatePipe],
  template: `
  <h1>Dashboard</h1>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (!d() && !error()) { <p class="muted">Loading…</p> }
  @if (d(); as d) {
    <div class="grid">
      <div class="card"><div class="muted">Approved questions</div><div class="stat">{{ d.totalApproved }}</div></div>
      <div class="card"><div class="muted">With answers</div><div class="stat">{{ d.answered }}</div></div>
      <div class="card"><div class="muted">Without answers</div><div class="stat">{{ d.unanswered }}</div></div>
      <div class="card"><div class="muted">Pending duplicate reviews</div><div class="stat">{{ d.pendingDuplicateReviews }}</div></div>
      <a class="card" routerLink="/review"><div class="muted">AI questions awaiting review</div><div class="stat">{{ d.pendingAiReviews }}</div></a>
    </div>
    <div class="grid">
      <div class="card"><h2>By topic</h2>
        @for (b of d.byTopic; track b.label) { <div class="row"><span>{{ b.label }}</span><span class="muted">· {{ b.count }}</span></div> }
        @empty { <p class="muted">No questions yet. <a routerLink="/questions/new">Add one</a> or <a routerLink="/import">import a file</a>.</p> }
      </div>
      <div class="card"><h2>By company</h2>
        @for (b of d.byCompany; track b.label) { <div class="row"><span>{{ b.label }}</span><span class="muted">· {{ b.count }}</span></div> }
        @empty { <p class="muted">No company attributions yet.</p> }
      </div>
      <div class="card"><h2>Recent imports</h2>
        @for (i of d.recentImports; track i.id) {
          <div class="row"><span>#{{ i.id }} {{ i.filename }}</span><span class="chip">{{ i.status }}</span>
          <span class="muted">{{ i.imported }}/{{ i.total }} · {{ i.createdAt | date:'short' }}</span></div>
        } @empty { <p class="muted">No imports yet.</p> }
      </div>
    </div>
    <p class="muted">Practice sessions, bookmarks and weak topics appear here once milestones 11–12 are delivered.</p>
  }`
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  d = signal<Dashboard | null>(null);
  error = signal<string | null>(null);
  ngOnInit() { this.api.dashboard().subscribe({ next: v => this.d.set(v), error: e => this.error.set(errorMessage(e)) }); }
}
