using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddAssetCapturedAt : Migration
    {
        // Reuse the partial-index name from AddTimelineAssetIndex so we can swap
        // the indexed column from FileCreatedAt to CapturedAt without leaving an
        // orphaned legacy index behind. CapturedAt is the EXIF-derived display
        // timestamp; FileCreatedAt is unreliable on Linux hosts where mtime/ctime
        // get rewritten when assets move between volumes.
        private const string IndexName = "IX_Assets_Timeline";

        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTime>(
                name: "CapturedAt",
                table: "Assets",
                type: "timestamp without time zone",
                nullable: false,
                defaultValue: new DateTime(1, 1, 1, 0, 0, 0, 0, DateTimeKind.Unspecified));

            // Backfill: prefer EXIF DateTimeOriginal (when already extracted),
            // fall back to FileCreatedAt. Rows whose EXIF hasn't been extracted
            // yet keep FileCreatedAt as a placeholder; the enrichment worker
            // will overwrite CapturedAt once it runs.
            migrationBuilder.Sql("""
                UPDATE "Assets" a
                SET "CapturedAt" = COALESCE(e."DateTimeOriginal", a."FileCreatedAt")
                FROM "AssetExifs" e
                WHERE e."AssetId" = a."Id";
            """);

            migrationBuilder.Sql("""
                UPDATE "Assets"
                SET "CapturedAt" = "FileCreatedAt"
                WHERE "CapturedAt" = '0001-01-01 00:00:00';
            """);

            // Swap the timeline index off FileCreatedAt and onto CapturedAt so
            // the hot ORDER BY CapturedAt DESC cursor scan stays index-only.
            migrationBuilder.Sql($"""DROP INDEX IF EXISTS "{IndexName}";""");
            migrationBuilder.Sql($"""
                CREATE INDEX IF NOT EXISTS "{IndexName}"
                ON "Assets" ("FolderId", "CapturedAt" DESC, "FileModifiedAt" DESC)
                WHERE "DeletedAt" IS NULL AND "IsArchived" = false;
            """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql($"""DROP INDEX IF EXISTS "{IndexName}";""");
            migrationBuilder.Sql($"""
                CREATE INDEX IF NOT EXISTS "{IndexName}"
                ON "Assets" ("FolderId", "FileCreatedAt" DESC, "FileModifiedAt" DESC)
                WHERE "DeletedAt" IS NULL AND "IsArchived" = false;
            """);

            migrationBuilder.DropColumn(
                name: "CapturedAt",
                table: "Assets");
        }
    }
}
