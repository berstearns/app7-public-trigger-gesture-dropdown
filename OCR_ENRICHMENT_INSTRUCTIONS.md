# OCR Enrichment: Generate `conversations_ocr.json` for All Chapters

## Goal

Enrich every chapter's bubble data with OCR text and per-word token bounding boxes.
The app already supports this: `FileImageDataSource` loads `conversations_ocr.json` in preference to `conversations.json`. Only one chapter has it so far (Chapitre_0420 in batch-01-hq-tokens). All others need processing.

## Reference Example

**Input** — `conversations.json` (version 1, bubbles only):
```json
{
  "version": 1,
  "conversations": {
    "img_002": [
      {"x": 0.7707, "y": 0.278, "width": 0.2262, "height": 0.2599}
    ]
  }
}
```

**Output** — `conversations_ocr.json` (version 2, bubbles + OCR):
```json
{
  "version": 2,
  "conversations": {
    "img_002": [
      {
        "x": 0.7159,
        "y": 0.0008,
        "width": 0.206,
        "height": 0.1341,
        "text": "NR.1I...\nNR.",
        "tokens": [
          {
            "text": "NR.1I...",
            "confidence": 81.1,
            "x": 0.785815,
            "y": 0.041753,
            "width": 0.086145,
            "height": 0.020878
          },
          {
            "text": "NR.",
            "confidence": 92.3,
            "x": 0.78207,
            "y": 0.064237,
            "width": 0.033709,
            "height": 0.020878
          }
        ],
        "ocrConfidence": 86.7
      }
    ]
  }
}
```

A complete reference file exists at:
```
~/.local/share/manga-reader/comics/batch-01-hq-tokens/Chapitre_0420/conversations_ocr.json
```

## Coordinate System

All coordinates are **page-normalized** (0.0 to 1.0 relative to full page dimensions):
- `x`, `y` — top-left corner of the bounding box
- `width`, `height` — size of the bounding box
- Bubble coords come from `conversations.json`
- Token coords are also page-normalized (NOT relative to the bubble)

## Per-Chapter Processing Steps

For each chapter directory:

1. **Read** `conversations.json` to get bubble bounding boxes per page
2. **For each page** (e.g. `img_002`):
   a. Load the page image from `images/` subfolder
   b. For each bubble, crop the region defined by `{x, y, width, height}`
   c. Run OCR on the cropped region to extract:
      - `text` — full text content of the bubble (lines joined by `\n`)
      - `tokens[]` — per-word bounding boxes with `text`, `confidence`, `x`, `y`, `width`, `height`
      - `ocrConfidence` — average confidence across all tokens
   d. Token bounding boxes from OCR will be relative to the crop; **convert them back to page-normalized coordinates**:
      ```
      token.x_page = bubble.x + (token.x_crop * bubble.width)
      token.y_page = bubble.y + (token.y_crop * bubble.height)
      token.width_page = token.width_crop * bubble.width
      token.height_page = token.height_crop * bubble.height
      ```
3. **Write** `conversations_ocr.json` with version 2, preserving bubble order from input
4. **Mark the chapter as done** in the checklist below

## OCR Tool

Use Tesseract with German language pack for One Piece DE batches, French for Chainsawman FR:
```bash
# German
tesseract image.png stdout -l deu --oem 1 tsv

# French  
tesseract image.png stdout -l fra --oem 1 tsv
```

TSV output gives per-word bounding boxes (level 5 = word). Convert pixel coords to normalized using image dimensions.

Alternative: Use Claude Vision API for higher accuracy (especially on manga fonts). The reference file was generated with `claude-vision-zero-shot` method.

## Directory Layout

```
~/.local/share/manga-reader/comics/
├── batch-01-hq-tokens/          # One Piece DE (Chapitre_0420-0444)
├── onepiece-de-batch-01-hq/     # One Piece DE (Chapitre_0420-0444) — duplicate chapters, different asset pack
├── onepiece-de-batch-02-hq/     # One Piece DE (Chapitre_0445-0469)
├── onepiece-de-batch-03-hq/     # One Piece DE (Chapitre_0470-0494)
└── chainsawman-fr-lq/           # Chainsaw Man FR (Chapitre_0001-0220)
```

