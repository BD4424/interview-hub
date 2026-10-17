import { Component, Input } from '@angular/core';

const PATHS: Record<string, string> = {
  dashboard: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
  practice: 'M12 3a9 9 0 100 18 9 9 0 000-18zm-2 13V8l6 4-6 4z',
  progress: 'M4 20h16M6 16l4-5 3 3 5-7',
  bookmark: 'M6 3h12v18l-6-4-6 4V3z',
  bank: 'M4 5h16M4 10h16M4 15h10M4 20h7',
  company: 'M4 21V7l8-4 8 4v14M9 21v-6h6v6',
  tag: 'M3 12V3h9l9 9-9 9-9-9zm5-5a1.5 1.5 0 100 3 1.5 1.5 0 000-3z',
  upload: 'M12 16V4m0 0l-5 5m5-5l5 5M4 20h16',
  review: 'M9 12l2 2 4-4M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z',
  settings: 'M12 15a3 3 0 100-6 3 3 0 000 6zm7.4-3a7.4 7.4 0 00-.1-1.3l2-1.6-2-3.4-2.4 1a7.6 7.6 0 00-2.2-1.3L14.4 3h-4l-.3 2.4a7.6 7.6 0 00-2.2 1.3l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 000 2.6l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 002.2 1.3l.3 2.4h4l.3-2.4a7.6 7.6 0 002.2-1.3l2.4 1 2-3.4-2-1.6c.1-.4.1-.9.1-1.3z',
  sun: 'M12 17a5 5 0 100-10 5 5 0 000 10zM12 1v2m0 18v2M4.2 4.2l1.4 1.4m12.8 12.8l1.4 1.4M1 12h2m18 0h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4',
  moon: 'M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z',
  flame: 'M12 22c4 0 7-3 7-7 0-4-3-6-4-9-1 2-2 3-4 3 0-2 0-4-2-7-1 4-4 7-4 13 0 4 3 7 7 7z',
  spark: 'M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4L12 2z',
  check: 'M5 13l4 4L19 7',
  x: 'M6 6l12 12M18 6L6 18',
  arrow: 'M5 12h14m-6-6l6 6-6 6',
  clock: 'M12 7v5l3 2M12 21a9 9 0 110-18 9 9 0 010 18z',
  star: 'M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9L12 3z',
  target: 'M12 21a9 9 0 110-18 9 9 0 010 18zm0-4a5 5 0 110-10 5 5 0 010 10zm0-4a1 1 0 110-2 1 1 0 010 2z',
  shuffle: 'M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5',
  mic: 'M12 15a3 3 0 003-3V6a3 3 0 00-6 0v6a3 3 0 003 3zm6-3a6 6 0 01-12 0M12 18v3',
  brain: 'M9 4a3 3 0 00-3 3v.5A3 3 0 004 10.3 3 3 0 005 16a3 3 0 004 3h0a2 2 0 002-2V6a2 2 0 00-2-2zm6 0a3 3 0 013 3v.5a3 3 0 012 2.8A3 3 0 0119 16a3 3 0 01-4 3h0a2 2 0 01-2-2V6a2 2 0 012-2z',
  plus: 'M12 5v14M5 12h14'
};

@Component({
  selector: 'app-icon', standalone: true,
  template: `<svg [attr.width]="size" [attr.height]="size" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      [attr.stroke-width]="stroke" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path"></path></svg>`,
  styles: [`:host { display: inline-flex; line-height: 0; }`]
})
export class IconComponent {
  @Input({ required: true }) name!: string;
  @Input() size = 18;
  @Input() stroke = 1.8;
  get path() { return PATHS[this.name] ?? PATHS['spark']; }
}
