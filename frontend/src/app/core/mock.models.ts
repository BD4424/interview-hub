import { Difficulty, Evaluation } from './models';

export type MockScope = 'FULL' | 'TOPICS' | 'CUSTOM';
export type MockStatus = 'IN_PROGRESS' | 'EVALUATING' | 'AWAITING_SELF_SCORE' | 'COMPLETED';
export type MockItemStatus = 'UNANSWERED' | 'ANSWERED' | 'EVALUATED' | 'FAILED';

export interface MockItem {
  id: number; position: number; questionId?: number | null; question: string; topic?: string | null; difficulty?: string | null;
  userAnswer?: string | null; flagged: boolean; status: MockItemStatus;
  score?: number | null; evaluation?: Evaluation | null; referenceAnswer?: string | null; error?: string | null;
}
export interface MockGroup { label: string; questions: number; answered: number; score?: number | null; }
export interface MockResults {
  overallScore?: number | null; percent?: number | null; verdict: string; answered: number; unanswered: number; failed: number;
  averageOnAnswered?: number | null; byTopic: MockGroup[]; byDifficulty: MockGroup[]; strongestTopic?: string | null;
  weakestTopic?: string | null; revisionTopics: string[]; weakCount: number;
}
export interface MockTest {
  id: number; title: string; scope: MockScope; topics: string[]; difficulty?: string | null; status: MockStatus; evaluationMode?: 'AI' | 'SELF' | null;
  questionCount: number; answeredCount: number; flaggedCount: number; timeLimitMinutes?: number | null; startedAt: string;
  deadlineAt?: string | null; remainingSeconds?: number | null; submittedAt?: string | null; completedAt?: string | null;
  timeTakenSeconds?: number | null; evaluatedCount: number; pendingCount: number; failedCount: number; aiAvailable: boolean;
  results?: MockResults | null; items: MockItem[];
}
export interface MockSummary {
  id: number; title: string; scope: MockScope; status: MockStatus; questionCount: number; answeredCount: number;
  timeLimitMinutes?: number | null; startedAt: string; submittedAt?: string | null; completedAt?: string | null;
  overallScore?: number | null; percent?: number | null;
}
export interface CreateMockRequest {
  scope: MockScope; topicIds?: number[]; difficulty?: Difficulty | null; questionCount?: number | null;
  timeLimitMinutes?: number | null; questionIds?: number[]; title?: string | null;
}
export interface MockSaveResult { itemId: number; status: MockItemStatus; flagged: boolean; savedAt: string; }