Each chapter folder contains:
```
Chapitre_XXXX/
├── images.json          # image manifest
├── conversations.json   # input: bubble bounding boxes (version 1)
├── conversations_ocr.json  # output: enriched with OCR (version 2) ← GENERATE THIS
└── images/              # page image files (jpg/png)
```

---

## Progress Checklist

### batch-01-hq-tokens (One Piece DE, lang: `deu`)

- [x] Chapitre_0420
- [ ] Chapitre_0421
- [ ] Chapitre_0422
- [ ] Chapitre_0423
- [ ] Chapitre_0424
- [ ] Chapitre_0425
- [ ] Chapitre_0426
- [ ] Chapitre_0427
- [ ] Chapitre_0428
- [ ] Chapitre_0429
- [ ] Chapitre_0430
- [ ] Chapitre_0431
- [ ] Chapitre_0432
- [ ] Chapitre_0433
- [ ] Chapitre_0434
- [ ] Chapitre_0435
- [ ] Chapitre_0436
- [ ] Chapitre_0437
- [ ] Chapitre_0438
- [ ] Chapitre_0439
- [ ] Chapitre_0440
- [ ] Chapitre_0441
- [ ] Chapitre_0442
- [ ] Chapitre_0443
- [ ] Chapitre_0444

### onepiece-de-batch-02-hq (One Piece DE, lang: `deu`)

- [ ] Chapitre_0445
- [ ] Chapitre_0446
- [ ] Chapitre_0447
- [ ] Chapitre_0448
- [ ] Chapitre_0449
- [ ] Chapitre_0450
- [ ] Chapitre_0451
- [ ] Chapitre_0452
- [ ] Chapitre_0453
- [ ] Chapitre_0454
- [ ] Chapitre_0455
- [ ] Chapitre_0456
- [ ] Chapitre_0457
- [ ] Chapitre_0458
- [ ] Chapitre_0459
- [ ] Chapitre_0460
- [ ] Chapitre_0461
- [ ] Chapitre_0462
- [ ] Chapitre_0463
- [ ] Chapitre_0464
- [ ] Chapitre_0465
- [ ] Chapitre_0466
- [ ] Chapitre_0467
- [ ] Chapitre_0468
- [ ] Chapitre_0469

### onepiece-de-batch-03-hq (One Piece DE, lang: `deu`)

- [ ] Chapitre_0470
- [ ] Chapitre_0471
- [ ] Chapitre_0472
- [ ] Chapitre_0473
- [ ] Chapitre_0474
- [ ] Chapitre_0475
- [ ] Chapitre_0476
- [ ] Chapitre_0477
- [ ] Chapitre_0478
- [ ] Chapitre_0479
- [ ] Chapitre_0480
- [ ] Chapitre_0481
- [ ] Chapitre_0482
- [ ] Chapitre_0483
- [ ] Chapitre_0484
- [ ] Chapitre_0485
- [ ] Chapitre_0486
- [ ] Chapitre_0487
- [ ] Chapitre_0488
- [ ] Chapitre_0489
- [ ] Chapitre_0490
- [ ] Chapitre_0491
- [ ] Chapitre_0492
- [ ] Chapitre_0493
- [ ] Chapitre_0494

### chainsawman-fr-lq (Chainsaw Man FR, lang: `fra`)

