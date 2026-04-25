using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Pgvector;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddFaceRecognition : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AlterDatabase()
                .Annotation("Npgsql:PostgresExtension:vector", ",,");

            migrationBuilder.AddColumn<DateTime>(
                name: "FaceDetectionCompletedAt",
                table: "Assets",
                type: "timestamp without time zone",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "People",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    OwnerId = table.Column<Guid>(type: "uuid", nullable: false),
                    Name = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    CoverFaceId = table.Column<Guid>(type: "uuid", nullable: true),
                    FaceCount = table.Column<int>(type: "integer", nullable: false),
                    IsHidden = table.Column<bool>(type: "boolean", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_People", x => x.Id);
                    table.ForeignKey(
                        name: "FK_People_Users_OwnerId",
                        column: x => x.OwnerId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "Faces",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    BoundingBoxX = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxY = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxW = table.Column<float>(type: "real", nullable: false),
                    BoundingBoxH = table.Column<float>(type: "real", nullable: false),
                    Confidence = table.Column<float>(type: "real", nullable: false),
                    Embedding = table.Column<Pgvector.Vector>(type: "vector(512)", nullable: false),
                    PersonId = table.Column<Guid>(type: "uuid", nullable: true),
                    IsManuallyAssigned = table.Column<bool>(type: "boolean", nullable: false),
                    IsRejected = table.Column<bool>(type: "boolean", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Faces", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Faces_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_Faces_People_PersonId",
                        column: x => x.PersonId,
                        principalTable: "People",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                });

            // FK is added after both tables exist to avoid circular dependency at create time.
            migrationBuilder.AddForeignKey(
                name: "FK_People_Faces_CoverFaceId",
                table: "People",
                column: "CoverFaceId",
                principalTable: "Faces",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);

            migrationBuilder.CreateIndex(
                name: "IX_Faces_AssetId",
                table: "Faces",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_Faces_PersonId",
                table: "Faces",
                column: "PersonId");

            migrationBuilder.CreateIndex(
                name: "IX_Faces_PersonId_IsRejected",
                table: "Faces",
                columns: new[] { "PersonId", "IsRejected" });

            migrationBuilder.CreateIndex(
                name: "IX_People_OwnerId",
                table: "People",
                column: "OwnerId");

            migrationBuilder.CreateIndex(
                name: "IX_People_OwnerId_IsHidden",
                table: "People",
                columns: new[] { "OwnerId", "IsHidden" });

            migrationBuilder.CreateIndex(
                name: "IX_People_CoverFaceId",
                table: "People",
                column: "CoverFaceId");

            // HNSW index for fast cosine-distance face similarity search.
            // m=16, ef_construction=64 are pgvector defaults — good recall/build-time tradeoff.
            migrationBuilder.Sql(@"CREATE INDEX IF NOT EXISTS ""IX_Faces_Embedding_HNSW"" ON ""Faces"" USING hnsw (""Embedding"" vector_cosine_ops) WITH (m = 16, ef_construction = 64);");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql(@"DROP INDEX IF EXISTS ""IX_Faces_Embedding_HNSW"";");

            migrationBuilder.DropForeignKey(
                name: "FK_People_Faces_CoverFaceId",
                table: "People");

            migrationBuilder.DropTable(name: "Faces");
            migrationBuilder.DropTable(name: "People");

            migrationBuilder.DropColumn(
                name: "FaceDetectionCompletedAt",
                table: "Assets");

            migrationBuilder.AlterDatabase()
                .OldAnnotation("Npgsql:PostgresExtension:vector", ",,");
        }
    }
}
