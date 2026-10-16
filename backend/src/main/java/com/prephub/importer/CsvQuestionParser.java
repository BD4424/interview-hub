package com.prephub.importer;

import com.prephub.importer.parser.ParsedFile;
import com.prephub.importer.parser.QuestionFileParser;
import com.prephub.importer.parser.TabularMapper;
import com.prephub.importer.parser.TextDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** CSV parser: RFC 4180 via {@link CsvReader}, columns mapped by {@link TabularMapper}. */
public class CsvQuestionParser implements QuestionFileParser {
    public static final String COMPANY_DELIMITER = TabularMapper.COMPANY_DELIMITER;
    public static final String TAG_DELIMITER = TabularMapper.TAG_DELIMITER;

    @Override public String format() { return "csv"; }
    @Override public Set<String> extensions() { return Set.of("csv"); }
    @Override public boolean tabular() { return true; }

    public ParsedFile parse(byte[] bytes) { return parse(bytes, Map.of()); }

    @Override
    public ParsedFile parse(byte[] bytes, Map<String, String> mapping) {
        List<List<String>> rows = CsvReader.parse(TextDecoder.decode(bytes));
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV file is empty");
        List<TabularMapper.SourceRow> data = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) data.add(new TabularMapper.SourceRow("row " + (r + 1), rows.get(r)));
        return TabularMapper.map(rows.get(0), data, mapping);
    }

    /** Kept for backwards compatibility; delegates to {@link TabularMapper#split}. */
    public static List<String> split(String raw, String delimiter) { return TabularMapper.split(raw, delimiter); }
}
