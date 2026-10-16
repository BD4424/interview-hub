import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { Settings } from '../core/models';

@Component({
  selector: 'app-settings', standalone: true,
  template: `
  <h1>Settings</h1>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  @if (s(); as s) {
    <div class="card"><h2>AI</h2>
      <p><span class="chip" [class.ok]="s.ai.enabled" [class.warn]="!s.ai.enabled">{{ s.ai.enabled ? 'Enabled' : 'Disabled' }}</span> {{ s.ai.status }}</p>
      <p class="muted">AI settings are backend environment variables (AI_ENABLED, OPENAI_API_KEY, OPENAI_MODEL, OPENAI_BASE_URL).
        The API key is never sent to the browser.</p>
    </div>
    <div class="card"><h2>Import</h2>
      <p>Formats: {{ s.imports.formats.join(', ') }} · Max upload: {{ s.imports.maxUploadMb }} MB</p>
      <p class="muted">Several companies in one cell: separate with "{{ s.imports.companyDelimiter }}". Several tags: separate with "{{ s.imports.tagDelimiter }}".
        In TXT/MD/DOCX/PDF files, mark questions with "Q:" and answers with "A:" for exact extraction; lines like "Company: X; Y" or "Tags: a, b" directly under a question are read as metadata.</p>
    </div>
  } @else { <p class="muted">Loading…</p> }`
})
export class SettingsComponent implements OnInit {
  private api = inject(ApiService);
  s = signal<Settings | null>(null); error = signal<string | null>(null);
  ngOnInit() { this.api.settings().subscribe({ next: v => this.s.set(v), error: e => this.error.set(errorMessage(e)) }); }
}
