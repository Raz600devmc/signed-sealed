package ru.realdocs.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CountryPresets {
   private static final String[] EN = new String[]{
      "SURNAME",
      "GIVEN NAMES",
      "NATIONALITY",
      "DATE OF BIRTH",
      "SEX",
      "PLACE OF BIRTH",
      "DATE OF ISSUE",
      "DATE OF EXPIRY",
      "AUTHORITY",
      "PASSPORT No.",
      "SIGNATURE"
   };
   private static final int BURGUNDY = 7019050;
   private static final Map<String, CountryPresets.Country> ALL = new LinkedHashMap<>();
   private static final Map<String, String> NAMES = Map.ofEntries(
      Map.entry("de", "Германия"),
      Map.entry("fr", "Франция"),
      Map.entry("it", "Италия"),
      Map.entry("es", "Испания"),
      Map.entry("pl", "Польша"),
      Map.entry("gb", "Великобритания"),
      Map.entry("nl", "Нидерланды"),
      Map.entry("se", "Швеция"),
      Map.entry("no", "Норвегия"),
      Map.entry("fi", "Финляндия"),
      Map.entry("ua", "Украина"),
      Map.entry("cz", "Чехия"),
      Map.entry("gr", "Греция"),
      Map.entry("pt", "Португалия"),
      Map.entry("ch", "Швейцария"),
      Map.entry("at", "Австрия"),
      Map.entry("tr", "Турция")
   );

   private CountryPresets() {
   }

   private static void add(CountryPresets.Country c) {
      ALL.put(c.key, c);
   }

   public static String countryName(String key) {
      return NAMES.getOrDefault(key, key);
   }

   public static String[] keys() {
      return ALL.keySet().toArray(new String[0]);
   }

   public static PassportTemplate build(String key) {
      CountryPresets.Country c = ALL.get(key);
      if (c == null) {
         return null;
      } else {
         String[] l = c.labels;
         String[] lab = new String[EN.length];

         for (int i = 0; i < EN.length; i++) {
            lab[i] = l == null ? EN[i] : l[i] + " / " + EN[i];
         }

         PassportTemplate t = new PassportTemplate();
         t.name = c.nameRu;
         t.title = c.title;
         t.org = c.sub;
         t.subtitle = "";
         t.bg = c.bg;
         t.accent = c.accent;
         t.pattern = 1;
         t.coverColor = c.cover;
         t.coverTitle = c.coverTitle;
         t.coverText = c.coverCountry;
         t.emblemCode = 1;
         t.flag = c.flag;
         t.serialPrefix = c.prefix;
         t.visaTitle = c.visa + (c.visa.equals("VISAS") ? "" : " · VISAS");
         t.visaSpreads = 3;
         t.fields.add(f("photo", "", 2, 34, 100, 150, 196, true, ""));
         t.fields.add(f("serial", lab[9], 3, 34, 306, 150, 34, true, ""));
         t.fields.add(f("sign", lab[10], 4, 34, 348, 150, 60, true, ""));
         t.fields.add(f("surname", lab[0], 0, 338, 96, 282, 36, true, ""));
         t.fields.add(f("name", lab[1], 0, 338, 138, 282, 36, true, ""));
         t.fields.add(f("nationality", lab[2], 0, 338, 180, 150, 36, true, c.nationality));
         t.fields.add(f("sex", lab[4], 0, 496, 180, 124, 36, true, "M / F"));
         t.fields.add(f("birth", lab[3], 1, 338, 222, 150, 36, true, ""));
         t.fields.add(f("birthPlace", lab[5], 0, 496, 222, 124, 36, true, ""));
         t.fields.add(f("issued", lab[6], 5, 338, 264, 150, 36, true, ""));
         t.fields.add(f("expiry", lab[7], 6, 496, 264, 124, 36, true, ""));
         t.fields.add(f("authority", lab[8], 0, 338, 306, 282, 36, false, c.authority));

         for (PassportTemplate.Field x : t.fields) {
            if (x.type == 0 || x.type == 1) {
               x.size = 14;
            }
         }

         return t;
      }
   }

   private static PassportTemplate.Field f(String key, String label, int type, int x, int y, int w, int h, boolean required, String hint) {
      PassportTemplate.Field f = new PassportTemplate.Field();
      f.key = key;
      f.label = label;
      f.type = type;
      f.x = x;
      f.y = y;
      f.w = w;
      f.h = h;
      f.required = required;
      f.hint = hint;
      f.size = 15;
      return f;
   }

   static {
      add(
         new CountryPresets.Country(
            "de",
            "Паспорт Германии",
            "BUNDESREPUBLIK\nDEUTSCHLAND",
            "REISEPASS",
            "BUNDESREPUBLIK DEUTSCHLAND",
            "REISEPASS · PASSPORT",
            "VISA",
            7019050,
            15854048,
            7217712,
            "h:000000,DD0000,FFCE00",
            "C",
            new String[]{
               "NAME",
               "VORNAMEN",
               "STAATSANGEHÖRIGKEIT",
               "GEBURTSTAG",
               "GESCHLECHT",
               "GEBURTSORT",
               "AUSSTELLUNGSDATUM",
               "GÜLTIG BIS",
               "BEHÖRDE",
               "PASS-NR.",
               "UNTERSCHRIFT"
            },
            "DEUTSCH",
            "STADT"
         )
      );
      add(
         new CountryPresets.Country(
            "fr",
            "Паспорт Франции",
            "RÉPUBLIQUE\nFRANÇAISE",
            "PASSEPORT",
            "RÉPUBLIQUE FRANÇAISE",
            "PASSEPORT · PASSPORT",
            "VISAS",
            7019050,
            15659254,
            2046611,
            "v:0055A4,FFFFFF,EF4135",
            "FR",
            new String[]{
               "NOM",
               "PRÉNOMS",
               "NATIONALITÉ",
               "DATE DE NAISSANCE",
               "SEXE",
               "LIEU DE NAISSANCE",
               "DATE DE DÉLIVRANCE",
               "DATE D'EXPIRATION",
               "AUTORITÉ",
               "PASSEPORT N°",
               "SIGNATURE"
            },
            "FRANÇAISE",
            "PRÉFECTURE"
         )
      );
      add(
         new CountryPresets.Country(
            "it",
            "Паспорт Италии",
            "REPUBBLICA\nITALIANA",
            "PASSAPORTO",
            "REPUBBLICA ITALIANA",
            "PASSAPORTO · PASSPORT",
            "VISTI",
            7019050,
            15660012,
            1990198,
            "v:009246,FFFFFF,CE2B37",
            "Y",
            new String[]{
               "COGNOME",
               "NOME",
               "CITTADINANZA",
               "DATA DI NASCITA",
               "SESSO",
               "LUOGO DI NASCITA",
               "DATA DI RILASCIO",
               "DATA DI SCADENZA",
               "AUTORITÀ",
               "PASSAPORTO N.",
               "FIRMA"
            },
            "ITALIANA",
            "QUESTURA"
         )
      );
      add(
         new CountryPresets.Country(
            "es",
            "Паспорт Испании",
            "REINO\nDE ESPAÑA",
            "PASAPORTE",
            "REINO DE ESPAÑA",
            "PASAPORTE · PASSPORT",
            "VISADOS",
            7019050,
            16116701,
            9050907,
            "h:AA151B,F1BF00,F1BF00,AA151B",
            "P",
            new String[]{
               "APELLIDOS",
               "NOMBRE",
               "NACIONALIDAD",
               "FECHA DE NACIMIENTO",
               "SEXO",
               "LUGAR DE NACIMIENTO",
               "FECHA DE EXPEDICIÓN",
               "FECHA DE CADUCIDAD",
               "AUTORIDAD",
               "PASAPORTE N.º",
               "FIRMA"
            },
            "ESPAÑOLA",
            "DIRECCIÓN GENERAL"
         )
      );
      add(
         new CountryPresets.Country(
            "pl",
            "Паспорт Польши",
            "RZECZPOSPOLITA\nPOLSKA",
            "PASZPORT",
            "RZECZPOSPOLITA POLSKA",
            "PASZPORT · PASSPORT",
            "WIZY",
            7019050,
            16051436,
            10562084,
            "h:FFFFFF,DC143C",
            "PL",
            new String[]{
               "NAZWISKO",
               "IMIONA",
               "OBYWATELSTWO",
               "DATA URODZENIA",
               "PŁEĆ",
               "MIEJSCE URODZENIA",
               "DATA WYDANIA",
               "DATA WAŻNOŚCI",
               "ORGAN WYDAJĄCY",
               "NR PASZPORTU",
               "PODPIS"
            },
            "POLSKIE",
            "WOJEWODA"
         )
      );
      add(
         new CountryPresets.Country(
            "gb",
            "Паспорт Великобритании",
            "UNITED KINGDOM\nOF GREAT BRITAIN\nAND NORTHERN IRELAND",
            "PASSPORT",
            "UNITED KINGDOM",
            "PASSPORT",
            "VISAS",
            1845313,
            15657955,
            1846874,
            "cross:012169,FFFFFF,C8102E",
            "GB",
            null,
            "BRITISH CITIZEN",
            "HM PASSPORT OFFICE"
         )
      );
      add(
         new CountryPresets.Country(
            "nl",
            "Паспорт Нидерландов",
            "KONINKRIJK\nDER NEDERLANDEN",
            "PASPOORT",
            "KONINKRIJK DER NEDERLANDEN",
            "PASPOORT · PASSPORT",
            "VISA",
            7019050,
            16051942,
            10239248,
            "h:AE1C28,FFFFFF,21468B",
            "N",
            new String[]{
               "NAAM",
               "VOORNAMEN",
               "NATIONALITEIT",
               "GEBOORTEDATUM",
               "GESLACHT",
               "GEBOORTEPLAATS",
               "DATUM VAN AFGIFTE",
               "GELDIG TOT",
               "INSTANTIE",
               "PASPOORTNR.",
               "HANDTEKENING"
            },
            "NEDERLANDSE",
            "BURG. VAN"
         )
      );
      add(
         new CountryPresets.Country(
            "se",
            "Паспорт Швеции",
            "SVERIGE",
            "PASS",
            "SVERIGE · SWEDEN",
            "PASS · PASSPORT",
            "VISUM",
            7019050,
            15659767,
            2051983,
            "nordic:006AA7,FECC00",
            "S",
            new String[]{
               "EFTERNAMN",
               "FÖRNAMN",
               "NATIONALITET",
               "FÖDELSEDATUM",
               "KÖN",
               "FÖDELSEORT",
               "UTFÄRDANDEDATUM",
               "GILTIG TILL",
               "MYNDIGHET",
               "PASSNUMMER",
               "NAMNTECKNING"
            },
            "SVENSK",
            "POLISEN"
         )
      );
      add(
         new CountryPresets.Country(
            "no",
            "Паспорт Норвегии",
            "NORGE",
            "PASS",
            "NORGE · NORWAY",
            "PASS · PASSPORT",
            "VISUM",
            10361650,
            15985900,
            9313067,
            "nordic:BA0C2F,FFFFFF,00205B",
            "N",
            new String[]{
               "ETTERNAVN",
               "FORNAVN",
               "NASJONALITET",
               "FØDSELSDATO",
               "KJØNN",
               "FØDESTED",
               "UTSTEDELSESDATO",
               "UTLØPSDATO",
               "MYNDIGHET",
               "PASSNR.",
               "UNDERSKRIFT"
            },
            "NORSK",
            "POLITIET"
         )
      );
      add(
         new CountryPresets.Country(
            "fi",
            "Паспорт Финляндии",
            "SUOMI\nFINLAND",
            "PASSI · PASS",
            "SUOMI · FINLAND",
            "PASSI · PASS · PASSPORT",
            "VIISUMIT",
            7019050,
            15791096,
            2574986,
            "nordic:FFFFFF,002F6C",
            "FP",
            new String[]{
               "SUKUNIMI",
               "ETUNIMET",
               "KANSALAISUUS",
               "SYNTYMÄAIKA",
               "SUKUPUOLI",
               "SYNTYMÄPAIKKA",
               "MYÖNTÄMISPÄIVÄ",
               "VOIMASSA",
               "VIRANOMAINEN",
               "PASSIN NRO",
               "ALLEKIRJOITUS"
            },
            "SUOMI",
            "POLIISI"
         )
      );
      add(
         new CountryPresets.Country(
            "ua",
            "Паспорт Украины",
            "УКРАЇНА\nUKRAINE",
            "ПАСПОРТ",
            "УКРАЇНА · UKRAINE",
            "ПАСПОРТ · PASSPORT",
            "ВІЗИ",
            1780555,
            16052704,
            2051739,
            "h:0057B7,FFD700",
            "FA",
            new String[]{
               "ПРІЗВИЩЕ",
               "ІМ'Я",
               "ГРОМАДЯНСТВО",
               "ДАТА НАРОДЖЕННЯ",
               "СТАТЬ",
               "МІСЦЕ НАРОДЖЕННЯ",
               "ДАТА ВИДАЧІ",
               "ДІЙСНИЙ ДО",
               "ОРГАН, ЩО ВИДАВ",
               "НОМЕР ПАСПОРТА",
               "ПІДПИС"
            },
            "УКРАЇНА",
            "ОРГАН"
         )
      );
      add(
         new CountryPresets.Country(
            "cz",
            "Паспорт Чехии",
            "ČESKÁ\nREPUBLIKA",
            "CESTOVNÍ PAS",
            "ČESKÁ REPUBLIKA",
            "CESTOVNÍ PAS · PASSPORT",
            "VÍZA",
            7019050,
            15725045,
            2771583,
            "czech:FFFFFF,D7141A,11457E",
            "CZ",
            new String[]{
               "PŘÍJMENÍ",
               "JMÉNO",
               "STÁTNÍ OBČANSTVÍ",
               "DATUM NAROZENÍ",
               "POHLAVÍ",
               "MÍSTO NAROZENÍ",
               "DATUM VYDÁNÍ",
               "PLATNOST DO",
               "ÚŘAD",
               "ČÍSLO PASU",
               "PODPIS"
            },
            "ČESKÁ REPUBLIKA",
            "MAGISTRÁT"
         )
      );
      add(
         new CountryPresets.Country(
            "gr",
            "Паспорт Греции",
            "ΕΛΛΗΝΙΚΗ\nΔΗΜΟΚΡΑΤΙΑ",
            "ΔΙΑΒΑΤΗΡΙΟ",
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ",
            "ΔΙΑΒΑΤΗΡΙΟ · PASSPORT",
            "ΘΕΩΡΗΣΕΙΣ",
            7019050,
            15660281,
            1727134,
            "greek:0D5EAF,FFFFFF",
            "AE",
            new String[]{
               "ΕΠΩΝΥΜΟ", "ΟΝΟΜΑ", "ΙΘΑΓΕΝΕΙΑ", "ΗΜ. ΓΕΝΝΗΣΗΣ", "ΦΥΛΟ", "ΤΟΠΟΣ ΓΕΝΝΗΣΗΣ", "ΗΜ. ΕΚΔΟΣΗΣ", "ΛΗΞΗ", "ΑΡΧΗ", "ΑΡ. ΔΙΑΒΑΤΗΡΙΟΥ", "ΥΠΟΓΡΑΦΗ"
            },
            "ΕΛΛΗΝΙΚΗ",
            "ΑΡΧΗ"
         )
      );
      add(
         new CountryPresets.Country(
            "pt",
            "Паспорт Португалии",
            "REPÚBLICA\nPORTUGUESA",
            "PASSAPORTE",
            "REPÚBLICA PORTUGUESA",
            "PASSAPORTE · PASSPORT",
            "VISTOS",
            7019050,
            15855844,
            3042094,
            "v:006600,FF0000,FF0000",
            "C",
            new String[]{
               "APELIDO",
               "NOME PRÓPRIO",
               "NACIONALIDADE",
               "DATA DE NASCIMENTO",
               "SEXO",
               "LOCAL DE NASCIMENTO",
               "DATA DE EMISSÃO",
               "VÁLIDO ATÉ",
               "AUTORIDADE",
               "PASSAPORTE N.º",
               "ASSINATURA"
            },
            "PORTUGUESA",
            "SEF"
         )
      );
      add(
         new CountryPresets.Country(
            "ch",
            "Паспорт Швейцарии",
            "SCHWEIZ · SUISSE\nSVIZZERA · SVIZRA",
            "PASS · PASSEPORT",
            "SCHWEIZ · SUISSE · SVIZZERA",
            "PASS · PASSEPORT · PASSAPORTO",
            "VISA",
            13111342,
            16183534,
            10492956,
            "swiss:D52B1E,FFFFFF",
            "X",
            new String[]{
               "NAME",
               "VORNAMEN",
               "STAATSANGEHÖRIGKEIT",
               "GEBURTSDATUM",
               "GESCHLECHT",
               "HEIMATORT",
               "AUSSTELLUNGSDATUM",
               "GÜLTIG BIS",
               "BEHÖRDE",
               "PASS-NR.",
               "UNTERSCHRIFT"
            },
            "SCHWEIZ",
            "FEDPOL"
         )
      );
      add(
         new CountryPresets.Country(
            "at",
            "Паспорт Австрии",
            "REPUBLIK\nÖSTERREICH",
            "REISEPASS",
            "REPUBLIK ÖSTERREICH",
            "REISEPASS · PASSPORT",
            "VISA",
            7019050,
            16051950,
            10165285,
            "h:ED2939,FFFFFF,ED2939",
            "P",
            new String[]{
               "FAMILIENNAME",
               "VORNAMEN",
               "STAATSANGEHÖRIGKEIT",
               "GEBURTSDATUM",
               "GESCHLECHT",
               "GEBURTSORT",
               "AUSSTELLUNGSDATUM",
               "GÜLTIG BIS",
               "BEHÖRDE",
               "REISEPASS-NR.",
               "UNTERSCHRIFT"
            },
            "ÖSTERREICH",
            "BH"
         )
      );
      add(
         new CountryPresets.Country(
            "tr",
            "Паспорт Турции",
            "TÜRKİYE\nCUMHURİYETİ",
            "PASAPORT",
            "TÜRKİYE CUMHURİYETİ",
            "PASAPORT · PASSPORT",
            "VİZELER",
            7347244,
            15986150,
            9313322,
            "crescent:E30A17,FFFFFF",
            "U",
            new String[]{
               "SOYADI", "ADI", "UYRUĞU", "DOĞUM TARİHİ", "CİNSİYETİ", "DOĞUM YERİ", "VERİLİŞ TARİHİ", "GEÇERLİLİK TARİHİ", "MAKAM", "PASAPORT NO", "İMZA"
            },
            "T.C.",
            "İL NÜFUS"
         )
      );
   }

   static record Country(
      String key,
      String nameRu,
      String coverCountry,
      String coverTitle,
      String title,
      String sub,
      String visa,
      int cover,
      int bg,
      int accent,
      String flag,
      String prefix,
      String[] labels,
      String nationality,
      String authority
   ) {
   }
}
