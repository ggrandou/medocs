# User database schema

Local database (Room / SQLite) describing what the user tracks, when they must think about it, what they did and what is left in stock. The model is generic: the same mechanism serves a daily treatment, a filter changed every 3 months, contact lenses replaced every 2 weeks or the dog's kibble.

## Principles

- **Stock is never stored**: it is the sum of movements. Cancelling or skipping an occurrence gives the stock back with no drift.
- **Future occurrences are not stored**: they are computed from the schedules. The journal (`occurrence`) only holds what happened: the user's answer, a postponement, or a missed occurrence.
- **"Wall-clock" times**: dates and times are `LocalDate` / `LocalTime` (ISO 8601 text). "8:00" stays 8:00 after a clock or time zone change. Only `timestamp` and `snoozed_until` are instants (epoch in milliseconds).
- **External information is copied**: whatever comes from an external source (public database, scan) is copied into `item`, with no foreign key. The source can change without touching the user's data. Fully manual entry is always possible.
- **Enumerations as text**: enumerations (`recurrence`, `status`, etc.) are stored as text. Adding a value requires no migration.
- Any entity change requires bumping the database version, writing a migration and versioning the exported schema (`android/app/schemas/`).

## Overview

```
item ──< schedule ──< schedule_time
  │                        │
  ├──< occurrence >────────┘   (schedule_time_id null: one-off occurrence)
  │        │
  └──< stock_movement >── occurrence (optional)
```

## Tables

### `item`

What is tracked.

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER PK | Auto-incremented |
| `name` | TEXT | Displayed name |
| `type` | TEXT | Free field. The database does not constrain its values; interpreting them is up to the app |
| `unit` | TEXT | Dose and stock unit: "comprimé", "ml", "paire", "kg"… |
| `packaging` | TEXT? | Label of a lot: "boîte", "paquet", "sac" |
| `units_per_package` | REAL? | Number of units per lot, for the "add a lot" button. Always editable |
| `track_stock` | INTEGER | 0 = reminder only, no stock |
| `threshold_days` | INTEGER | Alert when the stock only covers this many days of schedule (default 7) |
| `threshold_units` | REAL? | Alert below this number of units; used for items without a schedule |
| `photo_path` | TEXT? | Photo taken by the user, local file |
| `reference` | TEXT? | External identifier: barcode, code from a public database |
| `composition` | TEXT? | Descriptive text about contents or dosage, display only |
| `form` | TEXT? | Form or presentation (tablet, syrup, eye drops…), used to pick the icon |
| `archived` | INTEGER | 1 = hidden from lists, history kept |

### `schedule`

Recurrence rule of an item. An item can have several schedules.

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER PK | |
| `item_id` | INTEGER FK | → `item.id`, cascade delete |
| `recurrence` | TEXT | Rule type (see below) |
| `interval` | INTEGER | ≥ 1. Step of the rule, depending on the type |
| `interval_unit` | TEXT | `DAY`, `WEEK`, `MONTH`, `YEAR` |
| `weekdays` | INTEGER | Bit mask: bit 0 = Monday … bit 6 = Sunday |
| `rank` | INTEGER? | Rank in the month: 1 to 4, or -1 for the last one |
| `active_days` | INTEGER? | Length of the active phase of a cycle |
| `pause_days` | INTEGER? | Length of the pause of a cycle |
| `start_date` | TEXT | `LocalDate`. Anchor of all computations |
| `end_date` | TEXT? | Last day included. Null = no end; equal to `start_date` = one-time reminder |
| `active` | INTEGER | 0 = suspended |

#### Recurrence types

Columns not listed for a type are null or ignored.

