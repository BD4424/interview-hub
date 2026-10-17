import { Injectable, signal } from '@angular/core';

/** Local, per-browser preferences (theme, daily goal). Nothing here is sent to the server. */
@Injectable({ providedIn: 'root' })
export class PrefsService {
  theme = signal<'light' | 'dark'>((document.documentElement.getAttribute('data-theme') as 'light' | 'dark') || 'light');
  dailyGoal = signal<number>(this.readNumber('iph.dailyGoal', 5));

  toggleTheme() {
    const t = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(t);
    document.documentElement.setAttribute('data-theme', t);
    this.write('iph.theme', t);
  }

  setDailyGoal(n: number) {
    const v = Math.max(1, Math.min(50, Math.round(n || 1)));
    this.dailyGoal.set(v);
    this.write('iph.dailyGoal', String(v));
  }

  private readNumber(key: string, def: number) {
    try { const v = Number(localStorage.getItem(key)); return v > 0 ? v : def; } catch { return def; }
  }
  private write(key: string, v: string) { try { localStorage.setItem(key, v); } catch { /* private mode */ } }
}
