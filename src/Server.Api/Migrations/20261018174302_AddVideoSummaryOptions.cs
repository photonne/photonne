using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddVideoSummaryOptions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "DurationSeconds",
                table: "VideoSummaries",
                type: "integer",
                nullable: false,
                defaultValue: 300);

            migrationBuilder.AddColumn<int>(
                name: "Orientation",
                table: "VideoSummaries",
                type: "integer",
                nullable: false,
                defaultValue: 0);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "DurationSeconds",
                table: "VideoSummaries");

            migrationBuilder.DropColumn(
                name: "Orientation",
                table: "VideoSummaries");
        }
    }
}
