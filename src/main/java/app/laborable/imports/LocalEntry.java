package app.laborable.imports;

import java.time.LocalDate;

/** One municipal holiday as read from any source, before validation. */
public record LocalEntry(String ine, String municipalityName, LocalDate date, String name) {}
