using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class RenameMlJobToEnrichmentTask : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "AssetMlJobs");

            migrationBuilder.CreateTable(
                name: "AssetEnrichmentTasks",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    TaskType = table.Column<int>(type: "integer", nullable: false),
                    Status = table.Column<int>(type: "integer", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    StartedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: true),
                    CompletedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: true),
                    ErrorMessage = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: true),
                    ResultJson = table.Column<string>(type: "text", nullable: true),
                    AttemptCount = table.Column<int>(type: "integer", nullable: false),
                    NextRetryAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_AssetEnrichmentTasks", x => x.Id);
                    table.ForeignKey(
                        name: "FK_AssetEnrichmentTasks_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_AssetEnrichmentTasks_AssetId",
                table: "AssetEnrichmentTasks",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_AssetEnrichmentTasks_AssetId_TaskType_Status",
                table: "AssetEnrichmentTasks",
                columns: new[] { "AssetId", "TaskType", "Status" });

            migrationBuilder.CreateIndex(
                name: "IX_AssetEnrichmentTasks_Status_NextRetryAt",
                table: "AssetEnrichmentTasks",
                columns: new[] { "Status", "NextRetryAt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "AssetEnrichmentTasks");

            migrationBuilder.CreateTable(
                name: "AssetMlJobs",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AssetId = table.Column<Guid>(type: "uuid", nullable: false),
                    CompletedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: true),
                    CreatedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: false),
                    ErrorMessage = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: true),
                    JobType = table.Column<int>(type: "integer", nullable: false),
                    ResultJson = table.Column<string>(type: "text", nullable: true),
                    StartedAt = table.Column<DateTime>(type: "timestamp without time zone", nullable: true),
                    Status = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_AssetMlJobs", x => x.Id);
                    table.ForeignKey(
                        name: "FK_AssetMlJobs_Assets_AssetId",
                        column: x => x.AssetId,
                        principalTable: "Assets",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_AssetMlJobs_AssetId",
                table: "AssetMlJobs",
                column: "AssetId");

            migrationBuilder.CreateIndex(
                name: "IX_AssetMlJobs_AssetId_JobType_Status",
                table: "AssetMlJobs",
                columns: new[] { "AssetId", "JobType", "Status" });
        }
    }
}
