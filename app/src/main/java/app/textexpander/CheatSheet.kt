package app.textexpander

/** Built-in C reference. Becomes the default content of the "cheat" list (fully editable in the app). */
object CheatSheet {
    val entries: List<Pair<String, String>> = listOf(
        "Grundgerüst" to """
#include <stdio.h>

int main(void) {
    // dein Code
    return 0;
}""",

        "printf – Formatangaben" to """
%d    int             printf("%d\n", x);
%u    unsigned int
%ld   long
%f    double/float    printf("%.2f\n", d);  // 2 Nachkommastellen
%c    char
%s    String (char*)
%p    Adresse         printf("%p\n", (void*)ptr);
%x    Hexadezimal
%%    Prozentzeichen
%5d   Breite 5, rechtsbündig
\n neue Zeile    \t Tabulator""",

        "scanf – Eingabe" to """
int x;       scanf("%d", &x);     // & nicht vergessen!
double d;    scanf("%lf", &d);    // double einlesen = %lf
char c;      scanf(" %c", &c);    // Leerzeichen vor %c überspringt '\n'
char s[50];  scanf("%49s", s);    // KEIN & bei Arrays, stoppt bei Leerzeichen

// Rückgabewert prüfen:
if (scanf("%d", &x) != 1) {
    printf("Ungültige Eingabe\n");
}""",

        "Eingabepuffer leeren" to """
int ch;
while ((ch = getchar()) != '\n' && ch != EOF) { }""",

        "if / else if / else" to """
if (x > 0) {
    printf("positiv\n");
} else if (x < 0) {
    printf("negativ\n");
} else {
    printf("null\n");
}

// Vergleich ==   Zuweisung =
// und &&   oder ||   nicht !
int max = (a > b) ? a : b;   // Kurzform""",

        "switch" to """
switch (wahl) {
    case 1:
        printf("Eins\n");
        break;           // break nicht vergessen!
    case 2:
    case 3:
        printf("Zwei oder drei\n");
        break;
    default:
        printf("Sonstiges\n");
}""",

        "Schleifen" to """
for (int i = 0; i < n; i++) {   // n Durchläufe: 0 … n-1
    printf("%d\n", i);
}

while (x > 0) {                 // prüft vorher
    x--;
}

do {                            // läuft mindestens 1-mal
    scanf("%d", &x);
} while (x < 0);

break;      // Schleife sofort verlassen
continue;   // zum nächsten Durchlauf""",

        "Arrays" to """
int a[5];                       // a[0] … a[4]
int b[] = {3, 1, 4, 1, 5};      // Größe automatisch
int n = sizeof(b) / sizeof(b[0]);   // Anzahl Elemente (nur im selben Scope!)

for (int i = 0; i < n; i++) {
    printf("%d ", b[i]);
}

int m[3][4];                    // 3 Zeilen, 4 Spalten
m[1][2] = 7;

// An Funktionen immer die Größe mitgeben:
void ausgeben(int arr[], int n);""",

        "Strings" to """
#include <string.h>

char name[20] = "Max";          // endet mit '\0'
strlen(name);                    // Länge ohne '\0' → 3
strcpy(ziel, quelle);            // kopieren (ziel = quelle geht NICHT)
strcat(ziel, quelle);            // anhängen
strcmp(a, b) == 0                // gleich? (a == b vergleicht Adressen!)

// Ganze Zeile mit Leerzeichen einlesen:
fgets(name, sizeof(name), stdin);
name[strcspn(name, "\n")] = '\0';   // '\n' entfernen""",

        "Pointer" to """
int x = 10;
int *p = &x;      // p speichert die Adresse von x
*p = 20;          // ändert x über den Pointer → x == 20

printf("%d\n", *p);         // Wert: 20
printf("%p\n", (void*)p);   // Adresse

int a[3] = {1, 2, 3};
int *q = a;                 // Array-Name = Adresse von a[0]
*(q + 1) == a[1];           // Pointer-Arithmetik

int *leer = NULL;           // vor dem Benutzen auf NULL prüfen""",

        "Funktionen & Call by Reference" to """
int summe(int a, int b);        // Prototyp (wenn Funktion nach main steht)

int summe(int a, int b) {
    return a + b;
}

void tausche(int *a, int *b) {  // ändert die Originale
    int tmp = *a;
    *a = *b;
    *b = tmp;
}

tausche(&x, &y);                // Adressen übergeben""",

        "malloc / free" to """
#include <stdlib.h>

int n = 10;
int *arr = malloc(n * sizeof(int));
if (arr == NULL) {
    return 1;           // kein Speicher
}
arr[0] = 42;
free(arr);              // immer freigeben
arr = NULL;""",

        "struct" to """
typedef struct {
    char name[30];
    int alter;
} Person;

Person p = {"Anna", 21};
printf("%s\n", p.name);     // Zugriff mit .

Person *pp = &p;
pp->alter = 22;             // über Pointer mit ->""",

        "Dateien" to """
FILE *f = fopen("daten.txt", "w");   // "w" schreiben, "a" anhängen
if (f == NULL) { perror("fopen"); return 1; }
fprintf(f, "Wert: %d\n", x);
fclose(f);

// Zeilenweise lesen:
char zeile[100];
FILE *in = fopen("daten.txt", "r");
if (in != NULL) {
    while (fgets(zeile, sizeof(zeile), in) != NULL) {
        printf("%s", zeile);
    }
    fclose(in);
}""",

        "Operatoren" to """
%     Rest:  7 % 3 == 1    (gerade: x % 2 == 0)
++x   erst erhöhen, dann verwenden
x++   erst verwenden, dann erhöhen
+=  -=  *=  /=   Kurzschreibweise
5 / 2     == 2     Ganzzahldivision!
5 / 2.0   == 2.5
(double)a / b      Cast für Kommaergebnis""",

        "Typische Fehler" to """
if (x = 5)           →  if (x == 5)
if (x > 0);          →  kein ; nach if / for / while
scanf("%d", x)       →  scanf("%d", &x)
i <= n bei Arrays    →  i < n
char c = "a";        →  char c = 'a';   ("…" ist ein String)
Variable ohne Wert   →  int summe = 0;
Zufallszahl 1–6:     srand(time(NULL));  int r = rand() % 6 + 1;
                     // braucht <stdlib.h> und <time.h>""",
    ).map { (t, c) -> t to c.trimStart('\n') }
}
