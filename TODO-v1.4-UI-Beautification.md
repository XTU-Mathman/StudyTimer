# StudyTimer v1.4 — UI Beautification TODO

> Goal: Premium iOS-style polish — real blur, refined glassmorphism, gradient buttons, cleaner typography, icon-only bottom nav with selection pill.

---

## 1. DynamicBackgroundView — Real Blur Effect (RenderEffect)
**File:** `app/src/main/java/com/example/studytimer/DynamicBackgroundView.kt`

- [ ] Add `import android.graphics.RenderEffect` and `import android.graphics.Shader` (already imported)
- [ ] In `onAttachedToWindow()`, after `animator.start()`, apply real blur on Android 12+:
  ```kotlin
  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.CLAMP))
  }
  ```
- [ ] Add a `companion object` constant `BLUR_RADIUS = 60f` for easy tuning
- [ ] Keep existing `onDraw()` gradient logic untouched — the RenderEffect applies on top
- [ ] Test on API 31+ emulator to verify blur renders; verify API 28-30 still shows gradient without crash

---

## 2. Upgrade card_glass.xml — Refined Glassmorphism
**File:** `app/src/main/res/drawable/card_glass.xml`

- [ ] Replace solid stroke with **gradient stroke** using `<item>` + `<shape>` approach (layer-list trick: use a ring shape with gradient for the stroke effect)
- [ ] Increase shadow offset from `2dp` → `3dp` and change shadow color from `#0D000000` → `#14000000` (slightly more visible)
- [ ] Change glass fill from `#99FFFFFF` (60% opacity) → `#B3FFFFFF` (70% opacity) for a more solid frosted look
- [ ] Update stroke from `0.5dp` solid `glass_stroke` → `0.75dp` with a **top-heavy gradient** (white 40% alpha top → 10% alpha bottom)
- [ ] Add a second highlight line: a `1dp` top-edge highlight at `#22FFFFFF` for a sharper specular edge
- [ ] Keep corner radius at `20dp`

**New card_glass.xml target structure:**
```xml
<layer-list>
    <!-- Shadow: softer, slightly larger -->
    <item android:top="3dp"> <shape> solid #14000000, radius 20dp </shape> </item>
    <!-- Glass body -->
    <item> <shape> solid #B3FFFFFF, stroke 0.75dp #33FFFFFF, radius 20dp </shape> </item>
    <!-- Top specular highlight -->
    <item android:top="0dp"> <shape> solid #22FFFFFF, radius 20dp, height 1.5dp </shape> </item>
</layer-list>
```

---

## 3. Upgrade card_background.xml — Add Subtle Depth
**File:** `app/src/main/res/drawable/card_background.xml`

- [ ] Convert from simple `<shape>` to `<layer-list>` with shadow + body layers (matching card_glass pattern)
- [ ] Add a `1dp` top shadow layer with `#0A000000`
- [ ] Change fill from `@color/background_white` → `#F8FFFFFF` (slightly off-white, 97% opacity) for a softer feel
- [ ] Add a `0.5dp` inner stroke at `#08000000` for subtle border definition
- [ ] Keep corner radius at `16dp`

---

## 4. iOS-Style Bottom Navigation — Icon Only + Selection Pill
**File:** `app/src/main/res/layout/activity_main.xml`

- [ ] Change `app:labelVisibilityMode="labeled"` → `app:labelVisibilityMode="unlabeled"` to hide all text labels
- [ ] Set `app:itemIconSize="24dp"` for consistent icon sizing
- [ ] Replace `app:itemActiveIndicatorStyle` with a custom selection pill drawable (see step 9)
- [ ] Add `app:itemActiveIndicatorStyle="@style/NavIndicatorPill"` referencing new style
- [ ] Adjust BottomNavigationView height to `56dp` (explicit) for tighter icon-only layout
- [ ] Increase bottom nav container margin: `marginBottom="12dp"`, `marginHorizontal="20dp"` for more floating effect
- [ ] Update bottom nav container elevation from `4dp` → `8dp` for stronger float

**File:** `app/src/main/res/menu/bottom_nav_menu.xml`
- [ ] Remove `android:title` attributes from all 4 items (or set to `@null`) — keep icons only
- [ ] Keep all `android:icon` references as-is

**File:** `app/src/main/java/com/example/studytimer/MainActivity.kt`
- [ ] Update icon tint to use a `ColorStateList` selector: selected = `blue_primary`, unselected = `text_tertiary`
- [ ] Create `res/color/nav_icon_color_selector.xml` with `state_checked` and default states
- [ ] Apply via `app:itemIconTint="@color/nav_icon_color_selector"`

---

## 5. Create Nav Indicator Pill Drawable
**New File:** `app/src/main/res/drawable/nav_indicator_pill.xml`

- [ ] Create a rounded pill shape (3dp tall, full width rounded):
  ```xml
  <shape android:shape="rectangle">
      <solid android:color="@color/blue_primary" />
      <corners android:radius="12dp" />
      <size android:height="3dp" />
  </shape>
  ```
