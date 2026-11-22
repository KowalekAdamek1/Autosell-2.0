# AutoSell 2.0 (Fabric 1.21.4)

Sprzedaż rud w /sklep (anarchia.gg), Baritone, gracze w promieniu z licznikiem metrów, ESP, motywy GUI.
Klawisz: **Right Shift** (zmienisz w zakładce Ustawienia).

## Jak zbudować jara (GitHub, bez instalowania niczego)
1. Załóż konto na github.com i utwórz nowe repozytorium (New repository, może być prywatne).
2. Wgraj do niego CAŁĄ zawartość tego folderu (Add file > Upload files; przeciągnij pliki, razem z folderem `.github` i `src`).
3. Wejdź w zakładkę **Actions**. Build ruszy sam (albo kliknij "build" > "Run workflow").
4. Po ~3-5 min kliknij ukończony przebieg i na dole w **Artifacts** pobierz `autosell-jar` (zip z jarem).
5. Jar wrzuć do `.minecraft/mods` (potrzebny Fabric Loader 0.16+, Fabric API, Minecraft 1.21.4).

## Budowanie lokalnie
Potrzebny JDK 21 i Gradle 8.12+: `gradle build`, jar będzie w `build/libs/autosell-2.0.0.jar`.
