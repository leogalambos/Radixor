# Benchmark Corpora

The table below describes the Radixor resources used to build speed and quality corpora. `Total tokens` is the complete dictionary token count used by quality benchmarks. `Already-root tokens` counts fields where the token is already equal to the line root. `Changed tokens` is the speed workload before the minimum-size repeat rule.

| Default model ID | Version | SHA-256 | Language | Dictionary rows | Total tokens | Already-root tokens | Changed tokens | Speed timing tokens |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `cs-cz-default` | `1.0.0` | `62afdaa6dc7a721b54a0dc278a0c648a63ad52a34a412d27b5b52fbcde9c1ce4` | `CS_CZ` | 5,113 | 56,612 | 10,049 | 46,563 | 46,563 |
| `da-dk-default` | `1.0.0` | `3f7b670a0e7b872bda0381f5154ce058a4656297b39b7157b4ccf6560257cb90` | `DA_DK` | 4,179 | 32,256 | 8,356 | 23,900 | 23,900 |
| `nl-nl-default` | `1.0.0` | `c098034adc42da2ca3e419160e6dd2c2b3868f8af334303b3a191e09caadaf5e` | `NL_NL` | 4,992 | 31,466 | 9,981 | 21,485 | 21,485 |
| `us-uk-default` | `1.0.0` | `8c79122993499e437ea8b54b620832dca29019298f281c1f3132f4d1be885460` | `US_UK` | 396,939 | 1,004,374 | 793,874 | 210,500 | 210,500 |
| `fi-fi-default` | `1.0.0` | `ca2628b3db31fee92f1b612ebbbd5e956a6dbbfb10e721325e55ef528f26072f` | `FI_FI` | 57,027 | 1,865,215 | 110,525 | 1,754,690 | 1,754,690 |
| `fr-fr-default` | `1.0.0` | `a988658758952fd599dc7360e0234178a6d65ac46e5cedc7dcd325a7cb7e71d9` | `FR_FR` | 59,240 | 474,110 | 108,141 | 365,969 | 365,969 |
| `de-de-default` | `1.0.0` | `cbfa038122823f02e4bdb54b0035492c356b6ecd80f11eb11290d7a7248a59f5` | `DE_DE` | 54,092 | 333,036 | 90,535 | 242,501 | 242,501 |
| `he-il-default` | `1.0.0` | `9a47dc69bb7dab21aba0266b73cd74cdaeb17db94363796a0a56111ac8518256` | `HE_IL` | 2,358 | 61,071 | 4,715 | 56,356 | 56,356 |
| `hu-hu-default` | `1.0.0` | `359d46a01d751ec823705ad7f3dd1cc8f6663feb1a9d13cb04d0c6fb51ab646e` | `HU_HU` | 19,406 | 935,713 | 38,775 | 896,938 | 896,938 |
| `it-it-default` | `1.0.0` | `5e03be31c9761e30dbf24a47a5ced3d6ec949dabd31e92632fdd9f7c67fc2e12` | `IT_IT` | 10,009 | 337,546 | 20,004 | 317,542 | 317,542 |
| `nb-no-default` | `1.0.0` | `f495bffb44e79d27993e6e2e65d4b1204b29365dc93f481b2d8b96766fc90fd9` | `NB_NO` | 17,929 | 90,757 | 33,376 | 57,381 | 57,381 |
| `nn-no-default` | `1.0.0` | `900cf2005605aea2a3d8d731ec0b0c1f47fb4469b4ba6b9134145d4d026a0398` | `NN_NO` | 4,688 | 19,651 | 6,089 | 13,562 | 13,562 |
| `fa-ir-default` | `1.0.0` | `b29a0d168a6a97f980666aa40b74a0edd8b6be4ab3320a7abfbb76b3529f4ea1` | `FA_IR` | 69 | 3,770 | 138 | 3,632 | 5,000 |
| `pl-pl-unimorph` | `1.0.0` | `8191ed727097839cc808cbc5c56a1bd78b3c851e7733ad226ad9a51519a54721` | `PL_PL` | 9,990 | 132,308 | 19,957 | 112,351 | 112,351 |
| `pt-pt-default` | `1.0.0` | `7a035ff330a6f0548f446cd0d6617bc1cf4751292125a3564d3a255c5d6f516d` | `PT_PT` | 4,001 | 215,490 | 8,002 | 207,488 | 207,488 |
| `ru-ru-default` | `1.0.0` | `df7ea25e63a875eeec7a4185be685bd5372a3c568db85c34c44fdf5d8d980a40` | `RU_RU` | 37,410 | 806,279 | 74,808 | 731,471 | 731,471 |
| `es-es-default` | `1.0.0` | `7a1ec94cfdb1e9a95431289d62dc5579cb2a532d99532eeda90290072e569721` | `ES_ES` | 65,059 | 926,393 | 120,121 | 806,272 | 806,272 |
| `sv-se-default` | `1.0.0` | `d9be72e3d67c776622c4281e04e4063b9381e8f84a823d98ebf08888b82dff0c` | `SV_SE` | 12,371 | 110,468 | 24,731 | 85,737 | 85,737 |
| `uk-ua-default` | `1.0.0` | `cf3f612cfff16cb7763f99c55851069489b883c3bdd1a6576cd8c57a97e07eae` | `UK_UA` | 1,493 | 15,737 | 2,985 | 12,752 | 12,752 |
| `yi-default` | `1.0.0` | `f47de665c27dcd72833a82904e49c68a945bb5aca769a7ec5a0164e2c981a6d3` | `YI` | 802 | 4,300 | 1,524 | 2,776 | 5,000 |

Speed benchmarks process the complete changed-token dictionary sequence for the language. Only resources with fewer than 5,000 changed tokens are repeated to reach the minimum timing size; larger resources are not sampled or truncated.
