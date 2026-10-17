export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type QuestionType = 'CONCEPTUAL' | 'CODING' | 'SCENARIO_BASED' | 'DEBUGGING' | 'SYSTEM_DESIGN';
export type AttributionMode = 'NONE' | 'SINGLE_COMPANY' | 'PER_ROW';
export type ReviewStatus = 'PENDING' | 'NEEDS_REVIEW' | 'AUTO_SKIPPED' | 'IMPORTED' | 'MERGED' | 'REJECTED' | 'INVALID';
export type ReviewAction = 'APPROVE' | 'REJECT' | 'MERGE' | 'ADD_COMPANY' | 'KEEP_SEPARATE';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export const DIFFICULTIES: Difficulty[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
export const QUESTION_TYPES: QuestionType[] = ['CONCEPTUAL', 'CODING', 'SCENARIO_BASED', 'DEBUGGING', 'SYSTEM_DESIGN'];

export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface Topic { id: number; name: string; slug: string; description?: string; }
export interface Tag { id: number; name: string; usage: number; }
export interface Company { id: number; name: string; website?: string; industry?: string; notes?: string; questionCount: number; }
export interface Bucket { label: string; count: number; }

export interface CompanyAttribution {
  associationId?: number; companyId?: number | null; companyName?: string | null; role?: string | null;
  interviewRound?: string | null; jobLevel?: string | null; dateAsked?: string | null; sourceReference?: string | null;
  attributionStatus?: 'REPORTED' | 'VERIFIED' | 'UNVERIFIED' | null; notes?: string | null;
}
export interface Question {
  id: number; questionText: string; answerText?: string; topicId: number; topicName: string; tags: { id: number; name: string }[];
  difficulty?: Difficulty; questionType?: QuestionType; answerStatus: string; sourceType: string; sourceReference?: string;
  notes?: string; approvalStatus: ApprovalStatus; companies: CompanyAttribution[]; createdAt: string; updatedAt: string;
}
export interface QuestionRequest {
  questionText: string; answerText?: string | null; topicId: number; tags: string[]; difficulty?: Difficulty | null;
  questionType?: QuestionType | null; answerStatus?: string | null; sourceReference?: string | null; notes?: string | null;
  companies: CompanyAttribution[];
}
export interface ApiError { status: number; code: string; message: string; details: Record<string, any>; }

// ---- import
export interface ImportReport {
  totalExtracted: number; imported: number; merged: number; exactDuplicatesSkipped: number; duplicatesInFile: number;
  needsReview: number; pendingApproval: number; invalid: number; rejected: number; withoutAnswer: number; withoutCompany: number;
  companiesNotInDirectory: string[]; tagsNotInDirectory: string[];
}
export interface ImportJob {
  id: number; filename: string; format: string; status: 'PROCESSING' | 'READY_FOR_REVIEW' | 'COMPLETED' | 'FAILED';
  attributionMode: AttributionMode; defaultCompanyName?: string; attributionPolicy: string; errorMessage?: string;
  parseNotes: string[]; previousImportsOfSameFile: number[]; report: ImportReport; createdAt: string;
}
export interface CandidatePreview { reference: string; question: string; hasAnswer: boolean; topic?: string; tags: string[]; companies: string[]; difficulty?: string; }
export interface InspectResult {
  filename: string; format: string; tabular: boolean; headers: string[]; mapping: Record<string, string>; fields: string[];
  candidateCount: number; sample: CandidatePreview[]; notes: string[]; error?: string | null;
}
export interface Candidate {
  id: number; sourceReference: string; question: string; answer?: string; topic?: string; tags: string[]; companies: string[];
  difficulty?: string; questionType?: string; interviewRound?: string; role?: string; jobLevel?: string;
  matchType: string; reviewStatus: ReviewStatus; reason?: string; matchedQuestionId?: number; similarityScore?: number;
  matchedQuestion?: Question; createdQuestionId?: number; warnings: string[];
}

// ---- practice & progress (M11-12)
export type SessionType = 'TOPIC' | 'COMPANY' | 'TAG' | 'RANDOM' | 'MOCK' | 'REVISION' | 'SINGLE';
export type TurnStatus = 'ASKED' | 'AWAITING_SELF_SCORE' | 'EVALUATED' | 'SKIPPED';
export type Confidence = 'LOW' | 'MEDIUM' | 'HIGH';
export interface Rubric { technicalCorrectness?: number; conceptualUnderstanding?: number; completeness?: number;
  practicalApplication?: number; edgeCasesAndTradeoffs?: number; clarity?: number; }
export interface Evaluation {
  score: number; rubric?: Rubric | null; summary?: string; correctPoints: string[]; missedPoints: string[];
  incorrectStatements: string[]; improvedAnswer: string; revisionTopics: string[];
  followUpQuestion?: { question: string; expectedAnswerPoints: string[] } | null; model: string;
}
export interface Turn {
  id: number; turnNumber: number; type: 'BANK' | 'FOLLOW_UP'; status: TurnStatus; questionId?: number; question: string;
  topic?: string; difficulty?: Difficulty; tags: string[]; companies: string[]; sessionOnly: boolean; parentTurnId?: number;
  userAnswer?: string; confidence?: Confidence; score?: number; evaluation?: Evaluation | null; referenceAnswer?: string | null;
  expectedPoints: string[]; followUpAvailable: boolean; askedAt?: string; answeredAt?: string;
}
export interface Session {
  id: number; type: SessionType; status: 'ACTIVE' | 'COMPLETED'; title: string; topic?: string; company?: string; tags: string[];
  difficulty?: Difficulty; questionLimit: number; bankQuestionsAsked: number; startedAt: string; completedAt?: string;
  overallScore?: number; aiEnabled: boolean; currentTurnId?: number; turns: Turn[]; message?: string;
}
export interface StartSessionRequest {
  type: SessionType; topicId?: number | null; companyId?: number | null; tagIds?: number[]; difficulty?: Difficulty | null;
  questionLimit?: number | null; questionId?: number | null;
}
export interface SessionSummary { id: number; type: SessionType; status: string; title: string; answered: number; overallScore?: number; startedAt: string; completedAt?: string; }
export interface QuestionStatus { questionId: number; bookmarked: boolean; attempts: number; lastScore?: number; bestScore?: number; lastAttemptAt?: string; dueForRevision: boolean; }
export interface Attempt { id: number; sessionId?: number; userAnswer?: string; score?: number; confidence?: Confidence; evaluation?: Evaluation | null; attemptedAt: string; }
export interface Bookmark { questionId: number; notes?: string; createdAt: string; question: Question; }
export interface TopicProgress { topic: string; attempts: number; questionsAttempted: number; totalQuestions: number; averageScore?: number; }
export interface DayProgress { date: string; attempts: number; averageScore?: number; }
export interface ProgressSummary { totalAttempts: number; questionsAttempted: number; averageScore30d?: number; streakDays: number;
  todayAttempts: number; dueForRevision: number; bookmarks: number; sessionsCompleted: number; }
export interface Progress { summary: ProgressSummary; last30Days: DayProgress[]; topics: TopicProgress[]; weakTopics: TopicProgress[];
  recentSessions: SessionSummary[]; recentAttempts: { questionId: number; question: string; topic: string; score?: number; at: string }[]; }

export interface Dashboard {
  totalApproved: number; answered: number; unanswered: number; byTopic: Bucket[]; byCompany: Bucket[];
  pendingDuplicateReviews: number; pendingAiReviews: number;
  recentImports: { id: number; filename: string; status: string; total: number; imported: number; createdAt: string }[];
  practice: ProgressSummary; weakTopics: TopicProgress[]; recentSessions: SessionSummary[]; last14Days: DayProgress[];
}
export interface CompanySummary {
  company: Company; totalQuestions: number; answered: number; unanswered: number; bookmarked: number; practiced: number;
  byTopic: Bucket[]; byDifficulty: Bucket[]; byRound: Bucket[]; byTag: Bucket[];
}

// ---- AI (M9)
export type FollowUpMode = 'SESSION_ONLY' | 'REVIEW';
export type Persistence = 'SESSION_ONLY' | 'REVIEW_ELIGIBLE' | 'LINK_EXISTING';
export interface GenerateFollowUpsRequest {
  count?: number; mode?: FollowUpMode; companyId?: number | null; role?: string | null; interviewRound?: string | null;
  previousAnswer?: string | null; sessionQuestions?: string[];
}
export interface DuplicateInfo { matchType: string; matchingQuestionId: number; matchingText: string; matchingApproval: ApprovalStatus; similarity?: number; }
export interface FollowUpItem {
  parentQuestionId: number; question: string; expectedAnswerPoints: string[]; difficulty?: Difficulty;
  existingTags: string[]; suggestedNewTags: string[]; reason: string; persistence: Persistence; duplicate?: DuplicateInfo | null;
}
export interface FollowUpBatch { parentQuestionId: number; model: string; companyContext?: string | null; disclaimer: string; items: FollowUpItem[]; rejected: string[]; }
export interface SaveFollowUpResult { status: 'CREATED_PENDING_REVIEW' | 'LINKED_EXISTING'; questionId: number; approvalStatus: ApprovalStatus; message: string; }
export interface LinkedFollowUp { questionId: number; question: string; approvalStatus: ApprovalStatus; difficulty?: string; sequence?: number; }
export interface DraftAnswer { questionId: number; model: string; draft: string; note: string; }

// ---- semantic duplicates (M10) & settings
export interface DedupMatch { questionId: number; questionText: string; topic: string; approvalStatus: string; score: number; classification: string; }
export interface DedupCheck { exact?: DedupMatch | null; similar: DedupMatch[]; semanticEnabled: boolean; likelyThreshold: number; possibleThreshold: number; note?: string | null; }
export interface IndexStatus { enabled: boolean; model?: string; totalQuestions: number; indexed: number; pending: number; lastError?: string; lastRun?: string; message: string; }
export interface Calibration { likelyThreshold: number; possibleThreshold: number; sampled: number;
  closestPairs: { a: DedupMatch; b: DedupMatch; score: number }[]; nearestNeighbourScores: { range: string; questions: number }[]; guidance: string; }
export interface Settings {
  ai: { enabled: boolean; provider?: string; model?: string; status: string };
  semantic: { enabled: boolean; model?: string; likelyThreshold: number; possibleThreshold: number; index: IndexStatus };
  imports: { formats: string[]; maxUploadMb: number; companyDelimiter: string; tagDelimiter: string };
  timezone: string;
}
export interface ConnectionTest { chatOk: boolean; chatMessage: string; embeddingsOk: boolean; embeddingsMessage: string; }
export const SUGGESTED_TAGS_PREFIX = 'Suggested new tags (not applied): ';
