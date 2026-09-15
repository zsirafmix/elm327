# PDF report

## Generator

`PdfReportGenerator` uses Android `android.graphics.pdf.PdfDocument`.

## Filename convention

```
OBD_Report_{VIN}_{DATE}.pdf
```

- `VIN` — 17-char VIN or `UNKNOWN`
- `DATE` — `yyyyMMdd_HHmm` (device locale `US` for stable sorting)

Example: `OBD_Report_WBA3A5C50EF123456_20260915_0501.pdf`

## Sections

1. **Cover** — VIN, vehicle, date, READ ONLY notice  
2. **Vehicle & Adapter** — brand/model/platform/engine, adapter, protocol  
3. **Communication & ECU list** — address, name, category, online, DTC count  
4. **AI summary & Scores** — AI text + category percents / stars  

Each page: header (app name + section title), footer (page number + VIN + READ ONLY).

## Export flow

```mermaid
flowchart LR
  A[Generate PDF] --> B[cacheDir/reports/]
  B --> C[FileProvider URI]
  C --> D[ACTION_SEND share intent]
```

- Save path: `context.cacheDir/reports/`
- Share: `FileProvider` authority `${applicationId}.fileprovider` (`xml/file_paths.xml`)
- UI: Report screen → Generate → Share

## Unit test

`PdfMappingTest` validates filename mapping and required section manifest without rendering on JVM.
