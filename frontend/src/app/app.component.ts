import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { PrefsService } from './core/prefs.service';
import { IconComponent } from './shared/icon.component';

interface NavItem { path: string; label: string; icon: string; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
  <div class="shell">
    <header class="topbar">
      <button class="ghost menu" (click)="open.set(!open())" aria-label="Menu"><app-icon name="bank" /></button>
      <span class="logo-text">Interview Prep Hub</span>
    </header>
    <nav [class.open]="open()">
      <a class="brand" routerLink="/dashboard">
        <span class="logo"><app-icon name="spark" [size]="18" [stroke]="2.2" /></span>
        <span><strong>Interview Prep Hub</strong><small>Practice. Improve. Get hired.</small></span>
      </a>
      @for (g of groups; track g.title) {
        <div class="group">{{ g.title }}</div>
        @for (l of g.items; track l.path) {
          <a [routerLink]="l.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: l.path === '/questions' }">
            <app-icon [name]="l.icon" /> {{ l.label }}
          </a>
        }
      }
      <div class="foot">
        <a class="cta" routerLink="/practice"><app-icon name="practice" /> Start practicing</a>
        <button class="ghost theme" (click)="prefs.toggleTheme()">
          <app-icon [name]="prefs.theme() === 'dark' ? 'sun' : 'moon'" /> {{ prefs.theme() === 'dark' ? 'Light' : 'Dark' }} mode
        </button>
      </div>
    </nav>
    @if (open()) { <div class="scrim" (click)="open.set(false)"></div> }
    <main><router-outlet /></main>
  </div>`,
  styles: [`
    .shell { display: flex; min-height: 100vh; }
    nav { width: 248px; flex-shrink: 0; padding: 1rem .8rem; display: flex; flex-direction: column; gap: .1rem; position: sticky; top: 0; height: 100vh;
      overflow-y: auto; background: var(--card); border-right: 1px solid var(--line); }
    .brand { display: flex; gap: .65rem; align-items: center; padding: .3rem .5rem 1rem; color: var(--ink); text-decoration: none; }
    .brand small { display: block; color: var(--muted); font-size: .72rem; }
    .brand strong { font-size: .98rem; letter-spacing: -.01em; }
    .logo { width: 36px; height: 36px; border-radius: 10px; background: var(--grad); color: #fff; display: grid; place-items: center; box-shadow: var(--shadow-lg); }
    .group { font-size: .68rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); margin: .9rem .6rem .3rem; }
    nav a:not(.brand):not(.cta) { display: flex; align-items: center; gap: .6rem; color: var(--ink-2); padding: .5rem .65rem; border-radius: 10px;
      font-size: .9rem; font-weight: 500; text-decoration: none; transition: background .15s, color .15s; }
    nav a:not(.brand):not(.cta):hover { background: var(--bg-2); color: var(--ink); }
    nav a.active { background: var(--brand-soft) !important; color: var(--brand-ink) !important; font-weight: 700 !important; }
    .foot { margin-top: auto; padding-top: 1rem; display: flex; flex-direction: column; gap: .4rem; }
    .cta { display: flex; justify-content: center; align-items: center; gap: .5rem; background: var(--grad); color: #fff; padding: .7rem; border-radius: 12px;
      font-weight: 700; text-decoration: none !important; box-shadow: var(--shadow-lg); }
    .theme { justify-content: flex-start; }
    main { flex: 1; min-width: 0; padding: 1.75rem 2.25rem 3rem; max-width: 1240px; }
    .topbar { display: none; }
    .scrim { display: none; }
    @media (max-width: 860px) {
      .shell { flex-direction: column; }
      .topbar { display: flex; align-items: center; gap: .5rem; padding: .6rem .8rem; background: var(--card); border-bottom: 1px solid var(--line); position: sticky; top: 0; z-index: 20; }
      .logo-text { font-weight: 800; }
      nav { position: fixed; z-index: 30; left: 0; top: 0; transform: translateX(-100%); transition: transform .2s ease; box-shadow: var(--shadow-lg); }
      nav.open { transform: none; }
      .scrim { display: block; position: fixed; inset: 0; background: rgba(0, 0, 0, .35); z-index: 25; }
      main { padding: 1rem; }
    }
  `]
})
export class AppComponent {
  prefs = inject(PrefsService);
  open = signal(false);
  groups: { title: string; items: NavItem[] }[] = [
    { title: 'Prepare', items: [
      { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' }, { path: '/practice', label: 'Practice & mock interviews', icon: 'practice' },
      { path: '/progress', label: 'Progress', icon: 'progress' }, { path: '/bookmarks', label: 'Bookmarks', icon: 'bookmark' }] },
    { title: 'Library', items: [
      { path: '/questions', label: 'Question bank', icon: 'bank' }, { path: '/companies', label: 'Companies', icon: 'company' },
      { path: '/topics-tags', label: 'Topics & tags', icon: 'tag' }] },
    { title: 'Manage', items: [
      { path: '/import', label: 'Import questions', icon: 'upload' }, { path: '/review', label: 'AI review queue', icon: 'review' },
      { path: '/settings', label: 'Settings', icon: 'settings' }] }
  ];
  constructor() {
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.open.set(false));
  }
}
