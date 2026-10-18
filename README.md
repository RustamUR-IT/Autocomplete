# FlowKit – Android (Nothing Phone 3a)

Eine App, zehn Werkzeuge: Text-Kürzel, Smart Clipboard, Quick Launcher, Study Mode, Workout Mode,
KI-Text-Aktionen, Brain Dump, C-Spickzettel, Habit Friction und dynamische Snippets.
Funktioniert mit jeder Tastatur. Keine Internet-Berechtigung – alles bleibt auf dem Handy.

## 1. APK bauen

**Über GitHub (nichts installieren):**
1. Auf github.com ein neues **privates** Repository anlegen.
2. „uploading an existing file“ → den **Inhalt** dieses Ordners hineinziehen (inkl. `.github`, `gradle`, `gradlew`).
   Tipp: Versteckte Ordner zeigt der Windows-Explorer unter *Ansicht → Ausgeblendete Elemente*.
3. Tab **Actions** → Lauf „Build APK“ abwarten (~4 min) → unten **FlowKit-apk** herunterladen → entpacken → `app-release.apk`.

**Mit Android Studio:** Ordner öffnen → *Build → Build App Bundle(s)/APK(s) → Build APK(s)*.

## 2. Installieren & einrichten

1. `app-release.apk` aufs Handy kopieren, öffnen, „Unbekannte Apps installieren“ erlauben.
2. FlowKit öffnen und die Einrichtung oben abarbeiten:
   - **Bedienungshilfe einschalten** (für Kürzel, Launcher, Habit Friction).
     Ausgegraut? *Einstellungen → Apps → FlowKit → ⋮ → Eingeschränkte Einstellungen zulassen*, dann nochmal.
   - **„Nicht stören“-Zugriff** erlauben (für Study/Workout Mode).
   - **Benachrichtigungen** erlauben (Timer).
3. Schnelleinstellungen runterziehen → Stift → Kacheln **Brain Dump** und **Study Mode** hinzufügen.

## 3. Was wo ist

| Funktion | So benutzt du sie |
|---|---|
| Text-Kürzel | `;mail` `;adr` `;ty` `;lg` `;mfg` `;hallo` `;krank` `;main` `;pf` `;sf` `;for` … in jeder App tippen |
| Dynamische Snippets | `;date` `;today` `;now` `;week` `;iso` `;morgen` – Platzhalter: `{date} {isodate} {time} {weekday} {week} {tomorrow} {cursor}` |
| Smart Clipboard | Kategorien Texte / Links / Uni / Code. Antippen = kopieren. Text markieren → „In Clipboard speichern“ oder aus jeder App teilen |
| Quick Launcher | `;fh` `;moodle` `;gym` `;notiz` `;lernen` `;workout` `;cheat` `;clip` – Ziel: Webseite, App oder Aktion |
| Study Mode | DND + 50-min-Timer + öffnet deine Lern-Apps. Über Startseite, Kachel oder `;lernen` |
| Workout Mode | Musik (Play-Taste) + 60-min-Timer + Satzpausen-Timer mit Vibration, DND, Display bleibt an |
| Text-Aktionen | Text markieren → „Erklären“, „Zusammenfassen“, „Übersetzen“, „Als Lernfrage“ → geht an Copilot (änderbar: ChatGPT, Claude, Gemini) |
| Brain Dump | Kachel antippen → tippen → speichern |
| C-Spickzettel | scanf, printf, Arrays, Pointer, Strings, Schleifen, struct, malloc, Dateien, typische Fehler – erweiterbar |
| Habit Friction | Instagram, TikTok, Snapchat, X, Reddit zeigen erst 10 s Wartezeit + Alternativen, danach 5 min frei |

**Zuerst eintragen:** Ziele für `;fh`, `;moodle`, `;gym`, deine Mail bei `;mail`, Adresse bei `;adr`, Lern-Apps im Study Mode.

## Grenzen

- Mehrere Lern-Apps öffnen nacheinander; vorne liegt die letzte, die anderen sind unter „Letzte Apps“.
- Text-Aktionen: Der Prompt wird an die Assistenten-App geschickt **und** kopiert – falls das Eingabefeld leer bleibt, einfach einfügen.
- Die Startbildschirm-Anordnung kann eine App nicht ändern: Social Media selbst in einen Ordner auf die letzte Seite legen.
