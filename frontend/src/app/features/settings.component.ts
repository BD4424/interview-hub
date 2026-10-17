import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Calibration, ConnectionTest, DedupCheck, Settings } from '../core/models';
import { PrefsService } from '../core/prefs.service';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'app-settings', standalone: true, imports: [FormsModule, RouterLink, IconComponent],
  template: `
  <div class="page-header"><div><h1>Settings</h1><p>Connections, duplicate detection and personal preferences.</p></div></div>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (s(); as s) {
    <!-- AI -->
    <div class="card">
      <div class="row spread"><h2 style="margin:0">AI connection</h2>
        <button (click)="test()" [disabled]="testing()"><app-icon name="spark" /> {{ testing() ? 'Testing…' : 'Test connection' }}</button></div>
      <div class="grid" style="margin-top:.8rem">
        <div><div class="muted small">Chat model (grading, follow-ups)</div>
          <span class="chip" [class.ok]="s.ai.enabled" [class.warn]="!s.ai.enabled">{{ s.ai.enabled ? s.ai.model : 'off' }}</span>
          <div class="muted small">{{ s.ai.status }}</div></div>
        <div><div class="muted small">Embedding model (semantic duplicates)</div>
          <span class="chip" [class.ok]="s.semantic.enabled" [class.warn]="!s.semantic.enabled">{{ s.semantic.enabled ? s.semantic.model : 'off' }}</span></div>
      </div>
      @if (t(); as t) {
        <div class="banner" [class.ok]="t.chatOk && t.embeddingsOk" [class.error]="!(t.chatOk && t.embeddingsOk)" style="margin-top:.8rem">
          Chat: {{ t.chatOk ? '✓' : '✗' }} {{ t.chatMessage }}<br>Embeddings: {{ t.embeddingsOk ? '✓' : '✗' }} {{ t.embeddingsMessage }}
        </div>
      }
      @if (!s.ai.enabled) {
        <div class="divider"></div>
        <h3>Connect your OpenAI key</h3>
        <p class="muted">The key is read by the backend from environment variables only. It is never stored in the database, sent to the browser or logged. Restart the backend after setting it.</p>
        <div class="label">PowerShell (Windows)</div>
        <pre class="code">$env:AI_ENABLED="true"
$env:OPENAI_API_KEY="sk-...your key..."
$env:OPENAI_MODEL="gpt-4o-mini"
mvn spring-boot:run</pre>
        <div class="label">bash / macOS / Linux</div>
        <pre class="code">export AI_ENABLED=true OPENAI_API_KEY=sk-... OPENAI_MODEL=gpt-4o-mini
mvn spring-boot:run</pre>
        <p class="muted small">IntelliJ: Run configuration → Environment variables. Docker: put them in <code>.env</code> next to docker-compose.yml.</p>
      }
    </div>

    <!-- Semantic duplicates -->
    <div class="card">
      <h2 style="margin-top:0">Duplicate detection</h2>
      <p class="muted">Exact duplicates are always blocked. With embeddings on, differently-worded questions that ask the same thing are flagged for review —
        flagged as <strong>likely</strong> at ≥ {{ s.semantic.likelyThreshold }} and <strong>possible</strong> at ≥ {{ s.semantic.possibleThreshold }} similarity. Nothing is merged automatically.</p>
      @if (s.semantic.enabled) {
        <div class="row spread"><span>Index: {{ s.semantic.index.indexed }} / {{ s.semantic.index.totalQuestions }} questions</span><span class="muted small">{{ s.semantic.index.message }}</span></div>
        <div class="bar ok" style="margin:.4rem 0 .8rem"><span [style.width.%]="s.semantic.index.totalQuestions ? s.semantic.index.indexed / s.semantic.index.totalQuestions * 100 : 100"></span></div>
        <div class="row">
          <button class="secondary" (click)="reindex(false)" [disabled]="busy()">Index pending now</button>
          <button class="secondary" (click)="reindex(true)" [disabled]="busy()">Re-embed everything</button>
          <button class="secondary" (click)="calibrate()" [disabled]="busy()">Calibrate thresholds</button>
        </div>
        @if (cal(); as c) {
          <div class="divider"></div>
          <p class="muted">{{ c.guidance }} Sampled {{ c.sampled }} questions. Change thresholds with DEDUP_LIKELY_THRESHOLD / DEDUP_POSSIBLE_THRESHOLD.</p>
          <div class="hist">@for (h of c.nearestNeighbourScores; track h.range) {
            <div class="hcol"><span class="hb" [style.height.px]="8 + h.questions * 90 / maxHist(c)"></span><span class="muted small">{{ h.range }}</span><span class="small">{{ h.questions }}</span></div>
          }</div>
          <table style="margin-top:.8rem"><thead><tr><th>Score</th><th>Question A</th><th>Question B</th></tr></thead><tbody>
            @for (p of c.closestPairs; track $index) {
              <tr><td><span class="chip" [class.bad]="p.score >= c.likelyThreshold" [class.warn]="p.score >= c.possibleThreshold && p.score < c.likelyThreshold">{{ p.score }}</span></td>
                <td><a [routerLink]="['/questions', p.a.questionId, 'edit']">{{ p.a.questionText }}</a></td>
                <td><a [routerLink]="['/questions', p.b.questionId, 'edit']">{{ p.b.questionText }}</a></td></tr>
            }
          </tbody></table>
        }
      } @else { <div class="banner info">Turn on AI (above) to enable semantic duplicate detection.</div> }
      <div class="divider"></div>
      <h3>Similarity checker</h3>
      <textarea [(ngModel)]="probe" rows="2" placeholder="Type a question to see whether something similar is already in your bank"></textarea>
      <button class="secondary" style="margin-top:.5rem" (click)="check()" [disabled]="!probe.trim() || busy()">Check</button>
      @if (checkResult(); as r) {
        <div style="margin-top:.6rem">
          @if (r.exact) { <div class="banner error">Exact duplicate of <a [routerLink]="['/questions', r.exact.questionId, 'edit']">#{{ r.exact.questionId }}</a>: {{ r.exact.questionText }}</div> }
          @for (m of r.similar; track m.questionId) {
            <div class="row" style="padding:.3rem 0"><span class="chip" [class.bad]="m.classification === 'LIKELY_SEMANTIC_DUPLICATE'" [class.warn]="m.classification === 'POSSIBLE_DUPLICATE'">{{ m.score }}</span>
              <a [routerLink]="['/questions', m.questionId, 'edit']">{{ m.questionText }}</a><span class="muted small">{{ m.topic }}</span></div>
          }
          @if (!r.exact && !r.similar.length) { <div class="banner ok">Nothing similar found{{ r.semanticEnabled ? '' : ' (exact check only — semantic detection is off)' }}.</div> }
          @if (r.note) { <div class="muted small">{{ r.note }}</div> }
        </div>
      }
    </div>

    <!-- Preferences -->
    <div class="card">
      <h2 style="margin-top:0">Preferences</h2>
      <div class="grid">
        <div><label>Daily goal (answers per day)</label><input type="number" min="1" max="50" [ngModel]="prefs.dailyGoal()" (ngModelChange)="prefs.setDailyGoal($event)"></div>
        <div><label>Theme</label><button class="secondary" (click)="prefs.toggleTheme()"><app-icon [name]="prefs.theme() === 'dark' ? 'sun' : 'moon'" /> Switch to {{ prefs.theme() === 'dark' ? 'light' : 'dark' }}</button></div>
        <div><label>Time zone (streaks, daily stats)</label><div>{{ s.timezone }}</div><div class="muted small">APP_TIMEZONE on the backend</div></div>
      </div>
    </div>

    <div class="card">
      <h2 style="margin-top:0">Import</h2>
      <p>Formats: {{ s.imports.formats.join(', ') }} · Max upload: {{ s.imports.maxUploadMb }} MB</p>
      <p class="muted">Several companies in one cell: separate with "{{ s.imports.companyDelimiter }}". Several tags: "{{ s.imports.tagDelimiter }}".
        In TXT/MD/DOCX/PDF files, mark questions with "Q:" and answers with "A:" for exact extraction; lines like "Company: X; Y" or "Tags: a, b" under a question are read as metadata.</p>
    </div>
  } @else if (!error()) { <div class="skeleton" style="height:240px"></div> }`,
  styles: [`
    .code { background: #0f172a; color: #e2e8f0; padding: .8rem 1rem; border-radius: 10px; font-size: .82rem; overflow-x: auto; }
    .label { font-size: .75rem; font-weight: 800; text-transform: uppercase; letter-spacing: .06em; color: var(--muted); margin-top: .6rem; }
    .hist { display: flex; gap: .6rem; align-items: flex-end; }
    .hcol { display: flex; flex-direction: column; align-items: center; gap: .2rem; flex: 1; }
    .hb { width: 100%; background: var(--grad); border-radius: 6px 6px 2px 2px; }
  `]
})
export class SettingsComponent implements OnInit {
  private api = inject(ApiService);
  prefs = inject(PrefsService);
  s = signal<Settings | null>(null); error = signal<string | null>(null);
  t = signal<ConnectionTest | null>(null); testing = signal(false);
  cal = signal<Calibration | null>(null); busy = signal(false);
  checkResult = signal<DedupCheck | null>(null);
  probe = '';

