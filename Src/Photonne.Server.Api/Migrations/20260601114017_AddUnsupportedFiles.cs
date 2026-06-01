using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddUnsupportedFiles : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "UnsupportedFiles",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    FileName = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: false),
                    FullPath = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: false),
                    FileSize = table.Column<long>(type: "bigint", nullable: false),
                    Extension = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    FileCreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    FileModifiedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    DiscoveredAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    OwnerId = table.Column<Guid>(type: "uuid", nullable: true),
                    FolderId = table.Column<Guid>(type: "uuid", nullable: true),
                    ExternalLibraryId = table.Column<Guid>(type: "uuid", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_UnsupportedFiles", x => x.Id);
                    table.ForeignKey(
                        name: "FK_UnsupportedFiles_ExternalLibraries_ExternalLibraryId",
                        column: x => x.ExternalLibraryId,
                        principalTable: "ExternalLibraries",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "FK_UnsupportedFiles_Folders_FolderId",
                        column: x => x.FolderId,
                        principalTable: "Folders",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "FK_UnsupportedFiles_Users_OwnerId",
                        column: x => x.OwnerId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateIndex(
                name: "IX_UnsupportedFiles_ExternalLibraryId",
                table: "UnsupportedFiles",
                column: "ExternalLibraryId");

            migrationBuilder.CreateIndex(
                name: "IX_UnsupportedFiles_FolderId",
                table: "UnsupportedFiles",
                column: "FolderId");

            migrationBuilder.CreateIndex(
                name: "IX_UnsupportedFiles_FullPath",
                table: "UnsupportedFiles",
                column: "FullPath",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_UnsupportedFiles_OwnerId",
                table: "UnsupportedFiles",
                column: "OwnerId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "UnsupportedFiles");
        }
    }
}
