using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddObjectRecognition : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTime>(
                name: "ObjectRecognitionCompletedAt",
                table: "Assets",
                type: "timestamp without time zone",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "ObjectDetections",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    Label = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    ClassId = table.Column<int>(type: "integer", nullable: false),
                    Confidence = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxX = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxY = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxW = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxH = table.Column<float>(type: "real", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ObjectDetections", x => x.Id);
                    table.ForeignKey(
                        name: "FK_ObjectDetections_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_ObjectDetections_AssetId",
                table: "ObjectDetections",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_ObjectDetections_Label",
                table: "ObjectDetections",
                column: "Label");

            migrationBuilder.CreateIndex(
                name: "IX_ObjectDetections_AssetId_Label",
                table: "ObjectDetections",
                columns: new[] { "AssetId", "Label" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(name: "ObjectDetections");

            migrationBuilder.DropColumn(
                name: "ObjectRecognitionCompletedAt",
                table: "Assets");
        }
    }
}
