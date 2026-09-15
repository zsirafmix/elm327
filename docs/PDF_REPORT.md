# PDF report

Generated only from an existing **live** `DiagnosticSession` (after Connect + diagnostic).  
Filename: `OBD_Report_{VIN}_{DATE}.pdf` (`UNKNOWN` if VIN missing).

No “generate sample PDF from demo” path.

## v1.2.0 záró oldalak

- **AI összefoglaló (érthetően)** — plain language HU (+ optional details)
- **Tanácsok / ajánlások** — practical advice list from `AiExplanation.advice` / score-based fallback
- Files saved under app `filesDir/reports/`
