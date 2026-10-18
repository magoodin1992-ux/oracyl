# ORACYL — CANONICAL USER-EXPERIENCE CORPUS

**Version:** 1.0.0 · **Scope:** phone portrait + desktop / Samsung DeX · **Status:** design and intended behavior, not a shipped Android APK.

## 0. Read this first: the point of the assignment

Victor is being given the exact intended **end-user experience** for *Oracyl*, the Android application that will act as Victor's primary human-facing workspace. This is not a brainstorm, a style inspiration board, or an invitation to redesign the UI. The Operator has selected the aesthetic, layout, information architecture, and the two principal display modes. The attached self-contained HTML/CSS/JS is the reference implementation of visual appearance; the accompanying screenshots are for human verification. Victor can inspect code and machine-readable layout without parsing images.

**Outcome:** a single Oracyl Android application that works on the phone as a touch-first portrait chat/productivity interface and, when attached to Samsung DeX / a desktop-sized display, works as a full-screen or windowed command center. Both modes access the **same** chats, projects, research, tasks, apps, browser tabs, skills, accounts, pinned shortcuts, library, and settings. A resize/orientation switch changes the presentation, not the underlying identity or data.

**Authority boundary:** This is an application UI specification. It is not an instruction to alter Victor's learning kernel or grant autonomous access, credentials, permissions, or external-action authority. The Operator approves any changes to aesthetic canon.

## 1. User's primary goal

Open one application and communicate with Victor while being able to switch among research, scheduled work, in-app utilities, optional web tabs, projects, skills, accounts, and artifacts without scattering work across unrelated apps. Chat remains a primary surface, not the only surface. The same persistent resource should be accessible from its category, from a pin, from search, and from another layout mode where applicable. The system should make ongoing work legible and resumable, not just render pretty controls.

### What success feels like

- **Phone:** open Oracyl, see a clean black conversation screen, send or read messages, pull out a dark drawer to navigate, open Settings when needed, return to the same chat, and use large touch targets without horizontal scrolling.
- **Desktop / DeX:** Oracyl opens as the leftmost fixed app tab; a companion tab strip supports optional web/document tabs without making the application look like a site running inside another browser. A persistent left sidebar provides the eight collapsible categories, mixed-type Pinned shortcuts, Recent Chats, and anchored Library/Settings. The broad center panel is for active chat or other selected workspace. Composer stays accessible near the bottom.
- **Both:** a chat created on phone can be resumed on DeX without becoming a different object; pins, tabs, tasks and settings remain consistent; interrupted sessions recover; honest status is shown when a service is unavailable. Live features are not to be simulated as successes.

## 2. Immutable top-level navigation

Exactly in this order:

`Chats → Research → Tasks → Apps → Tabs → Projects → Skills → Accounts`

Each category supports a dropdown that exposes its children when expanded, with the standard category rows collapsed by default unless the interface is currently showing that category. An expanded Chats state may show the lower Pinned and Recent sections separately. **Do not confuse `Apps` (hosted interactive tools) with `Skills` (reusable capabilities).** `Tabs` means saved/open in-app browser or content tabs; it is not a synonym for the upper tab rail.

Below the eight categories: **Pinned** (links to chats, tasks, research, projects, browser tabs, etc.) followed by **Recent Chats** (chat recency only). At the bottom: **Library** and **Settings**. The phone drawer additionally has a prominent **New chat** action. Scroll overflow rather than dropping categories or pushing Library/Settings out of reach.

### Categories as end-user verbs

