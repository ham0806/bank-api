SELECT 'CREATE DATABASE bank_test OWNER bank'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_test')\gexec

