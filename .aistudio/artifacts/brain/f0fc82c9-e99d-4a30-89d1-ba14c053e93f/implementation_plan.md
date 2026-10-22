# InspectCam — Garment & Sample Inspection App

A mobile-first Android application designed for garment, fabric, and apparel sample inspectors to quickly capture high-resolution photos, categorize views (labels, trims, shading, stitches), scan barcodes, annotate observations, and generate formatted A4 PDF reports and ZIP archives directly on device without internet access.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions have been confirmed via interactive clarification:
> - **Platform Architecture**: Native Android application using Jetpack Compose, CameraX, and Room database for maximum camera performance, instant hardware barcode scanning, and reliable offline storage.
> - **Photo Naming Pattern**: Intelligent automatic sequencing formatted as `<Category> 01`, `<Category> 02` (e.g., *Front View 01*, *Care Label Barcode 01*) with per-category auto-increment and duplicate prevention.
> - **Export Capabilities**: Both formatted A4 PDF reports (3-column grid, company header, clean page breaks) and full ZIP archives containing original compressed photos organized by file name.

---

## 1. Overview & Core Concept

InspectCam streamlines quality control (QC) and sample auditing for garment manufacturing, fashion brands, buying houses, and independent inspectors. 

### Persona & Problem
Inspectors working in factory sample rooms, finishing lines, and warehouse receiving stations need to rapidly photograph samples (Front View, Back View, Inside, Main Label, Care Label Barcodes, Trims, Shading, and RFC comparisons) under fast turnaround times. Juggling standard camera apps with manual WhatsApp/email uploads results in lost metadata, misplaced photos, and tedious manual report generation.

### Key Value
- **Zero-Friction Capture Loop**: Camera opens instantly, captures the sample, automatically suggests the next sequence number (e.g. *Main Label 01*), saves with an optional comment or barcode, and immediately offers a "Save & Take Another" shortcut for continuous rapid-fire auditing.
- **100% Offline Durability**: All inspections, categories, photos, and thumbnails are stored securely on-device using Android Room and private local storage.
- **One-Tap Industry Reports**: Generates professional A4 PDF inspection sheets with standardized headers and 3-column photo grids, ready to share via WhatsApp, Gmail, or Drive.

---

## 2. User Experience & Visual Design

### Design Direction & Visual Identity
- **Aesthetic Direction**: High-precision industrial utility. Clean, high-contrast, designed for rapid single-handed use in bright factory light or dim sample rooms.
- **Color Palette**:
  - Primary / Action: Deep Graphite (`#1A1C1E`) with Safety Amber / Construction Gold (`#F59E0B` / `#D97706`) accents for primary capture buttons and active pill indicators.
  - Surface & Background: Crisp off-white (`#F8FAFC`) in light mode; Slate Dark (`#0F172A` / `#1E293B`) in dark mode.
  - Category Badges: Distinctive subtle tonal pills with bold counts.
- **Typography & Touch Ergonomics**:
  - Prominent Style Number and Color headers.
  - Generous 48dp+ tap targets across bottom action zones.
  - Bottom sheets and bottom action bars positioned within easy thumb reach.

