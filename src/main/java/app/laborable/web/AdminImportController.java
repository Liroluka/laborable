package app.laborable.web;

import app.laborable.imports.ImportModels.ImportRequest;
import app.laborable.imports.ImportModels.ImportResult;
import app.laborable.imports.ImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminImportController {

  private final ImportService imports;

  public AdminImportController(ImportService imports) {
    this.imports = imports;
  }

  @PostMapping("/admin/holidays/import")
  public ImportResult importHolidays(@Valid @RequestBody ImportRequest request) {
    return imports.run(request);
  }
}
