package com.prephub.practice;

import com.prephub.practice.PracticeDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Bookmarks, per-question practice status/history and progress analytics. */
@RestController
@RequestMapping("/api/v1")
public class PracticeQueryController {
    private final BookmarkService bookmarks;
    private final PracticeService practice;
    private final ProgressService progress;

    public PracticeQueryController(BookmarkService bookmarks, PracticeService practice, ProgressService progress) {
        this.bookmarks = bookmarks; this.practice = practice; this.progress = progress;
    }

    @GetMapping("/bookmarks") public List<BookmarkService.BookmarkDto> bookmarks() { return bookmarks.list(); }

    @PutMapping("/questions/{id}/bookmark")
    public BookmarkService.BookmarkDto bookmark(@PathVariable Long id, @Valid @RequestBody(required = false) BookmarkRequest r) {
        return bookmarks.save(id, r == null ? null : r.notes());
    }

    @DeleteMapping("/questions/{id}/bookmark") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbookmark(@PathVariable Long id) { bookmarks.remove(id); }

    @GetMapping("/questions/status")
    public List<QuestionStatus> status(@RequestParam List<Long> ids) { return bookmarks.status(ids); }

    @GetMapping("/questions/{id}/attempts")
    public List<AttemptDto> attempts(@PathVariable Long id) { return practice.attemptsFor(id); }

    @GetMapping("/progress") public Progress progress() { return progress.progress(); }
}
