import { Component, Input } from '@angular/core';
import { DayProgress } from '../core/models';

/** Tiny activity chart: bar height = attempts per day, colour = average score. */
@Component({
  selector: 'app-spark-bars', standalone: true,
  template: `
  <div class="bars" [style.height.px]="height">
    @for (d of days; track d.date) {
      <div class="col" [title]="d.date + ': ' + d.attempts + ' answered' + (d.averageScore != null ? ', avg ' + d.averageScore : '')">
        <span [style.height.%]="h(d)" [style.background]="c(d)"></span>
      </div>
    }
  </div>`,
  styles: [`
    .bars { display: flex; align-items: flex-end; gap: 4px; }
    .col { flex: 1; height: 100%; display: flex; align-items: flex-end; }
    .col span { width: 100%; min-height: 3px; border-radius: 4px 4px 2px 2px; transition: height .5s ease; }
  `]
})
export class SparkBarsComponent {
  @Input() days: DayProgress[] = [];
  @Input() height = 60;
  private get max() { return Math.max(1, ...this.days.map(d => d.attempts)); }
  h(d: DayProgress) { return d.attempts === 0 ? 4 : 15 + (d.attempts / this.max) * 85; }
  c(d: DayProgress) {
    if (!d.attempts) return 'var(--bg-2)';
    const s = d.averageScore ?? 5;
    return s >= 7.5 ? '#22c55e' : s >= 5.5 ? '#f59e0b' : '#ef4444';
  }
}