- [ ] Create companion style in `themes.xml`:
  ```xml
  <style name="NavIndicatorPill" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
      <item name="android:color">@color/blue_primary</item>
      <item name="android:height">3dp</item>
      <item name="shapeAppearance">@style/ShapeAppearance.NavPill</item>
  </style>
  <style name="ShapeAppearance.NavPill" parent="">
      <item name="cornerFamily">rounded</item>
      <item name="cornerSize">12dp</item>
  </style>
  ```
- [ ] Create `res/color/nav_icon_color_selector.xml`:
  ```xml
  <selector xmlns:android="http://schemas.android.com/apk/res/android">
      <item android:color="@color/blue_primary" android:state_checked="true" />
      <item android:color="@color/text_tertiary" />
  </selector>
  ```

---

## 6. Improve colors.xml — Refined Palette
**File:** `app/src/main/res/values/colors.xml`

- [ ] Refine primary blue: `#FF6B9FC7` → `#FF5B9BD5` (slightly more vivid, Apple-like blue)
- [ ] Add new color: `<color name="blue_gradient_start">#FF5B9BD5</color>` (for button gradients)
- [ ] Add new color: `<color name="blue_gradient_end">#FF7EB8DA</color>` (lighter blue for gradient end)
- [ ] Refine glass colors:
  - `glass_fill`: `#B3FFFFFF` (70% white — already in card_glass, add explicit color entry)
  - `glass_stroke`: change to `#29FFFFFF` (slightly more subtle)
  - `glass_shadow`: change to `#14000000` (slightly stronger)
- [ ] Add `<color name="nav_selected">@color/blue_primary</color>` and `<color name="nav_unselected">#FFB0A090</color>`
- [ ] Add `<color name="divider">#12000000</color>` for subtle section dividers
- [ ] Add `<color name="card_elevated_shadow">#1A000000</color>` for elevated card shadow
- [ ] Keep all existing chart colors unchanged

---

## 7. Upgrade Button Styles — Gradient Primary
**File:** `app/src/main/res/drawable/btn_pill_primary.xml`

- [ ] Replace flat `solid` fill with a gradient:
  - Convert to `<layer-list>` with shadow + gradient body
  - Shadow layer: `1dp` offset, `#1A000000`, radius `32dp`
  - Gradient body: use `<shape>` → `<gradient>` with `startColor="@color/blue_primary"`, `endColor="@color/blue_gradient_end"`, `angle="135"` (diagonal)
- [ ] Keep `32dp` corner radius and `0.5dp` stroke at `#40FFFFFF`
- [ ] Keep `<ripple>` wrapper for touch feedback

**New btn_pill_primary.xml target:**
```xml
<ripple android:color="#33FFFFFF">
    <item>
        <layer-list>
            <item android:top="2dp">
                <shape><solid android:color="#1A000000"/><corners android:radius="32dp"/></shape>
            </item>
            <item>
                <shape>
                    <gradient android:startColor="@color/blue_primary" android:endColor="@color/blue_gradient_end" android:angle="135"/>
                    <corners android:radius="32dp"/>
                    <stroke android:width="0.5dp" android:color="#40FFFFFF"/>
                </shape>
            </item>
        </layer-list>
    </item>
</ripple>
```

**File:** `app/src/main/res/drawable/btn_pill_outline.xml`
- [ ] Add `<ripple>` wrapper (currently missing) for proper touch feedback
- [ ] Increase stroke width from `1.5dp` → `1dp` for a more refined look

**File:** `app/src/main/res/drawable/btn_pill_danger.xml`
- [ ] Soften background from `#FFFEEBEB` → `#FFF5EAEA` (less harsh pink)
- [ ] Add a `1dp` stroke at `#20FF3B30` for subtle danger border

---

## 8. Add card_elevated.xml — Floating Cards with Stronger Shadow
**New File:** `app/src/main/res/drawable/card_elevated.xml`

- [ ] Create a `<layer-list>` with:
  - Layer 1: Larger shadow — `2dp` top offset, `4dp` bottom offset, `#1A000000`
  - Layer 2: Medium shadow — `0dp` top, `8dp` bottom, `#0D000000` (diffuse)
  - Layer 3: Card body — `solid #F8FFFFFF`, `0.5dp` stroke `#12000000`, `18dp` corner radius
- [ ] This drawable will be used for cards that need to float above the glass cards (e.g., modal cards, important stats)

**card_elevated.xml target:**
```xml
<layer-list>
    <item android:top="2dp" android:bottom="4dp">
        <shape><solid android:color="#1A000000"/><corners android:radius="18dp"/></shape>
    </item>
    <item android:bottom="8dp">
        <shape><solid android:color="#0D000000"/><corners android:radius="18dp"/></shape>
    </item>
    <item>
        <shape>
            <solid android:color="#F8FFFFFF"/>
            <stroke android:width="0.5dp" android:color="#12000000"/>
            <corners android:radius="18dp"/>
        </shape>
    </item>
</layer-list>
```