| Section | Human need | Intended completed product behavior |
|---|---|---|
| Chats | Talk to Victor; resume context | Open/create/send/continue a named persistent conversation |
| Research | Ask questions, investigate, inspect results | Create/continue research; browse reports, provenance, outputs |
| Tasks | Initiate and follow up work | Create/review/pause tasks with schedules and honest run states |
| Apps | Use utilities inside Oracyl | Launch/switch hosted interactive app workspaces |
| Tabs | Browse without leaving Oracyl | Maintain a real browser/document tab session; save via ✓ |
| Projects | Maintain coherent work areas | Group resources and return to a project's working state |
| Skills | Inspect/use Victor skills | View available capabilities and invoke with correct permissions |
| Accounts | Manage connections and scopes | Connect, inspect and disconnect services with user consent |
| Pinned | One-click return to important things | Pin mixed resource types; no copying data into duplicate items |
| Recent Chats | Continue latest conversations | Timestamp-ordered list with quick open |
| Library | Manage artifacts | Browse and open the user's permitted files/artifacts |
| Settings | Configure Oracyl | Open grouped settings, adjust supported preferences |

## 3. Desktop and DeX canonical surface

**Primary reference:** `reference/Desktop_1920x1080.png`. The browser render is exactly **1920 × 1080 screenshot pixels** at device scale factor 1. Canonical CSS source: `prototype/Oracyl_Responsive_Desktop_Portrait.html`.

**Layout:** 52 px-high top tab strip; 335 px-wide left sidebar; 81 px sidebar header; 43 px standard navigation rows; approximately 205 px allocated to Pinned in the reference; flexible Recent Chats, with footer (83 px) anchored to bottom; remaining width belongs to the main content. The message composer floats anchored near bottom of main canvas. These are **CSS pixels at the reference viewport**, not density-independent absolute device dimensions. Use adaptive constraints for DeX windows smaller than the canonical screenshot.

**Tab behavior:** far-left `Oracyl` tab is fixed and non-closeable. The reference has a `Research Notes` secondary tab; other tabs may be opened. A secondary tab has ✓ **save to Tabs** and X **close tab**. The upper bar is part of the Oracyl application, not Chrome's address bar/bookmarks/extension UI. No external brand, incidental browser logo, or operating-system taskbar should be drawn into the application.

**Sidebar:** header branding with the exact semicolon yin-yang icon; eight ordered expandable categories; Pinned mixed shortcuts; Recent Chats; Library and Settings anchored in the footer. Category expansion must not silently destroy items in the other lists.

**Main canvas:** left-aligned assistant content with avatar/name and action icons; user content right-aligned in rounded magenta message bubble; readable near-white text against almost-black background. Visible plus/attachment, voice/mic, and send transitions in the bottom composer. Example conversation content in the render is **fixture only**; it is not an end-user default prompt or hardcoded Victor personality requirement.

**Resizing:** full-window and windowed DeX must adjust fluidly. At portrait/small widths, transition to the phone navigation model rather than stretching/shrinking the 1920 design until unreadable. Respect Android window/safe-area insets, keyboard appearance and system bars; no OS status bar simulation in the native application.

## 4. Phone portrait canonical surface

**References:** `reference/Portrait_Chat_360x780.png`, `reference/Portrait_Menu_360x780.png`, `reference/Portrait_Settings_360x780.png`. Captured using **360 × 780 CSS viewport at deviceScaleFactor = 2**, resulting in **720 × 1560 PNG pixels**. These screenshots are web-preview evidence, not proof of physical-device performance.

**Chat page:** full black viewport behind content; Android system status bar at top (provided by Android runtime, never faked in the native host); compact top row with circular menu on left and new-chat / overflow on right; a right-aligned dark plum bubble for user; assistant response below, white on black, with copy/feedback/speaker/share/more; nearly full-width rounded composer anchored above bottom safe area with add, text field, microphone and pink voice/send control. Use keyboard inset and draft preservation when IME opens.

**Drawer:** menu opens an approximately 80%-width charcoal panel over chat, dims remaining chat with a scrim, and closes via outside tap, back, or explicit navigation. The drawer contains the same eight ordered expandable categories, then Pinned and Recent Chats, followed by Library/Settings and a pink New Chat action. The lists must remain independently usable by touch and scroll when taller than the viewport. The desktop sidebar does not remain visible as a competing second column.

