import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-coming-soon', standalone: true,
  template: `<h1>{{ data['title'] }}</h1>
  <div class="card"><p>This module is scheduled for milestone {{ data['milestone'] }}. The database tables already exist;
  the API and UI are not implemented yet.</p></div>`
})
export class ComingSoonComponent { data = inject(ActivatedRoute).snapshot.data; }