---

## 9. Improve Fragment Layouts — Whitespace & Typography (iOS Style)

### 9a. fragment_timer.xml
**File:** `app/src/main/res/layout/fragment_timer.xml`

- [ ] Increase page padding from `20dp` → `24dp` (horizontal)
- [ ] Increase title `textSize` from `26sp` → `28sp` for bolder presence
- [ ] Increase subtitle margin-bottom from `24dp` → `32dp` for more breathing room
- [ ] Increase card spacing (between subject card, mode cards, countdown card) from `16dp` → `20dp`
- [ ] Increase mode card height from `120dp` → `130dp`
- [ ] Change start button from flat to gradient: add `android:background="@drawable/btn_pill_primary"`
- [ ] Update button text style: `textSize="20sp"`, add `android:fontFamily="sans-serif-medium"`
- [ ] Increase bottom margin after motto text from `24dp` → `32dp`

### 9b. fragment_stats.xml
**File:** `app/src/main/res/layout/fragment_stats.xml`

- [ ] Increase title padding from `20dp` → `24dp` horizontal
- [ ] Increase toggle group margin from `20dp` → `24dp` horizontal
- [ ] Increase spacing between chart cards from `12dp` → `16dp`
- [ ] Add `padding="16dp"` to pie and line chart containers (currently `8dp`)
- [ ] Increase total duration card padding from `16dp` → `20dp`
- [ ] Increase overall bottom padding from `20dp` → `24dp`
- [ ] Add `android:fontFamily="sans-serif-medium"` to section headers

### 9c. fragment_todo.xml
**File:** `app/src/main/res/layout/fragment_todo.xml`

- [ ] Increase title padding from `20dp` → `24dp` horizontal
- [ ] Increase list horizontal padding from `20dp` → `24dp`
- [ ] Increase FAB margin from `24dp` → `28dp`
- [ ] Add subtitle line below "待办" title: `<TextView text="管理你的学习任务" textSize="14sp" textColor="@color/text_secondary" marginBottom="24dp"/>`
- [ ] Change FAB backgroundTint from `@color/blue_primary` to use a gradient background drawable

### 9d. fragment_profile.xml
**File:** `app/src/main/res/layout/fragment_profile.xml`

- [ ] Increase page padding from `20dp` → `24dp` horizontal
- [ ] Increase title bottom padding from `20dp` → `28dp`
- [ ] Increase spacing between menu cards from `8dp` → `12dp`
- [ ] Increase card internal padding from `16dp` → `20dp`
- [ ] Increase card corner radius usage: update `card_glass.xml` (already `20dp`) — keep
- [ ] Add `android:fontFamily="sans-serif-medium"` to all menu item title TextViews
- [ ] Increase subtitle `textSize` from `13sp` → `13sp` (keep) but change color to `text_secondary` (already done)

---

## 10. Update activity_main.xml — Nav Bar Polish
**File:** `app/src/main/res/layout/activity_main.xml`

- [ ] Wrap bottom navigation in a slightly larger container with `paddingVertical="6dp"` for vertical centering
- [ ] Increase nav container `marginHorizontal` from `16dp` → `20dp`
- [ ] Increase nav container `marginBottom` from `8dp` → `12dp`
- [ ] Increase container elevation from `4dp` → `8dp`
- [ ] Apply new `@style/NavIndicatorPill` for selection indicator
- [ ] Remove label text (handled in step 4)
- [ ] Apply `@color/nav_icon_color_selector` for icon tinting

---

## Summary Checklist

| # | Task | Files | Priority |
|---|------|-------|----------|
| 1 | RenderEffect blur on DynamicBackgroundView | DynamicBackgroundView.kt | 🔴 High |
| 2 | Refined card_glass.xml | card_glass.xml | 🔴 High |
| 3 | Upgrade card_background.xml | card_background.xml | 🟡 Medium |
| 4 | Icon-only bottom nav + selection pill | activity_main.xml, bottom_nav_menu.xml, MainActivity.kt | 🔴 High |
| 5 | Nav indicator drawable | nav_indicator_pill.xml (new), themes.xml, nav_icon_color_selector.xml (new) | 🔴 High |
| 6 | Refined color palette | colors.xml | 🔴 High |
| 7 | Gradient button styles | btn_pill_primary.xml, btn_pill_outline.xml, btn_pill_danger.xml | 🟡 Medium |
| 8 | card_elevated.xml | card_elevated.xml (new) | 🟡 Medium |
| 9 | Fragment whitespace & typography | fragment_timer.xml, fragment_stats.xml, fragment_todo.xml, fragment_profile.xml | 🔴 High |
| 10 | Main activity nav polish | activity_main.xml | 🟡 Medium |

**Recommended execution order:** 6 → 5 → 4 → 1 → 2 → 3 → 8 → 7 → 9 → 10
(colors first → nav drawables → nav layout → blur → cards → buttons → fragments → main activity)
