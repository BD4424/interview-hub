import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subscription, filter, switchMap, take, timer } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { AttributionMode, Candidate, Company, ImportJob, InspectResult, ReviewAction, Topic } from '../core/models';

@Component({
  selector: 'app-import-wizard', standalone: true, imports: [ReactiveFormsModule, FormsModule],
  template: `
  <h1>Import Questions</h1>
  <div class="row" style="margin-bottom:1rem">
    @for (s of steps; track s; let i = $index) { <span class="chip" [class.ok]="currentStep() === i + 1">{{ i + 1 }}. {{ s }}</span> }
  </div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }

  @if (!job()) {
    <!-- 1. select file -->
    <div class="card">
      <label>File: CSV, Excel (.xlsx), Word (.docx), PDF (text-based), TXT or Markdown</label>
      <input type="file" accept=".csv,.xlsx,.docx,.pdf,.txt,.md,.markdown" (change)="pick($event)">
      <p class="muted">Spreadsheets: one row per question; columns are mapped from their headers and can be changed below.
        Documents: mark questions with "Q:" and answers with "A:" for exact results (otherwise questions are detected from "?" and headings).
        Lines like "Company: Amazon; Google" or "Tags: a, b" under a question are read as metadata. Scanned PDFs (images) are not supported yet.</p>
      @if (inspecting()) { <p class="muted">Reading file…</p> }
    </div>

    <!-- 2. preview & mapping -->
    @if (inspect(); as r) {
      <div class="card">
        <h2>Preview <span class="chip diff">{{ r.format.toUpperCase() }}</span></h2>
        @for (n of r.notes; track n) { <div class="muted">• {{ n }}</div> }
        @if (r.error) { <div class="banner error" style="margin-top:.5rem">{{ r.error }}</div> }
        @if (r.tabular) {
          <h2>Column mapping</h2>
          <table><thead><tr><th>Column in file</th><th>Maps to</th></tr></thead><tbody>
            @for (h of r.headers; track h) {
              <tr><td>{{ h }}</td><td><select [(ngModel)]="mapping[h]">@for (f of r.fields; track f) { <option [value]="f">{{ f }}</option> }</select></td></tr>
            }
          </tbody></table>
          <button class="secondary" style="margin-top:.5rem" (click)="runInspect()">Apply mapping & refresh preview</button>
        }
        <p><strong>{{ r.candidateCount }}</strong> question(s) found. First {{ r.sample.length }}:</p>
        <table><thead><tr><th>Source</th><th>Question</th><th>Answer</th><th>Companies</th><th>Tags</th></tr></thead><tbody>
          @for (c of r.sample; track c.reference) {
            <tr><td class="muted">{{ c.reference }}</td><td class="pre">{{ c.question || '(empty)' }}</td><td>{{ c.hasAnswer ? 'yes' : '—' }}</td>
              <td>{{ c.companies.join('; ') || '—' }}</td><td>{{ c.tags.join(', ') || '—' }}</td></tr>
          }
        </tbody></table>
      </div>

      <!-- 3. attribution & defaults -->
      @if (!r.error && r.candidateCount > 0) {
        <form class="card" [formGroup]="f" (ngSubmit)="upload()">
          <h2>Company attribution & defaults</h2>
          <label>Company attribution</label>
          <select formControlName="mode">
            <option value="NONE">No company attribution (companies stay unknown; company values in the file are ignored)</option>
            <option value="SINGLE_COMPANY">One company for the whole file (questions with their own company override it)</option>
            <option value="PER_ROW">Per question, from the file's company values (one or several)</option>
          </select>
          @if (f.value.mode !== 'NONE') {
            <label>{{ f.value.mode === 'SINGLE_COMPANY' ? 'Company for this file *' : 'Fallback company for questions without one (optional)' }}</label>
            <select formControlName="defaultCompanyId"><option [ngValue]="null">— none —</option>
              @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select>
            <p class="muted">Not listed? Add it on the Companies page first.</p>
          }
          <label>Default topic for questions without a recognised topic</label>
          <select formControlName="defaultTopicId"><option [ngValue]="null">General</option>
            @for (t of topics(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select>
          <label>Extra tags for every question (comma separated, optional)</label>
          <input formControlName="tags">
          <div style="margin-top:1rem"><button type="submit" [disabled]="busy()">{{ busy() ? 'Processing…' : 'Extract & check duplicates' }}</button></div>
        </form>
      }
    }
  }

  <!-- 4. review & approve -->
  @if (job(); as j) {
    <div class="card">
      <div class="row" style="justify-content:space-between"><strong>#{{ j.id }} {{ j.filename }} <span class="chip diff">{{ j.format.toUpperCase() }}</span></strong><span class="chip">{{ j.status }}</span></div>
      <p><strong>Attribution policy:</strong> {{ j.attributionPolicy }}</p>
      @for (n of j.parseNotes; track n) { <div class="muted">• {{ n }}</div> }
      @if (j.previousImportsOfSameFile.length) { <div class="banner ok">This exact file was imported before (job {{ j.previousImportsOfSameFile.join(', ') }}). Existing questions are detected as duplicates and will not be re-created.</div> }
      @if (j.errorMessage) { <div class="banner error">{{ j.errorMessage }}</div> }
      <div class="grid">
        @for (s of stats(j); track s[0]) { <div><div class="muted">{{ s[0] }}</div><strong>{{ s[1] }}</strong></div> }
      </div>
      @if (j.report.companiesNotInDirectory.length) { <p class="muted">New companies that approval will create: {{ j.report.companiesNotInDirectory.join(', ') }}</p> }
      @if (j.report.tagsNotInDirectory.length) { <p class="muted">New tags that approval will create: {{ j.report.tagsNotInDirectory.join(', ') }}</p> }
      <div class="row">
        @if (j.report.invalid + j.report.rejected > 0) { <a class="btn secondary" [href]="api.failedRowsUrl(j.id)">Download failed rows CSV</a> }
        <button class="secondary" (click)="reset()">Start another import</button>
      </div>
    </div>

    @if (j.status !== 'PROCESSING' && j.status !== 'FAILED') {
      <div class="row" style="justify-content:space-between">
        <h2>Candidates</h2>
        <div class="row">
          <select [(ngModel)]="view"><option value="ALL">All</option><option value="PENDING">Ready to import</option>
            <option value="NEEDS_REVIEW">Needs review</option><option value="INVALID">Invalid</option><option value="DONE">Done / skipped</option></select>
          <button (click)="approveSelected()" [disabled]="busy() || !selected.size">Import {{ selected.size }} selected</button>
        </div>
      </div>
      @for (c of visible(); track c.id) {
        <div class="card">
          <div class="row">
            @if (c.reviewStatus === 'PENDING' && c.matchType === 'NEW_CANDIDATE') {
              <input type="checkbox" style="width:auto" [checked]="selected.has(c.id)" (change)="toggle(c.id)">
            }
            <span class="muted">{{ c.sourceReference }}</span>
            <span class="chip" [class.ok]="c.matchType === 'NEW_CANDIDATE'" [class.warn]="c.reviewStatus === 'NEEDS_REVIEW'" [class.bad]="c.reviewStatus === 'INVALID'">{{ c.matchType }}</span>
            <span class="chip diff">{{ c.reviewStatus }}</span>
          </div>
          <div class="grid" style="grid-template-columns: 1fr 1fr; margin-top:.5rem">
            <div>
              <div class="muted">Incoming</div>
              <strong class="pre">{{ c.question || '(empty)' }}</strong>
              <div>@if (c.topic) { <span class="chip">{{ c.topic }}</span> }
                @for (t of c.tags; track t) { <span class="chip">#{{ t }}</span> }
                @for (co of c.companies; track co) { <span class="chip company">{{ co }}</span> }
                @if (c.difficulty) { <span class="chip diff">{{ c.difficulty }}</span> }</div>
              @if (c.answer) { <p class="pre muted">{{ c.answer }}</p> }
            </div>
            @if (c.matchedQuestion; as m) {
              <div>
                <div class="muted">Existing #{{ m.id }} @if (c.similarityScore != null) { · similarity {{ c.similarityScore }} } @if (m.approvalStatus !== 'APPROVED') { · {{ m.approvalStatus }} }</div>
                <strong class="pre">{{ m.questionText }}</strong>
                <div><span class="chip">{{ m.topicName }}</span>
                  @for (t of m.tags; track t.id) { <span class="chip">#{{ t.name }}</span> }
                  @for (co of m.companies; track co.companyId) { <span class="chip company">{{ co.companyName }}</span> }
                  <span class="chip diff">{{ m.sourceType }}</span></div>
                @if (m.answerText) { <p class="pre muted">{{ m.answerText }}</p> } @else { <p class="muted">No existing answer</p> }
              </div>
            }
          </div>
          @if (c.reason) { <p class="muted">{{ c.reason }}</p> }
          @for (w of c.warnings; track w) { <span class="chip warn">{{ w }}</span> }
          @if (c.reviewStatus === 'NEEDS_REVIEW' || c.reviewStatus === 'PENDING' || c.reviewStatus === 'INVALID') {
            <div class="row" style="margin-top:.5rem">
              @if (c.matchedQuestionId) {
                <button (click)="act(c, 'MERGE')">Merge into #{{ c.matchedQuestionId }}</button>
                <button class="secondary" (click)="act(c, 'ADD_COMPANY')" [disabled]="!c.companies.length">Add company only</button>
                @if (c.matchType !== 'EXACT_DUPLICATE') { <button class="secondary" (click)="act(c, 'KEEP_SEPARATE')">Keep as separate</button> }
              }
              <button class="secondary" (click)="startEdit(c)">Edit & re-validate</button>
              <button class="danger" (click)="act(c, 'REJECT')">Reject</button>
            </div>
          }
          @if (editing() === c.id) {
            <div class="card" style="margin-top:.5rem">
              <label>Question</label><textarea [(ngModel)]="draft.question"></textarea>
              <label>Answer</label><textarea [(ngModel)]="draft.answer"></textarea>
              <div class="grid">
                <div><label>Topic</label><input [(ngModel)]="draft.topic"></div>
                <div><label>Tags (comma)</label><input [(ngModel)]="draft.tagsText"></div>
                <div><label>Companies (;)</label><input [(ngModel)]="draft.companiesText"></div>
                <div><label>Difficulty</label><input [(ngModel)]="draft.difficulty" placeholder="BEGINNER/INTERMEDIATE/ADVANCED"></div>
              </div>
              <div class="row" style="margin-top:.5rem"><button (click)="saveEdit(c)">Save & re-validate</button><button class="secondary" (click)="editing.set(null)">Cancel</button></div>
            </div>
          }
        </div>
      } @empty { <p class="muted">Nothing in this view.</p> }
    }
  }`
})
export class ImportWizardComponent implements OnInit, OnDestroy {
  api = inject(ApiService);
  steps = ['Select file', 'Preview & map columns', 'Attribution & defaults', 'Duplicates, review & approve'];
  busy = signal(false); inspecting = signal(false); error = signal<string | null>(null);
  companies = signal<Company[]>([]); topics = signal<Topic[]>([]);
  inspect = signal<InspectResult | null>(null);
  job = signal<ImportJob | null>(null); candidates = signal<Candidate[]>([]);
  editing = signal<number | null>(null);
  mapping: Record<string, string> = {};
  draft: any = {};
  view = 'ALL';
  selected = new Set<number>();
  file: File | null = null;
  private poll?: Subscription;
  f = inject(FormBuilder).group({ mode: ['NONE' as AttributionMode], defaultCompanyId: [null as number | null], defaultTopicId: [null as number | null], tags: [''] });