**Settings:** full-screen native page style: back action, centered Oracyl logo/identity without any copied person's account details, grouped rounded dark cards, separators, and controls. The grouped rows are:

1. Account, Workspace, Connections;
2. Personalization, Memory, Skills, Accent color;
3. General, Usage and limits, Notifications;
4. Privacy and permissions, About Oracyl.

These are **placeholder controls in the reference**, not evidence that those capabilities have been integrated. Settings must visibly communicate disconnected/preview states until real data exists. Do not reproduce incidental text, profile names, email addresses, notifications, or external brands from the original mobile screenshots.

## 5. Consistent identity and visual tokens

**Name:** `Oracyl` everywhere. Avoid `ChatGPT`, `OpenAI` branding, reference app account names, copied plan/upgrade language, and browser/notification detritus. `Victor` is the underlying assistant/system the user talks to; `Oracyl` is the product/application identity.

**Emblem:** `prototype/Oracyl_Semicolon_YinYang.svg` is the canonical source. A purple and near-black yin-yang with **one upper round dot and one visibly curved lower comma/tail**. The `;` distinction is intentional: **not two circular yin-yang dots**. Use the exact same vector geometry for title-tab, drawer/sidebar brand, assistant avatar, and app icon if applicable; change only rendered size.

**Desktop sample tokens:** app black `#050607`; text `#f5f5f6`; selected panel `#1c222b`; separator `#21262e`; purple `#b44de9`; magenta-pink `#ee3cbc`. **Phone sample tokens:** black `#000000`; drawer `#212121`; settings card `#303030`; user bubble `#50002e`; voice pink `#ee66a3`; purple `#b24ce9`. The exact implementation source remains the reference for gradients, radii, typography and small element spacing. Do not flatten phone settings into a white/material-default list.

## 6. Shared behavior across orientations and screens

This is one repository of user content exposed by two responsive presentations. A pinned chat and its Recent Chats entry must resolve to the same `chat_id`; saving a tab with ✓ adds a `tab_id` to the `Tabs` category rather than copying the website. Phone drawer and DeX persistent sidebar are two presentations of the same `navigation[]` data model.

**Behavior invariants:**

- Open category → reveal that category's child items without reordering other categories.
- Pin item → stable reference in Pinned; unpin → remove shortcut, not underlying object.
- Mark browser tab ✓ → save as a navigable entry in Tabs; X → close only that secondary tab; Oracyl primary tab cannot be closed.
- Open settings → preserve active chat/tab and draft; return to previous surface via back.
- Send a message → show pending/error/completed states according to the actual Victor service response. A locally echoed fixture is **not** proof of sending to Victor.
- Switch phone↔DeX or resize window → no data loss; preserve active thread, selected workspace, draft and pins.
- File/voice/account interactions → request appropriate user/platform permission; if not implemented, say so.
- Browser content is untrusted: sandbox page execution and do not provide privileged native/agent bridge APIs to arbitrary sites.

### Representative journeys

**Journey A — phone to DeX:** Operator asks Victor a question on the phone, exits app, starts DeX later, opens Oracyl, selects same chat and continues. Expected: stable thread identity, message history, draft recovery and consistent pin state.

**Journey B — web research:** Operator opens an in-app research page in a secondary tab, taps ✓; `Tabs` gains a saved reference. Operator can return to Oracyl chat (fixed first tab), resume the saved page, and optionally pin it to mixed-type Pinned. Expected: save-to-Tabs and pin-to-Pinned are not conflated.

**Journey C — tasks:** Operator opens Tasks, examines status and run result, then resumes a chat. Expected: no invented successful task executions; durable status and explicit user approval for externally consequential actions.

**Journey D — skills/apps:** Operator invokes a capability from Skills or launches a tool from Apps. Expected: Skills is metadata/execution contract, Apps is hosted visual workspace; explicit permission-scoped integration.

