import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { Tag, Topic } from '../core/models';

@Component({
  selector: 'app-topics-tags', standalone: true,
  template: `
  <h1>Topics & Tags</h1>
  <p class="muted">A question has exactly one primary topic and any number of tags.</p>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  <div class="grid" style="grid-template-columns: 1fr 1fr">
    <div class="card"><h2>Topics</h2>
      <div class="row"><input #tn placeholder="New topic" style="flex:1"><button (click)="addTopic(tn.value); tn.value=''">Add</button></div>
      <table><tbody>
        @for (t of topics(); track t.id) { <tr><td>{{ t.name }}</td><td><button class="secondary" (click)="delTopic(t)">Delete</button></td></tr> }
      </tbody></table>
    </div>
    <div class="card"><h2>Tags (most used first)</h2>
      <div class="row"><input #tg placeholder="New tag" style="flex:1"><button (click)="addTag(tg.value); tg.value=''">Add</button></div>
      <input placeholder="Search tags" (input)="q.set($any($event.target).value)" style="margin:.5rem 0">
      <table><tbody>
        @for (t of visibleTags(); track t.id) {
          <tr><td>#{{ t.name }}</td><td class="muted">{{ t.usage }}</td>
            <td class="row"><button class="secondary" (click)="rename(t)">Rename</button><button class="danger" (click)="delTag(t)">Delete</button></td></tr>
        }
      </tbody></table>
    </div>
  </div>`
})
export class TopicsTagsComponent implements OnInit {
  private api = inject(ApiService);
  topics = signal<Topic[]>([]); tags = signal<Tag[]>([]); q = signal(''); error = signal<string | null>(null);
  visibleTags = () => this.tags().filter(t => t.name.toLowerCase().includes(this.q().trim().toLowerCase()));
  ngOnInit() { this.load(); }
  load() { this.api.topics().subscribe(v => this.topics.set(v)); this.api.tags().subscribe(v => this.tags.set(v)); }
  private done = { next: () => { this.error.set(null); this.load(); }, error: (e: unknown) => this.error.set(errorMessage(e)) };
  addTopic(n: string) { if (n.trim()) this.api.createTopic(n).subscribe(this.done); }
  delTopic(t: Topic) { if (confirm(`Delete topic "${t.name}"?`)) this.api.deleteTopic(t.id).subscribe(this.done); }
  addTag(n: string) { if (n.trim()) this.api.createTag(n).subscribe(this.done); }
  rename(t: Tag) { const n = prompt('New tag name', t.name); if (n && n.trim()) this.api.updateTag(t.id, n).subscribe(this.done); }
  delTag(t: Tag) { if (confirm(`Delete tag "${t.name}"? It will be removed from ${t.usage} question(s).`)) this.api.deleteTag(t.id).subscribe(this.done); }
}
