package com.prephub.study;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class WeeklyReportController {
    private final WeeklyReportService service;
    public WeeklyReportController(WeeklyReportService service) { this.service = service; }

    @GetMapping("/weekly") public WeeklyReportService.WeeklyReport weekly() { return service.report(); }
}
