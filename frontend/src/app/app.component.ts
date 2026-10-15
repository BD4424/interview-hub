import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
  <div class="shell">
    <nav>
      <div class="brand">Interview Prep Hub</div>
      @for (l of links; track l.path) {
        <a [routerLink]="l.path" routerLinkActive="active">{{ l.label }}</a>
      }
    </nav>
    <main><router-outlet /></main>
  </div>`,
  styles: [`
    .shell { display: flex; min-height: 100vh; }
    nav { width: 220px; background: #1d2433; padding: 1rem .75rem; display: flex; flex-direction: column; gap: .15rem; position: sticky; top: 0; height: 100vh; }
    .brand { color: #fff; font-weight: 700; margin: 0 .5rem 1rem; }
    nav a { color: #c5cbd8; padding: .5rem .6rem; border-radius: 8px; font-size: .92rem; }
    nav a.active, nav a:hover { background: #2f3a52; color: #fff; }
    main { flex: 1; padding: 1.5rem 2rem; max-width: 1200px; }
    @media (max-width: 760px) { .shell { flex-direction: column; } nav { width: 100%; height: auto; position: static; flex-direction: row; flex-wrap: wrap; } main { padding: 1rem; } }
  `]
})
export class AppComponent {
  links = [
    { path: '/dashboard', label: 'Dashboard' }, { path: '/questions', label: 'Question Bank' },
    { path: '/companies', label: 'Companies' }, { path: '/topics-tags', label: 'Topics & Tags' },
    { path: '/import', label: 'Import Questions' }, { path: '/interview', label: 'AI Interview' },
    { path: '/bookmarks', label: 'Bookmarks' }, { path: '/progress', label: 'Progress' }, { path: '/settings', label: 'Settings' }
  ];
}