  ngOnInit() { this.load(); }
  load() { this.api.settings().subscribe({ next: v => this.s.set(v), error: e => this.error.set(errorMessage(e)) }); }
  test() {
    this.testing.set(true);
    this.api.testAi().subscribe({ next: v => { this.t.set(v); this.testing.set(false); }, error: e => { this.testing.set(false); this.error.set(errorMessage(e)); } });
  }
  reindex(reset: boolean) {
    if (reset && !confirm('Re-embed every question? This makes one embedding call per 64 questions.')) return;
    this.busy.set(true);
    this.api.reindex(reset).subscribe({ next: () => { this.busy.set(false); this.load(); }, error: e => { this.busy.set(false); this.error.set(errorMessage(e)); } });
  }
  calibrate() {
    this.busy.set(true);
    this.api.calibration(25).subscribe({ next: c => { this.cal.set(c); this.busy.set(false); }, error: e => { this.busy.set(false); this.error.set(errorMessage(e)); } });
  }
  check() {
    this.busy.set(true);
    this.api.checkDuplicate(this.probe).subscribe({ next: r => { this.checkResult.set(r); this.busy.set(false); }, error: e => { this.busy.set(false); this.error.set(errorMessage(e)); } });
  }
  maxHist(c: Calibration) { return Math.max(1, ...c.nearestNeighbourScores.map(h => h.questions)); }
}
