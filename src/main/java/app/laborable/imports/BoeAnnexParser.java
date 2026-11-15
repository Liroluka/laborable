package app.laborable.imports;

import app.laborable.domain.Model.HolidayKind;
import app.laborable.domain.Model.NewHoliday;
import app.laborable.domain.Model.Scope;
import app.laborable.persistence.MunicipalityRepository;
import java.io.IOException;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Parses the annex of the yearly BOE resolution "relación de fiestas laborales". The annex is a
 * table: one row per holiday ("12 Fiesta Nacional de España."), one column per autonomous
 * community, with the marks {@code *} (national, not replaceable), {@code **} (national, not
 * replaced) and {@code ***} (own holiday of the community). Month names are separator rows.
 */
@Component
public class BoeAnnexParser {

  public record Parsed(
      int year,
      List<NewHoliday> holidays,
      Set<String> regions,
      List<String> notes,
      List<String> warnings) {}

  private static final int MAX_DATES_PER_REGION = 14;
  private static final Pattern YEAR = Pattern.compile("(\\d{4})");
  private static final Pattern DAY_AND_NAME =
      Pattern.compile("^(\\d{1,2})[^\\p{L}\\d]*(\\p{L}.*)$");
  private static final Pattern MARK = Pattern.compile("^\\*{1,3}$");
  private static final Pattern NOTE =
      Pattern.compile("^\\d\\.\\s+En la Comunidad.*", Pattern.DOTALL);
  private static final List<String> MONTHS =
      List.of(
          "enero",
          "febrero",
          "marzo",
          "abril",
          "mayo",
          "junio",
          "julio",
          "agosto",
          "septiembre",
          "octubre",
          "noviembre",
          "diciembre");

  /** Ordered: the first fragment contained in the normalized header wins. */
  private static final Map<String, String> REGION_BY_HEADER = new LinkedHashMap<>();

  static {
    REGION_BY_HEADER.put("andalucia", "AN");
    REGION_BY_HEADER.put("aragon", "AR");
    REGION_BY_HEADER.put("asturias", "AS");
    REGION_BY_HEADER.put("balears", "IB");
    REGION_BY_HEADER.put("canarias", "CN");
    REGION_BY_HEADER.put("cantabria", "CB");
    REGION_BY_HEADER.put("castilla la mancha", "CM");
    REGION_BY_HEADER.put("castilla y leon", "CL");
    REGION_BY_HEADER.put("cataluna", "CT");
    REGION_BY_HEADER.put("extremadura", "EX");
    REGION_BY_HEADER.put("galicia", "GA");
    REGION_BY_HEADER.put("madrid", "MD");
    REGION_BY_HEADER.put("murcia", "MC");
    REGION_BY_HEADER.put("navarra", "NC");
    REGION_BY_HEADER.put("pais vasco", "PV");
    REGION_BY_HEADER.put("rioja", "RI");
    REGION_BY_HEADER.put("valenciana", "VC");
    REGION_BY_HEADER.put("ceuta", "CE");
    REGION_BY_HEADER.put("melilla", "ML");
  }

  public Parsed parse(String xml) {
    Document doc = read(xml);
    int year = annexYear(doc);
    Element table = firstTable(doc);
    List<Element> rows = descendants(table, "tr");

    List<String> columns = null;
    int month = 0;
    Map<String, Set<LocalDate>> datesByRegion = new LinkedHashMap<>();
    List<NewHoliday> holidays = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (Element row : rows) {
      List<String> cells = cellTexts(row);
      if (columns == null) {
        if (cells.size() >= 19
            && cells.stream().anyMatch(c -> normalize(c).contains("andalucia"))) {
          columns = regionColumns(cells);
          columns.forEach(r -> datesByRegion.put(r, new LinkedHashSet<>()));
        }
        continue;
      }
      if (cells.size() != columns.size() + 1) {
        throw new IllegalStateException(
            "BOE annex row has " + cells.size() + " cells, expected " + (columns.size() + 1));
      }
      String label = cells.get(0);
      int monthIndex = MONTHS.indexOf(normalize(label));
      if (monthIndex >= 0) {
        month = monthIndex + 1;
        continue;
      }
      Matcher m = DAY_AND_NAME.matcher(label);
      if (!m.matches()) {
        continue;
      }
      if (month == 0) {
        throw new IllegalStateException("Holiday row before any month heading: " + label);
      }
      LocalDate date = toDate(year, month, Integer.parseInt(m.group(1)), label);
      String name = m.group(2).replaceAll("\\s+", " ").replaceAll("\\.$", "").trim();
      addRow(date, name, columns, cells, holidays, datesByRegion, warnings);
    }
    if (columns == null) {
      throw new IllegalStateException("BOE annex table with community columns not found");
    }
    datesByRegion.forEach(
        (region, dates) -> {
          if (dates.size() > MAX_DATES_PER_REGION) {
            warnings.add(region + " has " + dates.size() + " holiday dates (> 14).");
          }
        });
    return new Parsed(year, holidays, Set.copyOf(columns), notes(doc), warnings);
  }

