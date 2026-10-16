package com.prephub.settings;

import com.prephub.ai.AiTextClient;
import com.prephub.importer.parser.ParserRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only runtime configuration for the Settings page. Never returns secrets. */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final AiTextClient ai;
    private final ParserRegistry parsers;
    private final DataSize maxUpload;

    public SettingsController(AiTextClient ai, ParserRegistry parsers,
                              @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxUpload) {
        this.ai = ai; this.parsers = parsers; this.maxUpload = maxUpload;
    }

    public record AiSettings(boolean enabled, String provider, String model, String status) {}
    public record ImportSettings(List<String> formats, long maxUploadMb, String companyDelimiter, String tagDelimiter) {}
    public record Settings(AiSettings ai, ImportSettings imports) {}

    @GetMapping
    public Settings get() {
        return new Settings(new AiSettings(ai.available(), ai.provider(), ai.model(), ai.status()),
                new ImportSettings(List.copyOf(parsers.supportedExtensions()), maxUpload.toMegabytes(), ";", ","));
    }
}
