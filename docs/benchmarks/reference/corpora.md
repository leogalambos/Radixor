# Benchmark Corpora

The table below describes the Radixor resources used to build speed and quality corpora. `Total tokens` is the complete dictionary token count used by quality benchmarks. `Already-root tokens` counts fields where the token is already equal to the line root. `Changed tokens` is the speed workload before the minimum-size repeat rule.

| Language resource | Dictionary rows | Total tokens | Already-root tokens | Changed tokens | Speed timing tokens |
| --- | ---: | ---: | ---: | ---: | ---: |
| `cs_cz` | 5,113 | 56,612 | 10,049 | 46,563 | 46,563 |
| `da_dk` | 4,179 | 32,256 | 8,356 | 23,900 | 23,900 |
| `de_de` | 39,315 | 213,440 | 73,799 | 139,641 | 139,641 |
| `es_es` | 65,059 | 926,393 | 120,121 | 806,272 | 806,272 |
| `fa_ir` | 69 | 3,770 | 138 | 3,632 | 5,000 |
| `fi_fi` | 57,027 | 1,865,215 | 110,525 | 1,754,690 | 1,754,690 |
| `fr_fr` | 59,240 | 474,110 | 108,141 | 365,969 | 365,969 |
| `he_il` | 2,358 | 61,071 | 4,715 | 56,356 | 56,356 |
| `hu_hu` | 19,406 | 935,713 | 38,775 | 896,938 | 896,938 |
| `it_it` | 10,009 | 337,546 | 20,004 | 317,542 | 317,542 |
| `nb_no` | 17,929 | 90,757 | 33,376 | 57,381 | 57,381 |
| `nl_nl` | 4,992 | 31,466 | 9,981 | 21,485 | 21,485 |
| `nn_no` | 4,688 | 19,651 | 6,089 | 13,562 | 13,562 |
| `pl_pl` | 9,990 | 132,308 | 19,957 | 112,351 | 112,351 |
| `pt_pt` | 4,001 | 215,490 | 8,002 | 207,488 | 207,488 |
| `ru_ru` | 37,410 | 806,279 | 74,808 | 731,471 | 731,471 |
| `sv_se` | 12,371 | 110,468 | 24,731 | 85,737 | 85,737 |
| `uk_ua` | 1,493 | 15,737 | 2,985 | 12,752 | 12,752 |
| `us_uk` | 396,939 | 1,004,374 | 793,874 | 210,500 | 210,500 |
| `yi` | 802 | 4,300 | 1,524 | 2,776 | 5,000 |

Speed benchmarks process the complete changed-token dictionary sequence for the language. Only resources with fewer than 5,000 changed tokens are repeated to reach the minimum timing size; larger resources are not sampled or truncated.
