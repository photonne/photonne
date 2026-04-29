using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddTextRecognition : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTime>(
                name: "TextRecognitionCompletedAt",
                table: "Assets",
                type: "timestamp without time zone",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "ExtractedTexts",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    Text = table.Column<string>(type: "text", nullable: false),
                    Confidence = table.Column<float>(type: "real", nullable: false),
                    BBoxX = table.Column<float>(type: "real", nullable: false),
                    BBoxY = table.Column<float>(type: "real", nullable: false),
                    BBoxWidth = table.Column<float>(type: "real", nullable: false),
                    BBoxHeight = table.Column<float>(type: "real", nullable: false),
                    LineIndex = table.Column<int>(type: "integer", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ExtractedTexts", x => x.Id);
                    table.ForeignKey(
                        name: "FK_ExtractedTexts_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_ExtractedTexts_AssetId",
                table: "ExtractedTexts",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_ExtractedTexts_AssetId_LineIndex",
                table: "ExtractedTexts",
                columns: new[] { "AssetId", "LineIndex" });

            // GIN full-text index over to_tsvector('simple', "Text"). The
            // 'simple' configuration intentionally avoids stemming so the
            // index works for any language the OCR returns (and keeps numeric
            // tokens like ticket numbers and serials searchable verbatim).
            // EF Core can't model expression GIN indexes, so this is raw SQL.
            migrationBuilder.Sql(
                "CREATE INDEX \"IX_ExtractedTexts_TextSearch\" " +
                "ON \"ExtractedTexts\" USING gin (to_tsvector('simple', \"Text\"));");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql("DROP INDEX IF EXISTS \"IX_ExtractedTexts_TextSearch\";");

            migrationBuilder.DropTable(name: "ExtractedTexts");

            migrationBuilder.DropColumn(
                name: "TextRecognitionCompletedAt",
                table: "Assets");
        }
    }
}
