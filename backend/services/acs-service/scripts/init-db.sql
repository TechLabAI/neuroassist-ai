-- scripts/init-db.sql
-- Inicializa schemas de tenant para desenvolvimento local

-- Schema do tenant de desenvolvimento
CREATE SCHEMA IF NOT EXISTS t_emp_dev;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_dev TO acs;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA t_emp_dev TO acs;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_dev TO acs;

-- Schema para isolamento de segundo tenant (testes de multi-tenancy)
CREATE SCHEMA IF NOT EXISTS t_emp_test;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO acs;