### Key User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│                              INSPECTCAM                                │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Home Screen         2. Inspection Overview      3. Live Camera Flow │
│ ┌────────────────────┐   ┌───────────────────────┐   ┌───────────────┐ │
│ │ Style # / Color    │──>│ Top Bar: Style, Color │──>│ Fullscreen View││
│ │ Date & Photo Count │   │ Category Chips (All,+)│   │ Rear/Front    │ │
│ │ [+ New Inspection] │   │ 3-Col Photo Grid      │   │ Flash/Torch   │ │
│ └────────────────────┘   │ [FAB: Camera] [Export]│   │ [Capture Button││
│                          └───────────────────────┘   └───────┬───────┘ │
│                                                              ▼         │
│ 4. Category Manager    5. Photo Detail & Zoom      Capture Preview     │
│ ┌────────────────────┐   ┌───────────────────────┐   ┌───────────────┐ │
│ │ Reorder / Rename   │   │ Fullsize Pinch-Zoom   │   │ Retake / Use  │ │
│ │ Delete (Move/Drop) │<──│ Edit Name & Category  │<──│ Bottom Sheet: │ │
│ └────────────────────┘   │ Barcode & Comments    │   │ • File Name   │ │
│                          └───────────────────────┘   │ • Category    │ │
│ 6. Export Dialog & PDF Preview                       │ • Barcode Scan│ │
│ ┌──────────────────────────────────────────────────┐ │ • Save / Next │ │
│ │ A4 Grid Layout | Header Table | Clean Pagination │ └───────────────┘ │
│ │ [Share PDF via Android Sheet] [Download ZIP]     │                   │
│ └──────────────────────────────────────────────────┘                   │
└────────────────────────────────────────────────────────────────────────┘
```

#### Detailed Screens
1. **Home Screen**:
   - Inspection card list displaying Style No, Color, Report Title, Creation Date, and photo badges.
   - Instant search bar (filter by style number, buyer, color, or report title).
   - Persistent FAB to create a "New Inspection".
   - Swipe or long-press card menu with Edit and Delete (with confirmation dialog).
2. **New / Edit Inspection Screen**:
   - Fields: Company Name, Report Title (e.g. "Washing & Styling Comparison"), Style No, Color, Buyer, Inspector Name.
   - Automatically pre-populates with the last-used Company Name and Inspector Name from local DataStore preferences.
3. **Inspection Screen (Workspace)**:
   - Header: Style No, Color, Report Title with quick actions (Export, Edit, Settings).
   - Horizontal scrollable filter chips: "All" with total count, followed by 10 default categories (Front View, Back View, Inside View, Inside Back View, Main Label, Care Label, Care Label Barcode, Trims, Shading, Sample vs RFC), plus a `+` chip to append custom categories.
   - Content area: Responsive 3-column photo grid. When "All" is active, items are neatly organized under category headers. Each card shows an image thumbnail, bold file name, and optional comment snippet.
   - Prominent bottom-right Camera FAB.
4. **Camera & Fast Annotation Flow**:
   - Full-screen CameraX live viewfinder with switch-camera (front/rear), torch toggle, and gallery picker fallback.
   - Freeze-frame preview with **Retake** and **Use Photo** options.
   - Bottom sheet modal pops up immediately:
     - **File Name**: Pre-filled with `<CurrentCategory> XX` (e.g., `Front View 01`), with duplicate detection.
     - **Category Picker**: Pre-selected to current active chip with a `+ New Category` inline option.
     - **Comment**: Multi-line observation notes.
     - **Scan Barcode Button**: Launches integrated CameraX barcode analyzer, reading Code128, Code39, EAN-13, EAN-8, UPC, and QR codes into the Barcode field.
     - **Actions**: `Save` (returns to grid) and `Save & Take Another` (instantly opens camera for the next shot).
5. **Photo Detail Screen**:
   - Interactive zoomable image (pinch-to-zoom and pan).
   - Editable metadata panel: File Name, Category (dropdown to reassign category), Comment, Barcode Value.
   - Horizontal swipe gesture to move between photos within the current category.
   - Action bar: Save Changes, Retake (replaces image), Delete (with confirmation), and Share Image.
6. **Category Management Screen**:
   - Reorder categories via up/down buttons.
   - Rename existing categories.
   - Safe Delete: If photos exist in the category, prompts user to either "Move photos to another category" or "Delete photos permanently".
7. **Export & Sharing Hub**:
   - **A4 PDF Engine**: Formats an official inspection report with a header table (Company Name, Report Title, Style, Color), category section titles, and 3-column photo grid with bold captions and comments. Paginated so photos never cut across page boundaries.
   - Scope filters: Export all categories or select specific categories; toggle comments on/off.
   - Native Android Share Sheet (`Intent.ACTION_SEND` with `FileProvider`) for instant delivery via WhatsApp, Email, or Slack.
   - **ZIP Archive Export**: Packages all compressed JPEG photos named after their assigned file names into a single `.zip` file.
8. **Settings Screen**:
   - Manage default Company Name and Inspector Name.
   - Image Compression Quality selection (Low: 1200px / 0.7, Medium: 1600px / 0.8, High: 2048px / 0.9).
   - Full JSON + photo backup and restore.

---

## 3. Key Product Decisions & Trade-Offs

- **Native Jetpack Compose vs Hybrid Web**:
  - *Chosen*: Native Jetpack Compose with CameraX and Room.
  - *Why*: Direct hardware access to camera sensors, zero latency during rapid capture, rock-solid offline filesystem I/O for large high-resolution images, and native Android `PdfDocument` rendering.
- **Image Compression Pipeline**:
  - *Chosen*: On capture, scale image down to max 1600px on the long dimension and encode to JPEG at 0.8 quality; generate a 256x256 thumbnail cached in Room/disk.
  - *Why*: Reduces raw 12MP/48MP camera files (10MB–25MB) to ~250KB–450KB while retaining crisp texture detail for fabric stitches and care label text. Prevents Out-Of-Memory (OOM) crashes during 50+ photo inspections and enables instant grid scrolling.
- **Repository Architecture**:
  - *Chosen*: Repositories (`InspectionRepository`, `CategoryRepository`, `PhotoRepository`) abstract all Room queries behind Kotlin `Flow`.
  - *Why*: Completely decouples the UI from the database, allowing future cloud syncing (Supabase, Firebase, or company REST APIs) without touching composable screens.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│ ┌───────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────┐ │
│ │  HomeScreen   │ │ InspectionView │ │ CameraFlowView │ │ PhotoDetail│ │
│ └───────┬───────┘ └───────┬────────┘ └───────┬────────┘ └──────┬─────┘ │
│         │                 │                  │                 │       │
│         └────────────┬────┴──────────────────┴─────────────────┘       │
│                      ▼                                                 │
│             AppViewModels (InspectionViewModel, SettingsViewModel)     │
│                      │                                                 │
│         ┌────────────┴───────────────────────────┐                     │
│         ▼                                        ▼                     │
│ ┌─────────────────────────────┐        ┌─────────────────────────────┐ │
│ │      Repository Layer       │        │       Services/Utils        │ │
│ │ • InspectionRepository      │        │ • CameraXCaptureManager     │ │
│ │ • CategoryRepository        │        │ • BarcodeAnalyzer (ML Kit)  │ │
│ │ • PhotoRepository           │        │ • ImageCompressionUtil      │ │
│ └──────────────┬──────────────┘        │ • PdfReportGenerator        │ │
│                ▼                       │ • ZipExportUtil             │ │
│ ┌─────────────────────────────┐        └─────────────────────────────┘ │
│ │        Room Database        │                                        │
│ │ • InspectionDao             │                                        │
│ │ • CategoryDao               │                                        │
│ │ • PhotoDao                  │                                        │
│ └──────────────┬──────────────┘                                        │
│                ▼                                                       │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │           Device Local Storage (App Files Dir & SQLite)            │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Model & Room Schema

- **InspectionEntity**:
  - `id: Long` (Auto-generated primary key)
  - `companyName: String`
  - `reportTitle: String` (e.g., "Washing & Styling Comparison")
  - `styleNo: String`
  - `color: String`
  - `buyer: String?`
  - `inspectorName: String`
  - `createdAt: Long`
  - `updatedAt: Long`

- **CategoryEntity**:
  - `id: Long` (Auto-generated primary key)
  - `inspectionId: Long` (Foreign Key referencing Inspection with CASCADE delete)
  - `name: String`
  - `sortOrder: Int`

- **PhotoEntity**:
  - `id: Long` (Auto-generated primary key)
  - `inspectionId: Long` (Foreign Key referencing Inspection)
  - `categoryId: Long` (Foreign Key referencing Category)
  - `fileName: String`
  - `imagePath: String` (Absolute path to compressed JPEG in app files directory)
  - `thumbnailPath: String` (Absolute path to 256px thumbnail)
  - `barcodeValue: String?`
  - `comment: String?`
  - `createdAt: Long`
  - `updatedAt: Long`

---

## 5. Execution Steps

1. **Project Dependencies & Configuration**:
   - Enable CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`), `coil-compose`, Room KTX/Runtime, and Google ML Kit Barcode Scanning in `build.gradle.kts` and `libs.versions.toml`.
   - Configure camera, storage, and file provider permissions in `AndroidManifest.xml`.
