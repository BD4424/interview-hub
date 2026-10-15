import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Question } from '../core/models';

@Component({
  selector: 'app-question-card', standalone: true, imports: [RouterLink],
  template: `
  <div class="card">
    <div class="row" style="justify-content: space-between">
      <strong class="pre" style="flex:1">{{ q.questionText }}</strong>
      <div class="row">
        <a class="btn secondary" [routerLink]="['/questions', q.id, 'edit']">Edit</a>
        <button class="danger" (click)="delete.emit(q)">Delete</button>
      </div>
    </div>
    <div style="margin-top:.5rem">
      <span class="chip">{{ q.topicName }}</span>
      @if (q.difficulty) { <span class="chip diff">{{ q.difficulty }}</span> }
      @if (q.questionType) { <span class="chip diff">{{ q.questionType }}</span> }
      @for (t of q.tags; track t.id) { <span class="chip">#{{ t.name }}</span> }
      @for (c of q.companies; track c.companyId) { <span class="chip company">{{ c.companyName }}@if (c.interviewRound) { · {{ c.interviewRound }} }</span> }
      @if (!q.companies.length) { <span class="chip diff">company unknown</span> }
      <span class="chip" [class.ok]="q.answerText" [class.warn]="!q.answerText">{{ q.answerText ? 'answer ' + q.answerStatus.toLowerCase() : 'no answer' }}</span>
    </div>
    @if (q.answerText) {
      <button class="secondary" style="margin-top:.5rem" (click)="open.set(!open())">{{ open() ? 'Hide' : 'Show' }} answer</button>
      @if (open()) { <p class="pre">{{ q.answerText }}</p> }
    }
  </div>`
})
export class QuestionCardComponent {
  @Input({ required: true }) q!: Question;
  @Output() delete = new EventEmitter<Question>();
  open = signal(false);
}
