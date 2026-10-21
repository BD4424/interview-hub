export type StepStatus = 'MASTERED' | 'IN_PROGRESS' | 'NOT_STARTED' | 'EMPTY';

export interface Bucket { label: string; count: number; }
export interface TopicQItem { questionId: number; question: string; difficulty?: string; lastScore?: number | null; attempts: number; bookmarked: boolean; }
export interface TopicOverview {
  id: number; name: string; slug: string; description?: string; notes?: string | null; notesUpdatedAt?: string | null;
  totalQuestions: number; answered: number; practiced: number; coverage?: number | null; currentScore?: number | null; mastery?: number | null;
  dueForRevision: number; byDifficulty: Bucket[]; byTag: Bucket[]; byCompany: Bucket[];
  weakest: TopicQItem[]; neverPracticed: TopicQItem[]; neverPracticedCount: number;
}
export interface PathStep { tagId: number; tagName: string; position: number; questionCount: number; practiced: number;
  mastery?: number | null; currentScore?: number | null; status: StepStatus; }
export interface TagOption { tagId: number; tagName: string; questionCount: number; }
export interface LearningPath { topicId: number; custom: boolean; masteredThreshold: number; steps: PathStep[]; nextTagId?: number | null;
  overallMastery?: number | null; availableTags: TagOption[]; }

export type FlashScope = 'DUE' | 'NEW' | 'ALL';
export type FlashGrade = 'KNEW' | 'SHAKY' | 'DIDNT';
export interface FlashCard { questionId: number; question: string; answer?: string | null; topic: string; difficulty?: string; tags: string[];
  lastScore?: number | null; attempts: number; bookmarked: boolean; reason: string; }
export interface FlashDeck { scope: FlashScope; available: number; cards: FlashCard[]; }
export interface GradeResult { questionId: number; grade: FlashGrade; score: number; attempts: number; nextReviewInDays: number; }

export type PlanPhase = 'UPCOMING' | 'TODAY' | 'PAST';
export interface PlanItem { questionId: number; question: string; topic: string; difficulty?: string; lastScore?: number | null; attempts: number; done: boolean; }
export interface PlanDay { date: string; items: PlanItem[]; }
export interface TopicReadiness { topicId: number; topic: string; questions: number; practiced: number; readiness?: number | null; }
export interface PlanSummary { id: number; title: string; companyId?: number | null; company?: string | null; interviewDate: string; daysLeft: number;
  phase: PlanPhase; topicIds: number[]; topics: string[]; dailyTarget?: number | null; notes?: string | null; questions: number; practiced: number;
  readiness?: number | null; todayDone: number; todayTarget: number; createdAt: string; }
export interface PlanDetail { plan: PlanSummary; scope: string; perDay: number; pendingTotal: number; today: PlanItem[]; upcoming: PlanDay[];
  unscheduled: number; byTopic: TopicReadiness[]; }
export interface PlanRequest { title?: string | null; companyId?: number | null; interviewDate: string; topicIds: number[];
  dailyTarget?: number | null; notes?: string | null; }

export interface WeekPeriod { from: string; to: string; answers: number; questions: number; activeDays: number; averageScore?: number | null; flashcards: number; }
export interface WeeklyReport { thisWeek: WeekPeriod; lastWeek: WeekPeriod; answersChangePct?: number | null; scoreChange?: number | null;
  mostImproved?: { topic: string; delta: number; previous?: number; current?: number } | null;
  weakest: { questionId: number; question: string; topic: string; lastScore: number; lastAttempt: string }[]; showAsMondaySummary: boolean; }