2. **Data Persistence & Repository Layer**:
   - Implement `InspectionEntity`, `CategoryEntity`, `PhotoEntity` and their corresponding DAOs (`InspectionDao`, `CategoryDao`, `PhotoDao`).
   - Create `InspectCamDatabase` with Room migration and initialization logic.
   - Implement repository classes with Kotlin Flow for reactive UI binding.
3. **Hardware Services & Image Pipeline**:
   - Implement `ImageProcessor` for resizing (max 1600px), JPEG compression (0.8 quality), and thumbnail creation.
   - Implement CameraX composable viewfinder with flip camera, torch control, and ML Kit Barcode Analyzer.
4. **Export Utilities**:
   - Build `PdfReportGenerator` utilizing Android's native `PdfDocument` to lay out the A4 header, 3-column photo grid, captions, and comments with automatic page breaks.
   - Build `ZipExportManager` to bundle all inspection images.
   - Configure `FileProvider` and sharing intents.
5. **UI Screen Implementation**:
   - `HomeScreen`: Searchable list, creation dialog/FAB, swipe-to-delete.
   - `InspectionScreen`: Category chips, 3-column photo grid, empty state prompts, export menu.
   - `CaptureModal`: Fast bottom-sheet with pre-filled auto-incrementing file name, category dropdown, comment field, and barcode scan button.
   - `PhotoDetailScreen`: Pinch-zoom viewer, metadata editing, category reassignment, next/previous swipe.
   - `CategoryManager`: Reordering, renaming, and safe deletion with photo relocation dialog.
   - `SettingsScreen`: Default inspector/company settings and data backup/restore.
6. **Compilation & Verification**:
   - Run `compile_applet` to verify compilation, test tags, and navigation integrity.
