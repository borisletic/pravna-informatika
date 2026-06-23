# app/libs/

Mesto za JAR fajlove koji nisu na Maven Central:
- ~~`dr-device.jar` - **Član 1**, za Celinu 5~~ — **NIJE potreban**. dr-device nije
  Java biblioteka nego CLIPS sistem; cela distribucija je u `dr-device/` u korenu
  repozitorijuma i poziva se kao spoljni proces (vidi `DrDeviceReasoner` i
  `data/rules/README.md`). Prevod LegalRuleML→RuleML→CLIPS radi Saxon-HE (Maven).
- `jcolibri2.jar` (i tranzitivne) - **Član 2**, za Celinu 6

Ovi JAR-ovi se referenciraju kao `system` scope u `pom.xml`.

**Ne pushovati JAR-ove direktno u git** (mogu biti veliki).
Umesto toga - dokumentovati u ovom README-u odakle ih je svako preuzeo,
ili koristiti `mvn install:install-file` u lokalnu .m2.