  ngOnInit() { this.api.companies().subscribe(v => this.companies.set(v)); this.api.topics().subscribe(v => this.topics.set(v)); }
  ngOnDestroy() { this.poll?.unsubscribe(); }

  currentStep() {
    if (this.job()) return 4;
    const r = this.inspect();
    if (!r) return 1;
    return r.error || r.candidateCount === 0 ? 2 : 3;
  }

  pick(e: Event) {
    this.file = (e.target as HTMLInputElement).files?.[0] ?? null;
    this.mapping = {}; this.inspect.set(null); this.error.set(null);
    if (this.file) this.runInspect(true);
  }

  runInspect(auto = false) {
    if (!this.file) return;
    this.inspecting.set(true); this.error.set(null);
    this.api.inspectImport(this.file, auto ? null : this.mapping).subscribe({
      next: r => { this.inspect.set(r); this.mapping = { ...r.mapping }; this.inspecting.set(false); },
      error: e => { this.inspecting.set(false); this.error.set(errorMessage(e)); }
    });
  }

  upload() {
    const v = this.f.getRawValue();
    const r = this.inspect();
    if (!this.file || !r) return;
    if (v.mode === 'SINGLE_COMPANY' && !v.defaultCompanyId) { this.error.set('Choose the company for this file.'); return; }
    this.busy.set(true); this.error.set(null);
    this.api.startImport(this.file, v.mode!, v.defaultCompanyId, v.defaultTopicId, v.tags ?? '', r.tabular ? this.mapping : null).subscribe({
      next: j => { this.job.set(j); this.waitForProcessing(j.id); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  /** Polls until processing finishes so the page never needs a reload. */
  private waitForProcessing(id: number) {
    this.poll?.unsubscribe();
    this.poll = timer(0, 1000).pipe(switchMap(() => this.api.importJob(id)), filter(j => j.status !== 'PROCESSING'), take(1))
      .subscribe({ next: j => { this.job.set(j); this.busy.set(false); this.loadCandidates(); }, error: e => { this.busy.set(false); this.error.set(errorMessage(e)); } });
  }

  private refresh() {
    const j = this.job(); if (!j) return;
    this.api.importJob(j.id).subscribe(x => this.job.set(x));
    this.loadCandidates();
  }

  private loadCandidates() {
    const j = this.job(); if (!j) return;
    this.api.candidates(j.id).subscribe(cs => {
      this.candidates.set(cs);
      this.selected = new Set(cs.filter(c => c.reviewStatus === 'PENDING' && c.matchType === 'NEW_CANDIDATE').map(c => c.id));
    });
  }

  visible() {
    const done = ['IMPORTED', 'MERGED', 'REJECTED', 'AUTO_SKIPPED'];
    return this.candidates().filter(c => this.view === 'ALL' || (this.view === 'DONE' ? done.includes(c.reviewStatus) : c.reviewStatus === this.view));
  }

  stats(j: ImportJob): [string, number][] {
    const r = j.report;
    return [['Extracted', r.totalExtracted], ['Imported', r.imported], ['Merged', r.merged], ['Exact duplicates skipped', r.exactDuplicatesSkipped],
      ['Duplicates within file', r.duplicatesInFile], ['Needs review', r.needsReview], ['Ready to import', r.pendingApproval],
      ['Invalid', r.invalid], ['Rejected', r.rejected], ['Without answer', r.withoutAnswer], ['Without company', r.withoutCompany]];
  }

  toggle(id: number) { this.selected.has(id) ? this.selected.delete(id) : this.selected.add(id); }

  approveSelected() {
    const j = this.job(); if (!j || !this.selected.size) return;
    if (!confirm(`Import ${this.selected.size} question(s) into the bank?`)) return;
    this.busy.set(true);
    this.api.approve(j.id, [...this.selected]).subscribe({
      next: x => { this.job.set(x); this.busy.set(false); this.loadCandidates(); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }

  act(c: Candidate, action: ReviewAction) {
    const j = this.job(); if (!j) return;
    if (action === 'REJECT' && !confirm('Reject this item? It will appear in the failed-rows CSV.')) return;
    this.api.resolve(j.id, c.id, action).subscribe({ next: () => { this.error.set(null); this.refresh(); }, error: e => this.error.set(errorMessage(e)) });
  }

  startEdit(c: Candidate) {
    this.editing.set(c.id);
    this.draft = { question: c.question, answer: c.answer ?? '', topic: c.topic ?? '', tagsText: c.tags.join(', '),
      companiesText: c.companies.join('; '), difficulty: c.difficulty ?? '', questionType: c.questionType,
      interviewRound: c.interviewRound, role: c.role, jobLevel: c.jobLevel };
  }

  saveEdit(c: Candidate) {
    const j = this.job(); if (!j) return;
    const d = this.draft;
    const body = { question: d.question, answer: d.answer, topic: d.topic, difficulty: d.difficulty || null, questionType: d.questionType,
      interviewRound: d.interviewRound, role: d.role, jobLevel: d.jobLevel,
      tags: String(d.tagsText).split(',').map((s: string) => s.trim()).filter(Boolean),
      companies: String(d.companiesText).split(';').map((s: string) => s.trim()).filter(Boolean) };
    this.api.editCandidate(j.id, c.id, body).subscribe({ next: () => { this.editing.set(null); this.refresh(); }, error: e => this.error.set(errorMessage(e)) });
  }

  reset() {
    this.poll?.unsubscribe(); this.job.set(null); this.candidates.set([]); this.inspect.set(null);
    this.mapping = {}; this.file = null; this.error.set(null); this.busy.set(false);
  }
}
