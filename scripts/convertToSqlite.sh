#!/bin/bash

echo "Started conversion of local PostgreSQL BiGGDB to SQLite..." \
&& pg_dump --schema-only bigg > bigg_schema.sql \
&& echo "Finished dumping PostgreSQL DB schema" \
&& pg_dump --data-only --column-inserts bigg > bigg.sql \
&& echo "Finished dumping PostgreSQL DB data." \
&& python cleanup_dump.py \
&& echo "Finished cleanup for migration to SQLite." \
&& sqlite3 "" ".read schema.sql" ".read converted.sql" ".save bigg.sqlite" \
&& echo "Finished creating SQLite DB." \
&& mv bigg.sqlite ../resources/edu/ucsd/sbrg/bigg \
&& echo "Removing temporary files..." \
&& rm bigg.sql converted.sql bigg_schema.sql schema.sql \
&& echo "Finished."