**Journey E — settings:** Operator opens phone drawer → Settings → e.g. Notifications → back → chat. Expected: settings nested navigation behaves normally; placeholder entries identify themselves as preview until backend exists.

## 7. Status accounting: prototype vs shipped requirements

The bundled HTML is a **working offline visual/interaction preview**, not an Android package and not a live connection to Victor. The current preview supports interface transitions (sidebar/drawer, expanding categories, demo pin/unpin or add pin, local new chat, sample copy/reaction and some browser-native share/speech synthesis actions). It does **not** provide durable storage, the actual model connection, real browser tab engine, secure accounts, Android speech capture, or a live task scheduler. A test that a menu clicked is not equivalent to fulfilling the real user goal behind it.

**Do not erase this distinction in acceptance reports.** Mark each feature `visual_pass`, `local_interaction_pass`, `backend_integrated`, `host_verified`, or `blocked`; never promote a demo to `host_verified` without evidence.

## 8. Implementation freedom vs locked appearance

Victor may use WebView, Kotlin Compose, or a measured hybrid. Android integration requires actual lifecycle-aware state handling, app navigation, safe insets, back dispatch, keyboard behavior, permissions, and recovery. Victor's exact implementation decisions remain open as long as the **Operator-approved UI** and behavioral outcomes are preserved. **Do not perform autonomous aesthetic redesign or kernel modifications.** When design conflicts with Android ergonomics or accessibility, document the conflict and propose the least-invasive reversible choice for Operator decision.

## 9. Acceptance methodology

The attached JSON test plan is the behavior target and the PNG renders are human visual baselines. Pixel-level screenshot difference may help locate deviations but cannot be treated as a universal pass/fail oracle across renderers, font rasterizers, density and host OS chrome. Validate structured geometry, canonical SVG path, interaction state, adaptive constraints, and app lifecycle separately. Verify on real Android phone and in Samsung DeX; Chromium screenshots alone certify neither Android packaging nor Victor integration.

**For each failure:** describe affected user journey, reproduction instructions, observed vs expected, impact, artifact path, and whether the failure is visual/interaction/integration/native. Fix within the already-approved design; do not introduce unrelated redesigns or unrequested features.

## 10. Package map

- `ORACYL_UI_CONTRACT.json` — machine-readable objective, navigation, per-category role, layout tokens, capability boundaries, and API intent.
- `ORACYL_UI_NODES.jsonl` — atomic knowledge statements with stable IDs, evidence references, and review statuses.
- `ORACYL_ACCEPTANCE_TESTS.json` — proposed graduation/acceptance checks, not certified pass claims.
- `schemas/oracyl_ui_contract.schema.json` — contract schema.
- `prototype/Oracyl_Responsive_Desktop_Portrait.html` — exact bundled working preview.
- `prototype/Oracyl_Semicolon_YinYang.svg` — canonical logo source.
- `reference/*.png` — independently generated screenshot baselines from the preview, not the private screenshots uploaded by Operator.
- `RECEIPTS.json` — source checksums, viewport reference dimensions and provenance.
- `tests/validate_corpus.py` — local corpus integrity checks (does not test Android runtime).

**Instruction to Victor:** ingest and obey this specification as the end-user UI goal; do not claim an Android feature has shipped because a demo control can be clicked. Build one Oracyl app serving both phone and DeX, report evidence against the acceptance plan, preserve the exact approved aesthetic, and surface genuine ambiguities to the Operator instead of inventing a different design.

## 11. Current bounded preview smoke-test receipt

An offline Chromium smoke run at 1920 × 1080 and 360 × 780 exercised desktop navigation, a secondary-tab checkmark, dropdown expansion, portrait drawer/settings, and local-only chat send, without uncaught JavaScript exceptions. **These are browser preview tests only; Android/DeX host and Victor backend remain unverified.** See `PREVIEW_SMOKE_RESULTS.json` and `tests/browser_smoke.py`.