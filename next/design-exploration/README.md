# Pingora hero exploration

Ten distinct hero directions, each with light/dark variants: 20 frames total. No production design is selected. These are visual concepts, not the released app or a connected backend.

## Viewers

- [Combined chooser](index.html): all ten concepts; theme controls; shortlist exact labels such as 02D or 08L. Shortlist state stays in this viewer and is not sent to chat.
- [Labelled Codex image frames](codex-images.html): concepts 01–05, each L/D, click to enlarge.
- [Labelled Flash screenshots](flash-previews.html): concepts 06–10, each L/D, click to enlarge.

L means light; D means dark. All generated device/message content is illustrative. The physical hub in 01 is a visual metaphor, not hardware required to use Pingora.

## Provenance

01–05: Codex gpt-6.1-sol directed ten separate built-in image_gen calls. Original PNGs in images/; full prompts and provenance in image-prompts.json. Gallery uses one browser-rasterized 900px display copy of 05L to stay below the inline preview payload limit; the original remains unchanged and available in the image-only viewer.

06–10: Antigravity gemini-3.8-flash authored self-contained HTML/CSS/JS in flash/. Parent reviewed copy/demo states, removed speculative latency/security claims and recaptured light/dark frames with html_preview. Source theme switches work. Device calls/transfers/replies/clipboard actions are clearly labelled local visual demos, not real system operations. No registry components or npm dependencies installed; skeuomorphism-ui was a material-language reference.

## Checks

- All five source theme toggles tested dark/light and back.
- All ten coded frames recaptured at width 1360 (866px captured height including renderer gutter).
- All gallery image paths resolve; source/gallery JavaScript syntax checked.
- Combined chooser checked at widths 728 and 390: 20 images loaded, ten selections work, light/dark labels update, shortlist records theme, no horizontal overflow.
- Desktop source captures reviewed visually. This is not a full accessibility audit, mobile source interaction audit, product build, or backend test.

See qa-evidence.json for captured console evidence. build_gallery.py regenerates index.html and concepts.json. Code-source links can be opened directly from flash/; all files are local/offline concept assets.
