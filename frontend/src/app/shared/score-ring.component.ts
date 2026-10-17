import { Component, Input } from '@angular/core';

/** Circular score (0-10, or a 0-100 percentage with [percent]="true"), colour-coded. */
@Component({
  selector: 'app-score-ring', standalone: true,
  template: `
  <div class="ring" [style.width.px]="size" [style.height.px]="size"
       [style.background]="'conic-gradient(' + color + ' ' + pct + '%, var(--bg-2) 0)'">
    <div class="inner">
      <div class="val" [style.font-size.px]="size * 0.28">{{ display }}</div>
      @if (label) { <div class="lbl">{{ label }}</div> }
    </div>
  </div>`,
  styles: [`
    .ring { border-radius: 50%; display: grid; place-items: center; animation: pop .45s ease both; transition: background .6s; }
    .inner { width: 78%; height: 78%; border-radius: 50%; background: var(--card); display: grid; place-content: center; text-align: center; }
    .val { font-weight: 800; letter-spacing: -.03em; line-height: 1; color: var(--ink); }
    .lbl { font-size: .68rem; color: var(--muted); margin-top: .2rem; text-transform: uppercase; letter-spacing: .06em; }
  `]
})
export class ScoreRingComponent {
  @Input() value: number | null | undefined = null;
  @Input() size = 96;
  @Input() label = '';
  @Input() percent = false;
  get pct() { const v = this.value ?? 0; return Math.max(0, Math.min(100, this.percent ? v : v * 10)); }
  get display() { return this.value == null ? '—' : this.percent ? Math.round(this.value) + '%' : (Math.round(this.value * 10) / 10).toString(); }
  get color() {
    const p = this.pct;
    return p >= 75 ? '#22c55e' : p >= 55 ? '#f59e0b' : p > 0 ? '#ef4444' : 'var(--bg-2)';
  }
}
