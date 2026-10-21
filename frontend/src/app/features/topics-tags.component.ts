import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Bucket, Tag, Topic } from '../core/models';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'app-topics-tags', standalone: true, imports: [RouterLink, IconComponent],
  template: `
  <div class="page-header"><div><h1>Topics & tags</h1>
    <p>Each question has one primary topic and any number of tags. Open a topic for its learning path, notes and progress.</p></div></div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }

  <h2>Topics</h2>
  <div class="tgrid">
    @for (t of topics(); track t.id) {
      <a class="card topic" [routerLink]="['/topics', t.id]">
        <div class="row spread"><strong>{{ t.name }}</strong><span class="chip diff">{{ count(t.name) }} q</span></div>
        @if (t.description) { <div class="muted small">{{ t.description }}</div> }
        <div class="go small">Open learning path <app-icon name="arrow" [size]="14" /></div>
        <button class="ghost del" (click)="delTopic(t, $event)" title="Delete topic"><app-icon name="x" [size]="14" /></button>
      </a>
    }
    <div class="card add">
      <input #tn placeholder="New topic, e.g. Design Patterns" (keydown.enter)="addTopic(tn.value); tn.value=''">
      <button class="secondary" (click)="addTopic(tn.value); tn.value=''"><app-icon name="plus" [size]="16" /> Add topic</button>
    </div>
  </div>

  <h2>Tags</h2>
  <div class="card">
    <div class="row">
      <input #tg placeholder="New tag" style="max-width:260px" (keydown.enter)="addTag(tg.value); tg.value=''">
      <button class="secondary" (click)="addTag(tg.value); tg.value=''">Add</button>
      <span style="flex:1"></span>
      <input placeholder="Search tags" style="max-width:260px" (input)="q.set($any($event.target).value)">
    </div>
    <table style="margin-top:.6rem"><thead><tr><th>Tag</th><th>Questions</th><th></th></tr></thead><tbody>
      @for (t of visibleTags(); track t.id) {
        <tr><td>#{{ t.name }}</td><td class="muted">{{ t.usage }}</td>
          <td><div class="row" style="justify-content:flex-end"><button class="secondary" (click)="rename(t)">Rename</button><button class="danger" (click)="delTag(t)">Delete</button></div></td></tr>
      } @empty { <tr><td colspan="3" class="muted">No tags.</td></tr> }
    </tbody></table>
  </div>`,
  styles: [`
    .tgrid { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); }
    .topic { position: relative; color: var(--ink); text-decoration: none; display: flex; flex-direction: column; gap: .35rem; }
    .topic .go { margin-top: auto; color: var(--brand); display: inline-flex; gap: .3rem; align-items: center; font-weight: 600; }
    .topic .del { position: absolute; right: .35rem; bottom: .35rem; opacity: 0; }
    .topic:hover .del { opacity: 1; }
    .add { display: flex; flex-direction: column; gap: .5rem; justify-content: center; border-style: dashed; }
  `]
})
export class TopicsTagsComponent implements OnInit {
  private api = inject(ApiService);
  topics = signal<Topic[]>([]); tags = signal<Tag[]>([]); counts = signal<Bucket[]>([]);
  q = signal(''); error = signal<string | null>(null);
  visibleTags = () => this.tags().filter(t => t.name.toLowerCase().includes(this.q().trim().toLowerCase()));

  ngOnInit() { this.load(); }
  load() {
    this.api.topics().subscribe(v => this.topics.set(v));
    this.api.tags().subscribe(v => this.tags.set(v));
    this.api.dashboard().subscribe({ next: d => this.counts.set(d.byTopic), error: () => {} });
  }
  count(name: string) { return this.counts().find(b => b.label === name)?.count ?? 0; }
  private done = { next: () => { this.error.set(null); this.load(); }, error: (e: unknown) => this.error.set(errorMessage(e)) };
  addTopic(n: string) { if (n.trim()) this.api.createTopic(n.trim()).subscribe(this.done); }
  delTopic(t: Topic, e: Event) {
    e.preventDefault(); e.stopPropagation();
    if (confirm(`Delete topic "${t.name}"? Only possible when no questions use it.`)) this.api.deleteTopic(t.id).subscribe(this.done);
  }
  addTag(n: string) { if (n.trim()) this.api.createTag(n.trim()).subscribe(this.done); }
  rename(t: Tag) { const n = prompt('New tag name', t.name); if (n && n.trim()) this.api.updateTag(t.id, n.trim()).subscribe(this.done); }
  delTag(t: Tag) { if (confirm(`Delete tag "${t.name}"? It will be removed from ${t.usage} question(s).`)) this.api.deleteTag(t.id).subscribe(this.done); }
}
