CREATE UNIQUE INDEX IF NOT EXISTS ux_student_enrollment_student_course ON student_enrollment_table (student_id, institution_course_id);

CREATE INDEX IF NOT EXISTS idx_student_enrollment_course ON student_enrollment_table (institution_course_id);