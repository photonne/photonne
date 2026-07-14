using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddMemories : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "Memories",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    OwnerId = table.Column<Guid>(type: "uuid", nullable: false),
                    Kind = table.Column<int>(type: "integer", nullable: false),
                    Title = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    Subtitle = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    CoverAssetId = table.Column<Guid>(type: "uuid", nullable: true),
                    WindowStart = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    WindowEnd = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    AssetCount = table.Column<int>(type: "integer", nullable: false),
                    Score = table.Column<double>(type: "double precision", nullable: false),
                    DedupeKey = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    FirstGeneratedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    LastGeneratedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    IsDismissed = table.Column<bool>(type: "boolean", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Memories", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Memories_Assets_CoverAssetId",
                        column: x => x.CoverAssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "FK_Memories_Users_OwnerId",
                        column: x => x.OwnerId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "MemoryAssets",
                columns: table => new
                {
                    MemoryId = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    Position = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_MemoryAssets", x => new { x.MemoryId, x.AssetId });
                    table.ForeignKey(
                        name: "FK_MemoryAssets_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_MemoryAssets_Memories_MemoryId",
                        column: x => x.MemoryId,
                        principalTable: "Memories",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_Memories_CoverAssetId",
                table: "Memories",
                column: "CoverAssetId");

            migrationBuilder.CreateIndex(
                name: "IX_Memories_OwnerId_DedupeKey",
                table: "Memories",
                columns: new[] { "OwnerId", "DedupeKey" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Memories_OwnerId_IsDismissed_Score",
                table: "Memories",
                columns: new[] { "OwnerId", "IsDismissed", "Score" },
                descending: new[] { false, false, true });

            migrationBuilder.CreateIndex(
                name: "IX_MemoryAssets_AssetId",
                table: "MemoryAssets",
                column: "AssetId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "MemoryAssets");

            migrationBuilder.DropTable(
                name: "Memories");
        }
    }
}