- [ ] Chapitre_0001
- [ ] Chapitre_0002
- [ ] Chapitre_0003
- [ ] Chapitre_0004
- [ ] Chapitre_0005
- [ ] Chapitre_0006
- [ ] Chapitre_0007
- [ ] Chapitre_0008
- [ ] Chapitre_0009
- [ ] Chapitre_0010
- [ ] Chapitre_0011
- [ ] Chapitre_0012
- [ ] Chapitre_0013
- [ ] Chapitre_0014
- [ ] Chapitre_0015
- [ ] Chapitre_0016
- [ ] Chapitre_0017
- [ ] Chapitre_0018
- [ ] Chapitre_0019
- [ ] Chapitre_0020
- [ ] Chapitre_0021
- [ ] Chapitre_0022
- [ ] Chapitre_0023
- [ ] Chapitre_0024
- [ ] Chapitre_0025
- [ ] Chapitre_0026
- [ ] Chapitre_0027
- [ ] Chapitre_0028
- [ ] Chapitre_0029
- [ ] Chapitre_0030
- [ ] Chapitre_0031
- [ ] Chapitre_0032
- [ ] Chapitre_0033
- [ ] Chapitre_0034
- [ ] Chapitre_0035
- [ ] Chapitre_0036
- [ ] Chapitre_0037
- [ ] Chapitre_0038
- [ ] Chapitre_0039
- [ ] Chapitre_0040
- [ ] Chapitre_0041
- [ ] Chapitre_0042
- [ ] Chapitre_0043
- [ ] Chapitre_0044
- [ ] Chapitre_0045
- [ ] Chapitre_0046
- [ ] Chapitre_0047
- [ ] Chapitre_0048
- [ ] Chapitre_0049
- [ ] Chapitre_0050
- [ ] Chapitre_0051
- [ ] Chapitre_0052
- [ ] Chapitre_0053
- [ ] Chapitre_0054
- [ ] Chapitre_0055
- [ ] Chapitre_0056
- [ ] Chapitre_0057
- [ ] Chapitre_0058
- [ ] Chapitre_0059
- [ ] Chapitre_0060
- [ ] Chapitre_0061
- [ ] Chapitre_0062
- [ ] Chapitre_0063
- [ ] Chapitre_0064
- [ ] Chapitre_0065
- [ ] Chapitre_0066
- [ ] Chapitre_0067
- [ ] Chapitre_0068
- [ ] Chapitre_0069
- [ ] Chapitre_0070
- [ ] Chapitre_0071
- [ ] Chapitre_0072
- [ ] Chapitre_0073
- [ ] Chapitre_0074
- [ ] Chapitre_0075
- [ ] Chapitre_0076
- [ ] Chapitre_0077
- [ ] Chapitre_0078
- [ ] Chapitre_0079
- [ ] Chapitre_0080
- [ ] Chapitre_0081
- [ ] Chapitre_0082
- [ ] Chapitre_0083
- [ ] Chapitre_0084
- [ ] Chapitre_0085
- [ ] Chapitre_0086
- [ ] Chapitre_0087
- [ ] Chapitre_0088
- [ ] Chapitre_0089
- [ ] Chapitre_0090
- [ ] Chapitre_0091
- [ ] Chapitre_0092
- [ ] Chapitre_0093
- [ ] Chapitre_0094
- [ ] Chapitre_0095
- [ ] Chapitre_0096
- [ ] Chapitre_0097
- [ ] Chapitre_0098
- [ ] Chapitre_0099
- [ ] Chapitre_0100
- [ ] Chapitre_0101
- [ ] Chapitre_0102
- [ ] Chapitre_0103
- [ ] Chapitre_0104
- [ ] Chapitre_0105
- [ ] Chapitre_0106
- [ ] Chapitre_0107
- [ ] Chapitre_0108
- [ ] Chapitre_0109
- [ ] Chapitre_0110
- [ ] Chapitre_0111
- [ ] Chapitre_0112
- [ ] Chapitre_0113
- [ ] Chapitre_0114
- [ ] Chapitre_0115
- [ ] Chapitre_0116
- [ ] Chapitre_0117
- [ ] Chapitre_0118
- [ ] Chapitre_0119
- [ ] Chapitre_0120
- [ ] Chapitre_0121
- [ ] Chapitre_0122
- [ ] Chapitre_0123
- [ ] Chapitre_0124
- [ ] Chapitre_0125
- [ ] Chapitre_0126
- [ ] Chapitre_0127
- [ ] Chapitre_0128
- [ ] Chapitre_0129
- [ ] Chapitre_0130
- [ ] Chapitre_0131
- [ ] Chapitre_0132
- [ ] Chapitre_0133
- [ ] Chapitre_0134
- [ ] Chapitre_0135
- [ ] Chapitre_0136
- [ ] Chapitre_0137
- [ ] Chapitre_0138
- [ ] Chapitre_0139
- [ ] Chapitre_0140
- [ ] Chapitre_0141
- [ ] Chapitre_0142
- [ ] Chapitre_0143
- [ ] Chapitre_0144
- [ ] Chapitre_0145
- [ ] Chapitre_0146
- [ ] Chapitre_0147
- [ ] Chapitre_0148
- [ ] Chapitre_0149
- [ ] Chapitre_0150
- [ ] Chapitre_0151
- [ ] Chapitre_0152
- [ ] Chapitre_0153
- [ ] Chapitre_0154
- [ ] Chapitre_0155
- [ ] Chapitre_0156
- [ ] Chapitre_0157
- [ ] Chapitre_0158
- [ ] Chapitre_0159
- [ ] Chapitre_0160
- [ ] Chapitre_0161
- [ ] Chapitre_0162
- [ ] Chapitre_0163
- [ ] Chapitre_0164
- [ ] Chapitre_0165
- [ ] Chapitre_0166
- [ ] Chapitre_0167
- [ ] Chapitre_0168
- [ ] Chapitre_0169
- [ ] Chapitre_0170
- [ ] Chapitre_0171
- [ ] Chapitre_0172
- [ ] Chapitre_0173
- [ ] Chapitre_0174
- [ ] Chapitre_0175
- [ ] Chapitre_0176
- [ ] Chapitre_0177
- [ ] Chapitre_0178
- [ ] Chapitre_0179
- [ ] Chapitre_0180
- [ ] Chapitre_0181
- [ ] Chapitre_0182
- [ ] Chapitre_0183
- [ ] Chapitre_0184
- [ ] Chapitre_0185
- [ ] Chapitre_0186
- [ ] Chapitre_0187
- [ ] Chapitre_0188
- [ ] Chapitre_0189
- [ ] Chapitre_0190
- [ ] Chapitre_0191
- [ ] Chapitre_0192
- [ ] Chapitre_0193
- [ ] Chapitre_0194
- [ ] Chapitre_0195
- [ ] Chapitre_0196
- [ ] Chapitre_0197
- [ ] Chapitre_0198
- [ ] Chapitre_0199
- [ ] Chapitre_0200
- [ ] Chapitre_0201
- [ ] Chapitre_0202
- [ ] Chapitre_0203
- [ ] Chapitre_0204
- [ ] Chapitre_0205
- [ ] Chapitre_0206
- [ ] Chapitre_0207
- [ ] Chapitre_0208
- [ ] Chapitre_0209
- [ ] Chapitre_0210
- [ ] Chapitre_0211
- [ ] Chapitre_0212
- [ ] Chapitre_0213
- [ ] Chapitre_0214
- [ ] Chapitre_0215
- [ ] Chapitre_0216
- [ ] Chapitre_0217
- [ ] Chapitre_0218
- [ ] Chapitre_0219
- [ ] Chapitre_0220

---

## Resuming

1. Read this file
2. Find the first unchecked `[ ]` item
3. Process that chapter
4. Mark it `[x]` in this file
5. Repeat

## Skipping `onepiece-de-batch-01-hq`

This batch has the same chapters (0420-0444) as `batch-01-hq-tokens`. Process only `batch-01-hq-tokens` — the generated `conversations_ocr.json` can be copied over:
```bash
for ch in ~/.local/share/manga-reader/comics/batch-01-hq-tokens/Chapitre_*/; do
  dest=~/.local/share/manga-reader/comics/onepiece-de-batch-01-hq/$(basename $ch)/
  [ -f "$ch/conversations_ocr.json" ] && cp "$ch/conversations_ocr.json" "$dest/"
done
```

## Validation

After generating a `conversations_ocr.json`, verify:
1. It parses as valid JSON
2. `version` is `2`
3. Same image keys as `conversations.json`
4. Same number of bubbles per image
5. Each bubble has `text`, `tokens`, and `ocrConfidence`
6. Token coordinates fall within page bounds (0.0-1.0)
7. Token coordinates fall within or near their parent bubble bounds
