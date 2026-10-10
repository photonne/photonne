using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddLocationSource : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "LocationSource",
                table: "AssetExifs",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            // Every existing row lands on None (0), including the ones that
            // already carry real GPS read from the file — the column simply
            // didn't exist when they were extracted.
            //
            // This backfill is not cosmetic. LocationSource.Exif (2) is what
            // makes a row usable as an ANCHOR for interpolation; leave them all
            // at None and there are no anchors, so the interpolation pass
            // quietly does nothing at all and no error is ever raised.
            migrationBuilder.Sql(
                """
                UPDATE "AssetExifs"
                SET "LocationSource" = 2
                WHERE "Latitude" IS NOT NULL AND "Longitude" IS NOT NULL;
                """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "LocationSource",
                table: "AssetExifs");
        }
    }
}
