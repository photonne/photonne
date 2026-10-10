using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddPlaces : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "GeocodeDistanceMeters",
                table: "AssetExifs",
                type: "integer",
                nullable: true);

            migrationBuilder.AddColumn<DateTime>(
                name: "GeocodedAt",
                table: "AssetExifs",
                type: "timestamp without time zone",
                nullable: true);

            migrationBuilder.AddColumn<Guid>(
                name: "PlaceId",
                table: "AssetExifs",
                type: "uuid",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "Places",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    GeonameId = table.Column<int>(type: "integer", nullable: false),
                    Name = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    CountryCode = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false),
                    Latitude = table.Column<double>(type: "double precision", nullable: false),
                    Longitude = table.Column<double>(type: "double precision", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Places", x => x.Id);
                });

            migrationBuilder.CreateIndex(
                name: "IX_AssetExifs_GeocodedAt",
                table: "AssetExifs",
                column: "GeocodedAt");

            migrationBuilder.CreateIndex(
                name: "IX_AssetExifs_PlaceId",
                table: "AssetExifs",
                column: "PlaceId");

            migrationBuilder.CreateIndex(
                name: "IX_Places_GeonameId",
                table: "Places",
                column: "GeonameId",
                unique: true);

            migrationBuilder.AddForeignKey(
                name: "FK_AssetExifs_Places_PlaceId",
                table: "AssetExifs",
                column: "PlaceId",
                principalTable: "Places",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_AssetExifs_Places_PlaceId",
                table: "AssetExifs");

            migrationBuilder.DropTable(
                name: "Places");

            migrationBuilder.DropIndex(
                name: "IX_AssetExifs_GeocodedAt",
                table: "AssetExifs");

            migrationBuilder.DropIndex(
                name: "IX_AssetExifs_PlaceId",
                table: "AssetExifs");

            migrationBuilder.DropColumn(
                name: "GeocodeDistanceMeters",
                table: "AssetExifs");

            migrationBuilder.DropColumn(
                name: "GeocodedAt",
                table: "AssetExifs");

            migrationBuilder.DropColumn(
                name: "PlaceId",
                table: "AssetExifs");
        }
    }
}