| `recurrence` | Columns used | Meaning | Examples |
|---|---|---|---|
| `PERIODIC` | `interval`, `interval_unit` | Every N days / weeks / months / years from `start_date` | Every day (1 `DAY`); every 2 weeks; every 3 months; every year |
| `WEEKDAYS` | `weekdays`, `interval` (in weeks) | Some days of the week, one week out of N (1 = every week). The anchor week is the one of `start_date` | Monday, Wednesday, Friday; Saturday every other week |
| `RANK_IN_MONTH` | `weekdays` (a single bit), `rank`, `interval` (in months) | The Nth weekday of the month, or the last one | First Monday of the month; last Friday, every 2 months |
| `CYCLE` | `active_days`, `pause_days` | Active phase of `active_days` days then pause of `pause_days` days, in a loop from `start_date`. Times apply every day of the active phase | 21 days on / 7 off; 2 weeks out of 3 (14 / 7); 5 days out of 7 |

Computation rules:

- In monthly or yearly `PERIODIC`, the day of the month of `start_date` is brought back to the last day of the month when the month is shorter (the 31st gives the 28th or 29th in February).
- In `RANK_IN_MONTH`, a rank missing from a month (fifth Monday) is replaced by the last matching day of the month only if `rank` is -1; otherwise the month is skipped.
- Recurrence computations are pure functions, tested on the JVM.

### `schedule_time`

A time of day of a schedule, with its quantity ("2 in the morning, 1 in the evening").

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER PK | |
| `schedule_id` | INTEGER FK | → `schedule.id`, cascade delete |
| `time` | TEXT | `LocalTime` |
| `quantity` | REAL? | Quantity in item units; decimals allowed (0.5). Null = nothing to deduct from stock |

Keeping the `id` of a time when it is edited keeps the link with its history.

### `occurrence`

Journal. A row exists as soon as the user acts on a planned occurrence, when the engine marks it "missed", or when a one-off occurrence is created.

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER PK | |
| `item_id` | INTEGER FK | → `item.id`, cascade delete |
| `schedule_time_id` | INTEGER? FK | → `schedule_time.id`, set to null on delete. Null = one-off |
| `scheduled_date` | TEXT? | Planned `LocalDate` |
| `scheduled_time` | TEXT? | Planned `LocalTime`, kept even if the schedule's time changes afterwards |
| `status` | TEXT | `SNOOZED`, `DONE`, `SKIPPED`, `MISSED` |
| `quantity` | REAL? | Quantity actually involved |
| `timestamp` | INTEGER | Instant of the action, or of the decision for `SKIPPED` / `MISSED` |
| `snoozed_until` | INTEGER? | For `SNOOZED`: instant of the new reminder |

`(schedule_time_id, scheduled_date)` is unique: a planned occurrence has a single row (null values of one-off occurrences are not constrained).

Statuses:

- `SNOOZED`: pending until `snoozed_until`.
- `DONE`, `SKIPPED`: the user's answer.
- `MISSED`: no answer after the tolerance delay.

### `stock_movement`

Stock journal. Current stock of an item = `SUM(delta)`.

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER PK | |
| `item_id` | INTEGER FK | → `item.id`, cascade delete |
| `delta` | REAL | Positive on entry, negative on exit |
| `type` | TEXT | `PURCHASE`, `CONSUMPTION`, `CORRECTION` |
| `timestamp` | INTEGER | Instant of the movement |
| `occurrence_id` | INTEGER? FK | → `occurrence.id`, cascade delete |

A `CONSUMPTION` movement is linked to its occurrence: deleting the occurrence, or switching it to "skipped", deletes the movement and gives the stock back. It is only created if the item has `track_stock = 1` and the quantity is not null.

## Indexes

- `schedule(item_id)`, `schedule_time(schedule_id)`, `occurrence(item_id)`, `stock_movement(item_id)`, `stock_movement(occurrence_id)`.
- `occurrence(schedule_time_id, scheduled_date)`: unique.

## History and deletions

- The past lives in `occurrence`, not in the schedules: editing or deleting a schedule does not change the history. Before such a change, the engine must have written the elapsed occurrences.
- Only deleting an `item` erases its history and stock (cascade).
- Deleting a schedule time detaches its occurrences (`schedule_time_id` null); they are still counted through their `scheduled_date`.

## Backup

All tables are serialized in the encrypted backup with their original identifiers, and foreign-key consistency is checked before any write. Any table or column added must enter the backup format and its version.
