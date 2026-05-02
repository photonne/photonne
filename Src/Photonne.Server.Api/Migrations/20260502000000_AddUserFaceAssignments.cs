using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddUserFaceAssignments : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "UserFaceAssignments",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    FaceId = table.Column<Guid>(type: "uuid", nullable: false),
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    PersonId = table.Column<Guid>(type: "uuid", nullable: true),
                    IsManuallyAssigned = table.Column<bool>(type: "boolean", nullable: false),
                    IsRejected = table.Column<bool>(type: "boolean", nullable: false),
                    SuggestedPersonId = table.Column<Guid>(type: "uuid", nullable: true),
                    SuggestedDistance = table.Column<float>(type: "real", nullable: true),
                    UpdatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_UserFaceAssignments", x => x.Id);
                    table.ForeignKey(
                        name: "FK_UserFaceAssignments_Faces_FaceId",
                        column: x => x.FaceId,
                        principalTable: "Faces",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_UserFaceAssignments_Users_UserId",
                        column: x => x.UserId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_UserFaceAssignments_People_PersonId",
                        column: x => x.PersonId,
                        principalTable: "People",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "FK_UserFaceAssignments_People_SuggestedPersonId",
                        column: x => x.SuggestedPersonId,
                        principalTable: "People",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_FaceId_UserId",
                table: "UserFaceAssignments",
                columns: new[] { "FaceId", "UserId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_UserId_PersonId",
                table: "UserFaceAssignments",
                columns: new[] { "UserId", "PersonId" });

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_UserId_PersonId_IsRejected",
                table: "UserFaceAssignments",
                columns: new[] { "UserId", "PersonId", "IsRejected" });

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_UserId_SuggestedPersonId",
                table: "UserFaceAssignments",
                columns: new[] { "UserId", "SuggestedPersonId" });

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_UserId",
                table: "UserFaceAssignments",
                column: "UserId");

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_PersonId",
                table: "UserFaceAssignments",
                column: "PersonId");

            migrationBuilder.CreateIndex(
                name: "IX_UserFaceAssignments_SuggestedPersonId",
                table: "UserFaceAssignments",
                column: "SuggestedPersonId");

            // Backfill: copy each existing Face's owner-side identity (PersonId,
            // IsManuallyAssigned, IsRejected, SuggestedPersonId, SuggestedDistance)
            // into a UserFaceAssignment row owned by the asset's owner. The legacy
            // columns on Faces stay in place so older app versions can still read
            // them; new code reads/writes only this table. gen_random_uuid() is
            // available out of the box on PostgreSQL ≥ 13.
            migrationBuilder.Sql(@"
                INSERT INTO ""UserFaceAssignments""
                    (""Id"", ""FaceId"", ""UserId"", ""PersonId"", ""IsManuallyAssigned"",
                     ""IsRejected"", ""SuggestedPersonId"", ""SuggestedDistance"", ""UpdatedAt"")
                SELECT
                    gen_random_uuid(),
                    f.""Id"",
                    a.""OwnerId"",
                    f.""PersonId"",
                    f.""IsManuallyAssigned"",
                    f.""IsRejected"",
                    f.""SuggestedPersonId"",
                    f.""SuggestedDistance"",
                    NOW() AT TIME ZONE 'UTC'
                FROM ""Faces"" f
                JOIN ""Assets"" a ON a.""Id"" = f.""AssetId""
                WHERE a.""OwnerId"" IS NOT NULL
                  AND (
                      f.""PersonId"" IS NOT NULL
                      OR f.""IsManuallyAssigned"" = TRUE
                      OR f.""IsRejected"" = TRUE
                      OR f.""SuggestedPersonId"" IS NOT NULL
                  );
            ");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "UserFaceAssignments");
        }
    }
}
