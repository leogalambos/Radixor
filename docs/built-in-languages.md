# Built-in Languages and Default Models

“Registered language” means that the repository maintains a dictionary and a
stable Java runtime mapping for that language. Packaging differs by runtime:
Java keeps the core dictionary-free and resolves individual model artifacts,
while the Java and Python `radixor-models-standard` aggregates install the same
reviewed 31 standard dictionaries.

The Java language enum carries language identity, writing direction, a legacy
resource-directory name, and the stable default model ID. A Java model
descriptor carries the independently versioned model identity and resource.
Python accepts the short alias or the same full model ID. See
[Model Selection and Loading](model-selection-and-loading.md) for Java and
[Python Usage and API](python/usage.md) for Python.

## Registered languages and models

The Java and Python standard aggregates contain the 31 IDs in `models/standard-model-projects.properties`. Every other active model is individually published and belongs to the Java-only extended aggregate; filtered alternatives use their separate opt-in Java aggregate.

| Language | Java enum | Model ID | Topology role | Package | Relative dictionary size |
| --- | --- | --- | --- | --- | --- |
| Adyghe | `ADY` | `ady-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 20,347 distinct usable word forms" title="Relative dictionary size 4 of 5; 20,347 distinct usable word forms">★★★★☆</span> (20,347 distinct forms) |
| Afrikaans | `AF_ZA` | `af-za-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 297,154 distinct usable word forms" title="Relative dictionary size 5 of 5; 297,154 distinct usable word forms">★★★★★</span> (297,154 distinct forms) |
| Aimele | `AIL` | `ail-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 3,061 distinct usable word forms" title="Relative dictionary size 2 of 5; 3,061 distinct usable word forms">★★☆☆☆</span> (3,061 distinct forms) |
| Akan | `AK` | `aka-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,890 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,890 distinct usable word forms">★★☆☆☆</span> (1,890 distinct forms) |
| Albanian | `SQ_AL` | `sq-al-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 10,218 distinct usable word forms" title="Relative dictionary size 3 of 5; 10,218 distinct usable word forms">★★★☆☆</span> (10,218 distinct forms) |
| Alsatian | `GSW` | `gsw-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,186 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,186 distinct usable word forms">★★☆☆☆</span> (1,186 distinct forms) |
| Amharic | `AM_ET` | `am-et-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 41,308 distinct usable word forms" title="Relative dictionary size 4 of 5; 41,308 distinct usable word forms">★★★★☆</span> (41,308 distinct forms) |
| Amuzgo | `AZG` | `azg-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 9,371 distinct usable word forms" title="Relative dictionary size 3 of 5; 9,371 distinct usable word forms">★★★☆☆</span> (9,371 distinct forms) |
| Ancient Greek | `GRC` | `grc-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 26,386 distinct usable word forms" title="Relative dictionary size 4 of 5; 26,386 distinct usable word forms">★★★★☆</span> (26,386 distinct forms) |
| Anglo-Norman | `XNO` | `xno-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 186 distinct usable word forms" title="Relative dictionary size 1 of 5; 186 distinct usable word forms">★☆☆☆☆</span> (186 distinct forms) |
| Arabic | `AR` | `ar-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 452,974 distinct usable word forms" title="Relative dictionary size 5 of 5; 452,974 distinct usable word forms">★★★★★</span> (452,974 distinct forms) |
| Armenian | `HY_AM` | `hy-am-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 246,576 distinct usable word forms" title="Relative dictionary size 5 of 5; 246,576 distinct usable word forms">★★★★★</span> (246,576 distinct forms) |
| Ashaninka | `CNI` | `cni-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 10,095 distinct usable word forms" title="Relative dictionary size 3 of 5; 10,095 distinct usable word forms">★★★☆☆</span> (10,095 distinct forms) |
| Assamese | `AS_IN` | `as-in-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 67,733 distinct usable word forms" title="Relative dictionary size 5 of 5; 67,733 distinct usable word forms">★★★★★</span> (67,733 distinct forms) |
| Asturian | `AST` | `ast-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 21,589 distinct usable word forms" title="Relative dictionary size 4 of 5; 21,589 distinct usable word forms">★★★★☆</span> (21,589 distinct forms) |
| Aymara | `AYM` | `aym-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 308,884 distinct usable word forms" title="Relative dictionary size 5 of 5; 308,884 distinct usable word forms">★★★★★</span> (308,884 distinct forms) |
| Azerbaijani | `AZ_AZ` | `az-az-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 6,669 distinct usable word forms" title="Relative dictionary size 3 of 5; 6,669 distinct usable word forms">★★★☆☆</span> (6,669 distinct forms) |
| Bashkir | `BAK` | `bak-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 10,723 distinct usable word forms" title="Relative dictionary size 3 of 5; 10,723 distinct usable word forms">★★★☆☆</span> (10,723 distinct forms) |
| Belarusian | `BE_BY` | `be-by-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 19,527 distinct usable word forms" title="Relative dictionary size 4 of 5; 19,527 distinct usable word forms">★★★★☆</span> (19,527 distinct forms) |
| Bengali | `BN_BD` | `bn-bd-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,847 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,847 distinct usable word forms">★★☆☆☆</span> (2,847 distinct forms) |
| Bininj Kun-wok | `GUP` | `gup-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 370 distinct usable word forms" title="Relative dictionary size 1 of 5; 370 distinct usable word forms">★☆☆☆☆</span> (370 distinct forms) |
| Braj | `BRA` | `bra-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,028 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,028 distinct usable word forms">★★☆☆☆</span> (2,028 distinct forms) |
| Breton | `BRE` | `bre-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,940 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,940 distinct usable word forms">★★☆☆☆</span> (1,940 distinct forms) |
| Bulgarian | `BG_BG` | `bg-bg-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 45,806 distinct usable word forms" title="Relative dictionary size 4 of 5; 45,806 distinct usable word forms">★★★★☆</span> (45,806 distinct forms) |
| Catalan | `CA_ES` | `ca-es-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 130,366 distinct usable word forms" title="Relative dictionary size 5 of 5; 130,366 distinct usable word forms">★★★★★</span> (130,366 distinct forms) |
| Cebuano | `CEB` | `ceb-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 430 distinct usable word forms" title="Relative dictionary size 1 of 5; 430 distinct usable word forms">★☆☆☆☆</span> (430 distinct forms) |
| Chichewa | `NY_MW` | `ny-mw-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,178 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,178 distinct usable word forms">★★★☆☆</span> (3,178 distinct forms) |
| Chichicapan Zapotec | `ZPV` | `zpv-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 818 distinct usable word forms" title="Relative dictionary size 2 of 5; 818 distinct usable word forms">★★☆☆☆</span> (818 distinct forms) |
| Chukchi | `CKT` | `ckt-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 302 distinct usable word forms" title="Relative dictionary size 1 of 5; 302 distinct usable word forms">★☆☆☆☆</span> (302 distinct forms) |
| Church Slavonic | `CHU` | `chu-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,652 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,652 distinct usable word forms">★★☆☆☆</span> (1,652 distinct forms) |
| Classical Armenian | `XCL` | `xcl-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 59,215 distinct usable word forms" title="Relative dictionary size 4 of 5; 59,215 distinct usable word forms">★★★★☆</span> (59,215 distinct forms) |
| Classical Syriac | `SYC` | `syc-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 27,529 distinct usable word forms" title="Relative dictionary size 4 of 5; 27,529 distinct usable word forms">★★★★☆</span> (27,529 distinct forms) |
| Congo Swahili | `SWC` | `swc-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 11,121 distinct usable word forms" title="Relative dictionary size 3 of 5; 11,121 distinct usable word forms">★★★☆☆</span> (11,121 distinct forms) |
| Copala Triqui | `CPA` | `cpa-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,741 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,741 distinct usable word forms">★★☆☆☆</span> (2,741 distinct forms) |
| Cornish | `COR` | `cor-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 162 distinct usable word forms" title="Relative dictionary size 1 of 5; 162 distinct usable word forms">★☆☆☆☆</span> (162 distinct forms) |
| Cree | `CRE` | `cre-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,076 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,076 distinct usable word forms">★★☆☆☆</span> (1,076 distinct forms) |
| Crimean Tatar | `CRH` | `crh-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 7,195 distinct usable word forms" title="Relative dictionary size 3 of 5; 7,195 distinct usable word forms">★★★☆☆</span> (7,195 distinct forms) |
| Czech | `CS_CZ` | `cs-cz-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 51,401 distinct usable word forms" title="Relative dictionary size 4 of 5; 51,401 distinct usable word forms">★★★★☆</span> (51,401 distinct forms) |
| Dakota | `DAK` | `dak-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,332 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,332 distinct usable word forms">★★★☆☆</span> (3,332 distinct forms) |
| Danish | `DA_DK` | `da-dk-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 27,921 distinct usable word forms" title="Relative dictionary size 4 of 5; 27,921 distinct usable word forms">★★★★☆</span> (27,921 distinct forms) |
| Dutch | `NL_NL` | `nl-nl-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 26,201 distinct usable word forms" title="Relative dictionary size 4 of 5; 26,201 distinct usable word forms">★★★★☆</span> (26,201 distinct forms) |
| Eastern Chatino | `CLY` | `cly-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,035 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,035 distinct usable word forms">★★☆☆☆</span> (2,035 distinct forms) |
| Egyptian Arabic | `ARZ` | `arz-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 17,937 distinct usable word forms" title="Relative dictionary size 4 of 5; 17,937 distinct usable word forms">★★★★☆</span> (17,937 distinct forms) |
| English | `US_UK` | `us-uk-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 591,946 distinct usable word forms" title="Relative dictionary size 5 of 5; 591,946 distinct usable word forms">★★★★★</span> (591,946 distinct forms) |
| Estonian | `ET_EE` | `et-ee-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 24,811 distinct usable word forms" title="Relative dictionary size 4 of 5; 24,811 distinct usable word forms">★★★★☆</span> (24,811 distinct forms) |
| Evenki | `EVN` | `evn-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 13,549 distinct usable word forms" title="Relative dictionary size 3 of 5; 13,549 distinct usable word forms">★★★☆☆</span> (13,549 distinct forms) |
| Faroese | `FO_FO` | `fo-fo-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 31,366 distinct usable word forms" title="Relative dictionary size 4 of 5; 31,366 distinct usable word forms">★★★★☆</span> (31,366 distinct forms) |
| Finnish | `FI_FI` | `fi-fi-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 1,788,784 distinct usable word forms" title="Relative dictionary size 5 of 5; 1,788,784 distinct usable word forms">★★★★★</span> (1,788,784 distinct forms) |
| French | `FR_FR` | `fr-fr-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 404,011 distinct usable word forms" title="Relative dictionary size 5 of 5; 404,011 distinct usable word forms">★★★★★</span> (404,011 distinct forms) |
| Friulian | `FUR` | `fur-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 5,007 distinct usable word forms" title="Relative dictionary size 3 of 5; 5,007 distinct usable word forms">★★★☆☆</span> (5,007 distinct forms) |
| Ga | `GAA` | `gaa-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 469 distinct usable word forms" title="Relative dictionary size 1 of 5; 469 distinct usable word forms">★☆☆☆☆</span> (469 distinct forms) |
| Galolen | `GAL` | `gal-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 25,436 distinct usable word forms" title="Relative dictionary size 4 of 5; 25,436 distinct usable word forms">★★★★☆</span> (25,436 distinct forms) |
| German | `DE_DE` | `de-de-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 277,266 distinct usable word forms" title="Relative dictionary size 5 of 5; 277,266 distinct usable word forms">★★★★★</span> (277,266 distinct forms) |
| Gothic | `GOT` | `got-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 134,332 distinct usable word forms" title="Relative dictionary size 5 of 5; 134,332 distinct usable word forms">★★★★★</span> (134,332 distinct forms) |
| Greek | `EL_GR` | `el-gr-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 76,869 distinct usable word forms" title="Relative dictionary size 5 of 5; 76,869 distinct usable word forms">★★★★★</span> (76,869 distinct forms) |
| Gulf Arabic | `AFB` | `afb-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 29,344 distinct usable word forms" title="Relative dictionary size 4 of 5; 29,344 distinct usable word forms">★★★★☆</span> (29,344 distinct forms) |
| Haida | `HAI` | `hai-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 5,385 distinct usable word forms" title="Relative dictionary size 3 of 5; 5,385 distinct usable word forms">★★★☆☆</span> (5,385 distinct forms) |
| Hebrew | `HE_IL` | `he-il-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 57,658 distinct usable word forms" title="Relative dictionary size 4 of 5; 57,658 distinct usable word forms">★★★★☆</span> (57,658 distinct forms) |
| Hiligaynon | `HIL` | `hil-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 393 distinct usable word forms" title="Relative dictionary size 1 of 5; 393 distinct usable word forms">★☆☆☆☆</span> (393 distinct forms) |
| Hsilimo | `HSI` | `hsi-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 158 distinct usable word forms" title="Relative dictionary size 1 of 5; 158 distinct usable word forms">★☆☆☆☆</span> (158 distinct forms) |
| Hungarian | `HU_HU` | `hu-hu-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 910,688 distinct usable word forms" title="Relative dictionary size 5 of 5; 910,688 distinct usable word forms">★★★★★</span> (910,688 distinct forms) |
| Icelandic | `IS_IS` | `is-is-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 52,197 distinct usable word forms" title="Relative dictionary size 4 of 5; 52,197 distinct usable word forms">★★★★☆</span> (52,197 distinct forms) |
| Indonesian | `ID_ID` | `id-id-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 21,296 distinct usable word forms" title="Relative dictionary size 4 of 5; 21,296 distinct usable word forms">★★★★☆</span> (21,296 distinct forms) |
| Ingrian | `IZH` | `izh-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,026 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,026 distinct usable word forms">★★☆☆☆</span> (1,026 distinct forms) |
| Irish | `GA_IE` | `ga-ie-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 24,035 distinct usable word forms" title="Relative dictionary size 4 of 5; 24,035 distinct usable word forms">★★★★☆</span> (24,035 distinct forms) |
| Italian | `IT_IT` | `it-it-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 324,366 distinct usable word forms" title="Relative dictionary size 5 of 5; 324,366 distinct usable word forms">★★★★★</span> (324,366 distinct forms) |
| Itelmen | `ITL` | `itl-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,659 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,659 distinct usable word forms">★★★☆☆</span> (3,659 distinct forms) |
| Japanese | `JA_JP` | `ja-jp-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 10,848 distinct usable word forms" title="Relative dictionary size 3 of 5; 10,848 distinct usable word forms">★★★☆☆</span> (10,848 distinct forms) |
| Kabardian | `KBD` | `kbd-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 3,054 distinct usable word forms" title="Relative dictionary size 2 of 5; 3,054 distinct usable word forms">★★☆☆☆</span> (3,054 distinct forms) |
| Kalaallisut | `KL_GL` | `kl-gl-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 321 distinct usable word forms" title="Relative dictionary size 1 of 5; 321 distinct usable word forms">★☆☆☆☆</span> (321 distinct forms) |
| Kannada | `KN_IN` | `kn-in-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,802 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,802 distinct usable word forms">★★★☆☆</span> (3,802 distinct forms) |
| Karelian | `KRL` | `krl-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 566 distinct usable word forms" title="Relative dictionary size 1 of 5; 566 distinct usable word forms">★☆☆☆☆</span> (566 distinct forms) |
| Kashubian | `CSB` | `csb-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 350 distinct usable word forms" title="Relative dictionary size 1 of 5; 350 distinct usable word forms">★☆☆☆☆</span> (350 distinct forms) |
| Kazakh | `KK_KZ` | `kk-kz-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 35,223 distinct usable word forms" title="Relative dictionary size 4 of 5; 35,223 distinct usable word forms">★★★★☆</span> (35,223 distinct forms) |
| Khakas | `KJH` | `kjh-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,172 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,172 distinct usable word forms">★★☆☆☆</span> (1,172 distinct forms) |
| Khaling | `KLR` | `klr-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 54,602 distinct usable word forms" title="Relative dictionary size 4 of 5; 54,602 distinct usable word forms">★★★★☆</span> (54,602 distinct forms) |
| Kodi | `KOD` | `kod-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 524 distinct usable word forms" title="Relative dictionary size 1 of 5; 524 distinct usable word forms">★☆☆☆☆</span> (524 distinct forms) |
| Kongo | `KON` | `kon-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 557 distinct usable word forms" title="Relative dictionary size 1 of 5; 557 distinct usable word forms">★☆☆☆☆</span> (557 distinct forms) |
| Kyrgyz | `KY_KG` | `ky-kg-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,997 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,997 distinct usable word forms">★★☆☆☆</span> (2,997 distinct forms) |
| Ladin | `LLD` | `lld-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 4,819 distinct usable word forms" title="Relative dictionary size 3 of 5; 4,819 distinct usable word forms">★★★☆☆</span> (4,819 distinct forms) |
| Latin | `LA` | `la-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 486,375 distinct usable word forms" title="Relative dictionary size 5 of 5; 486,375 distinct usable word forms">★★★★★</span> (486,375 distinct forms) |
| Latvian | `LV_LV` | `lv-lv-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 75,492 distinct usable word forms" title="Relative dictionary size 5 of 5; 75,492 distinct usable word forms">★★★★★</span> (75,492 distinct forms) |
| Lingala | `LIN` | `lin-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 230 distinct usable word forms" title="Relative dictionary size 1 of 5; 230 distinct usable word forms">★☆☆☆☆</span> (230 distinct forms) |
| Lithuanian | `LT_LT` | `lt-lt-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 28,889 distinct usable word forms" title="Relative dictionary size 4 of 5; 28,889 distinct usable word forms">★★★★☆</span> (28,889 distinct forms) |
| Livonian | `LIV` | `liv-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,861 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,861 distinct usable word forms">★★☆☆☆</span> (2,861 distinct forms) |
| Low German | `NDS` | `nds-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,495 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,495 distinct usable word forms">★★☆☆☆</span> (2,495 distinct forms) |
| Lower Sorbian | `DSB` | `dsb-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 12,039 distinct usable word forms" title="Relative dictionary size 3 of 5; 12,039 distinct usable word forms">★★★☆☆</span> (12,039 distinct forms) |
| Luganda | `LG_UG` | `lg-ug-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 4,673 distinct usable word forms" title="Relative dictionary size 3 of 5; 4,673 distinct usable word forms">★★★☆☆</span> (4,673 distinct forms) |
| Macedonian | `MK_MK` | `mk-mk-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 135,754 distinct usable word forms" title="Relative dictionary size 5 of 5; 135,754 distinct usable word forms">★★★★★</span> (135,754 distinct forms) |
| Magahi | `MAG` | `mag-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,448 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,448 distinct usable word forms">★★☆☆☆</span> (1,448 distinct forms) |
| Malagasy | `MG_MG` | `mg-mg-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 636 distinct usable word forms" title="Relative dictionary size 1 of 5; 636 distinct usable word forms">★☆☆☆☆</span> (636 distinct forms) |
| Maltese | `MT_MT` | `mt-mt-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,491 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,491 distinct usable word forms">★★☆☆☆</span> (1,491 distinct forms) |
| Manx | `GV_IM` | `gv-im-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 15 distinct usable word forms" title="Relative dictionary size 1 of 5; 15 distinct usable word forms">★☆☆☆☆</span> (15 distinct forms) |
| Maori | `MI_NZ` | `mi-nz-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 207 distinct usable word forms" title="Relative dictionary size 1 of 5; 207 distinct usable word forms">★☆☆☆☆</span> (207 distinct forms) |
| Mapudungun | `ARN` | `arn-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 548 distinct usable word forms" title="Relative dictionary size 1 of 5; 548 distinct usable word forms">★☆☆☆☆</span> (548 distinct forms) |
| Middle French | `FRM` | `frm-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 27,102 distinct usable word forms" title="Relative dictionary size 4 of 5; 27,102 distinct usable word forms">★★★★☆</span> (27,102 distinct forms) |
| Middle High German | `GMH` | `gmh-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 384 distinct usable word forms" title="Relative dictionary size 1 of 5; 384 distinct usable word forms">★☆☆☆☆</span> (384 distinct forms) |
| Middle Low German | `GML` | `gml-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 624 distinct usable word forms" title="Relative dictionary size 1 of 5; 624 distinct usable word forms">★☆☆☆☆</span> (624 distinct forms) |
| Mongolian | `MN_MN` | `mn-mn-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 17,231 distinct usable word forms" title="Relative dictionary size 4 of 5; 17,231 distinct usable word forms">★★★★☆</span> (17,231 distinct forms) |
| Murrinh-Patha | `MWF` | `mwf-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 592 distinct usable word forms" title="Relative dictionary size 1 of 5; 592 distinct usable word forms">★☆☆☆☆</span> (592 distinct forms) |
| Navajo | `NAV` | `nav-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 11,046 distinct usable word forms" title="Relative dictionary size 3 of 5; 11,046 distinct usable word forms">★★★☆☆</span> (11,046 distinct forms) |
| Neapolitan | `NAP` | `nap-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,497 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,497 distinct usable word forms">★★☆☆☆</span> (1,497 distinct forms) |
| North Frisian | `FRR` | `frr-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 364 distinct usable word forms" title="Relative dictionary size 1 of 5; 364 distinct usable word forms">★☆☆☆☆</span> (364 distinct forms) |
| Northern Sami | `SME` | `sme-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 54,435 distinct usable word forms" title="Relative dictionary size 4 of 5; 54,435 distinct usable word forms">★★★★☆</span> (54,435 distinct forms) |
| Norwegian Bokmål | `NB_NO` | `nb-no-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 73,170 distinct usable word forms" title="Relative dictionary size 5 of 5; 73,170 distinct usable word forms">★★★★★</span> (73,170 distinct forms) |
| Norwegian Nynorsk | `NN_NO` | `nn-no-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 16,937 distinct usable word forms" title="Relative dictionary size 4 of 5; 16,937 distinct usable word forms">★★★★☆</span> (16,937 distinct forms) |
| Old English | `ANG` | `ang-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 65,202 distinct usable word forms" title="Relative dictionary size 4 of 5; 65,202 distinct usable word forms">★★★★☆</span> (65,202 distinct forms) |
| Old French | `FRO` | `fro-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 94,338 distinct usable word forms" title="Relative dictionary size 5 of 5; 94,338 distinct usable word forms">★★★★★</span> (94,338 distinct forms) |
| Old High German | `GOH` | `goh-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 5,267 distinct usable word forms" title="Relative dictionary size 3 of 5; 5,267 distinct usable word forms">★★★☆☆</span> (5,267 distinct forms) |
| Old Irish | `SGA` | `sga-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 934 distinct usable word forms" title="Relative dictionary size 2 of 5; 934 distinct usable word forms">★★☆☆☆</span> (934 distinct forms) |
| Old Norse | `NON` | `non-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 46,089 distinct usable word forms" title="Relative dictionary size 4 of 5; 46,089 distinct usable word forms">★★★★☆</span> (46,089 distinct forms) |
| Old Saxon | `OSX` | `osx-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 12,361 distinct usable word forms" title="Relative dictionary size 3 of 5; 12,361 distinct usable word forms">★★★☆☆</span> (12,361 distinct forms) |
| Oodham | `OOD` | `ood-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,159 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,159 distinct usable word forms">★★☆☆☆</span> (1,159 distinct forms) |
| Otomi (ote) | `OTE` | `ote-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,417 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,417 distinct usable word forms">★★★☆☆</span> (3,417 distinct forms) |
| Pashto | `PS_AF` | `ps-af-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,945 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,945 distinct usable word forms">★★☆☆☆</span> (2,945 distinct forms) |
| Persian | `FA_IR` | `fa-ir-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,544 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,544 distinct usable word forms">★★★☆☆</span> (3,544 distinct forms) |
| Polish | `PL_PL` | `pl-pl-polimorf` | `optional` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 4,668,685 distinct usable word forms" title="Relative dictionary size 5 of 5; 4,668,685 distinct usable word forms">★★★★★</span> (4,668,685 distinct forms) |
| Polish | `PL_PL` | `pl-pl-unimorph` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 120,867 distinct usable word forms" title="Relative dictionary size 5 of 5; 120,867 distinct usable word forms">★★★★★</span> (120,867 distinct forms) |
| Portuguese | `PT_PT` | `pt-pt-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 211,091 distinct usable word forms" title="Relative dictionary size 5 of 5; 211,091 distinct usable word forms">★★★★★</span> (211,091 distinct forms) |
| Quechua | `QUE` | `que-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 147,914 distinct usable word forms" title="Relative dictionary size 5 of 5; 147,914 distinct usable word forms">★★★★★</span> (147,914 distinct forms) |
| Romanian | `RO_RO` | `ro-ro-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 48,565 distinct usable word forms" title="Relative dictionary size 4 of 5; 48,565 distinct usable word forms">★★★★☆</span> (48,565 distinct forms) |
| Russian | `RU_RU` | `ru-ru-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 759,333 distinct usable word forms" title="Relative dictionary size 5 of 5; 759,333 distinct usable word forms">★★★★★</span> (759,333 distinct forms) |
| Seneca | `SEE` | `see-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 5,318 distinct usable word forms" title="Relative dictionary size 3 of 5; 5,318 distinct usable word forms">★★★☆☆</span> (5,318 distinct forms) |
| Serbo-Croatian | `HBS` | `hbs-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 272,634 distinct usable word forms" title="Relative dictionary size 5 of 5; 272,634 distinct usable word forms">★★★★★</span> (272,634 distinct forms) |
| Shipibo-Conibo | `SHP` | `shp-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 7,729 distinct usable word forms" title="Relative dictionary size 3 of 5; 7,729 distinct usable word forms">★★★☆☆</span> (7,729 distinct forms) |
| Shona | `SN_ZW` | `sn-zw-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,640 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,640 distinct usable word forms">★★☆☆☆</span> (2,640 distinct forms) |
| Sotho, Southern | `ST_ZA` | `st-za-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 416 distinct usable word forms" title="Relative dictionary size 1 of 5; 416 distinct usable word forms">★☆☆☆☆</span> (416 distinct forms) |
| Southern Kurdish | `SDH` | `sdh-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 165 distinct usable word forms" title="Relative dictionary size 1 of 5; 165 distinct usable word forms">★☆☆☆☆</span> (165 distinct forms) |
| Spanish | `ES_ES` | `es-es-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 849,661 distinct usable word forms" title="Relative dictionary size 5 of 5; 849,661 distinct usable word forms">★★★★★</span> (849,661 distinct forms) |
| Swedish | `SV_SE` | `sv-se-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 95,181 distinct usable word forms" title="Relative dictionary size 5 of 5; 95,181 distinct usable word forms">★★★★★</span> (95,181 distinct forms) |
| Tagalog | `TL_PH` | `tl-ph-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,510 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,510 distinct usable word forms">★★☆☆☆</span> (2,510 distinct forms) |
| Turkish | `TR_TR` | `tr-tr-default` | `standalone` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 222,553 distinct usable word forms" title="Relative dictionary size 5 of 5; 222,553 distinct usable word forms">★★★★★</span> (222,553 distinct forms) |
| Ukrainian | `UK_UA` | `uk-ua-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 14,150 distinct usable word forms" title="Relative dictionary size 4 of 5; 14,150 distinct usable word forms">★★★★☆</span> (14,150 distinct forms) |
| Uyghur | `UG_CN` | `ug-cn-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 6,368 distinct usable word forms" title="Relative dictionary size 3 of 5; 6,368 distinct usable word forms">★★★☆☆</span> (6,368 distinct forms) |
| Uzbek | `UZ_UZ` | `uz-uz-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 9,835 distinct usable word forms" title="Relative dictionary size 3 of 5; 9,835 distinct usable word forms">★★★☆☆</span> (9,835 distinct forms) |
| Voro | `VRO` | `vro-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 412 distinct usable word forms" title="Relative dictionary size 1 of 5; 412 distinct usable word forms">★☆☆☆☆</span> (412 distinct forms) |
| Western Highland Chatino | `CTP` | `ctp-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,597 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,597 distinct usable word forms">★★☆☆☆</span> (2,597 distinct forms) |
| Xibe | `SJO` | `sjo-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,151 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,151 distinct usable word forms">★★★☆☆</span> (3,151 distinct forms) |
| Yanesha’ | `AME` | `ame-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,635 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,635 distinct usable word forms">★★☆☆☆</span> (2,635 distinct forms) |
| Yiddish | `YI` | `yi-default` | `default` | `standard` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 3,532 distinct usable word forms" title="Relative dictionary size 3 of 5; 3,532 distinct usable word forms">★★★☆☆</span> (3,532 distinct forms) |
| Yoloxóchitl Mixtec | `XTY` | `xty-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,754 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,754 distinct usable word forms">★★☆☆☆</span> (2,754 distinct forms) |
| Zarma | `DJE` | `dje-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 81 distinct usable word forms" title="Relative dictionary size 1 of 5; 81 distinct usable word forms">★☆☆☆☆</span> (81 distinct forms) |
| Zenzontepec Chatino | `CZN` | `czn-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,864 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,864 distinct usable word forms">★★☆☆☆</span> (1,864 distinct forms) |
| Zulu | `ZU_ZA` | `zu-za-default` | `standalone` | `extended` | <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 32,384 distinct usable word forms" title="Relative dictionary size 4 of 5; 32,384 distinct usable word forms">★★★★☆</span> (32,384 distinct forms) |

The maintained table deliberately avoids duplicating mutable provenance fields. Those values come from model module metadata and the generated model catalog.
## The Polish dual-model case

`PL_PL` represents Polish. It is not an alias for either source dictionary.

- `loadCompiled(Language.PL_PL, ...)` resolves `pl-pl-unimorph`.
- `registry.require("pl-pl-polimorf")` resolves the optional PoliMorf model.
- `StemmerPatchTrieLoader.loadCompiled("pl-pl-polimorf", true, reductionMode)` constructs its compiled trie explicitly; complete construction is verified with a dedicated 6 GiB test heap.
- Both artifacts may be present and loaded independently.
- Adding PoliMorf does not change the language default.
- Radixor does not merge their dictionaries or outputs automatically.

UniMorph and PoliMorf have different lexical sources and provenance. Applications should compare outputs with application-specific regression tests before changing an explicit model choice.

In Python, `Stemmer("pl")` selects `pl-pl-unimorph`. The standard Python data
package does not include PoliMorf; applications that need it must compile and
load it explicitly as a trusted custom model. As in Java, it never changes the
Polish default implicitly.

## Dependency patterns

Resolve the placeholders below from the
[Maven Central artifact page](https://central.sonatype.com/artifact/org.egothor/radixor)
and the [model catalog](stemmer-model-catalog.md), respectively.

Minimal English:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-model-us-uk-default:<compatible-model-version>'
}
```

