-- Cleanup script for test data
-- This runs after each test to clean up the database

TRUNCATE TABLE kb_documents RESTART IDENTITY CASCADE;
