window.BENCHMARK_DATA = {
  "metadata": {
    "timestamp": "2026-11-19T16:41:44.868654+00:00",
    "machine": {
      "os": "Windows-11-10.0.26200-SP0",
      "cpu_model": "AMD Ryzen 5 4500U with Radeon Graphics",
      "cores": 6,
      "ram_total_gb": 31.36181640625
    },
    "runtimes": {
      "go": "go version go1.27.4 windows/amd64",
      "java": "openjdk version \"25.0.3\" 2026-04-21 LTS",
      "scala": "3.3.4"
    },
    "corpus": {
      "documents": 10000,
      "total_tokens": 131985,
      "queries_per_run": 10000,
      "iterations": 5
    },
    "seed": 42,
    "sha256": {
      "corpus.tsv": "ae7601aa18b233fe0f6afcae06275756b8fbf5ae07cbff190e86f3679e6ee6bc",
      "writes.tsv": "52c07072abb1a4de5ee8bc78af123d55fd346da1f6e66fe26489b63ed1082740",
      "queries.txt": "35c211df3fa4829d0a9f9da0a0d780ad0f23f79ea7321b743af7d0f199c37129",
      "golden_queries.txt": "7a914aeb1a86a79103880e442609cc0b3b374ad0f572c19e96ee67fa3b543f7c"
    },
    "index_scale": {
      "documents": 100000,
      "total_tokens": 1320051,
      "sha256": {
        "corpus.tsv": "8843e0804b2a83d7fcc0fdc045fdb6ee04cd9e3d698144e046519fb7a1e5c524",
        "writes.tsv": "70d6e68b77febf23aca6b2360925064ee227e9c583b7e32f5e4fb7c91124817c",
        "queries.txt": "4734ea9066191ead80ea63ff4f4a7f3d6adfd3a01b3d3976a59d60e7f95bb972",
        "golden_queries.txt": "7a914aeb1a86a79103880e442609cc0b3b374ad0f572c19e96ee67fa3b543f7c"
      },
      "parity_check": {
        "passed": true,
        "queries_tested": 61,
        "match_rate_pct": 100.0,
        "notes": "Top-10 ordenado y BM25 con tolerancia absoluta 1e-4."
      },
      "medians": {
        "go": {
          "indexing_time_ms": 319.5781,
          "ram_retained_mb": 51.775878906,
          "cold_start_ms": 366.8939,
          "ram_rss_peak_mb": 142.97265625,
          "docs_per_sec": 312912.5556475866
        },
        "java": {
          "indexing_time_ms": 1967.0215,
          "ram_retained_mb": 92.02912139892578,
          "cold_start_ms": 2469.6384,
          "ram_rss_peak_mb": 1058.41015625,
          "docs_per_sec": 50838.28519413743
        },
        "scala": {
          "indexing_time_ms": 2149.6098,
          "ram_retained_mb": 66.92256164550781,
          "cold_start_ms": 3531.9524,
          "ram_rss_peak_mb": 766.421875,
          "docs_per_sec": 46520.070758888425
        }
      },
      "iqr": {
        "go": {
          "indexing_time_ms": 74.29140000000001,
          "ram_retained_mb": 6.1035000001652406e-05,
          "cold_start_ms": 123.48749999999995,
          "ram_rss_peak_mb": 1.10546875,
          "docs_per_sec": 82700.4400068805
        },
        "java": {
          "indexing_time_ms": 647.8512000000001,
          "ram_retained_mb": 0.10877227783203125,
          "cold_start_ms": 740.2027,
          "ram_rss_peak_mb": 52.9453125,
          "docs_per_sec": 22514.174346345928
        },
        "scala": {
          "indexing_time_ms": 431.82230000000004,
          "ram_retained_mb": 0.09949493408203125,
          "cold_start_ms": 1218.7874000000002,
          "ram_rss_peak_mb": 96.32421875,
          "docs_per_sec": 11407.862198924755
        }
      },
      "runs": {
        "go": [
          {
            "indexing_time_ms": 339.1585,
            "ram_retained_mb": 51.771003723,
            "cold_start_ms": 366.8939,
            "ram_rss_peak_mb": 142.97265625,
            "docs_per_sec": 294847.3943598642
          },
          {
            "indexing_time_ms": 319.5781,
            "ram_retained_mb": 51.775909424,
            "cold_start_ms": 455.0993,
            "ram_rss_peak_mb": 141.69921875,
            "docs_per_sec": 312912.5556475866
          },
          {
            "indexing_time_ms": 443.0248,
            "ram_retained_mb": 51.775924683,
            "cold_start_ms": 437.7347,
            "ram_rss_peak_mb": 143.9453125,
            "docs_per_sec": 225720.99801184944
          },
          {
            "indexing_time_ms": 256.1932,
            "ram_retained_mb": 51.775878906,
            "cold_start_ms": 314.2472,
            "ram_rss_peak_mb": 146.078125,
            "docs_per_sec": 390330.4225092626
          },
          {
            "indexing_time_ms": 264.8671,
            "ram_retained_mb": 51.775848389,
            "cold_start_ms": 305.5017,
            "ram_rss_peak_mb": 142.83984375,
            "docs_per_sec": 377547.8343667447
          }
        ],
        "java": [
          {
            "indexing_time_ms": 2050.9038,
            "ram_retained_mb": 92.02920532226562,
            "cold_start_ms": 2469.6384,
            "ram_rss_peak_mb": 1058.41015625,
            "docs_per_sec": 48758.99103604957
          },
          {
            "indexing_time_ms": 2206.656,
            "ram_retained_mb": 92.02912139892578,
            "cold_start_ms": 2490.1057,
            "ram_rss_peak_mb": 1037.703125,
            "docs_per_sec": 45317.4396009165
          },
          {
            "indexing_time_ms": 1378.9133,
            "ram_retained_mb": 91.9204330444336,
            "cold_start_ms": 2800.9621,
            "ram_rss_peak_mb": 1027.65625,
            "docs_per_sec": 72520.87567797047
          },
          {
            "indexing_time_ms": 1403.0526,
            "ram_retained_mb": 91.87307739257812,
            "cold_start_ms": 1725.8636,
            "ram_rss_peak_mb": 1090.6484375,
            "docs_per_sec": 71273.1653823955
          },
          {
            "indexing_time_ms": 1967.0215,
            "ram_retained_mb": 92.06497955322266,
            "cold_start_ms": 1749.903,
            "ram_rss_peak_mb": 1094.796875,
            "docs_per_sec": 50838.28519413743
          }
        ],
        "scala": [
          {
            "indexing_time_ms": 2149.6098,
            "ram_retained_mb": 66.92256164550781,
            "cold_start_ms": 4515.4796,
            "ram_rss_peak_mb": 648.12109375,
            "docs_per_sec": 46520.070758888425
          },
          {
            "indexing_time_ms": 2173.4407,
            "ram_retained_mb": 66.94119262695312,
            "cold_start_ms": 3750.1884,
            "ram_rss_peak_mb": 815.73828125,
            "docs_per_sec": 46009.9969601195
          },
          {
            "indexing_time_ms": 2294.5269,
            "ram_retained_mb": 66.83551788330078,
            "cold_start_ms": 3531.9524,
            "ram_rss_peak_mb": 939.3125,
            "docs_per_sec": 43581.96890173744
          },
          {
            "indexing_time_ms": 1552.6793,
            "ram_retained_mb": 66.83599090576172,
            "cold_start_ms": 2470.2974,
            "ram_rss_peak_mb": 719.4140625,
            "docs_per_sec": 64404.80014127837
          },
          {
            "indexing_time_ms": 1741.6184,
            "ram_retained_mb": 66.93548583984375,
            "cold_start_ms": 2531.401,
            "ram_rss_peak_mb": 766.421875,
            "docs_per_sec": 57417.85915904425
          }
        ]
      }
    },
    "runs": {
      "go": [
        {
          "indexing_time_ms": 35.3193,
          "ram_retained_mb": 5.941841125,
          "qps_single": 13684.902118053,
          "qps_concurrent": 16555.136779575,
          "p50": 55.5,
          "p90": 121.6,
          "p95": 233.7,
          "p99": 465.9,
          "p999": 2031.6,
          "cold_start_ms": 51.6438,
          "ram_rss_peak_mb": 30.5390625,
          "docs_per_sec": 283131.319137129
        },
        {
          "indexing_time_ms": 33.3137,
          "ram_retained_mb": 5.946685791,
          "qps_single": 12673.177384816,
          "qps_concurrent": 16286.366438359,
          "p50": 56.3,
          "p90": 136.5,
          "p95": 252.2,
          "p99": 540.0,
          "p999": 2262.0,
          "cold_start_ms": 49.2917,
          "ram_rss_peak_mb": 32.078125,
          "docs_per_sec": 300176.8041376371
        },
        {
          "indexing_time_ms": 31.6911,
          "ram_retained_mb": 5.946792603,
          "qps_single": 11903.716078278,
          "qps_concurrent": 16852.056846201,
          "p50": 57.8,
          "p90": 137.6,
          "p95": 262.5,
          "p99": 581.3,
          "p999": 3453.0,
          "cold_start_ms": 49.7632,
          "ram_rss_peak_mb": 30.88671875,
          "docs_per_sec": 315546.0050298033
        },
        {
          "indexing_time_ms": 43.4487,
          "ram_retained_mb": 5.941810608,
          "qps_single": 8347.920628972,
          "qps_concurrent": 9640.545047495,
          "p50": 69.2,
          "p90": 183.6,
          "p95": 342.8,
          "p99": 1028.2,
          "p999": 5696.0,
          "cold_start_ms": 150.8284,
          "ram_rss_peak_mb": 32.5625,
          "docs_per_sec": 230156.48339305894
        },
        {
          "indexing_time_ms": 44.6561,
          "ram_retained_mb": 5.94670105,
          "qps_single": 13185.73920111,
          "qps_concurrent": 14582.855592908,
          "p50": 55.9,
          "p90": 124.1,
          "p95": 231.4,
          "p99": 521.8,
          "p999": 1941.6,
          "cold_start_ms": 56.4151,
          "ram_rss_peak_mb": 31.5,
          "docs_per_sec": 223933.57234509953
        }
      ],
      "java": [
        {
          "indexing_time_ms": 194.3775,
          "ram_retained_mb": 8.8834228515625,
          "qps_single": 2034.2926820287635,
          "qps_concurrent": 3494.3903677828393,
          "p50": 420.4,
          "p90": 910.6,
          "p95": 1455.3,
          "p99": 2966.8,
          "p999": 7271.3,
          "cold_start_ms": 686.5441,
          "ram_rss_peak_mb": 352.76953125,
          "docs_per_sec": 51446.2836490849
        },
        {
          "indexing_time_ms": 228.5516,
          "ram_retained_mb": 8.840682983398438,
          "qps_single": 1939.874821429673,
          "qps_concurrent": 3643.8940747321676,
          "p50": 446.3,
          "p90": 944.2,
          "p95": 1488.9,
          "p99": 3162.3,
          "p999": 6382.7,
          "cold_start_ms": 722.6357,
          "ram_rss_peak_mb": 365.51953125,
          "docs_per_sec": 43753.795641771925
        },
        {
          "indexing_time_ms": 241.3657,
          "ram_retained_mb": 8.858047485351562,
          "qps_single": 2213.4149947710284,
          "qps_concurrent": 3202.9301686354747,
          "p50": 420.7,
          "p90": 837.9,
          "p95": 1182.3,
          "p99": 2136.3,
          "p999": 4953.1,
          "cold_start_ms": 589.9096,
          "ram_rss_peak_mb": 364.1640625,
          "docs_per_sec": 41430.90753988657
        },
        {
          "indexing_time_ms": 203.805,
          "ram_retained_mb": 8.816001892089844,
          "qps_single": 1779.134411256313,
          "qps_concurrent": 3736.2369872703603,
          "p50": 452.5,
          "p90": 1049.4,
          "p95": 1690.6,
          "p99": 3739.3,
          "p999": 8880.2,
          "cold_start_ms": 652.6668,
          "ram_rss_peak_mb": 374.21484375,
          "docs_per_sec": 49066.509653835776
        },
        {
          "indexing_time_ms": 198.5867,
          "ram_retained_mb": 8.909759521484375,
          "qps_single": 2013.9820705246173,
          "qps_concurrent": 3307.55220423341,
          "p50": 428.3,
          "p90": 936.7,
          "p95": 1420.8,
          "p99": 3010.3,
          "p999": 5776.0,
          "cold_start_ms": 631.385,
          "ram_rss_peak_mb": 355.12890625,
          "docs_per_sec": 50355.83954011019
        }
      ],
      "scala": [
        {
          "indexing_time_ms": 317.9124,
          "ram_retained_mb": 6.847465515136719,
          "qps_single": 2726.267007373707,
          "qps_concurrent": 3409.533857907165,
          "p50": 218.8,
          "p90": 766.4,
          "p95": 1053.8,
          "p99": 2379.8,
          "p999": 6379.0,
          "cold_start_ms": 1052.2864,
          "ram_rss_peak_mb": 297.51171875,
          "docs_per_sec": 31455.205899486777
        },
        {
          "indexing_time_ms": 307.27,
          "ram_retained_mb": 6.8914031982421875,
          "qps_single": 2658.9105183282823,
          "qps_concurrent": 2812.47265529516,
          "p50": 226.8,
          "p90": 812.4,
          "p95": 1115.9,
          "p99": 2305.9,
          "p999": 5595.5,
          "cold_start_ms": 1044.4566,
          "ram_rss_peak_mb": 302.84765625,
          "docs_per_sec": 32544.667556220917
        },
        {
          "indexing_time_ms": 290.0153,
          "ram_retained_mb": 6.845428466796875,
          "qps_single": 2615.3971411250773,
          "qps_concurrent": 3571.332113574611,
          "p50": 230.8,
          "p90": 825.9,
          "p95": 1142.9,
          "p99": 2387.0,
          "p999": 5004.5,
          "cold_start_ms": 1055.7694,
          "ram_rss_peak_mb": 305.5625,
          "docs_per_sec": 34480.939453883984
        },
        {
          "indexing_time_ms": 316.9151,
          "ram_retained_mb": 6.849739074707031,
          "qps_single": 2361.1437342470394,
          "qps_concurrent": 3370.231186063555,
          "p50": 244.5,
          "p90": 897.7,
          "p95": 1245.8,
          "p99": 2782.7,
          "p999": 6497.4,
          "cold_start_ms": 1082.4866,
          "ram_rss_peak_mb": 323.58203125,
          "docs_per_sec": 31554.192274208457
        },
        {
          "indexing_time_ms": 283.9638,
          "ram_retained_mb": 6.8480224609375,
          "qps_single": 2706.846592203302,
          "qps_concurrent": 3219.3339029139843,
          "p50": 229.1,
          "p90": 807.6,
          "p95": 1113.1,
          "p99": 2112.3,
          "p999": 4600.6,
          "cold_start_ms": 1027.5396,
          "ram_rss_peak_mb": 278.4921875,
          "docs_per_sec": 35215.75637458014
        }
      ]
    },
    "iqr": {
      "go": {
        "indexing_time_ms": 10.135000000000005,
        "ram_retained_mb": 0.004859924999999876,
        "qps_single": 1282.0231228320008,
        "qps_concurrent": 1972.2811866670017,
        "p50": 1.8999999999999986,
        "p90": 13.5,
        "p95": 28.80000000000001,
        "p99": 59.5,
        "p999": 1421.4,
        "cold_start_ms": 6.651900000000005,
        "ram_rss_peak_mb": 1.19140625,
        "docs_per_sec": 70020.32074457814
      },
      "java": {
        "indexing_time_ms": 29.9649,
        "ram_retained_mb": 0.0427398681640625,
        "qps_single": 94.41786059909055,
        "qps_concurrent": 336.34187049875754,
        "p50": 25.600000000000023,
        "p90": 33.60000000000002,
        "p95": 68.10000000000014,
        "p99": 195.5,
        "p999": 1495.3000000000002,
        "cold_start_ms": 55.15909999999997,
        "ram_rss_peak_mb": 10.390625,
        "docs_per_sec": 6602.043898338263
      },
      "scala": {
        "indexing_time_ms": 26.89979999999997,
        "ram_retained_mb": 0.0022735595703125,
        "qps_single": 91.44945107822468,
        "qps_concurrent": 190.19995499318065,
        "p50": 4.0,
        "p90": 18.299999999999955,
        "p95": 29.800000000000182,
        "p99": 81.09999999999991,
        "p999": 1374.5,
        "cold_start_ms": 11.312799999999925,
        "ram_rss_peak_mb": 8.05078125,
        "docs_per_sec": 2926.747179675527
      }
    },
    "protocol": {
      "warmup_queries": 2000,
      "warmup_mixed_searches": 800,
      "warmup_mixed_inserts": 200,
      "readers": 4,
      "writers": 1,
      "mixed_searches": 8000,
      "mixed_inserts": 2000,
      "cooldown_seconds": 2,
      "bm25_absolute_tolerance": 0.0001,
      "latency_phase": "sequential",
      "dispersion": "inclusive IQR, Q3-Q1"
    }
  },
  "parity_check": {
    "passed": true,
    "queries_tested": 61,
    "match_rate_pct": 100.0,
    "notes": "Top-10 ordenado y BM25 con tolerancia absoluta 1e-4."
  },
  "results": {
    "go": {
      "indexing_time_ms": 35.3193,
      "ram_retained_mb": 5.946685791,
      "qps_single": 12673.177384816,
      "qps_concurrent": 16286.366438359,
      "cold_start_ms": 51.6438,
      "ram_rss_peak_mb": 31.5,
      "docs_per_sec": 283131.319137129,
      "loc": 1730,
      "artifact_mb": 2.12744140625,
      "relative_x": {
        "indexing": 1.0,
        "qps_concurrent": 1.0,
        "p99": 1.0,
        "ram_retained": 1.0,
        "loc": 1.8191377497371188
      },
      "normalized_scores": {
        "indexing": 100.0,
        "throughput": 100.0,
        "p99_stability": 100.0,
        "memory_efficiency": 100.0,
        "code_conciseness": 54.971098265895954
      },
      "latency_us": {
        "p50": 56.3,
        "p90": 136.5,
        "p95": 252.2,
        "p99": 540.0,
        "p999": 2262.0
      }
    },
    "java": {
      "indexing_time_ms": 203.805,
      "ram_retained_mb": 8.858047485351562,
      "qps_single": 2013.9820705246173,
      "qps_concurrent": 3494.3903677828393,
      "cold_start_ms": 652.6668,
      "ram_rss_peak_mb": 364.1640625,
      "docs_per_sec": 49066.509653835776,
      "loc": 951,
      "artifact_mb": 21.38398838043213,
      "relative_x": {
        "indexing": 5.770357849674258,
        "qps_concurrent": 4.660717528446188,
        "p99": 5.57462962962963,
        "ram_retained": 1.4895771857927582,
        "loc": 1.0
      },
      "normalized_scores": {
        "indexing": 17.329947744167217,
        "throughput": 21.455923769175186,
        "p99_stability": 17.938411454007905,
        "memory_efficiency": 67.13314419271241,
        "code_conciseness": 100.0
      },
      "latency_us": {
        "p50": 428.3,
        "p90": 936.7,
        "p95": 1455.3,
        "p99": 3010.3,
        "p999": 6382.7
      }
    },
    "scala": {
      "indexing_time_ms": 307.27,
      "ram_retained_mb": 6.8480224609375,
      "qps_single": 2658.9105183282823,
      "qps_concurrent": 3370.231186063555,
      "cold_start_ms": 1052.2864,
      "ram_rss_peak_mb": 302.84765625,
      "docs_per_sec": 32544.667556220917,
      "loc": 1211,
      "artifact_mb": 34.11591911315918,
      "relative_x": {
        "indexing": 8.699776043126562,
        "qps_concurrent": 4.832418175259232,
        "p99": 4.407037037037037,
        "ram_retained": 1.1515695803705697,
        "loc": 1.2733964248159833
      },
      "normalized_scores": {
        "indexing": 11.494548768184334,
        "throughput": 20.693573356704704,
        "p99_stability": 22.69098243549878,
        "memory_efficiency": 86.83800067714577,
        "code_conciseness": 78.53014037985136
      },
      "latency_us": {
        "p50": 229.1,
        "p90": 812.4,
        "p95": 1115.9,
        "p99": 2379.8,
        "p999": 5595.5
      }
    }
  },
  "code_samples": {
    "ast_parser": {
      "insight": "Los tres analizadores aplican precedencia y construyen un árbol booleano.",
      "go": "func (p *parser) parseOr() Node {\n\tleft := p.parseAnd()\n\tfor p.err == nil && p.token.kind == or {\n\t\tp.advance()\n\t\tleft = Or{Left: left, Right: p.parseAnd()}\n\t}\n\treturn left\n}",
      "java": "    private QueryNode parseOr() {\n        QueryNode left = parseAnd();\n        while (match(Tokenizer.Type.OR)) {\n            QueryNode right = parseAnd();\n            left = new OrNode(left, right);\n        }\n        return left;\n    }",
      "scala": "  private def parseOr(tokens: List[Token]): Parsed =\n    parseAnd(tokens).flatMap((left, rest) => orTail(left, rest))\n\n  @tailrec\n  private def orTail(left: Query, tokens: List[Token]): Parsed = tokens match\n    case Token.Or :: rest =>\n      parseAnd(rest) match\n        case Right((right, remaining)) => orTail(Query.Or(left, right), remaining)"
    },
    "bm25_ranking": {
      "insight": "La frecuencia del término y la longitud documental determinan el aporte BM25.",
      "go": "func Score(weight float64, frequency int, norm float64) float64 {\n\tf := float64(frequency)\n\treturn weight * (f * (k1 + 1)) / (f + norm)\n}\n\n// TopK keeps the worst retained result at the root. Ties favor lower IDs.\ntype TopK struct {\n\tlimit int",
      "java": "                double numerator = f * (k1 + 1);\n                double denominator = f + k1 * (1.0 - b + b * (docLen / avgdl));\n                double termScore = idf * (numerator / denominator);\n\n                scores.merge(docId, termScore, Double::sum);\n            }\n        }\n",
      "scala": "  private def score(doc: Document, position: Int, terms: List[WeightedTerm], avgdl: Double): Double =\n    val lengthNorm = params.k1 * (1 - params.b + params.b * doc.length / avgdl)\n    terms.foldLeft(0.0) { (sum, term) =>\n      val f = term.frequencies(position)\n      if f == 0 then sum\n      else sum + term.idf * (f * (params.k1 + 1)) / (f + lengthNorm)\n    }"
    },
    "concurrency": {
      "insight": "Go usa RWMutex, Java combina hilos virtuales con StampedLock y Scala coordina fibras ZIO con un bloqueo transaccional STM.",
      "go": "\ti.mu.Lock()\n\tdefer i.mu.Unlock()\n\tif err := ctx.Err(); err != nil {\n\t\treturn err\n\t}\n\tif err := i.validateID(doc.ID); err != nil {\n\t\treturn err\n\t}",
      "java": "                            long stamp = lock.writeLock();\n                            try {\n                                engine.publish(row);\n                            } finally { lock.unlockWrite(stamp); }\n                        }\n                    } else {\n                        for (int n = worker; n < reads; n += 4) {\n                            long stamp = lock.readLock();",
      "scala": "      lock <- TReentrantLock.make.commit\n      gate <- Promise.make[Nothing, Unit]\n      fibers <- ZIO.foreach((0 until 5).toList) { lane =>\n        val work =\n          if lane == 4 then\n            ZIO.foreachDiscard(0 until writes)(n => ZIO.scoped(lock.writeLock *> ZIO.attempt(write.accept(n))))\n          else\n            ZIO.foreachDiscard(lane until 4 * writes by 4)(n => ZIO.scoped(lock.readLock *> ZIO.attempt(read.accept(n))))"
    }
  }
};
