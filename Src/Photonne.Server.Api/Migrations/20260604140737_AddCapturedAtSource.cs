using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddCapturedAtSource : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "CapturedAtSource",
                table: "Assets",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            // Backfill provenance for existing rows: a CapturedAt that matches
            // the stored EXIF DateTimeOriginal came from EXIF (source = 2);
            // everything else keeps the FileSystem default (0). Inferred (1)
            // and Manual (3) can only be produced going forward.
            migrationBuilder.Sql("""
                UPDATE "Assets" a
                SET "CapturedAtSource" = 2
                FROM "AssetExifs" e
                WHERE e."AssetId" = a."Id"
                  AND e."DateTimeOriginal" IS NOT NULL
                  AND a."CapturedAt" = e."DateTimeOriginal";
            """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "CapturedAtSource",
                table: "Assets");
        }
    }
}
