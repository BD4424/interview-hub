import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./features/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'questions', loadComponent: () => import('./features/question-list.component').then(m => m.QuestionListComponent) },
  { path: 'questions/new', loadComponent: () => import('./features/question-form.component').then(m => m.QuestionFormComponent) },
  { path: 'questions/:id/edit', loadComponent: () => import('./features/question-form.component').then(m => m.QuestionFormComponent) },
  { path: 'companies', loadComponent: () => import('./features/companies.component').then(m => m.CompaniesComponent) },
  { path: 'companies/:id', loadComponent: () => import('./features/company-detail.component').then(m => m.CompanyDetailComponent) },
  { path: 'topics-tags', loadComponent: () => import('./features/topics-tags.component').then(m => m.TopicsTagsComponent) },
  { path: 'import', loadComponent: () => import('./features/import-wizard.component').then(m => m.ImportWizardComponent) },
  { path: 'review', loadComponent: () => import('./features/review.component').then(m => m.ReviewComponent) },
  { path: 'interview', loadComponent: () => import('./features/coming-soon.component').then(m => m.ComingSoonComponent), data: { title: 'AI Interview', milestone: '11' } },
  { path: 'bookmarks', loadComponent: () => import('./features/coming-soon.component').then(m => m.ComingSoonComponent), data: { title: 'Bookmarks', milestone: '11' } },
  { path: 'progress', loadComponent: () => import('./features/coming-soon.component').then(m => m.ComingSoonComponent), data: { title: 'Progress', milestone: '12' } },
  { path: 'settings', loadComponent: () => import('./features/settings.component').then(m => m.SettingsComponent) },
  { path: '**', redirectTo: 'dashboard' }
];
