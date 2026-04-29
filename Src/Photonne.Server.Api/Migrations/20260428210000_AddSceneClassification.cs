using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddSceneClassification : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTime>(
                name: "SceneClassificationCompletedAt",
                table: "Assets",
                type: "timestamp without time zone",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "SceneClassifications",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    Label = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    ClassId = table.Column<int>(type: "integer", nullable: false),
                    Confidence = table.Column<float>(type: "real", nullable: false),
                    Rank = table.Column<int>(type: "integer", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_SceneClassifications", x => x.Id);
                    table.ForeignKey(
                        name: "FK_SceneClassifications_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_SceneClassifications_AssetId",
                table: "SceneClassifications",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_SceneClassifications_Label",
                table: "SceneClassifications",
                column: "Label");

            migrationBuilder.CreateIndex(
                name: "IX_SceneClassifications_AssetId_Label",
                table: "SceneClassifications",
                columns: new[] { "AssetId", "Label" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(name: "SceneClassifications");

            migrationBuilder.DropColumn(
                name: "SceneClassificationCompletedAt",
                table: "Assets");
        }
    }
}
