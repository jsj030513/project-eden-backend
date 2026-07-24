CREATE UNIQUE INDEX IF NOT EXISTS ux_memory_classifications_legacy_recognition
ON memory_classifications (
    recognition_id,
    provider,
    model_version,
    taxonomy_version
)
WHERE recognition_id IS NOT NULL;
