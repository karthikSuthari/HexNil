# HexNil OEM UI Design Guide

## Design Philosophy

HexNil is an **OEM Battery Intelligence Platform** — not a consumer battery saver app. The UI must reflect this with:

- **Industrial precision**: Clean, minimal, data-dense
- **Engineering-grade**: Professional diagnostic tool aesthetic
- **Zero gimmicks**: No animations, gradients, or decorative elements
- **Long-session friendly**: Calm, readable for extended analysis periods
- **Scalable**: Works on phones, tablets, and desktop-mirroring scenarios

---

## Color System

### Background Colors
- **Primary Background**: `#0F1115` (`oem_bg_primary`) — Near-black
- **Secondary Background**: `#161A20` (`oem_bg_secondary`) — Dark gray
- **Card Background**: `#1C2128` (`oem_bg_card`) — Elevated cards
- **Elevated Card**: `#22272E` (`oem_bg_elevated`) — Special emphasis

### Text Colors
- **Primary Text**: `#FFFFFF` (`oem_text_primary`) — Main content
- **Secondary Text**: `#8B949E` (`oem_text_secondary`) — Labels, descriptions
- **Tertiary Text**: `#6E7681` (`oem_text_tertiary`) — Captions, metadata
- **Disabled Text**: `#484F58` (`oem_text_disabled`) — Inactive elements

### Accent Color (Single)
- **Primary Accent**: `#58A6FF` (`oem_accent`) — Use sparingly for highlights
- **Light Accent**: `#79C0FF` (`oem_accent_variant`) — Hover/active states
- **Dark Accent**: `#1F6FEB` (`oem_accent_dim`) — Buttons, emphasis

### Status Colors (Subtle)
- **Safe**: `#238636` (`oem_status_safe`) — Green
- **Warning**: `#D29922` (`oem_status_warning`) — Yellow
- **Critical**: `#DA3633` (`oem_status_critical`) — Red
- **Info**: `#58A6FF` (`oem_status_info`) — Blue

---

## Typography

### Hierarchy
1. **Headline 1** (`TextAppearance.HexNil.Headline1`)
   - Size: 32sp, Bold
   - Use: Screen titles (rare)

2. **Headline 2** (`TextAppearance.HexNil.Headline2`)
   - Size: 24sp, Bold
   - Use: Section headers

3. **Headline 3** (`TextAppearance.HexNil.Headline3`)
   - Size: 20sp, Bold
   - Use: Card titles, module headers

4. **Body Large** (`TextAppearance.HexNil.BodyLarge`)
   - Size: 16sp
   - Use: Primary content

5. **Body** (`TextAppearance.HexNil.Body`)
   - Size: 14sp
   - Use: Secondary content, descriptions

6. **Caption** (`TextAppearance.HexNil.Caption`)
   - Size: 12sp
   - Use: Metadata, timestamps

7. **Label** (`TextAppearance.HexNil.Label`)
   - Size: 11sp, Bold, Letter-spaced
   - Use: Metric labels (e.g., "TEMPERATURE", "VOLTAGE")

8. **Metric** (`TextAppearance.HexNil.Metric`)
   - Size: 28sp, Bold, Condensed font
   - Use: Large numeric values

9. **Monospace** (`TextAppearance.HexNil.Monospace`)
   - Size: 13sp
   - Use: IP addresses, device IDs, technical codes

---

## Layout Rules

### Card Structure
Every screen follows this pattern:

```
┌─────────────────────────────────┐
│ STATUS SUMMARY CARD             │
│ ─────────────────────────────── │
│ [Primary metric + status]       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ KEY METRICS CARD                 │
│ ─────────────────────────────── │
│ [3-5 key metrics in rows]       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ ASSESSMENT CARD                  │
│ ─────────────────────────────── │
│ [Verdict, recommendations]      │
└─────────────────────────────────┘
```

### Spacing
- **Card Padding**: 16dp (`oem_card_padding`)
- **Card Margin**: 12dp (`oem_margin_medium`)
- **Screen Padding**: 16dp (`oem_padding_medium`)
- **Divider Height**: 1dp (`oem_divider_height`)
- **Divider Margin**: 16dp top/bottom

### Alignment
- **Left-align**: Labels, text content
- **Right-align**: Metric values, numeric data
- **Center-align**: Only for primary focus metrics (e.g., battery level)

---

## Screen-by-Screen Specifications

### Dashboard
**Purpose**: At-a-glance system health

**Structure**:
1. System Status Card (service status, uptime)
2. Primary Metrics Card (battery level, charging state)
3. Secondary Metrics Grid (temperature, voltage, current, health)
4. Remote Access Card (optional)
5. Database Statistics Card
6. Active Monitors Card
7. Quick Actions (buttons)

**Key Rules**:
- Battery level: Large (56sp), centered
- Status indicators: 10dp dot, green/yellow/red
- No emojis — use text labels only
- Monospace font for IP addresses

### Health Module
**Purpose**: Battery health and degradation tracking

**Structure**:
1. Status Summary Card (health %, cycle count)
2. Key Metrics Card (capacity, fade, degradation rate)
3. Assessment Card (verdict)

**Key Rules**:
- Show only essential metrics
- Hide advanced data behind expanders (future)
- Use status colors for health percentage

### Charging Module
**Purpose**: Charging session analysis

