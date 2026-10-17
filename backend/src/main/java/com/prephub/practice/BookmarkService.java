package com.prephub.practice;

import com.prephub.common.NotFoundException;
import com.prephub.practice.PracticeDtos.QuestionStatus;
import com.prephub.question.QuestionDtos;
import com.prephub.question.QuestionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookmarkService {
    private final BookmarkRepository bookmarks;
    private final QuestionRepository questions;
    private final PracticeStats stats;

    public BookmarkService(BookmarkRepository bookmarks, QuestionRepository questions, PracticeStats stats) {
        this.bookmarks = bookmarks; this.questions = questions; this.stats = stats;
    }

    public record BookmarkDto(Long questionId, String notes, Instant createdAt, QuestionDtos.QuestionDto question) {}

    @Transactional
    public BookmarkDto save(Long questionId, String notes) {
        var q = questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question", questionId));
        QuestionBookmark b = bookmarks.findByQuestionId(questionId).orElseGet(() -> {
            QuestionBookmark n = new QuestionBookmark();
            n.setQuestionId(questionId);
            return n;
        });
        b.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        try { b = bookmarks.saveAndFlush(b); }
        catch (DataIntegrityViolationException race) { b = bookmarks.findByQuestionId(questionId).orElseThrow(() -> race); }
        return new BookmarkDto(questionId, b.getNotes(), b.getCreatedAt(), QuestionDtos.toDto(q));
    }

    @Transactional
    public void remove(Long questionId) { bookmarks.findByQuestionId(questionId).ifPresent(bookmarks::delete); }

    @Transactional(readOnly = true)
    public List<BookmarkDto> list() {
        List<QuestionBookmark> all = bookmarks.findAllByOrderByCreatedAtDesc();
        Map<Long, com.prephub.question.Question> qs = questions.findAllById(all.stream().map(QuestionBookmark::getQuestionId).toList())
                .stream().collect(Collectors.toMap(com.prephub.question.Question::getId, Function.identity()));
        return all.stream().filter(b -> qs.containsKey(b.getQuestionId()))
                .map(b -> new BookmarkDto(b.getQuestionId(), b.getNotes(), b.getCreatedAt(), QuestionDtos.toDto(qs.get(b.getQuestionId())))).toList();
    }

    /** Practice status for a page of question cards (one call per page). */
    @Transactional(readOnly = true)
    public List<QuestionStatus> status(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> limited = ids.stream().distinct().limit(200).toList();
        Map<Long, PracticeStats.QStat> st = stats.questionStats(limited);
        Set<Long> bm = stats.bookmarkedIds();
        Instant now = Instant.now();
        return limited.stream().map(id -> {
            var s = st.get(id);
            boolean due = s != null && s.lastScore() != null && (s.lastScore() < PracticeMath.WEAK_SCORE || PracticeMath.isDue(s.lastAttempt(), s.lastScore(), s.attempts(), now));
            return new QuestionStatus(id, bm.contains(id), s == null ? 0 : s.attempts(), s == null ? null : s.lastScore(),
                    s == null ? null : s.bestScore(), s == null ? null : s.lastAttempt(), due);
        }).toList();
    }
}
