import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Company } from '../core/models';

@Component({
  selector: 'app-companies', standalone: true, imports: [RouterLink, ReactiveFormsModule],
  template: `
  <h1>Companies</h1>
  @if (error()) { <div class="banner error">{{ error() }}</div> }
  <form class="card row" [formGroup]="f" (ngSubmit)="create()">
    <input formControlName="name" placeholder="New company name" style="flex:2">
    <input formControlName="industry" placeholder="Industry (optional)" style="flex:1">
    <input formControlName="website" placeholder="Website (optional)" style="flex:1">
    <button type="submit" [disabled]="f.invalid">Add company</button>
  </form>
  <input placeholder="Filter companies" (input)="filter.set($any($event.target).value)" style="margin-bottom:1rem">
  <div class="grid">
    @for (c of visible(); track c.id) {
      <a class="card" [routerLink]="['/companies', c.id]"><strong>{{ c.name }}</strong><div class="muted">{{ c.questionCount }} question(s)</div></a>
    } @empty { <p class="muted">No companies yet.</p> }
  </div>`
})
export class CompaniesComponent implements OnInit {
  private api = inject(ApiService);
  all = signal<Company[]>([]); filter = signal(''); error = signal<string | null>(null);
  f = inject(FormBuilder).nonNullable.group({ name: ['', [Validators.required, Validators.maxLength(200)]], industry: [''], website: [''] });
  visible = () => this.all().filter(c => c.name.toLowerCase().includes(this.filter().trim().toLowerCase()));
  ngOnInit() { this.load(); }
  load() { this.api.companies().subscribe({ next: v => this.all.set(v), error: e => this.error.set(errorMessage(e)) }); }
  create() {
    const v = this.f.getRawValue();
    this.api.createCompany({ name: v.name, industry: v.industry || undefined, website: v.website || undefined })
      .subscribe({ next: () => { this.f.reset(); this.error.set(null); this.load(); }, error: e => this.error.set(errorMessage(e)) });
  }
}
