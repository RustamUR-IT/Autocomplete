# FlowKit – Android (Nothing Phone 3a)

Ein Werkzeugkasten fürs Handy: Text-Kürzel, smarte Vorschläge pro Person, Dokumente-Tresor mit Garantien,
Fokus-Modi für FH und Gym, Kopieren per Doppeltipp und mehr. Funktioniert mit jeder Tastatur.
**Keine Internet-Berechtigung** – alles bleibt auf dem Handy.

## Installieren

**Download (immer die neueste Version):**
https://github.com/RustamUR-IT/Autocomplete/releases/download/latest/FlowKit.apk

1. Link im Handy-Browser öffnen, APK öffnen, „Unbekannte Apps installieren“ erlauben.
   Play Protect warnt → „Weitere Details“ → „Trotzdem installieren“.
2. FlowKit öffnen und die Einrichtung oben abarbeiten:
   - **Bedienungshilfe einschalten.** Ausgegraut? Einstellungen → Apps → FlowKit → ⋮ → „Eingeschränkte Einstellungen zulassen“, dann nochmal.
   - **„Nicht stören“-Zugriff** (Fokus-/Orts-Modi) und **Benachrichtigungen** (Timer, Garantie-Erinnerungen).
3. Schnelleinstellungen → Stift → Kacheln **Brain Dump**, **Study Mode**, **Text kopieren** hinzufügen.

Updates installieren sich über die alte Version, Daten bleiben erhalten.

## Funktionen

Jede Funktion lässt sich unter **Einstellungen → Funktionen** einzeln abschalten.

| Funktion | So benutzt du sie |
|---|---|
| ⌨️ Text-Kürzel | `;mail` `;adr` `;ty` `;lg` `;mfg` `;hallo` `;krank` `;main` `;pf` `;sf` `;for` … in jeder App tippen |
| 📅 Dynamische Snippets | `;date` `;today` `;now` `;week` `;iso` `;morgen` – Platzhalter `{date} {isodate} {time} {weekday} {week} {tomorrow} {cursor}` |
| 💡 Smarte Vorschläge | Lernt lokal, wie du schreibst – pro Person. Chips über dem Eingabefeld (nur bei offener Tastatur): Wort fertig, nächstes Wort, ganze Sätze |
| ↩ Antwort-Paare | Merkt sich, was du auf welche Nachricht antwortest. Schreibt sie „gute Nacht“, kommt deine übliche Antwort |
| ⭐ Lieblingsantworten | Pro Person feste Antworten, immer zuerst |
| 🕘 Tageszeit | Schnellantworten, die du meist um diese Uhrzeit schickst, stehen weiter vorne |
| 😊 Emoji pro Person | Nach einem fertigen Wort kommt das Emoji, das du dieser Person am häufigsten schickst |
| 🙈 Ausblenden | Vorschlag lange drücken = nie mehr vorschlagen · Anzahl Chips einstellbar (1–5) |
| 👆 Doppeltippen = kopieren | Doppeltippen auf Text kopiert den Abschnitt (nicht in WhatsApp & Co., dort ist es Liken). Kachel „Text kopieren“: tippen = Abschnitt, halten = alles |
| 📁 Dokumente | Tresor mit Vorschaubildern, Kategorien, Suche, „Gültig bis“-Erinnerung. Erkennt Datum, Händler und Betrag im Dateinamen, speichert nichts doppelt. Mehrfachauswahl (teilen, Kategorie, löschen). Als ZIP sichern & einlesen. „Teilen → In FlowKit-Tresor speichern“ aus jeder App |
| 🔍 Gefunden | Ordner freigeben (Download, WhatsApp Documents …) → FlowKit findet Rechnungen, Verträge, Zeugnisse, Shop-Screenshots |
| 🔧 Garantien | Produkt + Kaufdatum + Beleg (Datum & Händler werden vorbelegt); Erinnerung 30 / 7 / 1 Tage vor Ablauf |
| 📋 Smart Clipboard | Gespeicherte Texte, Links, Uni, Code. Text markieren → „In Clipboard speichern“ |
| 🚀 Quick Launcher | `;fh` `;moodle` `;gym` `;notiz` `;lernen` … öffnen Seite, App oder Aktion |
| 🎯 Fokus-Modi | Study Mode (DND, 50-min-Timer, Lern-Apps) · Workout Mode (Musik, Timer, Satzpausen) |
| 📍 Orts-Modi | WLAN als Ort speichern: Gym → Workout Mode (1 Tipp), FH → „Nicht stören“ |
| ✨ Text-Aktionen | Text markieren → Erklären / Zusammenfassen / Übersetzen / Lernfrage → an Copilot, ChatGPT, Claude oder Gemini |
| 🧠 Brain Dump | Kachel antippen → Gedanke tippen → speichern |
| 💻 C-Spickzettel | scanf, printf, Arrays, Pointer, Strings, Schleifen, struct, malloc, Dateien, typische Fehler |
| 🛑 Habit Friction | Social Media zeigt erst 10 s Wartezeit + Alternativen; optional Doomscroll-Limit |
| 💾 Backup | Einstellungen → Backup speichern / Wiederherstellen (JSON, z. B. auf Google Drive) |

## Grenzen

- Vorschläge lernen nur aus dem, was du auf dem Handy schreibst – am Anfang erscheinen bewusst wenige Chips.
- Doppeltippen funktioniert nur, wo die App das Antippen meldet; sonst die Kachel „Text kopieren“ nutzen.
- Der Tresor-Inhalt ist nicht im JSON-Backup – dafür im Tresor „Als ZIP sichern“ nutzen.
- Workout/Study Mode über Orts-Modi kommen als Benachrichtigung (Android erlaubt kein ungefragtes Öffnen).

## Changelog

| Version | Änderungen |
|---|---|
| 4.3 | Dokumente: Vorschaubilder, Mehrfachauswahl, Tresor als ZIP sichern/einlesen, Betrag aus Dateinamen · Vorschläge: Emoji pro Person, Vorschlag lange drücken = ausblenden, Anzahl Chips einstellbar |
| 4.2 | Service fängt Fehler ab statt sich abzuschalten · Chips nur bei offener Tastatur, verschwinden bei Display aus · Dokumente: Datum & Händler aus Dateinamen, keine Duplikate, Shop-Screenshots als Beleg · Schnellantworten nach Tageszeit · Fehleranzeige in den Einstellungen |
| 4.1 | Langes Drücken kopiert nicht mehr · Doppeltippen nur auf Text, Chat-Apps ausgenommen |
| 4.0 | Dokumente-Tresor, automatische Funde, Garantien mit Erinnerung |
| 3.9 | Vorschläge: keine veralteten Chips, strengere Auswahl, kein Flackern |
| 3.8 | Chips folgen dem Eingabefeld · Kopier-Diagnose |
| 3.6–3.7 | Doppeltippen / Kachel „Text kopieren“ |
| 3.5 | Antwort-Paare |
| 3.4 | Funktionen an/aus · Backup · Doomscroll-Limit |
| 3.3 | Lieblingsantworten · Orts-Modi |
| 3.0–3.2 | Smarte Vorschläge · fester Signaturschlüssel |
| 2.0 | Clipboard, Launcher, Fokus-Modi, Text-Aktionen, Brain Dump, C-Spickzettel, Habit Friction |
| 1.0 | Text-Kürzel |
