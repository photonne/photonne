using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddEnrichmentTaskLivenessIndex : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateIndex(
                name: "IX_AssetEnrichmentTasks_TaskType_Status_CompletedAt",
                table: "AssetEnrichmentTasks",
                columns: new[] { "TaskType", "Status", "CompletedAt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_AssetEnrichmentTasks_TaskType_Status_CompletedAt",
                table: "AssetEnrichmentTasks");
        }
    }
}
