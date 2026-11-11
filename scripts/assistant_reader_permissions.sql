\set ON_ERROR_STOP on
SELECT :'assistant_login'<>current_user AS distinto \gset
\if :distinto
\else
\echo 'Usa una cuenta nueva y exclusiva para lectura.'
\quit 1
\endif
SELECT format('GRANT USAGE ON SCHEMA %I TO %I',current_schema(),:'assistant_login') \gexec
SELECT format('GRANT SELECT ON assistant_user_profile,assistant_package,assistant_location TO %I',:'assistant_login') \gexec
