using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <summary>
    /// Búsquedas que ignoran los acentos. Deja en la base de datos las dos piezas
    /// que usan las consultas de personas, etiquetas, escenas, objetos, texto libre
    /// y OCR:
    ///
    /// - <c>photonne_unaccent(text)</c>, envoltorio de la extensión <c>unaccent</c>.
    ///   Se declara IMMUTABLE (unaccent() no lo es, porque el diccionario se puede
    ///   recargar) que es lo que permitiría indexarla más adelante; el diccionario
    ///   va explícito para no depender del search_path.
    /// - la configuración de búsqueda <c>photonne_unaccent</c>, copia de 'simple'
    ///   con el diccionario unaccent delante, para el tsvector del OCR.
    ///
    /// Crear extensiones pide superusuario. En una instalación con un usuario
    /// recortado eso NO puede tumbar el arranque (las migraciones corren solas al
    /// levantar), así que si falla se sigue adelante y la función se crea como
    /// identidad: las búsquedas quedan como estaban — insensibles a mayúsculas,
    /// sensibles a acentos — en vez de reventar en cada consulta.
    /// </summary>
    public partial class AddUnaccentSearch : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql(@"
DO $do$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS unaccent;
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'Photonne: no se pudo crear la extensión unaccent (%). Las búsquedas seguirán distinguiendo acentos.', SQLERRM;
    END;

    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'unaccent') THEN
        EXECUTE $fn$
            CREATE OR REPLACE FUNCTION photonne_unaccent(text) RETURNS text
            LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
            AS 'SELECT unaccent(''unaccent''::regdictionary, $1)'
        $fn$;
    ELSE
        EXECUTE $fn$
            CREATE OR REPLACE FUNCTION photonne_unaccent(text) RETURNS text
            LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
            AS 'SELECT $1'
        $fn$;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'photonne_unaccent') THEN
        EXECUTE 'CREATE TEXT SEARCH CONFIGURATION photonne_unaccent (COPY = simple)';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'unaccent') THEN
        EXECUTE 'ALTER TEXT SEARCH CONFIGURATION photonne_unaccent '
             || 'ALTER MAPPING FOR hword, hword_part, word WITH unaccent, simple';
    END IF;
END
$do$;
");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql(@"
DROP TEXT SEARCH CONFIGURATION IF EXISTS photonne_unaccent;
DROP FUNCTION IF EXISTS photonne_unaccent(text);
");
        }
    }
}
