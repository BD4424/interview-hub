export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type QuestionType = 'CONCEPTUAL' | 'CODING' | 'SCENARIO_BASED' | 'DEBUGGING' | 'SYSTEM_DESIGN';
export type AttributionMode = 'NONE' | 'SINGLE_COMPANY' | 'PER_ROW';
export type ReviewStatus = 'PENDING' | 'NEEDS_REVIEW' | 'AUTO_SKIPPED' | 'IMPORTED' | 'MERGED' | 'REJECTED' | 'INVALID';
export type ReviewAction = 'APPROVE' | 'REJECT' | 'MERGE' | 'ADD_COMPANY' | 'KEEP_SEPARATE';
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
  notes?: string; approvalStatus: string; companies: CompanyAttribution[]; createdAt: string; updatedAt: string;
}
export interface QuestionRequest {
  questionText: string; answerText?: string | null; topicId: number; tags: string[]; difficulty?: Difficulty | null;
  questionType?: QuestionType | null; answerStatus?: string | null; sourceReference?: string | null; notes?: string | null;
  companies: CompanyAttribution[];
}
export interface ApiError { status: number; code: string; message: string; details: Record<string, any>; }

export interface ImportReport {
  totalExtracted: number; imported: number; merged: number; exactDuplicatesSkipped: number; duplicatesInFile: number;
  needsReview: number; pendingApproval: number; invalid: number; rejected: number; withoutAnswer: number; withoutCompany: number;
  companiesNotInDirectory: string[]; tagsNotInDirectory: string[];
}
export interface ImportJob {
  id: number; filename: string; format: string; status: 'PROCESSING' | 'READY_FOR_REVIEW' | 'COMPLETED' | 'FAILED';
  attributionMode: AttributionMode; defaultCompanyName?: string; attributionPolicy: string; errorMessage?: string;
  previousImportsOfSameFile: number[]; report: ImportReport; createdAt: string;
}
export interface Candidate {
  id: number; sourceReference: string; question: string; answer?: string; topic?: string; tags: string[]; companies: string[];
  difficulty?: string; questionType?: string; interviewRound?: string; role?: string; jobLevel?: string;
  matchType: string; reviewStatus: ReviewStatus; reason?: string; matchedQuestionId?: number; similarityScore?: number;
  matchedQuestion?: Question; createdQuestionId?: number; warnings: string[];
}
export interface Dashboard {
  totalApproved: number; answered: number; unanswered: number; byTopic: Bucket[]; byCompany: Bucket[];
  pendingDuplicateReviews: number; recentImports: { id: number; filename: string; status: string; total: number; imported: number; createdAt: string }[];
}
export interface CompanySummary {
  company: Company; totalQuestions: number; answered: number; unanswered: number;
  byTopic: Bucket[]; byDifficulty: Bucket[]; byRound: Bucket[]; byTag: Bucket[];
}
