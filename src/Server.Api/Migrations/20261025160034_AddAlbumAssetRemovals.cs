using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddAlbumAssetRemovals : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "AlbumAssetRemovals",
                columns: table => new
                {
                    AlbumId = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    Order = table.Column<int>(type: "integer", nullable: false),
                    AddedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    WasCover = table.Column<bool>(type: "boolean", nullable: false),
                    RemovedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    RemovedByUserId = table.Column<Guid>(type: "uuid", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_AlbumAssetRemovals", x => new { x.AlbumId, x.AssetId });
                    table.ForeignKey(
                        name: "FK_AlbumAssetRemovals_Albums_AlbumId",
                        column: x => x.AlbumId,
                        principalTable: "Albums",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_AlbumAssetRemovals_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_AlbumAssetRemovals_Users_RemovedByUserId",
                        column: x => x.RemovedByUserId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateIndex(
                name: "IX_AlbumAssetRemovals_AssetId",
                table: "AlbumAssetRemovals",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_AlbumAssetRemovals_RemovedAt",
                table: "AlbumAssetRemovals",
                column: "RemovedAt");

            migrationBuilder.CreateIndex(
                name: "IX_AlbumAssetRemovals_RemovedByUserId",
                table: "AlbumAssetRemovals",
                column: "RemovedByUserId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "AlbumAssetRemovals");
        }
    }
}
