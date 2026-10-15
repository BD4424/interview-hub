package com.prephub.importer;

import com.prephub.common.Enums.AttributionMode;

import java.util.List;

/**
 * Precedence rules (spec 8.C):
 *  NONE           -> never attribute, even if the file has a company column.
 *  SINGLE_COMPANY -> explicit row value overrides; otherwise the file default.
 *  PER_ROW        -> row value; otherwise the file default if one was chosen; otherwise unknown.
 * Never infers companies from question text.
 */
public final class CompanyAttributionResolver {
    private CompanyAttributionResolver() {}

    public static List<String> resolve(AttributionMode mode, String defaultCompanyName, List<String> rowCompanies) {
        if (mode == null || mode == AttributionMode.NONE) return List.of();
        if (rowCompanies != null && !rowCompanies.isEmpty()) return List.copyOf(rowCompanies);
        if (defaultCompanyName != null && !defaultCompanyName.isBlank()) return List.of(defaultCompanyName);
        return List.of();
    }

    public static String describe(AttributionMode mode, String defaultCompanyName) {
        return switch (mode == null ? AttributionMode.NONE : mode) {
            case NONE -> "No company attribution: company columns are ignored; companies stay unknown.";
            case SINGLE_COMPANY -> "All approved questions are attributed to '" + defaultCompanyName
                    + "' unless a row explicitly lists companies.";
            case PER_ROW -> "Companies come from each row's company column (';'-separated)"
                    + (defaultCompanyName != null ? "; rows without a value fall back to '" + defaultCompanyName + "'." : "; rows without a value stay unknown.");
        };
    }
}
