# Secure Notepad & Cultural Data Forensic Engine for Android

An authentic, ultra-lightweight, and security-oriented text editor application built natively for **Android API Level 29**. This project stands as a testament to pure, old-school software engineering: built with zero heavy frameworks, completely free of Modern Android Jetpack/AndroidX dependencies, and compiled using a blistering fast, pure shell-script pipeline.

## 🚀 Key Features

*   **Secure Storage:** Features both standard and encoding-aware saving configurations utilizing dynamic buffer chunks (`MaxFileChunk`).
*   **Cultural & Historical Forensics:** Contains an optimized historical database scanner (`1900-2100`) to pinpoint what years specific Gregorian dates (e.g., May 21st) overlapped with Ramadan, Eid al-Fitr, or Eid al-Adha, paired with a dedicated **Karbala Calendar** subsystem.
*   **Asynchronous Script Transliteration (New!):** Features a high-performance, non-blocking Latin-to-Arabic/Persian transliteration engine. It operates under two rigorous operational scope layers offloaded onto a background processing worker:
    *   *Global Conversion:* Processes the absolute raw document space without breaking formatting boundaries.
    *   *Targeted Selection Isolation:* Isolates a strict user-selected text range (e.g., processing `mdrsQ` into `مدرسة` or vice-versa) using dynamic `min`/`max` cursor memory boundaries to prevent UI layout freezing and handle complex reverse selection vectors safely.
*   **Adaptive Character Purging Engine:** Allows power-users to sweep junk bytes, strip invisible BOM signatures, normalize multi-platform mixed line endings into clean Unix LF (`\n`), hunt/replace zero-width characters with visible dots (`•`), and drop entire foreign alphabets by entering custom raw Decimal or Hexadecimal interval boundaries (e.g., `97-122` or `0600-06FF`).
*   **Smart Search & Replace:** Advanced Regex-based structural search engine paired with a dedicated single-action navigation cluster (Find Next).
*   **Unicode/Emoji Deep Analysis:** Fully optimized to map complex Surrogate Pairs (like Emojis) using pure integer `codePointAt` logic, revealing decimal, hex, binary, and official Unicode block metadata without crashing.
*   **Text Layout Architecture:** Supports instant Left-to-Right (LTR) and Right-to-Left (RTL) text orientation switching along with text/code beautification macros.
*   **MOptionPane UI Framework:** Driven by a completely custom, decoupled dialog abstraction layer built strictly over standard platform interfaces, running asynchronously via single-choice, input, and confirmation callbacks.

## 🛠️ Technical Highlights

*   **Target SDK Boundary:** Optimized natively for Android 10 (API Level 29).
*   **"Very Very Very Old School" Core:** Written in highly compatible Java 1.8 syntax. Strictly **zero lambdas** and zero modern compiler synthetic sugar to guarantee lightning-fast DX/D8 dexing pipelines.
*   **Zero Dependencies:** Independent of AndroidX, Jetpack, Material Components, or Gradle. It runs directly on bare-metal Android subsystem packages (`android.app.AlertDialog`, `android.widget.NumberPicker`).
*   **Memory-Safe Layouts:** Strictly uses native Android system fonts (`Sans-serif`, `Serif`, `Monospace`) mapped dynamically via layout structures to guarantee zero-overhead RAM allocations and eliminate Out-of-Memory (OOM) memory faults common with cyclic custom asset allocations.
*   **Industrial Shell Compilation:** Built via a custom `build.sh` script executing `aapt`, `javac`, `dx`, `jarsigner`, and `zipalign` manually with rigorous structural error trapping and emergency environment garbage collection routines.

---

## 🎛️ Menu Hierarchy & Command Map

The entire feature matrix is driven by a unified layout controller mapped inside `res/menu/menu.xml`. The layout is structurally segmented into professional action clusters:

1.  **File Operations:** Handling stream inputs, segment partitioning (`Save Current Segment`), and raw encodings (`UTF-8`, `Windows-1254`, `ISO-8859-9`, `US-ASCII`, etc.).
2.  **Find & Replace:** Dynamic Regex search injection and multi-target replacement pipelines.
3.  **Line Operations:** Structural sorting, page number purging, empty token stripping, and hexadecimal/decimal foreign alphabet range removal.
4.  **Tools:** Deep Unicode surrogate matrix analysis, footnote bracket encapsulation wrappers (`Footnotes to Brackets`), and contextual lexical expansion (`Append Meanings`).
5.  **Encode/Decode Tools:** Discrete hash generation algorithms (`SHA-256`) alongside low-level binary string encodings (`Base64`, `URL`).
6.  **Split & Merge Operations:** Cross-file string matches, localized multi-file queries, text file compilation partitioning, and inline geometric expressions evaluation.
7.  **Arabic/Farsi Engine:** Host cluster for calendar forensics and the multi-directional string transliteration nodes (`Global` / `Selected Bounds`).
8.  **Appearance & Formatting Layout:** Real-time text engine directives controlling read-only locks, typography metrics (Family, Style, Size), interface canvas styling (Background, Foreground, Selection bounds), layout bidi orientations (LTR/RTL), and micro beautifiers.

---

## ⚠️ CRITICAL FILE PERMISSION NOTICE (Android 29 Scoped Storage)

> [!IMPORTANT]
> To comply with Android 10 (API 29) file interaction security policies, the application declares all necessary storage handles inside the `AndroidManifest.xml`. However, due to platform permission enforcement boundaries:
>
> **The user MUST manually grant full storage access via system settings.**
> 
> **Steps to Activate:**
> 1. Long-press the **Notepad** application icon on your device launcher or navigate to **Settings -> Apps & Notifications**.
> 2. Open the **Notepad Application Info** menu page.
> 3. Click into the **Permissions** tab entry.
> 4. Manually switch the **Files and Media / Storage** access permission level to **"Allow management of all files"** or **"Allow"**.
> 
> Failure to perform this manual configuration step will block the core file read/write pipelines from binding with the active file subsystem layout!

---

## 📦 Workspace Hierarchy & Structure

```text
├── AndroidManifest.xml
├── assets/
├── res/
│   └── menu/
│       └── menu.xml          <-- Full custom text manipulation menu ecosystem
├── src/
│   └── net/
│       └── murat/
│           ├── notepad/
│           │   ├── Notepad.java         <-- Core controller & option selections
│           │   ├── Festival.java        <-- Static pre-compiled 200-year matrix
│           │   └── FestivalSearcher.java <-- Pure multi-calendar matching pipeline
│           └── uikit/
│               └── MOptionPane.java     <-- Framework-free async native modal factory
├── obj/                             <-- Temporary compilation bytecode buffer
├── bin/                             <-- Final targeted binary output deployment directory
└── build.sh                         <-- Professional CLI automation compiler routine
```

## 🛠️ Compilation, Installation & Deployment

This project is built directly from the command-line interface. To run the compilation pipeline, fire up your terminal workspace and execute:

```bash
# Grant execution permissions to our deployment script
chmod +x build.sh

# Run the professional compiler loop
./build.sh
```
The script will validate resource trees, compile source files down to Java 1.8 bytecode, pack the `classes.dex`, apply cryptographic signatures (`jarsigner`), and boundary-align (`zipalign`) the package into a production-ready, high-speed binary envelope inside `bin/Notepad.apk`.

## 📜 License

This project is licensed under the GNU GPL v3 License - see the `LICENSE` file for details.

---
"Code is poetry, but legacy-compliant, lightweight, secure code is an absolute masterpiece."
