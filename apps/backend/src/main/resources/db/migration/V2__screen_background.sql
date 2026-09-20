-- docs/json_contract.md §3.1 — the screen surface.
--
-- Stored as JSON rather than a colour column because the fill is a colour OR a
-- linear gradient, and a column per gradient stop would model the format
-- rather than the data. NULL means the contract carried no background, which
-- is not the same as a white one.
ALTER TABLE projects ADD COLUMN background TEXT;
