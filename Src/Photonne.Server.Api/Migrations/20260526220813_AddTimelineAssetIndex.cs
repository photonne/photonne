using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddTimelineAssetIndex : Migration
    {
        // Partial index tuned for the hot timeline query: filter on
        // FolderId IN (...) for visible folders, then order by FileCreatedAt DESC.
        // Only indexes the "live" rows (DeletedAt IS NULL AND NOT IsArchived) so
        // it stays compact regardless of trash / archive growth, and the
        // FileCreatedAt DESC ordering matches the cursor-paginated read pattern.
        // Hand-written because EF's HasIndex cannot model the partial WHERE
        // clause or DESC column ordering.
        private const string IndexName = "IX_Assets_Timeline";

        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql($"""
                CREATE INDEX IF NOT EXISTS "{IndexName}"
                ON "Assets" ("FolderId", "FileCreatedAt" DESC, "FileModifiedAt" DESC)
                WHERE "DeletedAt" IS NULL AND "IsArchived" = false;
            """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql($"""DROP INDEX IF EXISTS "{IndexName}";""");
        }
    }
}
