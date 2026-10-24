using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddPinPositions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "Position",
                table: "FolderPins",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "Position",
                table: "AlbumPins",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            // Existing pins keep the order they had (the last pinned first),
            // numbered per user across both tables.
            migrationBuilder.Sql("""
                WITH ranked AS (
                    SELECT "Kind", "UserId", "Id",
                           ROW_NUMBER() OVER (PARTITION BY "UserId" ORDER BY "PinnedAt" DESC) - 1 AS "Position"
                    FROM (
                        SELECT 'album' AS "Kind", "UserId", "AlbumId" AS "Id", "PinnedAt" FROM "AlbumPins"
                        UNION ALL
                        SELECT 'folder', "UserId", "FolderId", "PinnedAt" FROM "FolderPins"
                    ) AS pins
                )
                UPDATE "AlbumPins" AS p SET "Position" = r."Position"
                FROM ranked AS r
                WHERE r."Kind" = 'album' AND r."UserId" = p."UserId" AND r."Id" = p."AlbumId";
                """);
            migrationBuilder.Sql("""
                WITH ranked AS (
                    SELECT "Kind", "UserId", "Id",
                           ROW_NUMBER() OVER (PARTITION BY "UserId" ORDER BY "PinnedAt" DESC) - 1 AS "Position"
                    FROM (
                        SELECT 'album' AS "Kind", "UserId", "AlbumId" AS "Id", "PinnedAt" FROM "AlbumPins"
                        UNION ALL
                        SELECT 'folder', "UserId", "FolderId", "PinnedAt" FROM "FolderPins"
                    ) AS pins
                )
                UPDATE "FolderPins" AS p SET "Position" = r."Position"
                FROM ranked AS r
                WHERE r."Kind" = 'folder' AND r."UserId" = p."UserId" AND r."Id" = p."FolderId";
                """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Position",
                table: "FolderPins");

            migrationBuilder.DropColumn(
                name: "Position",
                table: "AlbumPins");
        }
    }
}