**Structure**:
1. Current Session Status Card
2. Key Metrics Card (speed, current, temp, efficiency)
3. Safety Assessment Card

**Key Rules**:
- Show active session prominently if charging
- Safety status: SAFE / WARNING / CRITICAL
- No charging animations

### Thermal Module
**Purpose**: Temperature monitoring

**Structure**:
1. Current Temperature Status Card (current + peak)
2. Key Metrics Card (events, avg temp, severity)
3. Assessment Card

**Key Rules**:
- Color-code temperature: Green (<35°C), Yellow (35-40°C), Red (>40°C)
- Show peak temperature prominently
- Event count as secondary metric

### Drain Module
**Purpose**: Battery drain analysis

**Structure**:
1. Drain Summary Card (screen-on + idle drain)
2. Key Metrics Card (events, avg rate, primary cause)
3. Assessment Card

**Key Rules**:
- Show screen-on vs idle drain side-by-side
- Primary cause: Display / System / App / Unknown
- No drain animations

### AI Analysis Module
**Purpose**: Technical diagnostic report

**Structure**:
1. Executive Summary Card
2. Risk Assessment Card (score + severity)
3. Root Cause Analysis Card
4. Recommendations Card
5. Technical Details Card (expandable)

**Key Rules**:
- **NO chat-style UI** — this is a report, not a conversation
- **NO conversational bubbles**
- Use structured text blocks
- Technical details in monospace
- Risk score: Large number (32sp)
- Severity: LOW / MEDIUM / HIGH / CRITICAL

---

## Component Guidelines

### Status Indicators
- **Size**: 10dp × 10dp circle
- **Colors**: Green (safe), Yellow (warning), Red (critical), Gray (off)
- **Position**: Left of status text, vertically centered

### Buttons
- **Primary**: Use sparingly — only for critical actions
- **Tonal**: For navigation, secondary actions
- **Outlined**: For tertiary actions
- **Text Size**: 12sp for compact buttons
- **No icons** unless they improve scanning speed

### Cards
- **Corner Radius**: 8dp (`oem_card_corner_radius`)
- **Elevation**: 0dp (flat design)
- **Background**: `oem_bg_card` (`#1C2128`)
- **Padding**: 16dp (`oem_card_padding`)
- **Divider**: 1dp line, `oem_divider` color

### Dividers
- **Height**: 1dp
- **Color**: `oem_divider` (`#21262D`)
- **Margin**: 16dp top/bottom
- **Use**: Separate sections within cards

---

## What NOT to Do

### ❌ Forbidden Elements
- Emojis in UI text
- Battery animations (charging/discharging)
- Gradient backgrounds
- Neon colors
- Decorative fonts
- Skeleton loaders (unless absolutely necessary)
- Chat-style bubbles
- Raw JSON display
- Redundant metrics across screens
- Consumer-style progress animations

### ❌ Forbidden Patterns
- Multiple accent colors
- Over-colorization
- Decorative icons
- Fancy charts without meaning
- Duplicate information
- Scrolling walls of text
- Hidden critical information

---

## Remaining Screens to Update

### Safety Fragment
- Follow same card structure
- Show: Event count, severity breakdown, recent events
- Assessment: Safety status (SAFE / ATTENTION / FAIL)

### Idle Fragment
- Show: Drain rate, doze effectiveness, wake-up count
- Assessment: Standby performance verdict

### Calibration Fragment
- Show: SOC drift, calibration status, recommendations
- Assessment: Calibration quality

### Reports Fragment
- Table-based layout
- Print-friendly spacing
- Export buttons (PDF/CSV)

### Compliance Fragment
- Certification-style layout
- PASS / ATTENTION / FAIL indicators
- Structured evidence tables

### Settings Fragment
- Simple list-based layout
- Toggles for services
- Database management section

---

## Implementation Checklist

- [x] Design system (colors, typography, themes)
- [x] Dashboard layout redesign
- [x] Health module layout
- [x] Charging module layout
- [x] Thermal module layout
- [x] Drain module layout
- [x] AI Analysis module layout
- [x] Remove emojis from Java code
- [ ] Safety Fragment redesign
- [ ] Idle Fragment redesign
- [ ] Calibration Fragment redesign
- [ ] Reports Fragment redesign
- [ ] Compliance Fragment redesign
- [ ] Settings Fragment redesign
- [ ] Update all Fragment Java classes to use new text styles
- [ ] Test on tablet layouts
- [ ] Verify dark theme consistency

---

## Testing Guidelines

### Visual Testing
1. **Dark Theme**: All screens must use OEM dark theme
2. **Typography**: Verify hierarchy is clear
3. **Spacing**: Check card margins and padding
4. **Colors**: Ensure status colors are subtle, not overwhelming
5. **Readability**: Test with long text content

### Functional Testing
1. **Data Density**: Verify all key metrics are visible
2. **Scrolling**: Ensure no horizontal scroll
3. **Tablet**: Test on larger screens
4. **Remote Dashboard**: Verify HTML dashboard matches app UI style

---

## Final Notes

This UI is designed for **OEM engineers, QA teams, and certification reviewers**. It must:

- Look professional and trustworthy
- Present data clearly and accurately
- Avoid consumer app patterns
- Scale to different screen sizes
- Work for extended analysis sessions

**Remember**: This is diagnostic software, not a consumer product. Clarity and precision over visual appeal.