  private void addRow(
      LocalDate date,
      String name,
      List<String> columns,
      List<String> cells,
      List<NewHoliday> holidays,
      Map<String, Set<LocalDate>> datesByRegion,
      List<String> warnings) {
    Map<String, String> marks = new LinkedHashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      String mark = cells.get(i + 1).replaceAll("[\\p{Z}\\s]", "");
      if (MARK.matcher(mark).matches()) {
        marks.put(columns.get(i), mark);
      }
    }
    if (marks.isEmpty()) {
      return;
    }
    marks.keySet().forEach(r -> datesByRegion.get(r).add(date));
    Set<String> distinct = new LinkedHashSet<>(marks.values());
    boolean everywhere = marks.size() == columns.size() && distinct.size() == 1;
    String first = distinct.iterator().next();
    if (everywhere && !first.equals("***")) {
      HolidayKind kind =
          first.equals("*") ? HolidayKind.NATIONAL_FIXED : HolidayKind.NATIONAL_SUBSTITUTABLE;
      holidays.add(new NewHoliday(date, name, Scope.NATIONAL, kind, null, null));
      return;
    }
    marks.forEach(
        (region, mark) -> {
          HolidayKind kind =
              mark.equals("***") ? HolidayKind.REGIONAL : HolidayKind.NATIONAL_SUBSTITUTABLE;
          if (mark.equals("*")) {
            warnings.add(
                "'" + name + "' " + date + " is marked national but not in all communities.");
            kind = HolidayKind.NATIONAL_FIXED;
          }
          holidays.add(new NewHoliday(date, name, Scope.REGIONAL, kind, region, null));
        });
  }

  private static List<String> regionColumns(List<String> headerCells) {
    List<String> regions = new ArrayList<>();
    for (String header : headerCells) {
      String normalized = normalize(header.replaceAll("\\(\\d+\\)", ""));
      String code =
          REGION_BY_HEADER.entrySet().stream()
              .filter(e -> normalized.contains(e.getKey()))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("Unknown community column: " + header));
      regions.add(code);
    }
    if (new LinkedHashSet<>(regions).size() != regions.size()) {
      throw new IllegalStateException("Duplicate community columns in BOE annex");
    }
    return regions;
  }

  private static LocalDate toDate(int year, int month, int day, String label) {
    try {
      return LocalDate.of(year, month, day);
    } catch (DateTimeException e) {
      throw new IllegalStateException("Invalid date in BOE annex row: " + label, e);
    }
  }

  private static String normalize(String text) {
    return MunicipalityRepository.normalize(text);
  }

  private static int annexYear(Document doc) {
    for (Element p : descendants(doc.getDocumentElement(), "p")) {
      if ("anexo_tit".equals(p.getAttribute("class"))) {
        Matcher m = YEAR.matcher(p.getTextContent());
        if (m.find()) {
          return Integer.parseInt(m.group(1));
        }
      }
    }
    throw new IllegalStateException("Annex year heading not found in BOE document");
  }

  private static Element firstTable(Document doc) {
    NodeList tables = doc.getElementsByTagName("table");
    if (tables.getLength() == 0) {
      throw new IllegalStateException("No table found in BOE document");
    }
    return (Element) tables.item(0);
  }

  private static List<String> notes(Document doc) {
    List<String> notes = new ArrayList<>();
    for (Element p : descendants(doc.getDocumentElement(), "p")) {
      String text = p.getTextContent().replaceAll("[\\p{Z}\\s]+", " ").trim();
      if (NOTE.matcher(text).matches()) {
        notes.add(text);
      }
    }
    return notes;
  }

  private static List<String> cellTexts(Element row) {
    List<String> cells = new ArrayList<>();
    for (Node n = row.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && (e.getTagName().equals("td") || e.getTagName().equals("th"))) {
        cells.add(e.getTextContent().replaceAll("[\\p{Z}\\s]+", " ").trim());
      }
    }
    return cells;
  }

  private static List<Element> descendants(Element root, String tag) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = root.getElementsByTagName(tag);
    for (int i = 0; i < nodes.getLength(); i++) {
      result.add((Element) nodes.item(i));
    }
    return result;
  }

  private static Document read(String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new IllegalStateException("BOE document is not valid XML", e);
    }
  }
}
