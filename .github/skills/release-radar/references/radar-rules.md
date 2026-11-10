# Radar rules

1. **Evidence.** Every row links to its source. Use the item's `url` from the collection. A source that is not in the collection (a docs page, an event announcement) goes under `## 手動で追加した項目` (or `## Manual additions`). The checker cannot verify those rows. The checker reads the link target, not the link text: if the text shows a URL, it must be the same URL as the target.
2. **Status, verbatim.** Copy the status words the source uses for the item:
   - VS Code release notes: the label in the heading, for example `Preview`, `Experimental` or `Research Preview`.
   - GitHub Changelog: the words in the title, for example `public preview`, `limited public preview` or `generally available`.
   - `GA` may abbreviate `generally available` or `general availability`. Every other word must match.
   - If the title or heading has no label, you may copy a status word from the text when it clearly describes this item. Otherwise write `表記なし`.
   - Never infer GA from a missing label.
3. **Stable and Insiders.** VS Code rows name the version. Rows from Insiders release notes say `Insiders`. Insiders notes change until the release, so check them again before you rely on them.
4. **Preview and Experimental are different words.** Keep the one the source uses. A feature can change its label between versions, so cite the version you checked.
5. **Dates.** Changelog rows show the post date (`2026-10-07` or `10/7`). VS Code rows show the version, and may add the date.
6. **Unknowns.** If you cannot confirm something, write `未確認` and say what would confirm it.
7. **Checked date.** The metadata block records `since`, `checkedAt` and the collection file. When you update the radar, collect again and check again.
