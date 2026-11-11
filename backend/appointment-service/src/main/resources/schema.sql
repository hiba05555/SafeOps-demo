CREATE UNIQUE INDEX IF NOT EXISTS uq_appointment_booked_owner_start
    ON appointment (owner_subject, starts_at)
    WHERE status = 'BOOKED';