All 31 standard-aggregate defaults:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-models-standard:<compatible-catalog-version>'
}
```

The standard pack is metadata-only and excludes optional PoliMorf.

Every individual model artifact carries its own provenance and licensing material. UniMorph
models carry model-specific audited licenses and notices because their official language
repositories identify different lexical sources, contributors, and applicable terms. Each notice preserves upstream
attribution and records the Radixor transformations and Leo Galambos contribution statement.
Legacy imports disclose when an exact historical revision was not recorded; this is a
reproducibility limitation, not a claim that the source or license is unknown. The active new-model
set includes CC BY-SA 3.0, CC BY-SA 4.0, CC BY 4.0, and LGPLLR material; unsupported evidence is
quarantined rather than assigned a generic license.

## Loading a language default

```java
final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.US_UK,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

The call discovers the default descriptor from the runtime classpath, verifies its compressed resource, parses the GZip UTF-8 dictionary, and constructs a read-only trie. A missing default throws `StemmerModelNotFoundException`; there is no arbitrary fallback.

## Writing direction

Arabic (including Gulf and Egyptian Arabic), Persian, Hebrew, Pashto, Southern Kurdish, Classical Syriac, Uyghur, Urdu, and Yiddish declare right-to-left writing metadata for presentation. Writing direction does not reorder characters in a Java `String`: all built-in natural-language models therefore use backward traversal from the stored sequence end, where suffixes remain located. Explicit forward traversal is reserved for deliberately prefix-oriented custom data. The selected traversal must remain aligned across dictionary parsing, trie lookup, patch generation, persistence, and application; model identity and writing direction are separate concerns.

## Custom and persisted alternatives

Registered model artifacts are a convenient reproducible baseline. Applications may instead load caller-owned textual dictionaries or persist compiled `.radixor.gz` tries. Those paths are distinct from model artifact discovery:

- a model `stemmer.gz` is a compressed textual dictionary plus descriptor/index metadata;
- a `.radixor.gz` created by the binary writer is a persisted compiled trie;
- a source dictionary is upstream input, not automatically a valid model artifact.

See [Dictionary Format](dictionary-format.md), [CLI Compilation](cli-compilation.md), and [Stemmer Models](stemmer-models.md).

## Benchmark interpretation

Benchmark rows must identify the Radixor model ID used. Default rows use the default IDs above. Optional Polish PoliMorf comparisons must be labeled `pl-pl-polimorf`; they are not interchangeable with the historical default Polish row. Continue with [Benchmarking](benchmarking.md) and [Reproducibility](benchmarks/reference/reproducibility.md).
